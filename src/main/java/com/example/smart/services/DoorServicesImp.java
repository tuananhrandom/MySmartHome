package com.example.smart.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.smart.entities.Door;
import com.example.smart.repositories.DoorRepositories;
import com.example.smart.repositories.UserRepository;
import com.example.smart.websocket.ClientWebSocketHandler;
import com.example.smart.websocket.DoorSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DoorServicesImp implements DoorServices {
    @Autowired
    DeviceActivityService deviceActivityService;
    @Autowired
    DoorRepositories doorRepo;
    @Autowired
    UserRepository userRepo;
    @Autowired
    ClientWebSocketHandler clientWebSocketHandler;
    @Autowired
    DoorSocketHandler doorSocketHandler;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Override
    public List<Door> getAllDoor() {
        return doorRepo.findAll();
    }

    @Override
    public void userRemoveDoor(Long doorId, Long userId) {
        Door selectedDoor = doorRepo.findById(userId).get();
        // tránh trường hợp một api từ user khác xóa door của user khác
        if (selectedDoor.getUser().getUserId() == userId) {
            selectedDoor.setUser(null);
        }
        doorRepo.save(selectedDoor);
        try {
            doorSocketHandler.sendControlSignal(doorId, "ownerId:" + -1);
        } catch (Exception e) {
            throw new IllegalArgumentException("Door not found");
        }
    }

    @Override
    public List<Door> getDoorByUserId(Long userId) {
        return doorRepo.findByUser_UserId(userId);

    }

    // update từ ESP32
    @Override
    public void updateDoorStatus(Long doorId, Integer doorStatus, Integer doorLockDown, String doorIp, Long ownerId) {
        Door selectedDoor = doorRepo.findById(doorId)
                .orElseThrow(() -> new IllegalArgumentException("Door not found"));
        Integer oldStatus = selectedDoor.getDoorStatus();
        Integer oldAlarmStatus = selectedDoor.getDoorLockDown();
        selectedDoor.setDoorStatus(doorStatus);
        selectedDoor.setDoorIp(doorIp);
        selectedDoor.setDoorLockDown(doorLockDown);
        if (ownerId == -1) {
            selectedDoor.setUser(null);
        } else {
            selectedDoor.setUser(userRepo.findById(ownerId).get());
        }

        // Lưu thay đổi vào database
        doorRepo.save(selectedDoor);

        // Gửi thông báo đến client qua WebSocket nếu có ClientWebSocketHandler
        if (clientWebSocketHandler != null && selectedDoor.getUser() != null) {
            clientWebSocketHandler.notifyDoorUpdate(selectedDoor);
        }

        // Nếu doorStatus hoặc doorLockDown là null, thì đây là trường hợp thiết bị
        // offline
        if (doorStatus == null || doorLockDown == null) {
            deviceActivityService.logDoorActivity(doorId, "DISCONNECT", oldStatus, null, oldAlarmStatus,
                    null, null, null, doorIp, ownerId);
        } else {
            // lưu log cho thay đổi trạng thái
            if (oldStatus != null && doorStatus != oldStatus) {
                String activityType = doorStatus == 1 ? "OPEN" : "CLOSE";
                deviceActivityService.logDoorActivity(doorId, activityType, oldStatus, doorStatus, null,
                        null, null, null, doorIp, ownerId);
            }

            // lưu log cho thay đổi trạng thái báo động
            if (oldAlarmStatus != null && doorLockDown != oldAlarmStatus) {
                String activityType = doorLockDown == 1 ? "ALARM_ON" : "ALARM_OFF";
                deviceActivityService.logDoorActivity(doorId, activityType, null, null, oldAlarmStatus,
                        doorLockDown, null, null, doorIp, ownerId);
            }
        }
    }

    @Override
    public void userAddDoor(Long doorId, Long userId, String doorName) {
        // Kiểm tra cửa tồn tại và chưa có chủ sở hữu
        if (doorRepo.existsById(doorId) && doorRepo.findById(doorId).get().getUser() == null) {
            Door thisDoor = doorRepo.findById(doorId).get();

            try {
                // Gửi yêu cầu thay đổi chủ sở hữu đến ESP32 và đợi phản hồi
                CompletableFuture<Boolean> response = doorSocketHandler.sendControlSignalWithResponse(doorId,
                        "ownerId:" + userId, "ownerId");

                // Đợi kết quả từ ESP32 (với timeout 10 giây đã được xử lý trong phương thức)
                boolean accepted = response.get(); // Sẽ chờ tối đa 10 giây

                if (accepted) {
                    // Nếu ESP32 chấp nhận, lưu thông tin vào database
                    thisDoor.setUser(userRepo.findById(userId).get());
                    thisDoor.setDoorName(doorName);
                    doorRepo.save(thisDoor);

                    // Thông báo đến client
                    if (clientWebSocketHandler != null) {
                        clientWebSocketHandler.notifyDoorUpdate(thisDoor);
                    }

                    System.out.println("ESP32 accepted ownership change for door: " + doorId);
                } else {
                    // Nếu ESP32 từ chối hoặc timeout
                    throw new IllegalStateException("ESP32 rejected ownership change or did not respond");
                }
            } catch (Exception e) {
                // Xử lý ngoại lệ (có thể do mất kết nối, timeout, vv)
                throw new IllegalStateException("Failed to communicate with ESP32: " + e.getMessage(), e);
            }
        } else if (doorRepo.existsById(doorId) && doorRepo.findById(doorId).get().getUser().getUserId() == userId) {
            Door thisdoor = doorRepo.findById(doorId).get();
            thisdoor.setDoorName(doorName);
            doorRepo.save(thisdoor);
            clientWebSocketHandler.notifyDoorUpdate(thisdoor);
        } else {
            throw new IllegalArgumentException("door not found or already owned");
        }
    }

    @Override
    public void updateDoorAlert(Long doorId, Integer doorAlert) {
        Door thisDoor = doorRepo.findById(doorId).get();
        if (thisDoor != null) {
            thisDoor.setDoorAlert(doorAlert);
            doorRepo.save(thisDoor);
            clientWebSocketHandler.notifyDoorUpdate(thisDoor);
        } else {
            System.err.println("Can't find Door");
        }
    }

    // user bật tắt báo động cửa
    @Override
    public void toggleDoorAlarm(Long doorId, Long userId) {
        Door thisDoor = doorRepo.findById(doorId).orElseThrow(() -> new IllegalArgumentException("Door not found"));
        if (thisDoor.getUser() != null && thisDoor.getUser().getUserId() == userId) {
            thisDoor.setDoorLockDown(thisDoor.getDoorLockDown() == 1 ? 0 : 1);
            Integer doorLockDown = thisDoor.getDoorLockDown();
            String doorIp = thisDoor.getDoorIp();
            Long ownerId = thisDoor.getOwnerId();
            doorRepo.save(thisDoor);
            try {
                doorSocketHandler.sendControlSignal(doorId, "doorLockDown:" + thisDoor.getDoorLockDown());
                clientWebSocketHandler.notifyDoorUpdate(thisDoor);
                // lưu log
                String activityType = doorLockDown == 1 ? "ALARM_ON" : "ALARM_OFF";
                deviceActivityService.logDoorActivity(doorId, activityType, null, null, null,
                        null,
                        null, null, doorIp, ownerId);
            } catch (Exception e) {
                throw new RuntimeException("Can't send control signal to door");
            }
        }
    }

    // admin thêm một thiết bị cửa mới vào db
    @Override
    public void adminAddNewDoor(Long doorId) {
        Door newDoor = new Door();
        newDoor.setDoorId(doorId);
        newDoor.setDoorName(null);
        newDoor.setDoorStatus(null);
        newDoor.setDoorIp(null);
        newDoor.setUser(null);
        newDoor.setDoorLockDown(0);
        doorRepo.save(newDoor);
    }

    @Override
    public Door findByDoorName(String doorName) {
        return doorRepo.findByDoorName(doorName);
    }

    @Override
    public Door findByDoorId(Long doorId) {
        return doorRepo.findById(doorId).orElseThrow(() -> new IllegalArgumentException("Door not found"));
    }

    @Override
    public boolean nameIsExist(String doorName) {
        return doorRepo.findByDoorName(doorName) != null;
    }

    @Override
    public boolean idIsExist(Long doorId) {
        return doorRepo.findById(doorId).isPresent();
    }

    @Override
    public boolean ipIsExist(String doorIp) {
        return doorRepo.findByDoorIp(doorIp) != null;
    }

    @Override
    public Door findByDoorIp(String doorIp) {
        return doorRepo.findByDoorIp(doorIp);
    }

    @Override
    public void deleteDoor(Long doorId) {
        Door selected = doorRepo.findById(doorId).get();
        doorRepo.delete(selected);
    }

}

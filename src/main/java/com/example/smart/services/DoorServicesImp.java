package com.example.smart.services;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    @Autowired
    NotificationService notificationService;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Override
    public List<Door> getAllDoor() {
        return doorRepo.findAll();
    }

    @Override
    public void userRemoveDoor(Long doorId, Long userId) {
        Door selectedDoor = doorRepo.findById(doorId).get();
        // tránh trường hợp một api từ user khác xóa door của user khác
        if (selectedDoor.getUser().getUserId().equals(userId)) {
            selectedDoor.setUser(null);
        }
        // xóa lịch sử
        deviceActivityService.deleteDeviceActivities("DOOR", doorId);
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

            // Tạo thông báo khi cửa bị mất kết nối
            if (selectedDoor.getUser() != null) {
                notificationService.createNotification(
                        "DOOR",
                        "Mất kết nối thiết bị",
                        "Cửa " + selectedDoor.getDoorName() + " đã mất kết nối với hệ thống",
                        selectedDoor.getUser().getUserId());
            }
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
        } else if (doorRepo.existsById(doorId)
                && doorRepo.findById(doorId).get().getUser().getUserId().equals(userId)) {
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
        try {
            System.out.println("Attempting to toggle alarm for door: " + doorId + " by user: " + userId);

            Door thisDoor = doorRepo.findById(doorId).orElseThrow(() -> {
                System.err.println("Door not found with ID: " + doorId);
                return new IllegalArgumentException("Door not found");
            });

            System.out.println("Found door: " + thisDoor.getDoorName() + " with current alarm status: "
                    + thisDoor.getDoorLockDown());

            if (thisDoor.getUser() == null) {
                System.err.println("Door " + doorId + " has no owner");
                throw new IllegalArgumentException("Door has no owner");
            }

            if (!thisDoor.getUser().getUserId().equals(userId)) {
                System.err.println("User " + userId + " is not the owner of door " + doorId);
                throw new IllegalArgumentException("User is not the owner of this door");
            }

            // Đảo trạng thái báo động
            Integer newLockDown = thisDoor.getDoorLockDown() == 1 ? 0 : 1;
            System.out.println("Changing door alarm status from " + thisDoor.getDoorLockDown() + " to " + newLockDown);

            thisDoor.setDoorLockDown(newLockDown);
            String doorIp = thisDoor.getDoorIp();
            Long ownerId = thisDoor.getOwnerId();

            doorRepo.save(thisDoor);
            System.out.println("Saved new door status to database");

            try {
                String command = "doorLockDown:" + newLockDown;
                System.out.println("Sending command to door: " + command);
                doorSocketHandler.sendControlSignal(doorId, command);

                clientWebSocketHandler.notifyDoorUpdate(thisDoor);
                System.out.println("Notified clients of door update");

                String activityType = newLockDown == 1 ? "ALARM_ON" : "ALARM_OFF";
                deviceActivityService.logDoorActivity(doorId, activityType, null, null, null, null, null, null, doorIp,
                        ownerId);
                System.out.println("Logged door activity: " + activityType);

            } catch (Exception e) {
                System.err.println("Error sending control signal to door: " + e.getMessage());
                throw new RuntimeException("Failed to send control signal to door: " + e.getMessage());
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid door operation: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Unexpected error in toggleDoorAlarm: " + e.getMessage());
            throw new RuntimeException("Failed to toggle door alarm: " + e.getMessage());
        }
    }

    // admin thêm người dùng vào cửa
    @Override
    public void adminAddUserToDoor(Long doorId, Long userId) {
        Door door = doorRepo.findById(doorId).orElseThrow(() -> new IllegalArgumentException("Door not found"));
        door.setUser(userRepo.findById(userId).get());
        door.setDoorName("");
        doorRepo.save(door);
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
        newDoor.setDoorLockDown(null);
        newDoor.setCreatedTime(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
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
        deviceActivityService.deleteDeviceActivities("DOOR", doorId);
        doorRepo.delete(selected);
    }

    @Override
    public Door getDoorById(Long id) {
        return doorRepo.findById(id).orElse(null);
    }

    @Override
    public List<Door> getDoorsByRange(Long start, Long end) {
        return doorRepo.findByDoorIdBetween(start, end);
    }

    @Override
    public void updateDoor(Door door) {
        doorRepo.save(door);
        if (clientWebSocketHandler != null && door.getUser() != null) {
            clientWebSocketHandler.notifyDoorUpdate(door);
        }
    }

    @Override
    public List<Door> getDoorsByDateRange(LocalDateTime start, LocalDateTime end) {
        return doorRepo.findByCreatedTimeBetween(start, end);
    }
}

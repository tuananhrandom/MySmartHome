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
        Door thisDoor = doorRepo.findById(doorId).orElseThrow(() -> new IllegalArgumentException("Door not found"));
        if (thisDoor.getUser() != null && thisDoor.getUser().getUserId() == userId) {
            thisDoor.setUser(null);
            doorRepo.save(thisDoor);
        }
    }
    @Override
    public List<Door> getDoorByUserId(Long userId) {
         return doorRepo.findByUser_UserId(userId);
        
    }

    @Override
    public void updateDoorStatus(Long doorId, Integer doorStatus, Integer doorLockDown, String doorIp) {
        Door selectedDoor = doorRepo.findById(doorId)
                .orElseThrow(() -> new IllegalArgumentException("Door not found"));
        selectedDoor.setDoorStatus(doorStatus);
        selectedDoor.setDoorIp(doorIp);
        selectedDoor.setDoorLockDown(doorLockDown);
        doorRepo.save(selectedDoor);
    }

    @Override
    public void userAddDoor(Long doorId, Long userId, String doorName) {
        // Kiểm tra đèn tồn tại và chưa có chủ sở hữu
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

                    System.out.println("ESP32 accepted ownership change for light: " + doorId);
                } else {
                    // Nếu ESP32 từ chối hoặc timeout
                    throw new IllegalStateException("ESP32 rejected ownership change or did not respond");
                }
            } catch (Exception e) {
                // Xử lý ngoại lệ (có thể do mất kết nối, timeout, vv)
                throw new IllegalStateException("Failed to communicate with ESP32: " + e.getMessage(), e);
            }
        } else {
            throw new IllegalArgumentException("Light not found or already owned");
        }
    }
    // user bật tắt báo động cửa
    @Override
    public void toggleDoorAlarm(Long doorId, Long userId) {
        Door thisDoor = doorRepo.findById(doorId).orElseThrow(() -> new IllegalArgumentException("Door not found"));
        if (thisDoor.getUser() != null && thisDoor.getUser().getUserId() == userId) {
            thisDoor.setDoorLockDown(thisDoor.getDoorLockDown() == 1 ? 0 : 1);
            doorRepo.save(thisDoor);
            try {
                doorSocketHandler.sendControlSignal(doorId, "doorLockDown:" + thisDoor.getDoorLockDown());
                clientWebSocketHandler.notifyDoorUpdate(thisDoor);
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

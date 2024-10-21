package com.example.smart.services;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.smart.entities.Door;
import com.example.smart.entities.Notification;
import com.example.smart.repositories.DoorRepositories;
import com.example.smart.repositories.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DoorServicesImp implements DoorServices {
    @Autowired
    DoorRepositories doorRepo;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    NotificationServiceImp notificationService;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Override
    public List<Door> getAllDoor() {
        return doorRepo.findAll();
    }

    @Override
    public void updateDoorStatus(Long doorId, Integer doorStatus, Integer doorLockDown, String doorIp) {
        Door selectedDoor = doorRepo.findById(doorId)
                .orElseThrow(() -> new IllegalArgumentException("Door not found"));

        selectedDoor.setDoorStatus(doorStatus);
        selectedDoor.setDoorIp(doorIp);
        selectedDoor.setDoorLockDown(doorLockDown);

        // Tự động cập nhật doorAlert nếu doorStatus = 1 và doorLockDown = 1
        if (doorStatus == 1 && doorLockDown == 1) {
            selectedDoor.setDoorAlert(1); // doorAlert sẽ bằng 1
            doorRepo.save(selectedDoor);
            sendSseEvent(selectedDoor, "door-update");
            sendDoorNotification(doorId, selectedDoor.getDoorName());
        } else {
            doorRepo.save(selectedDoor);
            sendSseEvent(selectedDoor, "door-update");
        }
    }

    @Override
    public void updateAlert(Long doorId, Integer doorAlert) {
        Door selectedDoor = doorRepo.findById(doorId).get();
        selectedDoor.setDoorAlert(doorAlert);
        doorRepo.save(selectedDoor);
    }

    // @Override
    // public void updateDoorStatusFront(Long doorId, Integer doorStatus, Integer
    // doorLockDown, Integer doorAlert,
    // String doorIp) {
    // Door selectedDoor = doorRepo.findById(doorId)
    // .orElseThrow(() -> new IllegalArgumentException("Door not found"));

    // selectedDoor.setDoorStatus(doorStatus);
    // selectedDoor.setDoorIp(doorIp);
    // selectedDoor.setDoorLockDown(doorLockDown);
    // selectedDoor.setDoorAlert(doorAlert);
    // doorRepo.save(selectedDoor);
    // sendSseEvent(selectedDoor, "door-update");
    // }

    @Override
    public void newDoor(Door door) {
        doorRepo.save(door);
        sendSseEvent(door, "door-new");
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
        sendSseEvent(selected, "door-delete");
    }

    @Override
    public SseEmitter createSseEmitter() {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minutes timeout
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        return emitter;
    }

    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        List<SseEmitter> deadEmitters = new ArrayList<>();
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("heartbeat"));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        });
        emitters.removeAll(deadEmitters);
    }

    @Override
    public void sendSseEvent(Door door, String eventName) {
        List<SseEmitter> deadEmitters = new ArrayList<>();
        emitters.forEach(emitter -> {
            try {
                String doorData = new ObjectMapper().writeValueAsString(door);
                emitter.send(SseEmitter.event().name(eventName).data(doorData));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        });
        emitters.removeAll(deadEmitters);
    }

    @Override
    public void sendDoorNotification(Long doorId, String doorName) {
        // Lấy thời gian hiện tại theo định dạng ISO
        LocalDateTime currentTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        String formattedTime = currentTime.format(formatter);

        // Tạo nội dung thông báo
        Notification notification = new Notification();
        notification.setNotificationImage("/picture/door.png");
        notification.setNotificationTitle(doorName + " " + doorId + " Alert");
        notification.setNotificationContent("Door Opened At: " + formattedTime);
        notification.setTime(currentTime);
        notificationRepository.save(notification);
        notificationService.sendSseEvent(notification, "notification-update");

        // In ra console hoặc log
        System.out.println("Notification sent: " + notification.getNotificationTitle());
    }
}

package com.example.smart.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.smart.entities.Door;
import com.example.smart.repositories.DoorRepositories;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DoorServicesImp implements DoorServices {
    @Autowired
    DoorRepositories doorRepo;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Override
    public List<Door> getAllDoor() {
        return doorRepo.findAll();
    }

    @Override
    public void updateDoorStatusESP(Long doorId, Integer doorStatus, Integer doorLockDown, String doorIp) {
        Door selectedDoor = doorRepo.findById(doorId)
                .orElseThrow(() -> new IllegalArgumentException("Door not found"));
    
        selectedDoor.setDoorStatus(doorStatus);
        selectedDoor.setDoorIp(doorIp);
        selectedDoor.setDoorLockDown(doorLockDown);
    
        // Tự động cập nhật doorAlert nếu doorStatus = 1 và doorLockDown = 1
        if (doorStatus == 1 && doorLockDown == 1) {
            selectedDoor.setDoorAlert(1); // doorAlert sẽ bằng 1
        }
        doorRepo.save(selectedDoor);
        sendSseEvent(selectedDoor, "door-update");
    }
    @Override
    public void updateDoorStatusFront(Long doorId, Integer doorStatus, Integer doorLockDown, Integer doorAlert, String doorIp) {
        Door selectedDoor = doorRepo.findById(doorId)
                .orElseThrow(() -> new IllegalArgumentException("Door not found"));
    
        selectedDoor.setDoorStatus(doorStatus);
        selectedDoor.setDoorIp(doorIp);
        selectedDoor.setDoorLockDown(doorLockDown);
        selectedDoor.setDoorAlert(doorAlert);
        doorRepo.save(selectedDoor);
        sendSseEvent(selectedDoor, "door-update");
    }

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
}

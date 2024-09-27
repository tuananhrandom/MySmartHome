package com.example.smart.services;

import java.util.List;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.smart.entities.Door;

public interface DoorServices {
    public List<Door> getAllDoor();

    public void updateDoorStatus(Long doorId, Integer doorStatus, Integer doorLockDown, String doorIp);

    public void newDoor(Door door);

    public Door findByDoorName(String doorName);

    public Door findByDoorId(Long doorId);

    public boolean nameIsExist(String doorName);

    public boolean idIsExist(Long doorId);

    public boolean ipIsExist(String doorIp);

    public Door findByDoorIp(String doorIp);

    public void deleteDoor(Long doorId);

    public SseEmitter createSseEmitter();

    public void sendSseEvent(Door door, String eventName);
}

package com.example.smart.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.smart.entities.Door;

public interface DoorServices {
    public List<Door> getAllDoor();

    public void updateDoorStatus(Long doorId, Integer doorStatus, Integer doorLockDown, String doorIp, Long ownerId);

    public void userAddDoor(Long doorId, Long userId, String doorName);

    public void userRemoveDoor(Long doorId, Long userId);

    public List<Door> getDoorByUserId(Long userId);

    public void toggleDoorAlarm(Long doorId, Long userId);

    public void adminAddNewDoor(Long doorId);

    public Door findByDoorName(String doorName);

    public Door findByDoorId(Long doorId);

    public boolean nameIsExist(String doorName);

    public boolean idIsExist(Long doorId);

    public boolean ipIsExist(String doorIp);

    public Door findByDoorIp(String doorIp);

    public void deleteDoor(Long doorId);

    public void updateDoorAlert(Long doorId, Integer doorAlert);

    public Door getDoorById(Long id);

    public List<Door> getDoorsByRange(Long start, Long end);

    public void updateDoor(Door door);

    public List<Door> getDoorsByDateRange(LocalDateTime start, LocalDateTime end);

    public void adminAddUserToDoor(Long doorId, Long userId);
}

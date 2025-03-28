package com.example.smart.controllers;

import java.util.List;
import java.util.Map;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.example.smart.DTO.ChangeDoorDTO;
import com.example.smart.entities.Door;
import com.example.smart.services.DoorServicesImp;
import com.example.smart.websocket.DoorSocketHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/door")
@CrossOrigin(origins = "http://localhost:3000")
public class DoorControllers {

    @Autowired
    DoorServicesImp doorServices;
    @Autowired
    DoorSocketHandler doorSocketHandler;

    @GetMapping("/all")
    public List<Door> getAllDoor() {
        return doorServices.getAllDoor();
    }
    // người dùng lấy đèn với ID của mình từ database
    @GetMapping("/{userId}")
    public List<Door> getDoorByUserId(@PathVariable Long userId) {
        return doorServices.getDoorByUserId(userId);
    }
    // người dùng thêm quyền sở hữu một đèn mới và đèn này sẽ được hiển thị trong
    // dashboard của họ.
    @PostMapping("/newdoor")
    public ResponseEntity<?> userAddNewDoor(
            @RequestParam Long userId,
            @RequestBody Map<String, Object> request) {
        System.out.println("request: " + request);

        String doorName = (String) request.get("doorName");
        Long doorId = ((Number) request.get("doorId")).longValue(); // Chuyển Object về Long

        doorServices.userAddDoor(doorId, userId, doorName);

        return new ResponseEntity<>(doorId, HttpStatus.CREATED);
    }
    // user toggle door alarm
    @PutMapping("/toggle")
    public ResponseEntity<?> toggleDoorAlarm(@RequestParam Long doorId, @RequestParam Long userId) {
        doorServices.toggleDoorAlarm(doorId, userId);
        return new ResponseEntity<>("Door alarm toggled", HttpStatus.OK);
    }

    // admin tạo ra đèn mới với một ID cố định được đặt trong database
    @PostMapping("/admin/newdoor")
    public ResponseEntity<?> newDoor(@RequestParam Long doorId) {
        doorServices.adminAddNewDoor(doorId);
        return new ResponseEntity<>(doorId, HttpStatus.CREATED);
    }

    // @PutMapping("/update/{doorId}")
    // public ResponseEntity<?> updateLightStatus(@PathVariable Long doorId, @RequestBody ChangeDoorDTO request) {
    //     Integer doorStatus = request.getDoorStatus();
    //     Integer doorLockDown = request.getDoorLockDown();
    //     String doorIp = request.getDoorIp();
    //     if (doorServices.idIsExist(doorId)) {
    //         doorServices.updateDoorStatus(doorId, doorStatus, doorLockDown, doorIp);
    //         try{
    //             doorSocketHandler.sendControlSignal(doorId, "doorLockDown: "+  doorLockDown);
    //             return new ResponseEntity<>("Door updated", HttpStatus.OK);
    //         } catch (IOException e) {
    //             e.printStackTrace();
    //             return new ResponseEntity<>("Failed to send control signal", HttpStatus.INTERNAL_SERVER_ERROR);
    //         }
    //     } else {
    //         return new ResponseEntity<>("Door doesn't exist", HttpStatus.NOT_FOUND);
    //     }
    // }

    // @PostMapping("/newDoor")
    // public ResponseEntity<?> newDoor(@RequestBody Door door) {
    //     doorServices.newDoor(door);
    //     return new ResponseEntity<>(door, HttpStatus.CREATED);

    // }
    // admin xóa cửa ra khỏi DB 
    @DeleteMapping("/delete/{doorId}")
    public ResponseEntity<?> deleteDoor(@PathVariable Long doorId) {
        if (doorServices.idIsExist(doorId)) {
            doorServices.deleteDoor(doorId);
            return new ResponseEntity<>("Deleted", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("IP not found ", HttpStatus.BAD_REQUEST);
        }

    }
}

package com.example.smart.controllers;

import java.util.List;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.example.smart.DTO.changeDoorDTO;
import com.example.smart.entities.Door;
import com.example.smart.services.DoorServicesImp;
import com.example.smart.websocket.DoorSocketHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/door")
public class DoorControllers {

    @Autowired
    DoorServicesImp doorServices;
    @Autowired
    DoorSocketHandler doorSocketHandler;

    @GetMapping("/all")
    public List<Door> getAllDoor() {
        return doorServices.getAllDoor();
    }

    @PutMapping("/update/{doorId}") // cập nhật trạng thái của đèn arduino từ client
    public ResponseEntity<?> updateLightStatus(@PathVariable Long doorId, @RequestBody changeDoorDTO request) {
        Integer doorStatus = request.getDoorStatus();
        String doorIp = request.getDoorIp();
        Integer doorLockDown = request.getDoorLockDown();
        if (doorServices.idIsExist(doorId)) {
            doorServices.updateDoorStatus(doorId, doorStatus, doorLockDown, doorIp);
            try {
                doorSocketHandler.sendControlSignal(doorId, "doorStatus:" + doorStatus);
                return new ResponseEntity<>("Door updated", HttpStatus.OK);
            } catch (IOException e) {
                // Xử lý ngoại lệ IOException
                e.printStackTrace(); // In ra chi tiết lỗi
                return new ResponseEntity<>("Failed to send control signal", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            return new ResponseEntity<>("Door doesn't exist", HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/newDoor")
    public ResponseEntity<?> newDoor(@RequestBody Door door) {
        doorServices.newDoor(door);
        return new ResponseEntity<>(door, HttpStatus.CREATED);
        
    }

    @DeleteMapping("/delete/{doorId}")
    public ResponseEntity<?> deleteDoor(@PathVariable Long doorId) {
        if (doorServices.idIsExist(doorId)) {
            doorServices.deleteDoor(doorId);
            return new ResponseEntity<>("Deleted", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("IP not found ", HttpStatus.BAD_REQUEST);
        }

    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDoors() {
    return doorServices.createSseEmitter();
    }

}

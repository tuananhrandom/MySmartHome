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

import jakarta.websocket.server.PathParam;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/door")
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
        System.out.println(doorName);
        Long doorId = ((Number) request.get("doorId")).longValue(); // Chuyển Object về Long

        doorServices.userAddDoor(doorId, userId, doorName);

        return new ResponseEntity<>(doorId, HttpStatus.CREATED);
    }

    // user toggle door alarm
    @PutMapping("/toggle")
    public ResponseEntity<?> toggleDoorAlarm(@RequestParam Long doorId, @RequestParam Long userId) {
        try {
            System.out.println("Received toggle request for door: " + doorId + " by user: " + userId);
            doorServices.toggleDoorAlarm(doorId, userId);
            return new ResponseEntity<>("Door alarm toggled successfully", HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid door operation: " + e.getMessage());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (RuntimeException e) {
            System.err.println("Error toggling door alarm: " + e.getMessage());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return new ResponseEntity<>("An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // admin tạo ra đèn mới với một ID cố định được đặt trong database
    @PostMapping("/admin/newdoor")
    public ResponseEntity<?> newDoor(@RequestParam Long doorId) {
        doorServices.adminAddNewDoor(doorId);
        return new ResponseEntity<>(doorId, HttpStatus.CREATED);
    }

    // @PutMapping("/update/{doorId}")
    // public ResponseEntity<?> updateLightStatus(@PathVariable Long doorId,
    // @RequestBody ChangeDoorDTO request) {
    // Integer doorStatus = request.getDoorStatus();
    // Integer doorLockDown = request.getDoorLockDown();
    // String doorIp = request.getDoorIp();
    // if (doorServices.idIsExist(doorId)) {
    // doorServices.updateDoorStatus(doorId, doorStatus, doorLockDown, doorIp);
    // try{
    // doorSocketHandler.sendControlSignal(doorId, "doorLockDown: "+ doorLockDown);
    // return new ResponseEntity<>("Door updated", HttpStatus.OK);
    // } catch (IOException e) {
    // e.printStackTrace();
    // return new ResponseEntity<>("Failed to send control signal",
    // HttpStatus.INTERNAL_SERVER_ERROR);
    // }
    // } else {
    // return new ResponseEntity<>("Door doesn't exist", HttpStatus.NOT_FOUND);
    // }
    // }

    // @PostMapping("/newDoor")
    // public ResponseEntity<?> newDoor(@RequestBody Door door) {
    // doorServices.newDoor(door);
    // return new ResponseEntity<>(door, HttpStatus.CREATED);

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

    // user da check cua va tat canh bao tren front end
    @PutMapping("check/{doorId}")
    public ResponseEntity<?> changeDoorAlert(@PathVariable Long doorId) {
        doorServices.updateDoorAlert(doorId, 0);
        return new ResponseEntity<>("OK", HttpStatus.OK);
    }

    @DeleteMapping("/delete/user")
    public ResponseEntity<?> userRemoveDoor(@RequestParam Long doorId, @RequestParam Long userId) {
        doorServices.userRemoveDoor(doorId, userId);
        return new ResponseEntity<>("Deleted", HttpStatus.OK);
    }

    @GetMapping("/find/{id}")
    public ResponseEntity<?> getDoorById(@PathVariable Long id) {
        Door door = doorServices.getDoorById(id);
        if (door != null) {
            return ResponseEntity.ok(door);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/range/{start}/{end}")
    public ResponseEntity<?> getDoorsByRange(@PathVariable Long start, @PathVariable Long end) {
        List<Door> doors = doorServices.getDoorsByRange(start, end);
        return ResponseEntity.ok(doors);
    }

    @DeleteMapping("/admin/delete/{id}")
    public ResponseEntity<?> adminDeleteDoor(@PathVariable Long id) {
        try {
            doorServices.deleteDoor(id);
            return ResponseEntity.ok("Door deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting door: " + e.getMessage());
        }
    }

    @PutMapping("/admin/reset/{id}")
    public ResponseEntity<?> adminResetDoor(@PathVariable Long id) {
        try {
            Door door = doorServices.getDoorById(id);
            if (door == null) {
                return ResponseEntity.notFound().build();
            }

            // Reset các giá trị về mặc định
            door.setDoorName(null);
            door.setDoorStatus(null);
            door.setDoorIp(null);
            door.setUser(null);
            door.setDoorLockDown(0);
            door.setDoorAlert(0);

            doorServices.updateDoor(door);
            return ResponseEntity.ok("Door reset successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error resetting door: " + e.getMessage());
        }
    }

    @GetMapping("/daterange")
    public ResponseEntity<?> getDoorsByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
            LocalDateTime start = LocalDate.parse(startDate, formatter).atStartOfDay();
            LocalDateTime end = LocalDate.parse(endDate, formatter).atTime(23, 59, 59);

            List<Door> doors = doorServices.getDoorsByDateRange(start, end);
            return ResponseEntity.ok(doors);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid date format. Please use MM/dd/yyyy format");
        }
    }

    @PostMapping("/admin/add-user")
    public ResponseEntity<?> adminAddUserToDoor(@RequestBody Map<String, Object> request) {
        try {
            Long doorId = ((Number) request.get("deviceId")).longValue();
            Long userId = ((Number) request.get("userId")).longValue();

            Door door = doorServices.getDoorById(doorId);
            if (door == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Không tìm thấy cửa với ID: " + doorId);
            }

            doorServices.adminAddUserToDoor(doorId, userId);
            return ResponseEntity.ok("Đã thêm người dùng vào cửa thành công");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi khi thêm người dùng vào cửa: " + e.getMessage());
        }
    }
}

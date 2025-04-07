package com.example.smart.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.smart.entities.Camera;
import com.example.smart.services.CameraService;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/camera")
public class CameraController {
    @Autowired
    CameraService cameraService;

    @GetMapping("/all")
    public List<Camera> getAllCameras() {
        return cameraService.getAllCamera();
    }

    @GetMapping("/{userId}")
    public List<Camera> getCameraByUserId(@PathVariable Long userId) {
        return cameraService.getCameraByUserId(userId);
    }

    // admin thêm mới một đèn vào DB
    @PostMapping("/admin/new")
    public ResponseEntity<?> AdminAddNewCamera(@RequestParam Long cameraId) {
        cameraService.adminAddNewCamera(cameraId);

        return new ResponseEntity<>("Created", HttpStatus.OK);
    }

    @DeleteMapping("/user/delete")
    public ResponseEntity<?> UserRemoveCamera(@RequestParam Long cameraId, @RequestParam Long userId) {
        cameraService.userRemoveCamera(cameraId, userId);
        return new ResponseEntity<>("Deleted", HttpStatus.OK);
    }

    // người dùng thêm quyền sở hữu một đèn mới và đèn này sẽ được hiển thị trong
    // dashboard của họ.
    @PostMapping("/newcamera")
    public ResponseEntity<?> userAddNewcamera(
            @RequestParam Long userId,
            @RequestBody Map<String, Object> request) {
        System.out.println("request: " + request);

        String cameraName = (String) request.get("cameraName");
        Long cameraId = ((Number) request.get("cameraId")).longValue(); // Chuyển Object về Long

        cameraService.userAddcamera(cameraId, userId, cameraName);

        return new ResponseEntity<>(cameraId, HttpStatus.CREATED);
    }

}

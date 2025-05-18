package com.example.smart.controllers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.smart.entities.Camera;
import com.example.smart.entities.CameraRecording;
import com.example.smart.services.CameraService;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/camera")
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

    // lấy về các video có trong camera
    @GetMapping("/video/all/{cameraId}")
    public List<CameraRecording> getRecordings(@PathVariable Long cameraId) {
        return cameraService.getRecordingsByCameraId(cameraId);
    }

    @GetMapping("/video/{id}")
    public ResponseEntity<?> streamVideo(@PathVariable Long id) {
        try {
            Resource video = cameraService.getVideoFile(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .body(video);
        } catch (IOException e) {
            Map<String, String> error = Map.of("error", "Video not found or could not be loaded.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error);
        }
    }

    // xóa đi video
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecording(@PathVariable Long id) {
        cameraService.deleteRecording(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/delete/user")
    public ResponseEntity<?> userDeleteCamera(@RequestParam Long userId, @RequestParam Long cameraId) {
        cameraService.userDeleteCamera(cameraId, userId);
        return new ResponseEntity<>("Deleted", HttpStatus.OK);
    }

    @GetMapping("/find/{id}")
    public ResponseEntity<?> getCameraById(@PathVariable Long id) {
        Camera camera = cameraService.getCameraById(id);
        if (camera != null) {
            return ResponseEntity.ok(camera);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/range/{start}/{end}")
    public ResponseEntity<?> getCamerasByRange(@PathVariable Long start, @PathVariable Long end) {
        List<Camera> cameras = cameraService.getCamerasByRange(start, end);
        return ResponseEntity.ok(cameras);
    }

    @DeleteMapping("/admin/delete/{id}")
    public ResponseEntity<?> adminDeleteCamera(@PathVariable Long id) {
        try {
            cameraService.adminDeletecamera(id, null); // userId có thể là null vì đây là thao tác của admin
            return ResponseEntity.ok("Camera deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting camera: " + e.getMessage());
        }
    }

    @PutMapping("/admin/reset/{id}")
    public ResponseEntity<?> adminResetCamera(@PathVariable Long id) {
        try {
            Camera camera = cameraService.getCameraById(id);
            if (camera == null) {
                return ResponseEntity.notFound().build();
            }

            // Reset các giá trị về mặc định
            camera.setCameraName(null);
            camera.setCameraStatus(null);
            camera.setCameraIp(null);
            camera.setUser(null);

            cameraService.updateCamera(camera);
            return ResponseEntity.ok("Camera reset successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error resetting camera: " + e.getMessage());
        }
    }
    @PutMapping("/toggle-record/{cameraId}")
    public ResponseEntity<?> toggleRecording(@PathVariable Long cameraId) {
        try {
            cameraService.toggleRecordCamera(cameraId);
            return ResponseEntity.ok("Camera recording toggled successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error toggling camera recording: " + e.getMessage());
        }
 
    }
}

package com.example.smart.controllers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.smart.DTO.ChangeLightDTO;
import com.example.smart.entities.Light;
import com.example.smart.services.LightServicesImp;
import com.example.smart.websocket.LightSocketHandler;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/api/light")
@CrossOrigin(origins = "http://localhost:3000")
public class LightControllers {

    @Autowired
    LightServicesImp lightServices;
    @Autowired
    LightSocketHandler lightSocketHandler;

    @GetMapping("/all")
    public List<Light> getAllLight() {
        return lightServices.getAllLight();
    }

    @GetMapping("/{userId}")
    public List<Light> getLightByUserId(@PathVariable Long userId) {
        return lightServices.getLightByUserId(userId);
    }

    // @PutMapping("/update/{lightId}") // cập nhật trạng thái của đèn arduino từ
    // client
    // public ResponseEntity<?> updateLightStatus(@PathVariable Long lightId,
    // @RequestBody ChangeLightDTO request) {
    // Integer lightStatus = request.getLightStatus();
    // String lightIp = request.getLightIp();
    // if (lightServices.idIsExist(lightId)) {
    // // lightServices.updateLightStatus(lightId, lightStatus, lightIp);
    // try {
    // lightSocketHandler.sendControlSignal(lightId, "lightStatus:" + lightStatus);
    // return new ResponseEntity<>("Light updated", HttpStatus.OK);
    // } catch (IOException e) {
    // // Xử lý ngoại lệ IOException
    // e.printStackTrace(); // In ra chi tiết lỗi
    // return new ResponseEntity<>("Failed to send control signal",
    // HttpStatus.INTERNAL_SERVER_ERROR);
    // }
    // } else {
    // return new ResponseEntity<>("Light doesn't exist", HttpStatus.NOT_FOUND);
    // }
    // }
    @PutMapping("/toggle")
    public ResponseEntity<?> toggleLight(@RequestParam Long lightId, @RequestParam Long userId) {
        try {
            lightServices.toggleLight(lightId, userId);
            return new ResponseEntity<>("Light updated", HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid light operation: " + e.getMessage());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (RuntimeException e) {
            System.err.println("Error toggling light: " + e.getMessage());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return new ResponseEntity<>("An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // @PutMapping("/arduino/update/{lightId}") // cập nhật trạng thái của đèn
    // arduino từ arduino
    // public ResponseEntity<?> updateLightStatuforArduino(@PathVariable Long
    // lightId,
    // @RequestBody changeLightDTO request) {
    // Integer lightStatus = request.getLightStatus();
    // if (lightServices.idIsExist(lightId)) {
    // lightServices.updateLightStatus(lightId, lightStatus);
    // return new ResponseEntity<>("Light updated", HttpStatus.OK);
    // } else {
    // return new ResponseEntity<>("Light doesn't exist", HttpStatus.NOT_FOUND);
    // }
    // }
    // public ResponseEntity<?> updateLightStatuforArduino(@PathVariable Long
    // lightId,
    // @RequestBody changeLightDTO request) {
    // Integer lightStatus = request.getLightStatus();
    // if (lightServices.idIsExist(lightId)) {
    // lightServices.updateLightStatus(lightId, lightStatus);
    // return new ResponseEntity<>("Light updated", HttpStatus.OK);
    // } else {
    // return new ResponseEntity<>("Light doesn't exist", HttpStatus.NOT_FOUND);
    // }
    // }

    // admin tạo ra đèn mới với một ID cố định được đặt trong database
    @PostMapping("/admin/newlight")
    public ResponseEntity<?> newLight(@RequestParam Long lightId) {
        lightServices.adminAddNewLight(lightId);
        return new ResponseEntity<>(lightId, HttpStatus.CREATED);
    }

    // người dùng thêm quyền sở hữu một đèn mới và đèn này sẽ được hiển thị trong
    // dashboard của họ.
    @PostMapping("/newlight")
    public ResponseEntity<?> userAddNewLight(
            @RequestParam Long userId,
            @RequestBody Map<String, Object> request) {
        System.out.println("request: " + request);
        System.out.println("userId: " + userId);
        String lightName = (String) request.get("lightName");
        Long lightId = ((Number) request.get("lightId")).longValue(); // Chuyển Object về Long

        lightServices.userAddLight(lightId, userId, lightName);

        return new ResponseEntity<>(lightId, HttpStatus.CREATED);
    }

    @DeleteMapping("/delete/{lightId}")
    public ResponseEntity<?> deleteLight(@PathVariable Long lightId) {
        if (lightServices.idIsExist(lightId)) {
            lightServices.deleteLight(lightId);
            return new ResponseEntity<>("Deleted", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("IP not found ", HttpStatus.BAD_REQUEST);
        }

    }

    @DeleteMapping("/delete/user")
    public ResponseEntity<?> userDeleteLight(@RequestParam Long userId, @RequestParam Long lightId) {
        lightServices.userDeleteLight(lightId, userId);
        return new ResponseEntity<>("Deleted", HttpStatus.OK);
    }

    // @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    // public SseEmitter streamLights() {
    // return lightServices.createSseEmitter();
    // }

    @GetMapping("/find/{id}")
    public ResponseEntity<?> getLightById(@PathVariable Long id) {
        Light light = lightServices.getLightById(id);
        if (light != null) {
            return ResponseEntity.ok(light);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/range/{start}/{end}")
    public ResponseEntity<?> getLightsByRange(@PathVariable Long start, @PathVariable Long end) {
        List<Light> lights = lightServices.getLightsByRange(start, end);
        return ResponseEntity.ok(lights);
    }

    @DeleteMapping("/admin/delete/{id}")
    public ResponseEntity<?> adminDeleteLight(@PathVariable Long id) {
        try {
            lightServices.deleteLight(id);
            return ResponseEntity.ok("Light deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting light: " + e.getMessage());
        }
    }

    @PutMapping("/admin/reset/{id}")
    public ResponseEntity<?> adminResetLight(@PathVariable Long id) {
        try {
            Light light = lightServices.getLightById(id);
            if (light == null) {
                return ResponseEntity.notFound().build();
            }

            // Reset các giá trị về mặc định
            light.setLightName(null);
            light.setLightStatus(null);
            light.setLightIp(null);
            light.setUser(null);

            lightServices.updateLight(light);
            return ResponseEntity.ok("Light reset successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error resetting light: " + e.getMessage());
        }
    }

    @GetMapping("/daterange")
    public ResponseEntity<?> getLightsByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
            LocalDateTime start = LocalDate.parse(startDate, formatter).atStartOfDay();
            LocalDateTime end = LocalDate.parse(endDate, formatter).atTime(23, 59, 59);

            List<Light> lights = lightServices.getLightsByDateRange(start, end);
            return ResponseEntity.ok(lights);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid date format. Please use MM/dd/yyyy format");
        }
    }

    @PostMapping("/admin/add-user")
    public ResponseEntity<?> adminAddUserToLight(@RequestBody Map<String, Object> request) {
        try {
            Long lightId = ((Number) request.get("deviceId")).longValue();
            System.out.println("lightId: " + lightId);
            Long userId = ((Number) request.get("userId")).longValue();
            System.out.println("userId: " + userId);

            Light light = lightServices.getLightById(lightId);
            if (light == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Không tìm thấy đèn với ID: " + lightId);
            }

            lightServices.adminAddUserToLight(lightId, userId);
            return ResponseEntity.ok("Đã thêm người dùng vào đèn thành công");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi khi thêm người dùng vào đèn: " + e.getMessage());
        }
    }
}

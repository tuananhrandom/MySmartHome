package com.example.smart.controllers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
@RequestMapping("/light")
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

    @PutMapping("/update/{lightId}") // cập nhật trạng thái của đèn arduino từ client
    public ResponseEntity<?> updateLightStatus(@PathVariable Long lightId, @RequestBody ChangeLightDTO request) {
        Integer lightStatus = request.getLightStatus();
        String lightIp = request.getLightIp();
        if (lightServices.idIsExist(lightId)) {
            // lightServices.updateLightStatus(lightId, lightStatus, lightIp);
            try {
                lightSocketHandler.sendControlSignal(lightId, "lightStatus:" + lightStatus);
                return new ResponseEntity<>("Light updated", HttpStatus.OK);
            } catch (IOException e) {
                // Xử lý ngoại lệ IOException
                e.printStackTrace(); // In ra chi tiết lỗi
                return new ResponseEntity<>("Failed to send control signal", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            return new ResponseEntity<>("Light doesn't exist", HttpStatus.NOT_FOUND);
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
}

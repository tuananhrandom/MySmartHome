package com.example.smart.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.smart.entities.Light;
import com.example.smart.repositories.LightRepositories;
import com.example.smart.repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.smart.services.SseService;
import com.example.smart.websocket.LightSocketHandler;

@Service
public class LightServicesImp implements LightServices {
    @Autowired
    LightSocketHandler lightSocketHandler;
    @Autowired
    LightRepositories lightRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired(required = false)
    private com.example.smart.websocket.ClientWebSocketHandler clientWebSocketHandler;

    @Override
    public List<Light> getAllLight() {
        return lightRepo.findAll();
    }

    @Override
    public List<Light> getLightByUserId(Long userId) {
        return lightRepo.findByUser_UserId(userId);
    }

    @Override
    public Light getLightById(Long lightId) {
        return lightRepo.findById(lightId).orElseThrow(() -> new IllegalArgumentException("Light not found"));
    }

    // cập nhật thông tin đèn từ ESP32
    @Override
    public void updateLightStatus(Long lightId, Integer lightStatus, String lightIp, Long ownerId) {
        Light selectedLight = lightRepo.findById(lightId)
                .orElseThrow(() -> new IllegalArgumentException("Light not found with ID: " + lightId));

        selectedLight.setLightStatus(lightStatus);
        selectedLight.setLightIp(lightIp);
        if (ownerId == -1) {
            selectedLight.setUser(null);
        } else {
            selectedLight.setUser(userRepo.findById(ownerId).get());
        }

        // Lưu thay đổi vào database
        lightRepo.save(selectedLight);

        // Gửi thông báo đến client qua WebSocket nếu có ClientWebSocketHandler
        if (clientWebSocketHandler != null && selectedLight.getUser() != null) {
            clientWebSocketHandler.notifyLightUpdate(selectedLight);
        } else {

        }
    }

    // admin thêm một đèn mới được tạo ra vào database, đèn mới được tạo ra chỉ để
    // xác thực Id này đã được tạo mới tất cả các trương khác đều là rỗng chờ đơi
    // ESP32 và user thêm dữ liệu
    @Override
    public void adminAddNewLight(Long lightId) {
        Light newLight = new Light();
        newLight.setLightId(lightId);
        newLight.setLightName(null);
        newLight.setLightStatus(null);
        newLight.setLightIp(null);
        newLight.setUser(null);
        lightRepo.save(newLight);
    }

    // user thêm một đèn mới vào quyền sở hữu của họ(cập nhật ownerId cho đèn, và
    // đồng thời người dùng đặt tên cho đèn)
    @Override
    public void userAddLight(Long lightId, Long userId, String lightName) {
        if (lightRepo.existsById(lightId) && lightRepo.findById(lightId).get().getUser() == null) {
            Light thisLight = lightRepo.findById(lightId).get();
            thisLight.setUser(userRepo.findById(userId).get());
            thisLight.setLightName(lightName);
            lightRepo.save(thisLight);
        } else {
            throw new IllegalArgumentException("Light not found or already owned");
        }
    }

    @Override
    public Light findByLightName(String lightName) {
        return lightRepo.findByLightName(lightName);
    }

    @Override
    public Light findByLightId(Long lightId) {
        return lightRepo.findById(lightId).orElseThrow(() -> new IllegalArgumentException("Light not found"));
    }

    @Override
    public boolean nameIsExist(String lightName) {
        return lightRepo.findByLightName(lightName) != null;
    }

    @Override
    public boolean idIsExist(Long lightId) {
        return lightRepo.findById(lightId).isPresent();
    }

    @Override
    public boolean ipIsExist(String lightIp) {
        return lightRepo.findByLightIp(lightIp) != null;
    }

    @Override
    public Light findByLightIp(String lightIp) {
        return lightRepo.findByLightIp(lightIp);
    }

    @Override
    public void deleteLight(Long lightId) {
        Light selected = lightRepo.findById(lightId).get();
        lightRepo.delete(selected);
        Long recipientId = selected.getUser().getUserId();
    }

    @Override
    public void userDeleteLight(Long lightId, Long userId) {
        Light selectedLight = lightRepo.findById(lightId).get();
        // tránh trường hợp một api từ user khác xóa light của user khác
        if (selectedLight.getUser().getUserId() == userId) {
            selectedLight.setUser(null);
        }
        lightRepo.save(selectedLight);
        try {
            lightSocketHandler.sendControlSignal(lightId, "ownerId:" + -1);
        } catch (Exception e) {
            throw new IllegalArgumentException("Light not found");
        }
    }

}
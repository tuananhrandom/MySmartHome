package com.example.smart.services;

import java.util.List;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.smart.entities.Light;

public interface LightServices {
    public List<Light> getAllLight();

    public void toggleLight(Long lightIp, Long ownerId);

    // public void updateLightStatus(Long lightId, Integer lightStatus, String
    // lightIp);
    // update lightStatus from ESP32, ESP32 sẽ là nơi tạo ra Light đầu tiên chứ
    // không phải từ frontEnd
    public void updateLightStatus(Long lightId, Integer lightStatus, String lightIp, Long ownerId);

    // public void newLight(Light light);
    public List<Light> getLightByUserId(Long userId);

    public Light getLightById(Long lightId);

    // admin thêm một đèn mới được tạo ra vào database
    public void adminAddNewLight(Long lightId);

    // user thêm một đèn mới vào quyền sở hữu của họ(cập nhật ownerId cho đèn, và
    // đồng thời người dùng đặt tên cho đèn)
    public void userAddLight(Long lightId, Long userId, String lightName);

    public Light findByLightName(String lightName);

    public Light findByLightId(Long lightId);

    public boolean nameIsExist(String lightName);

    public boolean idIsExist(Long lightId);

    public boolean ipIsExist(String lightIp);

    public Light findByLightIp(String lightIp);

    public void deleteLight(Long lightId);

    public void userDeleteLight(Long lightId, Long userId);

}

package com.example.smart.services;

import java.util.List;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.smart.entities.Light;

public interface LightServices {
    public List<Light> getAllLight();

    public void updateLightStatus(Long lightId, Integer lightStatus, String lightIp);

    public void newLight(Light light);

    public Light findByLightName(String lightName);

    public Light findByLightId(Long lightId);

    public boolean nameIsExist(String lightName);

    public boolean idIsExist(Long lightId);

    public boolean ipIsExist(String lightIp);

    public Light findByLightIp(String lightIp);

    public void deleteLight(Long lightId);

    public SseEmitter createSseEmitter();

    public void sendSseEvent(Light light);
}

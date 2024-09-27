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
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LightServicesImp implements LightServices {
    @Autowired
    LightRepositories lightRepo;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Override
    public List<Light> getAllLight() {
        return lightRepo.findAll();
    }

    @Override
    public void updateLightStatus(Long lightId, Integer lightStatus, String lightIp) {
        Light selectedLight = lightRepo.findById(lightId)
                .orElseThrow(() -> new IllegalArgumentException("Light not found"));
        selectedLight.setLightStatus(lightStatus);
        selectedLight.setLightIp(lightIp);
        lightRepo.save(selectedLight);
        sendSseEvent(selectedLight,"light-update");
    }

    @Override
    public void newLight(Light light) {
        lightRepo.save(light);
        sendSseEvent(light, "light-new");
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
        sendSseEvent(selected, "light-delete");
    }
    @Override
    public SseEmitter createSseEmitter() {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minutes timeout
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        return emitter;
    }

    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        List<SseEmitter> deadEmitters = new ArrayList<>();
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("heartbeat"));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        });
        emitters.removeAll(deadEmitters);
    }
    @Override
    public void sendSseEvent(Light light, String eventName) {
        List<SseEmitter> deadEmitters = new ArrayList<>();
        emitters.forEach(emitter -> {
            try {
                // Convert Light object to JSON string
                String lightData = new ObjectMapper().writeValueAsString(light);
                emitter.send(SseEmitter.event().name(eventName).data(lightData));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        });
        emitters.removeAll(deadEmitters);
    }

}
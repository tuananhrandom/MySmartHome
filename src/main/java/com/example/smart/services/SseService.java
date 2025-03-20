package com.example.smart.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SseService {
    private final ObjectMapper objectMapper;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private Map<Long, SseEmitter> recipientEmitters = new ConcurrentHashMap<>();

    public SseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // tạo emiter
    public SseEmitter createSseEmitter() {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minutes timeout
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        return emitter;
    }

    // tạo emiter cho người dùng nhất định theo recipientId
    public SseEmitter createRecipientEmitter(Long recipientId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        recipientEmitters.put(recipientId, emitter);

        emitter.onCompletion(() -> recipientEmitters.remove(recipientId));
        emitter.onTimeout(() -> recipientEmitters.remove(recipientId));
        emitter.onError((e) -> recipientEmitters.remove(recipientId));
        return emitter;
    }

    public void sendSseEvent(Object object, String eventName) {
        System.out.println("da vao den sendSse");
        List<SseEmitter> deadEmitters = new ArrayList<>();
        emitters.forEach(emitter -> {
            try {

                // Sử dụng objectMapper đã cấu hình thay vì tạo mới
                String objectData = objectMapper.writeValueAsString(object);
                System.out.println("da vao den String object");
                emitter.send(SseEmitter.event().name(eventName).data(objectData));
                System.out.println("da gui du lieu");
            } catch (IOException e) {
                deadEmitters.add(emitter);
                System.out.println("loi gui du lieu: " + e.getMessage());
            }
        });
        System.out.println("thoat ra ngoai");
        emitters.removeAll(deadEmitters);
    }

    // gửi SSE đến một recipient nhất định
    public void sendSseEventToRecipient(Object object, Long recipientId, String eventName) {
        SseEmitter thisEmitter = recipientEmitters.get(recipientId);
        if (thisEmitter != null) {
            try {
                String data = objectMapper.writeValueAsString(object);
                thisEmitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                System.err.println("Error sending SSE: " + e.getMessage());
            }
        }
    }
}

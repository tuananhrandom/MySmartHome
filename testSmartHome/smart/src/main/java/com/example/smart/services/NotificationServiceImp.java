package com.example.smart.services;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import com.example.smart.entities.Notification;
import com.example.smart.repositories.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;

@Service
public class NotificationServiceImp implements NotificationService {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    @Autowired
    NotificationRepository notificationRepository;

    @Override
    public List<Notification> getAllNotifications() {
        List<Notification> notifications = notificationRepository.findAll();
        return notifications;
    }

    // tạo mới thông báo
    @Override
    public void createNotification(Notification newNotification) {
        notificationRepository.save(newNotification);
        sendSseEvent(newNotification, "notification-update");
    }

    // xóa thông báo theo id
    @Override
    public void deleteNotification(Long id) {
        if (notificationRepository.existsById(id)) {
            Notification selectedNotification = notificationRepository.findById(id).get();
            notificationRepository.deleteById(id);
            sendSseEvent(selectedNotification, "notification-delete");
        }
    }

    // xóa toàn bộ thông báo
    @Override
    public void deleteAllNotification() {
        notificationRepository.deleteAll();
        sendSseTextOnlyEvent("notification-delete-all");
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
    public void sendSseEvent(Notification notification, String eventName) {
        List<SseEmitter> deadEmitters = new ArrayList<>();
        emitters.forEach(emitter -> {
            try {
                // Cấu hình ObjectMapper
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule()); // Đăng ký module Java Time
                objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // Đảm bảo nó serialize dạng                                                                                      // ISO-8601
                // Convert notification object to JSON string
                String notificationData = objectMapper.writeValueAsString(notification);

                emitter.send(SseEmitter.event().name(eventName).data(notificationData));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        });

        emitters.removeAll(deadEmitters);
    }
    @Override
    public void sendSseTextOnlyEvent(String eventName){
        List<SseEmitter> deadEmitters = new ArrayList<>();
        emitters.forEach(emitter -> {
            try {
                // Convert Light object to JSON string
                emitter.send(SseEmitter.event().name(eventName));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        });
        emitters.removeAll(deadEmitters);
    }

}

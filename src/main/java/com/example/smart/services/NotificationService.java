package com.example.smart.services;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.example.smart.entities.Notification;

public interface NotificationService {
    public List<Notification> getAllNotifications();
    public void createNotification(Notification notification);
    public void deleteNotification(Long id);
    public void deleteAllNotification();
    public SseEmitter createSseEmitter();
    public void sendSseEvent(Notification notification, String eventName);
    public void sendSseTextOnlyEvent(String eventName);
}

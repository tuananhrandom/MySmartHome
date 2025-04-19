package com.example.smart.services;

import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.smart.entities.Devices;
import com.example.smart.entities.Notification;

public interface NotificationService {
    public List<Notification> getAllNotifications();

    public void createNotification(String notificationType, String notificationTitle, String notificationContent,
            Long userId);

    public void deleteNotification(Long id);

    public void deleteAllNotification();

    public List<Notification> getUserNotifications(Long userId);

    public List<Notification> getUserNotificationsByType(Long userId, String deviceType);

}

package com.example.smart.services;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CopyOnWriteArrayList;

import com.example.smart.entities.Notification;
import com.example.smart.repositories.NotificationRepository;
import com.example.smart.repositories.UserRepository;
import com.example.smart.websocket.ClientWebSocketHandler;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class NotificationServiceImp implements NotificationService {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    @Autowired
    ClientWebSocketHandler clientWebSocketHandler;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    UserRepository userRepo;

    // lấy về tất cả các thông báo (kiểm thử)
    @Override
    public List<Notification> getAllNotifications() {
        List<Notification> notifications = notificationRepository.findAll();
        return notifications;
    }

    // người dùng lấy về tất cả các thông báo
    @Override
    public List<Notification> getUserNotifications(Long userId) {
        return notificationRepository.findByUser_UserId(userId);
    }

    // người dùng lấy về thông báo theo từng loại thiết bị một
    @Override
    public List<Notification> getUserNotificationsByType(Long userId, String deviceType) {
        return notificationRepository.findByUser_UserIdAndNotificationType(userId, deviceType);
    }

    // tạo mới thông báo
    @Override
    public void createNotification(String notificationType, String notificationTitle, String notificationContent,
            Long userId) {
        try {
            Notification newNotification = new Notification();
            newNotification.setNotificationType(notificationType);
            newNotification.setNotificationTitle(notificationTitle);
            newNotification.setNotificationContent(notificationContent);
            newNotification.setUser(userRepo.findById(userId).get());
            newNotification.setTime(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
            notificationRepository.save(newNotification);
            clientWebSocketHandler.notifyNotificationUpdate(newNotification);

        } catch (Exception e) {
            System.err.println(e);
        }
    }

    // xóa thông báo theo id
    @Override
    public void deleteNotification(Long id) {
        if (notificationRepository.existsById(id)) {
            Notification selectedNotification = notificationRepository.findById(id).get();
            notificationRepository.deleteById(id);
        }
    }

    // xóa toàn bộ thông báo
    @Override
    public void deleteAllNotification() {
        notificationRepository.deleteAll();
    }

    @Override
    public boolean hasUnreadNotifications(Long userId) {
        List<Notification> notifications = notificationRepository.findByUser_UserId(userId);
        return notifications.stream().anyMatch(notification -> !notification.getIsRead());
    }
}

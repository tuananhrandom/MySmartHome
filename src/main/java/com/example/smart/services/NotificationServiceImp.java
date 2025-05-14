package com.example.smart.services;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
        return notificationRepository.findByUser_UserIdOrderByTimeDesc(userId);
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

    // xóa toàn bộ thông báo dựa trên id người dùng.
    @Override
    @Transactional
    public void deleteAllNotificationByUser(Long userId) {
        try {
            if (!userRepo.existsById(userId)) {
                throw new RuntimeException("User not found with id: " + userId);
            }
            notificationRepository.deleteByUser_UserId(userId);
        } catch (Exception e) {
            System.err.println("Error deleting notifications: " + e.getMessage());
            throw new RuntimeException("Failed to delete notifications: " + e.getMessage());
        }
    }

    // xóa toàn bộ thông báo
    @Override
    public void deleteAllNotification() {
        notificationRepository.deleteAll();
    }

    @Override
    public boolean hasUnreadNotifications(Long userId) {
        List<Notification> notifications = notificationRepository.findByUser_UserIdOrderByTimeDesc(userId);
        return notifications.stream().anyMatch(notification -> !notification.getIsRead());
    }

    @Transactional
    public void markAllNotificationsAsRead(Long userId) {
        try {
            System.out.println("Bắt đầu cập nhật thông báo cho user: " + userId);
            int updatedCount = notificationRepository.markAllNotificationsAsReadByUserId(userId);
            System.out.println("Đã cập nhật " + updatedCount + " thông báo");

            // Kiểm tra lại sau khi cập nhật
            List<Notification> notifications = notificationRepository.findByUser_UserIdOrderByTimeDesc(userId);
            long unreadCount = notifications.stream().filter(n -> !n.getIsRead()).count();
            System.out.println("Số thông báo chưa đọc còn lại: " + unreadCount);
        } catch (Exception e) {
            System.err.println("Lỗi khi cập nhật thông báo: " + e.getMessage());
            e.printStackTrace();
            throw e; // Ném lại exception để rollback transaction
        }
    }
}

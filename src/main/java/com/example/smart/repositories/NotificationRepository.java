package com.example.smart.repositories;

import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.smart.entities.Devices;
import com.example.smart.entities.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    public List<Notification> findByUser_UserId(Long userId);

    public List<Notification> findByUser_UserIdAndNotificationType(Long userId, String NotificationType);

}

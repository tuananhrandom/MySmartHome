package com.example.smart.repositories;

import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.smart.entities.Devices;
import com.example.smart.entities.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    public List<Notification> findByUser_UserIdOrderByTimeDesc(Long userId);

    public List<Notification> findByUser_UserIdAndNotificationType(Long userId, String NotificationType);

    public void deleteByUser_UserId(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.userId = :userId AND n.isRead = false")
    int markAllNotificationsAsReadByUserId(@Param("userId") Long userId);
}

package com.example.smart.repositories;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.smart.entities.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long>{
    
}

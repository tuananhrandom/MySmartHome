package com.example.smart.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.smart.entities.Notification;
import com.example.smart.services.NotificationServiceImp;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/notification")
@CrossOrigin(origins = "http://localhost:3000")
public class NotificationController {
    @Autowired
    NotificationServiceImp notificationServiceImp;

    @GetMapping("/all")
    public List<Notification> getAllNotification() {
        return notificationServiceImp.getAllNotifications();
    }

    @GetMapping("/{userId}")
    public List<Notification> getAllNotificationByUserId(@PathVariable Long userId) {
        return notificationServiceImp.getUserNotifications(userId);
    }

    @DeleteMapping("/delete/all")
    public ResponseEntity<?> deleteAllLight() {
        notificationServiceImp.deleteAllNotification();
        return new ResponseEntity<>("Delete Done", HttpStatus.OK);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteNotificationById(@PathVariable Long id) {
        notificationServiceImp.deleteNotification(id);
        return new ResponseEntity<>("Delete Done", HttpStatus.OK);
    }

}

package com.example.smart.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.smart.entities.PushSubscription;
import com.example.smart.entities.Notification;
import com.example.smart.entities.User;
import com.example.smart.repositories.PushSubscriptionRepository;
import com.example.smart.services.NotificationServiceImp;
import com.example.smart.services.WebPushService;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/api/notification")
@CrossOrigin(origins = "http://localhost:3000")
public class NotificationController {
    @Autowired
    private WebPushService webPushService;

    @Autowired
    private PushSubscriptionRepository subscriptionRepository;

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

    // @DeleteMapping("/delete/all")
    // public ResponseEntity<?> deleteAllLight() {
    // notificationServiceImp.deleteAllNotification();
    // return new ResponseEntity<>("Delete Done", HttpStatus.OK);
    // }
    @DeleteMapping("/delete/all/{userId}")
    public ResponseEntity<?> deleteNotificationByUserId(@PathVariable Long userId) {
        try {
            notificationServiceImp.deleteAllNotificationByUser(userId);
            return new ResponseEntity<>("All notifications deleted successfully", HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred while deleting notifications",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteNotificationById(@PathVariable Long id) {
        notificationServiceImp.deleteNotification(id);
        return new ResponseEntity<>("Delete Done", HttpStatus.OK);
    }

    @GetMapping("/unread/{userId}")
    public ResponseEntity<?> checkUnreadNotifications(@PathVariable Long userId) {
        boolean hasUnread = notificationServiceImp.hasUnreadNotifications(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("hasUnread", hasUnread));
    }

    @PostMapping("/mark-all-read/{userId}")
    public ResponseEntity<?> markAllNotificationsAsRead(@PathVariable Long userId) {
        notificationServiceImp.markAllNotificationsAsRead(userId);
        return ResponseEntity.ok().body(Map.of("message", "All notifications marked as read"));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody PushSubscription subscription,
            @AuthenticationPrincipal User user) {
        try {
            PushSubscription entity = new PushSubscription();
            entity.setUserId(user.getUserId());
            entity.setEndpoint(subscription.getEndpoint());
            entity.setP256dh(subscription.getP256dh());
            entity.setAuth(subscription.getAuth());

            subscriptionRepository.save(entity);
            return ResponseEntity.ok("Subscribed successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to subscribe: " + e.getMessage());
        }
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestBody PushSubscription subscription,
            @AuthenticationPrincipal User user) {
        try {
            subscriptionRepository.deleteByEndpointAndUserId(subscription.getEndpoint(), user.getUserId());
            return ResponseEntity.ok("Unsubscribed successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to unsubscribe: " + e.getMessage());
        }
    }
}

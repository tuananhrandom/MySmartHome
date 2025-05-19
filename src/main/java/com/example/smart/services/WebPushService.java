package com.example.smart.services;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;

import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
public class WebPushService {
    private final PushService pushService;

    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    public WebPushService(@Value("${vapid.public.key:}") String publicKey,
            @Value("${vapid.private.key:}") String privateKey) {
        try {
            if (publicKey == null || publicKey.isEmpty() || privateKey == null || privateKey.isEmpty()) {
                this.pushService = null;
                return;
            }
            this.pushService = new PushService(publicKey, privateKey);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize PushService", e);
        }
    }

    public void sendNotification(Subscription subscription, String title, String body) {
        try {
            String payload = String.format("{\"title\":\"%s\",\"body\":\"%s\"}", title, body);
            Notification notification = new Notification(subscription, payload);
            pushService.send(notification);
        } catch (GeneralSecurityException | InterruptedException | ExecutionException | IOException | JoseException e) {
            throw new RuntimeException("Could not send web push notification", e);
        }
    }

    public void sendNotificationToAllDevices(List<Subscription> subscriptions, String title, String body) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Subscription subscription : subscriptions) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                sendNotification(subscription, title, body);
            });
            futures.add(future);
        }

        // Chờ tất cả gửi xong
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
}

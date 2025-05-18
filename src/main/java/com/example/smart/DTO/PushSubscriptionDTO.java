package com.example.smart.DTO;

import lombok.Data;

@Data
public class PushSubscriptionDTO {
    private String endpoint;
    private SubscriptionKeys keys;

    @Data
    public static class SubscriptionKeys {
        private String p256dh;
        private String auth;
    }
}

package com.example.smart.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long scheduleId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "device_type")
    private String deviceType; // "light" hoặc "door"

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "action")
    private String action; // "on" hoặc "off"

    @Column(name = "time")
    private LocalTime time;

    @Column(name = "days_of_week")
    private String daysOfWeek; // "1,2,3,4,5,6,7" (1=Monday, 7=Sunday)

    @Column(name = "is_active")
    private Boolean isActive = true;
}
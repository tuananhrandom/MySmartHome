package com.example.smart.DTO;

import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleDto {
    private Long userId;
    private String deviceType; // "light" hoặc "door"
    private Long deviceId;
    private Integer action; // "1" hoặc "0"
    private LocalTime time;
    private String daysOfWeek; // "1,2,3,4,5,6,7" (1=Monday, 7=Sunday)
    private Boolean isActive = true;
}

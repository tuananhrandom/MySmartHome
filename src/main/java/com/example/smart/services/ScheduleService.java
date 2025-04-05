package com.example.smart.services;

import com.example.smart.DTO.ScheduleDto;
import com.example.smart.entities.Schedule;
import java.util.List;

public interface ScheduleService {
    Schedule createSchedule(ScheduleDto scheduleDto);

    Schedule updateSchedule(Long scheduleId, Schedule schedule);

    void deleteSchedule(Long scheduleId);

    List<Schedule> getUserSchedules(Long userId);

    List<Schedule> getDeviceSchedules(String deviceType, Long deviceId);

    Schedule getScheduleById(Long scheduleId);

    void toggleSchedule(Long scheduleId, Boolean isActive);

    List<Schedule> getAllSchedules();
}
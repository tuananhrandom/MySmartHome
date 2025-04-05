package com.example.smart.repositories;

import com.example.smart.entities.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findByUser_UserId(Long userId);

    List<Schedule> findByDeviceTypeAndDeviceId(String deviceType, Long deviceId);

    List<Schedule> findByIsActiveTrueAndTime(LocalTime time);

    List<Schedule> findByUser_UserIdAndDeviceTypeAndDeviceId(Long userId, String deviceType, Long deviceId);

}
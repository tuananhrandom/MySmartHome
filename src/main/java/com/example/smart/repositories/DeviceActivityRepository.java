package com.example.smart.repositories;

import java.time.LocalDateTime;
import java.util.List;

import org.bytedeco.opencv.opencv_core.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.smart.entities.DeviceActivity;

@Repository
public interface DeviceActivityRepository extends JpaRepository<DeviceActivity, Long> {
        List<DeviceActivity> findByUser_UserId(Long userId);

        List<DeviceActivity> findByDeviceTypeAndDeviceIdOrderByActivityTimeDesc(String deviceType, Long deviceId);

        List<DeviceActivity> findByActivityTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

        List<DeviceActivity> findByDeviceTypeAndDeviceIdAndActivityTimeBetweenOrderByActivityTimeDesc(
                        String deviceType, Long deviceId, LocalDateTime startTime, LocalDateTime endTime);

        List<DeviceActivity> findByUser_UserIdAndDeviceTypeAndDeviceId(
                        Long userId, String deviceType, Long deviceId);

        List<DeviceActivity> findByUser_UserIdAndActivityTimeBetween(
                        Long userId, LocalDateTime startTime, LocalDateTime endTime);

        void deleteByDeviceTypeAndDeviceId(String deviceType, Long deviceId);
}

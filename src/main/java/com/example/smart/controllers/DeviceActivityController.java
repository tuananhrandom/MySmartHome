package com.example.smart.controllers;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.smart.entities.DeviceActivity;
import com.example.smart.services.DeviceActivityService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/device-activities")
@RequiredArgsConstructor
public class DeviceActivityController {

    private final DeviceActivityService deviceActivityService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<List<DeviceActivity>> getAllActivities() {
        return ResponseEntity.ok(deviceActivityService.getAllActivities());
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('USER') or @userService.isCurrentUserById(#userId)")
    public ResponseEntity<List<DeviceActivity>> getActivitiesByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(deviceActivityService.getActivitiesByUserId(userId));
    }

    @GetMapping("/device")
    // @PreAuthorize("hasAuthority('USER') or @userService.isCurrentUserOwnerOfDevice(#deviceType, #deviceId)")
    public ResponseEntity<List<DeviceActivity>> getActivitiesByDevice(
            @RequestParam String deviceType,
            @RequestParam Long deviceId) {
        return ResponseEntity.ok(deviceActivityService.getActivitiesByDeviceTypeAndId(deviceType, deviceId));
    }

    @GetMapping("/time-range")
    @PreAuthorize("hasAuthority('USER') or @userService.isCurrentUser(#userId)")
    public ResponseEntity<List<DeviceActivity>> getActivitiesByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Long userId) {
        
        if (userId != null) {
            return ResponseEntity.ok(deviceActivityService.getActivitiesByTimeRange(startTime, endTime));
        }
        
        return ResponseEntity.ok(deviceActivityService.getActivitiesByTimeRange(startTime, endTime));
    }

    @DeleteMapping("/cleanup")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<Void> cleanupOldActivities(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime olderThan) {
        deviceActivityService.deleteActivitiesOlderThan(olderThan);
        return ResponseEntity.ok().build();
    }
}
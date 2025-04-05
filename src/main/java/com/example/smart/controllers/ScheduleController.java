package com.example.smart.controllers;

import com.example.smart.DTO.ScheduleDto;
import com.example.smart.entities.Schedule;
import com.example.smart.services.ScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@CrossOrigin(origins = "http://localhost:3000")
public class ScheduleController {

    @Autowired
    private ScheduleService scheduleService;

    // API tạo lịch trình mới
    // POST /api/schedules
    // Body: Đối tượng Schedule chứa thông tin lịch trình (userId, deviceType,
    // deviceId, action, time, daysOfWeek)
    // Return: Lịch trình đã được tạo
    @PostMapping
    public ResponseEntity<Schedule> createSchedule(@RequestBody ScheduleDto scheduleDto) {
        return ResponseEntity.ok(scheduleService.createSchedule(scheduleDto));
    }

    // API cập nhật lịch trình
    // PUT /api/schedules/{scheduleId}
    // Path variable: ID của lịch trình cần cập nhật
    // Body: Đối tượng Schedule chứa thông tin cập nhật
    // Return: Lịch trình sau khi cập nhật
    @PutMapping("/{scheduleId}")
    public ResponseEntity<Schedule> updateSchedule(@PathVariable Long scheduleId, @RequestBody Schedule schedule) {
        return ResponseEntity.ok(scheduleService.updateSchedule(scheduleId, schedule));
    }

    // API xóa lịch trình
    // DELETE /api/schedules/{scheduleId}
    // Path variable: ID của lịch trình cần xóa
    // Return: 200 OK nếu xóa thành công
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.ok().build();
    }

    // API lấy danh sách lịch trình của một user
    // GET /api/schedules/user/{userId}
    // Path variable: ID của user
    // Return: Danh sách các lịch trình của user đó
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Schedule>> getUserSchedules(@PathVariable Long userId) {
        return ResponseEntity.ok(scheduleService.getUserSchedules(userId));
    }

    // API lấy danh sách lịch trình của một thiết bị
    // GET /api/schedules/device/{deviceType}/{deviceId}
    // Path variables: Loại thiết bị (light/door) và ID của thiết bị
    // Return: Danh sách các lịch trình của thiết bị đó
    @GetMapping("/device/{deviceType}/{deviceId}")
    public ResponseEntity<List<Schedule>> getDeviceSchedules(
            @PathVariable String deviceType,
            @PathVariable Long deviceId) {
        return ResponseEntity.ok(scheduleService.getDeviceSchedules(deviceType, deviceId));
    }

    // API lấy thông tin chi tiết của một lịch trình
    // GET /api/schedules/{scheduleId}
    // Path variable: ID của lịch trình
    // Return: Thông tin chi tiết của lịch trình
    @GetMapping("/{scheduleId}")
    public ResponseEntity<Schedule> getScheduleById(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(scheduleService.getScheduleById(scheduleId));
    }

    // API bật/tắt lịch trình
    // PUT /api/schedules/{scheduleId}/toggle
    // Path variable: ID của lịch trình
    // Query param: isActive - true để bật, false để tắt
    // Return: 200 OK nếu thành công
    @PutMapping("/{scheduleId}/toggle")
    public ResponseEntity<Void> toggleSchedule(
            @PathVariable Long scheduleId,
            @RequestParam Boolean isActive) {
        scheduleService.toggleSchedule(scheduleId, isActive);
        return ResponseEntity.ok().build();
    }
}
package com.example.smart.tasks;

import com.example.smart.entities.Schedule;
import com.example.smart.services.CameraService;
import com.example.smart.services.DoorServicesImp;
import com.example.smart.services.LightServicesImp;
import com.example.smart.services.ScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Component
public class ScheduleTask {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private LightServicesImp lightService;

    @Autowired
    private DoorServicesImp doorService;
    @Autowired
    private CameraService cameraService;

    @Scheduled(fixedRate = 60000) // Kiểm tra mỗi phút
    public void checkSchedules() {
        LocalTime currentTime = LocalTime.now().withSecond(0).withNano(0);
        DayOfWeek currentDay = DayOfWeek.from(java.time.LocalDate.now());
        int currentDayValue = currentDay.getValue();
        System.out.println("Time:" + currentTime + "Day:" + currentDayValue);
        // Lấy tất cả các lịch đang hoạt động và có thời gian trùng với thời gian hiện
        // tại
        // List<Schedule> activeSchedules = scheduleService.getDeviceSchedules("light",
        // null);
        // activeSchedules.addAll(scheduleService.getDeviceSchedules("door", null));
        List<Schedule> activeSchedules = scheduleService.getAllSchedules();

        for (Schedule schedule : activeSchedules) {
            if (schedule.getIsActive() &&
                    schedule.getTime().equals(currentTime) &&
                    schedule.getDaysOfWeek().contains(String.valueOf(currentDayValue))) {

                executeSchedule(schedule);
            }
        }
    }

    private void executeSchedule(Schedule schedule) {
        try {
            if ("light".equals(schedule.getDeviceType())) {
                if ("on".equals(schedule.getAction())) {
                    lightService.toggleLight(schedule.getDeviceId(), schedule.getUser().getUserId());
                } else if ("off".equals(schedule.getAction())) {
                    lightService.toggleLight(schedule.getDeviceId(), schedule.getUser().getUserId());
                }
            } else if ("door".equals(schedule.getDeviceType())) {
                if ("on".equals(schedule.getAction())) {
                    doorService.toggleDoorAlarm(schedule.getDeviceId(), schedule.getUser().getUserId());
                } else if ("off".equals(schedule.getAction())) {
                    doorService.toggleDoorAlarm(schedule.getDeviceId(), schedule.getUser().getUserId());
                }
            } else if ("camera".equals(schedule.getDeviceType())) {
                if ("on".equals(schedule.getAction())) {
                    cameraService.toggleRecordCamera(schedule.getDeviceId());
                } else if ("off".equals(schedule.getAction())) {
                    cameraService.toggleRecordCamera(schedule.getDeviceId());
                }
            }
        } catch (Exception e) {
            System.err.println("Error executing schedule: " + schedule.getScheduleId() + " - " + e.getMessage());
        }
    }
}
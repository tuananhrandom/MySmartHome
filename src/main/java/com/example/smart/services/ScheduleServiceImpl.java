package com.example.smart.services;

import com.example.smart.DTO.ScheduleDto;
import com.example.smart.entities.Schedule;
import com.example.smart.entities.User;
import com.example.smart.repositories.ScheduleRepository;
import com.example.smart.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScheduleServiceImpl implements ScheduleService {

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Schedule createSchedule(ScheduleDto scheduleDto) {
        // Kiểm tra user tồn tại
        // User user = userRepository.findById(schedule.getUser().getUserId())
        // .orElseThrow(() -> new IllegalArgumentException("User not found"));
        // schedule.setUser(user);
        // return scheduleRepository.save(schedule);
        Schedule newSchedule = new Schedule();
        newSchedule.setUser(userRepository.findById(scheduleDto.getUserId()).get());
        newSchedule.setDeviceType(scheduleDto.getDeviceType());
        newSchedule.setDeviceId(scheduleDto.getDeviceId());
        if (scheduleDto.getAction() == 1) {
            newSchedule.setAction("on");
        } else {
            newSchedule.setAction("off");
        }
        newSchedule.setTime(scheduleDto.getTime());
        newSchedule.setDaysOfWeek(scheduleDto.getDaysOfWeek());
        newSchedule.setIsActive(scheduleDto.getIsActive());
        return scheduleRepository.save(newSchedule);
    }

    @Override
    public Schedule updateSchedule(Long scheduleId, Schedule schedule) {
        Schedule existingSchedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found"));

        existingSchedule.setDeviceType(schedule.getDeviceType());
        existingSchedule.setDeviceId(schedule.getDeviceId());
        existingSchedule.setAction(schedule.getAction());
        existingSchedule.setTime(schedule.getTime());
        existingSchedule.setDaysOfWeek(schedule.getDaysOfWeek());
        existingSchedule.setIsActive(schedule.getIsActive());

        return scheduleRepository.save(existingSchedule);
    }

    @Override
    public void deleteSchedule(Long scheduleId) {
        scheduleRepository.deleteById(scheduleId);
    }

    @Override
    public List<Schedule> getUserSchedules(Long userId) {
        return scheduleRepository.findByUser_UserId(userId);
    }

    @Override
    public List<Schedule> getDeviceSchedules(String deviceType, Long deviceId) {
        return scheduleRepository.findByDeviceTypeAndDeviceId(deviceType, deviceId);
    }

    @Override
    public Schedule getScheduleById(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found"));
    }

    @Override
    public void toggleSchedule(Long scheduleId, Boolean isActive) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found"));
        schedule.setIsActive(isActive);
        scheduleRepository.save(schedule);
    }

    @Override
    public List<Schedule> getAllSchedules() {
        return scheduleRepository.findAll();
    }
}
package com.example.smart.repositories;

import com.example.smart.entities.Camera;
import com.example.smart.entities.CameraRecording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CameraRecordingRepository extends JpaRepository<CameraRecording, Long> {
    List<CameraRecording> findByCameraOrderByStartTimeDesc(Camera camera);

    List<CameraRecording> findByCameraAndStartTimeAfterAndEndTimeBeforeOrderByStartTimeDesc(
            Camera camera, LocalDateTime startTime, LocalDateTime endTime);

    List<CameraRecording> findByCamera_CameraIdOrderByStartTimeDesc(Long cameraId);
}
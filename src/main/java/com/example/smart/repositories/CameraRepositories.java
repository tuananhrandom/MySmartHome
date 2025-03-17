package com.example.smart.repositories;

import java.util.List;
import com.example.smart.entities.Camera;
import org.springframework.data.jpa.repository.JpaRepository;


public interface CameraRepositories extends JpaRepository<Camera, Long> {
    public List<Camera> findByUserUserId(Long userId);


}

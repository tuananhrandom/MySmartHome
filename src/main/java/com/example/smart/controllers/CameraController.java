package com.example.smart.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.smart.entities.Camera;
import com.example.smart.services.CameraService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("/camera")
public class CameraController {
    @Autowired
    CameraService cameraService;
    @GetMapping("/all")
    public List<Camera> getAllLights(@RequestParam String param) {
        return cameraService.getAllCamera();
    }

    @GetMapping("/{userId}")
    public List<Camera> getCameraByUserId(@PathVariable Long userId) {
        return cameraService.getCameraByUserId(userId);
    }
    

}

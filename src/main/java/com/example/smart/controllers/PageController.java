package com.example.smart.controllers;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.example.smart.entities.Door;
import com.example.smart.entities.Light;
import com.example.smart.entities.Notification;
import com.example.smart.entities.devices;
import com.example.smart.services.DoorServices;
import com.example.smart.services.LightServices;
import com.example.smart.services.NotificationService;
import com.example.smart.services.NotificationServiceImp;

import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {
    @Autowired
    LightServices lightServices;
    @Autowired
    DoorServices doorServices;

    @Autowired
    NotificationServiceImp notificationServiceImp;

    @GetMapping("/home")
    public String getAllLight(Model model) {
        List<Light> lights = lightServices.getAllLight();
        model.addAttribute("deviceTypes", devices.values());
        model.addAttribute("lights", lights);
        List<Notification> notifications = notificationServiceImp.getAllNotifications();
        model.addAttribute("notifications", notifications);
        return "home";
    }

    @GetMapping("/devices/{deviceType}")
    public String getDevicesByType(@PathVariable String deviceType, Model model) {
        if (deviceType.equals("Light")) {
            List<Light> lights = lightServices.getAllLight();
            model.addAttribute("lights", lights);
            return "fragments/lightTable";
        } else if (deviceType.equals("Door")) {
            List<Door> doors = doorServices.getAllDoor();
            model.addAttribute("doors", doors);
            return "fragments/doorTable";
        } else if (deviceType.equals("Camera")) {
            return "fragments/CameraTable";
        }

        return "fragments/lightTable";
    }

    @GetMapping("/notification")
    public String getNotification(Model model) {
        List<Notification> notifications = notificationServiceImp.getAllNotifications();
        model.addAttribute("notifications", notifications);
        return "notification";
    }

    @GetMapping("/testcam")
    public String getTestCam() {
        return "test";
    }

}

package com.example.smart.entities;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "device_activities")
public class DeviceActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long activityId;
    
    @Column(name = "device_type", nullable = false)
    private String deviceType; // "LIGHT", "DOOR", "CAMERA"
    
    @Column(name = "device_id", nullable = false)
    private Long deviceId;
    
    @Column(name = "device_name", nullable = true)
    private String deviceName;
    
    @Column(name = "activity_type", nullable = false)
    private String activityType; // "ON", "OFF", "CONNECT", "DISCONNECT", "OPEN", "CLOSE", "ALARM_ON", "ALARM_OFF", "STREAM_START", "STREAM_END"
    
    @Column(name = "previous_state", nullable = true)
    private String previousState;
    
    @Column(name = "current_state", nullable = true) 
    private String currentState;
    
    @Column(name = "ip_address", nullable = true)
    private String ipAddress;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "activity_time", nullable = false)
    private LocalDateTime activityTime;
    
    @Column(name = "description", nullable = true, columnDefinition = "NVARCHAR(MAX)")
    private String description;
    
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "user_id", nullable = true)
    private User user;
    
    public String getFormattedDate() {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return activityTime.format(dateFormatter);
    }
    
    public String getFormattedTime() {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        return activityTime.format(timeFormatter);
    }
}
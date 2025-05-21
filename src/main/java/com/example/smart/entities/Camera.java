package com.example.smart.entities;

import jakarta.persistence.Id;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "cameras")
public class Camera {
    @Id
    private Long cameraId;

    @Column(name = "cameraName", nullable = true)
    private String cameraName;
    @Column(name = "cameraIp", nullable = true)
    private String cameraIp;
    @Column(name = "cameraStatus", nullable = true)
    private Integer cameraStatus;
    @Column(name = "isRecord", nullable = false)
    private Boolean isRecord = false;
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "userId", nullable = true)
    private User user;

    @ManyToMany
    @JoinTable(name = "camera_shared_users", joinColumns = @JoinColumn(name = "camera_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    @JsonIgnore
    private Set<User> sharedUsers;

    @OneToMany(mappedBy = "camera", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<CameraRecording> recordings;

    @JsonProperty("ownerId")
    public Long getOwnerId() {
        return user != null ? user.getUserId() : null;
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "cameraCreatedTime", nullable = false)
    private LocalDateTime createdTime;

    public String getTimeCreate() {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        return createdTime.format(timeFormatter);
    }

    public String getDateCreate() {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return createdTime.format(dateFormatter);
    }
}
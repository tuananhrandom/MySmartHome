package com.example.smart.entities;

import jakarta.persistence.Id;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
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
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "userId", nullable = true)
    private User user;

    @JsonProperty("ownerId")
    public Long getOwnerId() {
        return user != null ? user.getUserId() : null;
    }

}
package com.example.smart.entities;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.ManyToOne;

@Entity
@Table(name = "doors")
@Getter
@Setter
public class Door {
    @Id
    private Long doorId;

    @Column(name = "doorName", unique = false, nullable = true)
    private String doorName;

    @Column(name = "doorStatus", unique = false, nullable = true)
    private Integer doorStatus = null;
    @Column(name = "doorLockDown")
    private Integer doorLockDown = null;
    @Column(name = "dooralert")
    private Integer doorAlert = 0;
    @Column(name = "doorIp", unique = false, nullable = true)
    private String doorIp = null;
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "ownerId", nullable = true)
    User user;

    @JsonProperty("ownerId")
    public Long getOwnerId() {
        return user != null ? user.getUserId() : null;
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "doorCreatedTime", nullable = false)
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

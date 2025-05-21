package com.example.smart.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.FetchType;

@Entity
@Getter
@Setter
@Table(name = "lights")
public class Light {

    // @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    Long lightId;

    @Column(name = "lightname", nullable = true)
    private String lightName;

    @Column(name = "lightstatus")
    private Integer lightStatus = null;

    @Column(name = "lightIP")
    private String lightIp = null;
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "ownerId", nullable = true)
    User user;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "lightCreatedTime", nullable = false)
    private LocalDateTime createdTime;

    @JsonProperty("ownerId")
    public Long getOwnerId() {
        return user != null ? user.getUserId() : null;
    }

    public String getDateCreate() {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return createdTime.format(dateFormatter);
    }

    public String getTimeCreate() {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        return createdTime.format(timeFormatter);
    }

}

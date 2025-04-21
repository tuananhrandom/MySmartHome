package com.example.smart.entities;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.ManyToOne;

@Entity
@Getter
@Setter
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long notificationId;
    @Column(name = "notificationtype", nullable = false)
    private String notificationType;
    @Column(name = "notificationtitle", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String notificationTitle;
    @Column(name = "notificationcontent", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String notificationContent;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "notificationtime", nullable = false)
    private LocalDateTime time;
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "actorId", nullable = true)
    User user;

    public String getDateCreate() {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return time.format(dateFormatter);
    }

    public String getTimeCreate() {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        return time.format(timeFormatter);
    }
}

package com.example.smart.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name ="notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long notificationId;
    @Column(name = "notificationimage", nullable = false)
    private String notificationImage;
    @Column(name="notificationtitle", nullable = false)
    private String notificationTitle;
    @Column(name="notificationcontent", nullable = false)
    private  String notificationContent;
    @Column(name="notificationtime", nullable = false)
    private LocalDateTime time;
    @ManyToOne
    @JoinColumn(name ="actorId", nullable =false)
    User actor;
    

}

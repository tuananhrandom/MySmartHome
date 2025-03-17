package com.example.smart.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "doors")
@Getter
@Setter
public class Door {
    @Id
    private Long doorId;
    @Column(name = "doorName", unique = false, nullable = false)
    private String doorName;
    @ManyToOne
    @JoinColumn(name = "ownerId", nullable = true)
    User user;
    @Column(name = "doorStatus", unique = false, nullable = true)
    private Integer doorStatus = null;
    @Column(name = "doorLockDown")
    private Integer doorLockDown = null;
    @Column(name = "dooralert")
    private Integer doorAlert = 0;
    @Column(name = "doorIp", unique = false, nullable = true)
    private String doorIp = null;
}

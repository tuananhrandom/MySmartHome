package com.example.smart.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.smart.entities.Door;

public interface DoorRepositories extends JpaRepository<Door, Long> {
    public Door findByDoorName(String DoorName);

    public Door findByDoorIp(String DoorIp);

}

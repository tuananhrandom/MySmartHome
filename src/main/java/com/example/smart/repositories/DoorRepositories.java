package com.example.smart.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.smart.entities.Door;
import java.util.List;

public interface DoorRepositories extends JpaRepository<Door, Long> {
    public Door findByDoorName(String DoorName);

    public Door findByDoorIp(String DoorIp);

    public List<Door> findByUser_UserId(Long userId);

}

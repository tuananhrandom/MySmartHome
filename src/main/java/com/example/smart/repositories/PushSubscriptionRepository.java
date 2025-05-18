package com.example.smart.repositories;

import com.example.smart.entities.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM PushSubscription p WHERE p.endpoint = :endpoint AND p.userId = :userId")
    void deleteByEndpointAndUserId(@Param("endpoint") String endpoint, @Param("userId") Long userId);
}

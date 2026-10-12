package com.mixyn.outfit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutfitRepository extends JpaRepository<Outfit, Long> {

    List<Outfit> findByUserIdOrderByCreatedAtDesc(Long userId);
}

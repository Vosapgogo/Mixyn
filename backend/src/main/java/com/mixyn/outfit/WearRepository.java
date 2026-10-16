package com.mixyn.outfit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WearRepository extends JpaRepository<Wear, Long> {

    List<Wear> findByUserIdOrderByWornOn(Long userId);

    Optional<Wear> findByUserIdAndWornOn(Long userId, LocalDate wornOn);

    /** The last day an outfit was worn, if it ever was. */
    Optional<Wear> findFirstByOutfitIdOrderByWornOnDesc(Long outfitId);

    List<Wear> findByOutfitId(Long outfitId);

    long countByOutfitId(Long outfitId);

    void deleteByOutfitId(Long outfitId);
}

package com.mixyn.outfit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutfitRepository extends JpaRepository<Outfit, Long> {

    /** Every outfit of the user, earlier versions included (the calendar needs them). */
    List<Outfit> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** The user's saved outfits as they are now: the list in the Outfits tab. */
    List<Outfit> findByUserIdAndReplacedByIsNullOrderByCreatedAtDesc(Long userId);

    /** The earlier versions of an outfit, kept for the days it was worn before it was changed. */
    List<Outfit> findByReplacedBy(Long outfitId);

    /** Every outfit that has this item as one of its pieces. */
    List<Outfit> findDistinctByPiecesItemId(Long itemId);
}

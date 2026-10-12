package com.mixyn.outfit;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One day of the outfit calendar: on this day the user wore this saved outfit ("Wear it today").
 * A day holds one outfit, which the database guarantees: wearing another one replaces it.
 */
@Entity
@Table(name = "outfit_wears", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "worn_on"}))
public class Wear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "outfit_id", nullable = false)
    private Long outfitId;

    /** The day as the user's phone saw it; no time and no time zone. */
    @Column(name = "worn_on", nullable = false)
    private LocalDate wornOn;

    protected Wear() {
    }

    public Wear(Long userId, Long outfitId, LocalDate wornOn) {
        this.userId = userId;
        this.outfitId = outfitId;
        this.wornOn = wornOn;
    }

    public Long getOutfitId() {
        return outfitId;
    }

    public void setOutfitId(Long outfitId) {
        this.outfitId = outfitId;
    }

    public LocalDate getWornOn() {
        return wornOn;
    }
}

package com.mixyn.outfit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * An outfit the user saved. Its pieces are rows of their own (table outfit_pieces): which item, and
 * where it is worn. The picture of the whole outfit, as the app drew it, is a file like the item photos.
 */
@Entity
@Table(name = "outfits")
public class Outfit {

    /** One piece of the outfit: the item and its place. */
    @Embeddable
    public static class Piece {

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private Slot slot;

        @Column(name = "item_id", nullable = false)
        private Long itemId;

        protected Piece() {
        }

        public Piece(Slot slot, Long itemId) {
            this.slot = slot;
            this.itemId = itemId;
        }

        public Slot getSlot() {
            return slot;
        }

        public Long getItemId() {
            return itemId;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "image_file", nullable = false)
    private String imageFile;

    /** In the order they are listed on the outfit's card: top, bottom, shoes, then the layers. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "outfit_pieces", joinColumns = @JoinColumn(name = "outfit_id"))
    @OrderColumn(name = "position")
    private List<Piece> pieces = new ArrayList<>();

    /** The outfit's categories, comma-separated ("Casual,Work"), from the same list as item tags. */
    private String tags;

    /** One or more seasons, comma-separated ("Spring,Autumn"), or "All". */
    @Column(length = 30)
    private String season;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * Set on an earlier version of an outfit: the id of the outfit as it is now. When an outfit that
     * was worn is changed, its worn days move to such a copy, so the calendar keeps showing what was
     * worn on them. A copy isn't listed with the saved outfits and can't be changed.
     */
    @Column(name = "replaced_by")
    private Long replacedBy;

    protected Outfit() {
    }

    public Outfit(Long userId, String imageFile, List<Piece> pieces) {
        this.userId = userId;
        this.imageFile = imageFile;
        this.pieces.addAll(pieces);
    }

    /** A copy of this outfit as it is right now (pieces, picture, labels, date), kept for the days it was worn. */
    Outfit versionAsWorn() {
        List<Piece> copied = new ArrayList<>();
        for (Piece piece : pieces) {
            copied.add(new Piece(piece.getSlot(), piece.getItemId()));
        }
        Outfit before = new Outfit(userId, imageFile, copied);
        before.tags = tags;
        before.season = season;
        before.createdAt = createdAt;
        before.replacedBy = id;
        return before;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getImageFile() {
        return imageFile;
    }

    public void setImageFile(String imageFile) {
        this.imageFile = imageFile;
    }

    public List<Piece> getPieces() {
        return pieces;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** The outfit as it is now, if this is an earlier version of it; null for an outfit as it is. */
    public Long getReplacedBy() {
        return replacedBy;
    }
}

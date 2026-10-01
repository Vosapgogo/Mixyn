package com.mixyn.item;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findByUserIdOrderByCreatedAtDesc(Long userId);
}

package com.mixyn.item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * When Hibernate first creates a table it adds database checks listing the allowed values of its enum
 * columns ("category in ('TOP', 'BOTTOM', …)"), and it never updates them afterwards. A database
 * created before bags and accessories existed would therefore refuse the new values. The lists live in
 * the enums (Category, ItemType, and Slot for the pieces of a saved outfit), so the checks are simply
 * removed at startup.
 */
@Component
public class EnumColumnFix implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnumColumnFix.class);

    private final JdbcTemplate jdbc;

    public EnumColumnFix(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute("ALTER TABLE items DROP CONSTRAINT IF EXISTS items_category_check");
            jdbc.execute("ALTER TABLE items DROP CONSTRAINT IF EXISTS items_type_check");
            jdbc.execute("ALTER TABLE outfit_pieces DROP CONSTRAINT IF EXISTS outfit_pieces_slot_check");
        } catch (Exception e) {
            log.warn("Could not remove the old enum checks: {}", e.toString());
        }
    }
}

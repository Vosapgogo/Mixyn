package com.mixyn.item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * When Hibernate first creates the items table it adds database checks listing the allowed values of
 * the enum columns ("category in ('TOP', 'BOTTOM', …)"), and it never updates them afterwards. A database
 * created before bags and accessories existed would therefore refuse the new values. The lists live in
 * Category and ItemType, so the checks are simply removed at startup.
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
        } catch (Exception e) {
            log.warn("Could not remove the old enum checks from the items table: {}", e.toString());
        }
    }
}

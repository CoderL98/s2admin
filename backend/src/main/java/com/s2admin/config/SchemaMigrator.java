package com.s2admin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 无 Flyway 依赖时的轻量迁移:按表是否存在执行 V2 SQL。
 * MySQL/Postgres/prod 可配合 ddl-auto=validate 使用。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn("sys_user", "pwd_reset", "INTEGER DEFAULT 0");
        ensureColumn("sys_user", "lock_until", "BIGINT");
        ensureColumn("sys_user", "province", "VARCHAR(50)");
        ensureColumn("sys_user", "city", "VARCHAR(50)");
        ensureColumn("sys_user", "district", "VARCHAR(50)");
        ensureColumn("sys_user", "tenant_id", "BIGINT");
        ensureColumn("sys_file", "tenant_id", "BIGINT");
        ensureColumn("sys_approval", "tenant_id", "BIGINT");
        ensureColumn("sys_approval", "approved_user_ids", "TEXT");
        ensureColumn("sys_flow_node", "node_type", "INTEGER DEFAULT 1");
        ensureColumn("sys_flow_node", "sign_mode", "INTEGER DEFAULT 1");
        ensureColumn("sys_flow_node", "reject_to", "INTEGER");
        ensureColumn("sys_flow_node", "condition_expr", "VARCHAR(200)");
        ensureColumn("sys_flow_node", "yes_seq", "INTEGER");
        ensureColumn("sys_flow_node", "no_seq", "INTEGER");
        if (tableExists("sys_dept")) {
            return;
        }
        String vendor = detectVendor();
        String path = "db/migration/" + vendor + "/V2__p1_modules.sql";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                log.warn("未找到迁移脚本 {}", path);
                return;
            }
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String stmt : sql.split(";")) {
                String trimmed = stmt.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                jdbcTemplate.execute(trimmed);
            }
            log.info("已执行 P1 表结构迁移: {}", path);
        } catch (Exception e) {
            log.warn("P1 表结构迁移跳过(将回退 Hibernate ddl-auto): {}", e.getMessage());
        }
    }

    private void ensureColumn(String table, String column, String definition) {
        if (!tableExists(table)) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("已补齐列 {}.{}", table, column);
        } catch (Exception e) {
            log.debug("补齐列 {}.{} 跳过: {}", table, column, e.getMessage());
        }
    }

    private boolean tableExists(String table) {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String detectVendor() {
        if (jdbcTemplate.getDataSource() == null) {
            return "sqlite";
        }
        try (var conn = jdbcTemplate.getDataSource().getConnection()) {
            String url = conn.getMetaData().getURL();
            if (url != null && url.contains("mysql")) {
                return "mysql";
            }
            if (url != null && url.contains("postgres")) {
                return "postgres";
            }
        } catch (Exception ignored) {
        }
        return "sqlite";
    }
}

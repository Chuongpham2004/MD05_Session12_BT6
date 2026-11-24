package org.example.md05_session12_bt6.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final String serviceName;

    public HealthController(JdbcTemplate jdbcTemplate,
                            @Value("${spring.application.name}") String serviceName) {
        this.jdbcTemplate = jdbcTemplate;
        this.serviceName = serviceName;
    }

    /** Healthcheck nội bộ, dùng cho Docker HEALTHCHECK. */
    @GetMapping("/health")
    public Map<String, Object> health() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", serviceName);
        body.put("hostname", InetAddress.getLocalHost().getHostName());
        body.put("status", "UP");
        return body;
    }

    /** Kiểm tra service gọi được DB qua tên "db" trong network nội bộ. */
    @GetMapping({"/api/users/db-check", "/api/orders/db-check"})
    public Map<String, Object> dbCheck() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", serviceName);
        try {
            body.put("db", jdbcTemplate.queryForObject("select version()", String.class));
            body.put("status", "CONNECTED");
        } catch (Exception e) {
            body.put("status", "DB_UNREACHABLE");
            body.put("error", e.getMessage());
        }
        return body;
    }
}

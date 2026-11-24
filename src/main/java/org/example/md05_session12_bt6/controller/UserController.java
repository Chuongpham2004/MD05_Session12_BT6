package org.example.md05_session12_bt6.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Profile("user")
@RequestMapping("/api/users")
public class UserController {

    @GetMapping
    public List<Map<String, Object>> getUsers() {
        return List.of(
                Map.of("id", 1, "name", "Nguyen Van A"),
                Map.of("id", 2, "name", "Tran Thi B")
        );
    }
}

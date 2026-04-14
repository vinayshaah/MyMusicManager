package com.example.demo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.repo.MusicRepository;
import com.example.repo.TestMusicRepository;

import java.util.Map;

@RestController
@ComponentScan("com.example.repo")
//@EnableJpaRepositories(basePackages = "com.example.repo") // Specifically find JPA repos
@RequestMapping("/api/health")
public class HealthCheckController {

    @Autowired
    private MusicRepository testMusicRepository;

    @GetMapping("/db")
    public Map<String, Object> checkDatabase() {
        try {
            long count = testMusicRepository.count();
            return Map.of(
                "status", "UP",
                "database", "PostgreSQL (Connected)",
                "total_records", count,
                "message", "Connection successful!"
            );
        } catch (Exception e) {
            return Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "details", "Ensure your Docker container 'music-db' is running."
            );
        }
    }
}
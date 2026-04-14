package com.example.repo;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.entity.MusicSong;

@Repository
@Profile("test")
public interface TestMusicRepository extends JpaRepository<MusicSong, Long> {
    // Methods here only exist when the 'test' profile is active
    @Query("SELECT COUNT(m) FROM MusicSong m WHERE m.title LIKE 'Mock%'")
    long countMockSongs();
}
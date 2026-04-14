package com.example.repo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.entity.MusicSong;

import java.util.List;

@Repository
public interface MusicRepository extends JpaRepository<MusicSong, Long> {

    @Query(value = "SELECT * FROM music_catalog " +
                   "ORDER BY embedding <=> cast(:queryVector as vector) " +
                   "LIMIT :limit", nativeQuery = true)
    List<MusicSong> findSimilarSongs(@Param("queryVector") String queryVector, 
                                     @Param("limit") int limit);

    @Query(value = "SELECT id,song_title,movie_name,year,singers,lyricists,composer,semantic_summary,embedding FROM music_catalog " +
               "ORDER BY embedding <=> cast(:queryVector as vector) " +
               "LIMIT 5",
                nativeQuery = true)
    List<MusicSong> findTop5SemanticMatches(@Param("queryVector") String queryVector);
}
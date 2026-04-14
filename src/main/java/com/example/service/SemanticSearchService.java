package com.example.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.entity.MusicSong;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class SemanticSearchService {

    @PersistenceContext
    private EntityManager entityManager;

    public List<MusicSong> findTop5SemanticMatches(String queryVector) {
        String sql = "SELECT * FROM music_catalog " +
                     "ORDER BY embedding <=> CAST(:queryVector AS vector) " +
                     "LIMIT 5";

        return entityManager.createNativeQuery(sql, MusicSong.class)
                .setParameter("queryVector", queryVector)
                .getResultList();
    }

}

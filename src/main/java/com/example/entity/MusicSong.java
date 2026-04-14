package com.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "music_catalog")
public class MusicSong {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") 
    private Long id;

    @Column(name = "song_title", nullable = false)
    private String title;
    @Column(name = "movie_name")
    private String movieName;
    @Column(name = "semantic_summary", columnDefinition = "TEXT") // Store Gemini's summary     
    private String thematicSummary;
    @Column(name = "embedding", columnDefinition = "vector(768)") // Store Gemini's embedding vector
    private double[] embedding ;
    // Getters and Setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getMovieName() {
        return movieName;
    }
    public void setMovieName(String movieName) {
        this.movieName = movieName;
    }
    public String getThematicSummary() {
        return thematicSummary;
    }
    public void setThematicSummary(String thematicSummary) {
        this.thematicSummary = thematicSummary;
    }
    public double[] getEmbedding() {
        return embedding;
    }
    public void setEmbedding(double[] embedding) {
        this.embedding = embedding;
    }

}

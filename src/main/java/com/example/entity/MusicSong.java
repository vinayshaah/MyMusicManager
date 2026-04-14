package com.example.entity;

import com.example.util.VectorConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
    public Integer getYear() {
        return year;
    }
    public void setYear(Integer year) {
        this.year = year;
    }
    public String getSinger() {
        return singer;
    }
    public void setSinger(String singer) {
        this.singer = singer;
    }
    public String getLyricist() {
        return lyricist;
    }
    public void setLyricist(String lyricist) {
        this.lyricist = lyricist;
    }
    public String getComposer() {
        return composer;
    }
    public void setComposer(String composer) {
        this.composer = composer;
    }
    @Column(name = "movie_name")
    private String movieName;
    @Column(name = "year")
    private Integer year;
    @Column(name = "singers", columnDefinition = "TEXT")
    private String singer;
    @Column(name = "lyricists", columnDefinition = "TEXT")
    private String lyricist;
    @Column(name = "composer", columnDefinition = "TEXT")  
    private String composer;
    @Column(name = "semantic_summary", columnDefinition = "TEXT") // Store Gemini's summary     
    private String thematicSummary;
    @Convert(converter = VectorConverter.class)
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

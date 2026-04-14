package com.example.dto;

import com.example.entity.MusicSong;

public class MusicSongDTO {
    private String songTitle;
    private String movieName;
    private Integer releaseYear;
    private String semanticSummary;

    public MusicSongDTO(MusicSong song) {
        this.songTitle = song.getTitle();
        this.movieName = song.getMovieName();
        this.releaseYear = song.getYear();
        this.semanticSummary = song.getThematicSummary();
    }

    public String getSongTitle() { return songTitle; }
    public String getMovieName() { return movieName; }
    public Integer getReleaseYear() { return releaseYear; }
    public String getSemanticSummary() { return semanticSummary; }
}
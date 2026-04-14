package com.example.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.example.entity.MusicSong;
import com.example.repo.MusicRepository;
import com.google.gson.JsonObject;

import org.springframework.beans.factory.annotation.Autowired;

@Service
public class MusicScrapingWorker {

    @Autowired
    private MockGeminiService mockGeminiService;

    @Autowired
    private GeminiSongExtractorService geminiSongExtractorService;

    @Autowired
    private WikiScraperService wikiScraperService;

    @Autowired
    private MusicRepository repository; 

    @RabbitListener(queues = "movie_url_queue")
    public void processMovieMessage(String movieUrl) {
        System.out.println("Worker received URL: " + movieUrl);
        List<Map<String, String>> songs = new ArrayList<>();
        try {
            // 1. JSoup: Scrape the movie's soundtrack table
            songs = wikiScraperService.scrapeSongsFromMovie(movieUrl);
            // 2. Gemini: For each song, get an 'emotional/thematic' summary
            for (Map<String, String> song : songs) {
                String songName = song.get("title");
                String movieName = movieUrl.substring(movieUrl.lastIndexOf("/")+1).replace("_", " ");
                String prompt = "Analyze the song '"+songName+"' from the movie '" + movieName + "'. Provide a concise one-sentence description of its emotional theme, mood, and lyrical context. For example: 'A melancholic and soulful track about finding a glimmer of hope amidst deep grief and loss.' ";
                /*JsonObject requestBody = geminiSongExtractorService.buildRequestBody(prompt); // This is a placeholder. You would call the actual Gemini API here and get the response.
                String thematicSummary = geminiSongExtractorService.callGeminiAPI(requestBody); */
                String thematicSummary = mockGeminiService.getThematicSummary(songName, movieName); // Mock response for testing without hitting Gemini API
                System.out.println("Thematic summary obtained for " + songName + " from " + movieUrl + ": " + thematicSummary);
                // 3. Gemini: Convert summary to Vector
                // Get the "Vector" from Gemini Embedding API
                //double[] vector = geminiSongExtractorService.getEmbedding(thematicSummary);
                double[] vector = mockGeminiService.getEmbedding(thematicSummary); // Mock embedding for testing without hitting Gemini API
                System.out.println("Embedding vector obtained for " + songName + " from " + movieUrl + ": " + vector.length + " dimensions");
                // You would then take the response and save it to your database along with the song and movie info.

                // 4. JPA: Save to Postgres
                MusicSong entity = new MusicSong();
                entity.setTitle(songName);
                entity.setThematicSummary(thematicSummary);
                entity.setEmbedding(vector);
                repository.save(entity);
            }
            
            
            
            System.out.println("Successfully processed and embedded: " + movieUrl);
            
        } catch (Exception e) {
            // If it fails, the message can be returned to the queue or sent to a Dead Letter Queue
            System.err.println("Failed to process " + movieUrl + ": " + e.getMessage());
        }
    }
}
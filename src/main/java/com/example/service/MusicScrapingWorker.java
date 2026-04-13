package com.example.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class MusicScrapingWorker {

    @RabbitListener(queues = "movie_url_queue")
    public void processMovieMessage(String movieUrl) {
        System.out.println("Worker received URL: " + movieUrl);
        
        try {
            // 1. JSoup: Scrape the movie's soundtrack table
            // 2. Gemini: For each song, get an 'emotional/thematic' summary
            // 3. Gemini: Convert summary to Vector
            // 4. JPA: Save to Postgres
            
            System.out.println("Successfully processed and embedded: " + movieUrl);
            
        } catch (Exception e) {
            // If it fails, the message can be returned to the queue or sent to a Dead Letter Queue
            System.err.println("Failed to process " + movieUrl + ": " + e.getMessage());
        }
    }
}
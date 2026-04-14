package com.example.service;

import org.springframework.stereotype.Service;
import java.util.Random;

@Service
public class MockGeminiService {

    private final Random random = new Random();

    // 1. Enrich incomplete metadata
    public String enrichMetadata(String songTitle) {
        simulateLatency(500);
        return "Singer: Mock Artist, Lyricist: Mock Poet, Composer: Mock Maestro";
    }

    // 2. Retrieve thematic summary
    public String getThematicSummary(String songTitle, String movie) {
        simulateLatency(800);
        String[] moods = {"melancholic", "joyful", "romantic", "nostalgic", "energetic"};
        String mood = moods[random.nextInt(moods.length)];
        return "This is a " + mood + " song from " + movie + " that evokes deep emotions of " + mood + ".";
    }

    // 3. Get embedding vector (768 dimensions)
    public double[] getEmbedding(String text) {
        simulateLatency(300);
        double[] mockVector = new double[768];
        for (int i = 0; i < 768; i++) {
            // Generates random values between -1.0 and 1.0
            mockVector[i] = -1.0 + (2.0 * random.nextDouble());
        }
        return mockVector;
    }

    private void simulateLatency(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
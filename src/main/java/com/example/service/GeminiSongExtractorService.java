package com.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Service
public class GeminiSongExtractorService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiSongExtractorService.class);
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

    @Value("${google.gemini.api-key}")
    private String apiKey;

    /**
     * Extract songs from Wikipedia soundtrack HTML using Google Gemini.
     * 
     * @param movieUrl the URL of the movie page
     * @param soundtrackHtml the HTML content of the soundtrack section
     * @return List of songs with extracted metadata (title, singers, composer, lyricists, length)
     */
    public List<Map<String, String>> extractSongsWithGemini(String movieUrl, String soundtrackHtml) {
        List<Map<String, String>> songs = new ArrayList<>();
        
        try {
            logger.info("[GeminiSongExtractorService] Extracting songs from {} using Gemini AI", movieUrl);
            
            // Create request body
            String prompt = buildExtractionPrompt(soundtrackHtml);
            JsonObject requestBody = buildRequestBody(prompt);
            
            // Make HTTP request to Gemini API
            String response = callGeminiAPI(requestBody);
            
            logger.debug("[GeminiSongExtractorService] Gemini response: {}", response);

            // Parse JSON response from Gemini
            songs = parseGeminiResponse(response);
            
            logger.info("[GeminiSongExtractorService] Successfully extracted {} songs from {}", songs.size(), movieUrl);
            
        } catch (Exception e) {
            logger.error("[GeminiSongExtractorService] Error extracting songs from {}: {}", movieUrl, e.getMessage());
        }
        
        return songs;
    }

    /**
     * Build the request body for Gemini API.
     */
    private JsonObject buildRequestBody(String prompt) {
        JsonObject requestBody = new JsonObject();
        
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        parts.add(part);
        
        content.add("parts", parts);
        contents.add(content);
        
        requestBody.add("contents", contents);
        
        // Add generation config for better JSON output
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.2);
        generationConfig.addProperty("topP", 0.8);
        generationConfig.addProperty("topK", 40);
        generationConfig.addProperty("maxOutputTokens", 2048);
        requestBody.add("generationConfig", generationConfig);
        
        return requestBody;
    }

    /**
     * Call the Gemini API using HTTP client.
     */
    private String callGeminiAPI(JsonObject requestBody) throws Exception {
        String url = GEMINI_API_URL + "?key=" + apiKey;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            logger.error("[GeminiSongExtractorService] Gemini API returned status code: {}", response.statusCode());
            logger.error("[GeminiSongExtractorService] Response body: {}", response.body());
            throw new Exception("Gemini API error: " + response.statusCode());
        }
        
        return response.body();
    }

    /**
     * Build a prompt for Gemini to extract song details from Wikipedia HTML.
     */
    private String buildExtractionPrompt(String soundtrackHtml) {
        return """
            You are a Wikipedia metadata extractor. Your task is to extract all songs from the provided Wikipedia soundtrack section HTML.
            
            Return ONLY a valid JSON array with the following structure:
            [
              {
                "title": "Song Title",
                "singers": "Singer Name(s)",
                "composer": "Composer Name",
                "lyricists": "Lyricist Name(s)",
                "length": "Duration (e.g., 3:45)"
              }
            ]
            
            IMPORTANT RULES:
            1. Extract every song exactly as written in the HTML
            2. If a field is not found on the page, use an empty string ""
            3. Do NOT add explanations or comments
            4. Return ONLY the JSON array, nothing else
            5. Preserve original text capitalization and formatting
            
            Here is the Wikipedia soundtrack HTML:
            
            """ + soundtrackHtml;
    }

    /**
     * Parse the JSON response from Gemini and convert to list of song maps.
     */
    private List<Map<String, String>> parseGeminiResponse(String jsonResponse) {
        List<Map<String, String>> songs = new ArrayList<>();
        
        try {
            // Parse the Gemini API response structure
            JsonObject responseObj = gson.fromJson(jsonResponse, JsonObject.class);
            
            // Extract the text content from candidates
            if (responseObj.has("candidates") && responseObj.get("candidates").isJsonArray()) {
                JsonArray candidates = responseObj.getAsJsonArray("candidates");
                if (candidates.size() > 0) {
                    JsonObject candidate = candidates.get(0).getAsJsonObject();
                    if (candidate.has("content") && candidate.get("content").isJsonObject()) {
                        JsonObject content = candidate.getAsJsonObject("content");
                        if (content.has("parts") && content.get("parts").isJsonArray()) {
                            JsonArray parts = content.getAsJsonArray("parts");
                            if (parts.size() > 0) {
                                JsonObject part = parts.get(0).getAsJsonObject();
                                if (part.has("text")) {
                                    String textContent = part.get("text").getAsString();
                                    songs = parseJsonContent(textContent);
                                }
                            }
                        }
                    }
                }
            }
            
            logger.debug("[GeminiSongExtractorService] Parsed {} songs from Gemini response", songs.size());
            
        } catch (Exception e) {
            logger.error("[GeminiSongExtractorService] Failed to parse Gemini response: {}", e.getMessage());
        }
        
        return songs;
    }

    /**
     * Parse the JSON content extracted from Gemini response text.
     */
    private List<Map<String, String>> parseJsonContent(String textContent) {
        List<Map<String, String>> songs = new ArrayList<>();
        
        try {
            // Clean up the response in case Gemini adds markdown or extra text
            String cleanJson = textContent.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            }
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            // Parse JSON array
            JsonArray songsArray = gson.fromJson(cleanJson, JsonArray.class);

            if (songsArray != null) {
                for (var element : songsArray) {
                    if (element.isJsonObject()) {
                        JsonObject songObj = element.getAsJsonObject();
                        Map<String, String> song = new HashMap<>();
                        
                        song.put("title", getJsonString(songObj, "title"));
                        song.put("singers", getJsonString(songObj, "singers"));
                        song.put("composer", getJsonString(songObj, "composer"));
                        song.put("lyricists", getJsonString(songObj, "lyricists"));
                        song.put("length", getJsonString(songObj, "length"));
                        
                        songs.add(song);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("[GeminiSongExtractorService] Failed to parse JSON content: {}", e.getMessage());
        }
        
        return songs;
    }

    /**
     * Helper to safely extract string from JSON object.
     */
    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString().trim();
        }
        return "";
    }
}

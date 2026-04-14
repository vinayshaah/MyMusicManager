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
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";
    //private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent";
    private static final int MAX_PROMPT_LENGTH = 38000;

    @Value("${google.gemini.api-key}")
    private String apiKey;

    /**
     * Enrich incomplete songs with missing metadata using Google Gemini.
     * Gemini fills in only the fields that are empty, preserving Jsoup data.
     * 
     * @param movieUrl the URL of the movie page
     * @param soundtrackHtml the HTML content of the soundtrack section
     * @param incompleteSongs songs extracted by Jsoup with potentially missing fields
     * @return List of songs with enriched metadata (Jsoup data + Gemini enhancements)
     */
    public List<Map<String, String>> extractSongsWithGemini(String movieUrl, String soundtrackHtml, 
                                                             List<Map<String, String>> incompleteSongs) {
        List<Map<String, String>> enrichedSongs = new ArrayList<>(incompleteSongs);
        
        try {
            logger.info("[GeminiSongExtractorService] Enriching {} incomplete songs from {} using Gemini AI", 
                incompleteSongs.size(), movieUrl);
            
            // Create request body with dynamic prompt
            String prompt = buildExtractionPrompt(soundtrackHtml, incompleteSongs);
            logger.info("[GeminiSongExtractorService] Built prompt for {} songs: {}", incompleteSongs.size(), prompt);
            prompt = truncatePrompt(prompt);
            JsonObject requestBody = buildRequestBody(prompt);
            logger.info("[GeminiSongExtractorService] Request body JSON: {}", requestBody.toString());
            
            // Make HTTP request to Gemini API
            String response = callGeminiAPI(requestBody);
            
            logger.debug("[GeminiSongExtractorService] Gemini response: {}", response);

            // Parse JSON response and merge with original incomplete songs
            List<Map<String, String>> geminiSongs = parseGeminiResponse(response, incompleteSongs);
            
            // Merge Gemini results with original songs (Gemini fills in the gaps)
            enrichedSongs = mergeSongs(incompleteSongs, geminiSongs);
            
            logger.info("[GeminiSongExtractorService] Successfully enriched {} songs from {}", 
                enrichedSongs.size(), movieUrl);
            
        } catch (Exception e) {
            logger.error("[GeminiSongExtractorService] Error enriching songs from {}: {}", movieUrl, e.getMessage());
            enrichedSongs = incompleteSongs; // Fallback to original incomplete songs if Gemini fails   
        }
        
        return enrichedSongs;
    }

    /**
     * Build the request body for Gemini API.
     */
    public JsonObject buildRequestBody(String promptText) {
        JsonObject requestBody = new JsonObject();

        // 1. Create the 'parts' array and add the text object
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", promptText);

        JsonArray partsArray = new JsonArray();
        partsArray.add(textPart);

        // 2. Create the 'content' object and add the 'parts' array
        JsonObject contentObject = new JsonObject();
        contentObject.add("parts", partsArray);

        // 3. Create the 'contents' array (Gemini expects a list of messages)
        JsonArray contentsArray = new JsonArray();
        contentsArray.add(contentObject);

        // 4. Build the final requestBody
        requestBody.add("contents", contentsArray);

        // Add generation config (this part was mostly correct, just needs to be at the
        // root)
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.1); // Lowered slightly for more accurate metadata extraction
        generationConfig.addProperty("topP", 0.8);
        generationConfig.addProperty("topK", 40);
        generationConfig.addProperty("maxOutputTokens", 2048);
        generationConfig.addProperty("responseMimeType", "application/json");
        requestBody.add("generationConfig", generationConfig);

        return requestBody;
    }

    private String truncatePrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        if (prompt.length() <= MAX_PROMPT_LENGTH) {
            return prompt;
        }
        String truncated = prompt.substring(0, MAX_PROMPT_LENGTH);
        logger.warn("[GeminiSongExtractorService] Prompt truncated to {} characters to avoid request size limits.", MAX_PROMPT_LENGTH);
        return truncated + "\n\n... [truncated for size]";
    }

    /**
     * Call the Gemini API using HTTP client.
     */
    public String callGeminiAPI(JsonObject requestBody) throws Exception {
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
        
        logger.info("[GeminiSongExtractorService] Gemini response body: {}", response.body());
        return response.body();
    }

    /**
     * Build a dynamic prompt for Gemini to enrich incomplete songs.
     * Shows Gemini what we already have and asks it to fill in only the missing fields.
     */
    private String buildExtractionPrompt(String soundtrackHtml, List<Map<String, String>> incompleteSongs) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a Wikipedia metadata enhancer. Your task is to enrich songs with missing metadata from the provided Wikipedia soundtrack section HTML.\n\n");
        prompt.append("Below are songs already extracted from the page. Some fields may be empty. Your job is to fill in ONLY the empty fields using information from the HTML.\n\n");
        prompt.append("Songs to enrich:\n");
        
        // Show Gemini the incomplete songs
        for (int i = 0; i < incompleteSongs.size(); i++) {
            Map<String, String> song = incompleteSongs.get(i);
            prompt.append(String.format("%d. Title: %s | Singers: %s | Composer: %s | Lyricists: %s | Length: %s%n",
                i + 1,
                song.getOrDefault("title", ""),
                song.getOrDefault("singers", ""),
                song.getOrDefault("composer", ""),
                song.getOrDefault("lyricists", ""),
                song.getOrDefault("length", "")));
        }
        
        prompt.append("\nReturn ONLY a valid JSON array with the same number of objects, in the same order, with enriched metadata:\n");
        prompt.append("[\n");
        prompt.append("  {\n");
        prompt.append("    \"title\": \"Song Title\",\n");
        prompt.append("    \"singers\": \"Singer Name(s)\",\n");
        prompt.append("    \"composer\": \"Composer Name\",\n");
        prompt.append("    \"lyricists\": \"Lyricist Name(s)\",\n");
        prompt.append("    \"length\": \"Duration (e.g., 3:45)\"\n");
        prompt.append("  }\n");
        prompt.append("]\n\n");
        prompt.append("IMPORTANT RULES:\n");
        prompt.append("1. Keep existing non-empty values from the list above - DO NOT change them\n");
        prompt.append("2. Fill in ONLY empty fields using the HTML below\n");
        prompt.append("3. If a field cannot be found, leave it as an empty string\n");
        prompt.append("4. Return ONLY the JSON array, nothing else\n");
        prompt.append("5. Preserve original text capitalization and formatting\n");
        prompt.append("6. CRITICAL: Return the SAME NUMBER of songs in the SAME ORDER\n\n");
        prompt.append("Here is the Wikipedia soundtrack HTML:\n\n");
        prompt.append(soundtrackHtml);
        
        return prompt.toString();
    }

    /**
     * Parse the JSON response from Gemini and convert to list of song maps.
     */
    private List<Map<String, String>> parseGeminiResponse(String jsonResponse, List<Map<String, String>> incompleteSongs) {
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
            // If parsing fails, return original incomplete songs
            return incompleteSongs;
        }
        
        return songs;
    }

    /**
     * Merge Gemini-enriched songs with original incomplete songs.
     * Preserves original values and fills in empty fields from Gemini results.
     */
    private List<Map<String, String>> mergeSongs(List<Map<String, String>> originalSongs, 
                                                   List<Map<String, String>> geminiSongs) {
        logger.info("[GeminiSongExtractorService] Merging {} original songs with {} Gemini-enriched songs",
            originalSongs.size(), geminiSongs.size());
        
        List<Map<String, String>> mergedSongs = new ArrayList<>();
        
        for (int i = 0; i < originalSongs.size(); i++) {
            Map<String, String> merged = new HashMap<>(originalSongs.get(i));
            
            // Only merge if Gemini has a corresponding song at the same position
            if (i < geminiSongs.size()) {
                Map<String, String> geminiSong = geminiSongs.get(i);
                
                // Fill in missing fields from Gemini (only if original field is empty)
                for (String field : Arrays.asList("title", "singers", "composer", "lyricists", "length")) {
                    String originalValue = merged.getOrDefault(field, "").trim();
                    String geminiValue = geminiSong.getOrDefault(field, "").trim();
                    
                    // Use Gemini value only if original is empty or "not found"
                    if (originalValue.isEmpty() || originalValue.equals("not found")) {
                        if (!geminiValue.isEmpty() && !geminiValue.equals("not found")) {
                            merged.put(field, geminiValue);
                            logger.debug("[GeminiSongExtractorService] Enriched song {} field {}: {}",
                                merged.get("title"), field, geminiValue);
                        }
                    }
                }
            }
            
            mergedSongs.add(merged);
        }
        
        return mergedSongs;
    }

    /**
     * Identify which fields are missing in a song.
     */
    private Set<String> getMissingFields(Map<String, String> song) {
        Set<String> missingFields = new HashSet<>();
        for (String field : Arrays.asList("title", "singers", "composer", "lyricists", "length")) {
            String value = song.getOrDefault(field, "").trim();
            if (value.isEmpty() || value.equals("not found")) {
                missingFields.add(field);
            }
        }
        return missingFields;
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

    public double[] getEmbedding(String thematicSummary) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEmbedding'");
    }
}

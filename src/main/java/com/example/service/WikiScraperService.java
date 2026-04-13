package com.example.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WikiScraperService {

    private static final Logger logger = LoggerFactory.getLogger(WikiScraperService.class);
    private boolean needHeader = true;

    @Autowired
    private GeminiSongExtractorService geminiExtractor;
    @Autowired
    private MusicProducerService musicProducerService;

    public List<Map<String, String>> scrapeMovies(String url) {
        List<Map<String, String>> movies = new ArrayList<>();
        try {
            logger.info("[WikiScraperService] Fetching URL: {}", url);
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();

            Elements rows = doc.select("table.wikitable tr");
            for (Element row : rows) {
                Elements columns = row.select("td");
                if (columns.size() > 1) {
                    Element link = columns.get(0).select("a").first();
                    if (link != null) {
                        String movieTitle = link.text();
                        String relativeUrl = link.attr("href");
                        String fullUrl = "https://en.wikipedia.org" + relativeUrl;

                        logger.info("[WikiScraperService] Found movie: {} -> {}", movieTitle, fullUrl);
                        scrapeSongsFromMovie(fullUrl);
                        //push movie URL to RabbitMQ for further processing (e.g., by a Gemini-based service)   
                        musicProducerService.queueMovieUrl(fullUrl);

                        // Polite delay to avoid Wikipedia rate limits
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }

                        Map<String, String> entry = new HashMap<>();
                        entry.put("title", movieTitle);
                        entry.put("url", fullUrl);
                        movies.add(entry);
                    }
                }
            }
            logger.info("[WikiScraperService] Total movies found: {}", movies.size());
        } catch (Exception e) {
            logger.error("[WikiScraperService] Error scraping {}: {}", url, e.getMessage());
        }
        return movies;
    }


    /**
     * Scrape songs from a given movie Wikipedia page.
     * Uses Jsoup for initial extraction, falls back to Gemini AI if any song data is incomplete.
     * @param movieUrl  
     */
    public void scrapeSongsFromMovie(String movieUrl) {
        logger.info("[WikiScraperService] scrapeSongsFromMovie starts with movieUrl: {}", movieUrl);
        String outputFile = "C:\\Users\\Vinay Kumar\\Documents\\VinayOraDocs\\Non-Technical Stuffs\\Other Resources\\HelloWorld\\movie_songs.csv";
        try {
            // ensure parent dir exists
            File f = new File(outputFile);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            // Connect to the specific movie page
            Document doc = Jsoup.connect(movieUrl).userAgent("Mozilla/5.0").get();
            
            // 1. Locate the Soundtrack section in the Wikipedia page
            Element soundtrackHeader = getSoundtrackHeader(doc);
            
            // 2. Resolve the document to use (follow Main article link if present)
            Document workingDoc = resolveSoundtrackDocument(doc, soundtrackHeader);
            
            // 3. Re-locate headers in the working document
            soundtrackHeader = getSoundtrackHeader(workingDoc);
            Element trackHeader = retrieveTrackHeader(workingDoc);
            Element trackTable = workingDoc.select("table.tracklist").first();
            
            List<Map<String, String>> finalSongs = new ArrayList<>();
            String sourceMethod = "None";
            
            // Try to extract songs using Jsoup
            if (trackTable != null && (soundtrackHeader != null || trackHeader != null)) {
                finalSongs = extractFromTrackListing(movieUrl, trackTable);
                sourceMethod = "Track Listing";
            } else if (soundtrackHeader != null) {
                Element songTable = soundtrackHeader.parent().nextElementSiblings()
                                    .select("table.wikitable").first();
                
                if (songTable != null) {
                    finalSongs = extractFromWikiTable(movieUrl, songTable);
                    sourceMethod = "WikiTable";
                } else {
                    finalSongs = extractFromUnorderedList(movieUrl, soundtrackHeader);
                    sourceMethod = "Unordered List";
                }
            }
            
            // Check if extracted songs have complete data
            if (!finalSongs.isEmpty() && !areAllSongsComplete(finalSongs)) {
                logger.info("[WikiScraperService] Incomplete song data detected for {}. Using Gemini AI to enrich missing fields...", movieUrl);
                
                Element headerForHtml = soundtrackHeader != null ? soundtrackHeader : trackHeader;
                String soundtrackHtml = headerForHtml != null ? extractSoundtrackSectionHtml(headerForHtml) : "";
                if (!soundtrackHtml.isBlank()) {
                    // Pass incomplete songs to Gemini for enrichment (not replacement)
                    List<Map<String, String>> enrichedSongs = geminiExtractor.extractSongsWithGemini(
                        movieUrl, soundtrackHtml, finalSongs);
                    
                    if (!enrichedSongs.isEmpty()) {
                        finalSongs = enrichedSongs;
                        sourceMethod = "Jsoup + Gemini AI";
                        logger.info("[WikiScraperService] Successfully enriched {} songs using Gemini AI for {}", 
                            finalSongs.size(), movieUrl);
                    }
                } else {
                    logger.warn("[WikiScraperService] Gemini enrichment skipped for {} because soundtrack HTML could not be extracted.", movieUrl);
                }
            } else if (!finalSongs.isEmpty()) {
                logger.info("[WikiScraperService] All song data is complete from Jsoup for {} ({})", movieUrl, sourceMethod);
            }
            
            // Write final songs to CSV
            writesongsToCSV(movieUrl, finalSongs, f, sourceMethod);
            
        } catch (Exception e) {
            logger.error("[WikiScraperService] Error scraping {}: {}", movieUrl, e.getMessage());
        }
        logger.info("[WikiScraperService] scrapeSongsFromMovie ends");
    }

    /**
     * Check if all songs have complete data (no empty fields for key attributes).
     */
    private String extractSoundtrackSectionHtml(Element soundtrackHeader) {
        logger.info("[WikiScraperService] extractSoundtrackSectionHtml starts with soundtrackHeader present: {}", soundtrackHeader != null);
        Element sectionRoot = soundtrackHeader;
        //if ("span".equalsIgnoreCase(soundtrackHeader.tagName())) {
            sectionRoot = soundtrackHeader.parent();
        //}

        StringBuilder htmlBuilder = new StringBuilder();
        for (Element sibling : sectionRoot.nextElementSiblings()) {
            String tagName = sibling.tagName();
            if (tagName.matches("h2|h3|h4|h5|h6")) {
                break;
            }
            htmlBuilder.append(sibling.outerHtml());
            if (htmlBuilder.length() > 38000) {
                htmlBuilder.setLength(38000);
                htmlBuilder.append("\n<!-- truncated for Gemini request size -->");
                break;
            }
        }
        logger.info("[WikiScraperService] extractSoundtrackSectionHtml ends");
        return htmlBuilder.toString();
    }

    private boolean areAllSongsComplete(List<Map<String, String>> songs) {
        logger.info("[WikiScraperService] areAllSongsComplete starts with songs size: {}", songs.size());
        for (Map<String, String> song : songs) {
            String title = song.getOrDefault("title", "").trim();
            String singers = song.getOrDefault("singers", "").trim();
            String composer = song.getOrDefault("composer", "").trim();
            String lyricists = song.getOrDefault("lyricists", "").trim();
            
            // If ANY required field is empty or contains "not found" / "Could not find", data is incomplete
            if (title.isEmpty() || title.contains("not found") || title.contains("Could not find") ||
                singers.isEmpty() || singers.contains("not found") || singers.equals("Unknown") ||
                composer.isEmpty() || composer.contains("not found") ||
                lyricists.isEmpty() || lyricists.contains("not found")) {
                return false;
            }
        }
        logger.info("[WikiScraperService] areAllSongsComplete ends");
        return true;
    }

    /**
     * Extract songs from track listing table and return as list.
     */
    private List<Map<String, String>> extractFromTrackListing(String movieUrl, Element trackTable) {
        logger.info("[WikiScraperService] extractFromTrackListing starts with movieUrl: {}, trackTable present: {}", movieUrl, trackTable != null);
        List<Map<String, String>> songs = new ArrayList<>();
        try {
            Elements rows = trackTable.select("tr");
            boolean headerFound = false;
            int titleIdx = -1, lyricsIdx = -1, musicIdx = -1, singerIdx = -1, lengthIdx = -1;

            for (Element row : rows) {
                if (row.hasClass("tracklist-total-length")) continue;
                Elements ths = row.select("th");
                if (!ths.isEmpty() && !headerFound) {
                    headerFound = true;
                    for (int i = 0; i < ths.size(); i++) {
                        String h = ths.get(i).text().trim().toLowerCase();
                        if (h.contains("title") || h.contains("song") || h.contains("track"))
                            titleIdx = i-1;
                        else if (h.contains("lyrics") || h.contains("lyric"))
                            lyricsIdx = i-1;
                        else if (h.contains("music") || h.contains("composer"))
                            musicIdx = i-1;
                        else if (h.contains("singer(s)") || h.contains("vocal") || h.contains("artist"))
                            singerIdx = i-1;
                        else if (h.contains("length") || h.contains("time") || h.contains("duration"))
                            lengthIdx = i-1;
                    }
                    continue;
                }

                Elements cells = row.select("td");
                if (cells.isEmpty()) continue;

                String title = "";
                String lyrics = "";
                String music = "";
                String singers = "";
                String length = "";

                if (headerFound) {
                    if (titleIdx >= 0 && titleIdx < cells.size()) 
                        title = cells.get(titleIdx).text().replaceAll("\"", "");
                    else 
                        title = "";

                    if (lyricsIdx >= 0 && lyricsIdx < cells.size()) 
                        lyrics = cells.get(lyricsIdx).text();
                    else
                        lyrics = "";

                    if (musicIdx >= 0 && musicIdx < cells.size()) 
                        music = cells.get(musicIdx).text();
                    else 
                        music = "";

                    if (singerIdx >= 0 && singerIdx < cells.size()) 
                        singers = cells.get(singerIdx).text();
                    else 
                        singers = "";

                    if (lengthIdx >= 0 && lengthIdx < cells.size()) 
                        length = cells.get(lengthIdx).text();
                    else 
                        length = "";
                } else {
                    title = "";
                    lyrics = "";
                    music = "";
                    singers = "";
                    length = "";
                }

                logger.debug("[WikiScraperService] Track Data - movieUrl: {}, title: {}, lyrics: {}, music: {}, singers: {}, length: {}", 
                    movieUrl, title, lyrics, music, singers, length);

                Map<String, String> song = new HashMap<>();
                song.put("title", title);
                song.put("singers", singers);
                song.put("composer", music);
                song.put("lyricists", lyrics);
                song.put("length", length);
                songs.add(song);
            }
        } catch (Exception e) {
            logger.error("[WikiScraperService] Error in extractFromTrackListing: {}", e.getMessage());
        }
        logger.info("[WikiScraperService] extractFromTrackListing ends");
        return songs;
    }

    /**
     * Extract songs from wiki table and return as list.
     */
    private List<Map<String, String>> extractFromWikiTable(String movieUrl, Element songTable) {
        logger.info("[WikiScraperService] extractFromWikiTable starts with movieUrl: {}, songTable present: {}", movieUrl, songTable != null);
        List<Map<String, String>> songs = new ArrayList<>();
        try {
            Elements rows = songTable.select("tr");
            boolean headerFound = false;
            int titleIdx = -1, singerIdx = -1, lyricsIdx = -1, musicIdx = -1, lengthIdx = -1;

            for (Element row : rows) {
                Elements ths = row.select("th");
                if (!ths.isEmpty() && !headerFound) {
                    headerFound = true;
                    for (int i = 0; i < ths.size(); i++) {
                        String h = ths.get(i).text().trim().toLowerCase();
                        if (h.contains("title") || h.contains("song") || h.contains("track")) 
                            titleIdx = i;
                        else if (h.contains("singer(s)") || h.contains("vocal") || h.contains("artist")) 
                            singerIdx = i;
                        else if (h.contains("lyrics") || h.contains("lyric")) 
                            lyricsIdx = i;
                        else if (h.contains("music") || h.contains("composer")) 
                            musicIdx = i;
                        else if (h.contains("length") || h.contains("time") || h.contains("duration")) 
                            lengthIdx = i;
                    }
                    continue;
                }

                Elements cells = row.select("td");
                if (cells.isEmpty()) continue;

                String title = "";
                String singers = "";
                String music = "";
                String lyrics = "";
                String length = "";

                if (headerFound) {
                    if (titleIdx >= 0 && titleIdx < cells.size()) 
                        title = cells.get(titleIdx).text().replaceAll("\\[.*?\\]", "");
                    else 
                        title = "";

                    if (singerIdx >= 0 && singerIdx < cells.size()) singers = cells.get(singerIdx).text();
                    else 
                        singers = "";

                    if (musicIdx >= 0 && musicIdx < cells.size()) music = cells.get(musicIdx).text();
                    else 
                        music = "";

                    if (lyricsIdx >= 0 && lyricsIdx < cells.size()) lyrics = cells.get(lyricsIdx).text();
                    else 
                        lyrics = "";

                    if (lengthIdx >= 0 && lengthIdx < cells.size()) length = cells.get(lengthIdx).text();
                    else 
                        length = "";
                }

                logger.debug("[WikiScraperService] WikiTable Data - movieUrl: {}, title: {}, lyrics: {}, music: {}, singers: {}, length: {}", 
                    movieUrl, title, lyrics, music, singers, length);

                Map<String, String> song = new HashMap<>();
                song.put("title", title);
                song.put("singers", singers);
                song.put("composer", music);
                song.put("lyricists", lyrics);
                song.put("length", length);
                songs.add(song);
            }
        } catch (Exception e) {
            logger.error("[WikiScraperService] Error in extractFromWikiTable: {}", e.getMessage());
        }
        logger.info("[WikiScraperService] extractFromWikiTable ends");
        return songs;
    }

    /**
     * Extract songs from unordered list and return as list.
     */
    private List<Map<String, String>> extractFromUnorderedList(String movieUrl, Element soundtrackHeader) {
        logger.info("[WikiScraperService] extractFromUnorderedList starts with movieUrl: {}, soundtrackHeader present: {}", movieUrl, soundtrackHeader != null);
        List<Map<String, String>> songs = new ArrayList<>();
        try {
            Element songList = soundtrackHeader.parent().nextElementSiblings().select("ul").first();
            if (songList != null) {
                for (Element li : songList.select("li")) {
                    String text = li.text().replaceAll("\\[.*?\\]", "").trim();
                    String title = text;
                    String singers = "";
                    String music = "";
                    String lyrics = "";
                    String length = "";

                    // Heuristic splits: dash, slash, or ' by '
                    String[] parts = text.split("\\s*[–—-]\\s*|\\s*/\\s*|\\s+by\\s+");
                    if (parts.length >= 2) {
                        title = parts[0].trim();
                        singers = parts[1].trim();
                    } else {
                        // fallback: try to find italicized title
                        Element it = li.selectFirst("i");
                        if (it != null) 
                            title = it.text().trim();
                    }

                    logger.debug("[WikiScraperService] Unordered List Data - movieUrl: {}, title: {}, singers: {}", 
                        movieUrl, title, singers);

                    Map<String, String> song = new HashMap<>();
                    song.put("title", title);
                    song.put("singers", singers);
                    song.put("composer", music);
                    song.put("lyricists", lyrics);
                    song.put("length", length);
                    songs.add(song);
                }
            }
        } catch (Exception e) {
            logger.error("[WikiScraperService] Error in extractFromUnorderedList: {}", e.getMessage());
        }
        logger.info("[WikiScraperService] extractFromUnorderedList ends");
        return songs;
    }

    /**
     * Write songs to CSV file.
     */
    private void writesongsToCSV(String movieUrl, List<Map<String, String>> songs, File f, String sourceMethod) throws IOException {
        logger.info("[WikiScraperService] writesongsToCSV starts with movieUrl: {}, songs size: {}, sourceMethod: {}", movieUrl, songs.size(), sourceMethod);
        try (FileWriter fw = new FileWriter(f, true)) {
            if (needHeader) {
                fw.write("movieUrl,title,singers,composer,lyricists,length,source" + System.lineSeparator());
                this.needHeader = false;
            }
            
            for (Map<String, String> song : songs) {
                String title = song.getOrDefault("title", "");
                String singers = song.getOrDefault("singers", "");
                String composer = song.getOrDefault("composer", "");
                String lyricists = song.getOrDefault("lyricists", "");
                String length = song.getOrDefault("length", "");
                
                String line = escapeCsv(movieUrl) + "," + 
                             escapeCsv(title) + "," + 
                             escapeCsv(singers) + "," + 
                             escapeCsv(composer) + "," + 
                             escapeCsv(lyricists) + "," + 
                             escapeCsv(length) + "," + 
                             escapeCsv(sourceMethod) + System.lineSeparator();
                fw.write(line);
                logger.debug("[WikiScraperService] Wrote CSV song entry: {}", line.trim());
            }
            
            logger.info("[WikiScraperService] Successfully wrote {} songs to CSV for {}", songs.size(), movieUrl);
        }
        logger.info("[WikiScraperService] writesongsToCSV ends");
    }


    private Element getSoundtrackHeader(Document doc) {
        logger.info("[WikiScraperService] getSoundtrackHeader starts with doc title: {}", doc.title());
        // 1. Find the Soundtrack section by its ID (standard Wiki format)
        Element soundtrackHeader = doc.getElementById("Soundtrack");

        // 2. If no ID, check for Music ID
        if (soundtrackHeader == null) {
            soundtrackHeader = doc.getElementById("Music");
        }

        // 3. If no ID, search for a heading containing the word "Soundtrack" or "Music"
        if (soundtrackHeader == null) {
            soundtrackHeader = doc.select("h2:contains(Soundtrack), h3:contains(Soundtrack), h2:contains(Music), h3:contains(Music)").first();
        }
        logger.info("[WikiScraperService] getSoundtrackHeader ends. soundtrackHeader: {}", soundtrackHeader);
        return soundtrackHeader;
    }

    private Element retrieveTrackHeader(Document doc) {
        logger.info("[WikiScraperService] retrieveTrackHeader starts with doc title: {}", doc.title());
        // TODO Auto-generated method stub
        // 1. Target the 'Track listing' sub-heading specifically
        Element trackHeader = doc.select("span#Track_listing").first();
        
        // If the ID isn't there, look for any H2 or H3 containing 'Track listing'
        if (trackHeader == null) {
            trackHeader = doc.select("h2:contains(Track listing), h3:contains(Track listing)").first();
        }
        logger.info("[WikiScraperService] retrieveTrackHeader ends");
        return trackHeader;
    }

    /**
     * Resolve the document to use for soundtrack extraction.
     * If a "Main article" link is found in a div.hatnote.navigation-not-searchable sibling, fetch that page.
     * Otherwise, use the original movie document.
     */
    private Document resolveSoundtrackDocument(Document movieDoc, Element soundtrackHeader) throws IOException {
        logger.info("[WikiScraperService] resolveSoundtrackDocument starts with movieDoc title: {}, soundtrackHeader present: {}", movieDoc.title(), soundtrackHeader != null);
        if (soundtrackHeader != null) {
            // Get the parent container of the soundtrack header (e.g., div.mw-heading)
            Element headerContainer = soundtrackHeader.parent();
            
            if (headerContainer != null) {
                // Iterate through siblings of the header container to find the hatnote div
                Element sibling = headerContainer.nextElementSibling();
                while (sibling != null) {
                    // Check if this sibling is a hatnote div with "Main article" text
                    if ("div".equals(sibling.tagName()) && sibling.hasClass("hatnote") && sibling.hasClass("navigation-not-searchable")) {
                        if (sibling.text().contains("Main article")) {
                            Element mainArticleLink = sibling.selectFirst("a");
                            if (mainArticleLink != null) {
                                String href = mainArticleLink.attr("href");
                                if (href.startsWith("/wiki/")) {
                                    String fullUrl = "https://en.wikipedia.org" + href;
                                    logger.info("[WikiScraperService] Following Main article link to separate soundtrack page: {}", fullUrl);
                                    return Jsoup.connect(fullUrl).userAgent("Mozilla/5.0").get();
                                }
                            }
                            break; // Stop after finding the first hatnote with "Main article"
                        }
                    }
                    sibling = sibling.nextElementSibling();
                }
            }
        }
        logger.info("[WikiScraperService] resolveSoundtrackDocument ends");
        return movieDoc; // Default to original document
    }


    private String escapeCsv(String s) {
        if (s == null) return "";
        String escaped = s.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

}

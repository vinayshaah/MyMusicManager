package com.example.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
     * @param movieUrl  
     */
    public void scrapeSongsFromMovie(String movieUrl) {
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
            Element trackHeader = retrieveTrackHeader(doc);
            // Check for 'Track listing' section.
            Element trackTable = doc.select("table.tracklist").first();
            //if the wikipedia page has a soundtrack and track listing section.
            if(trackTable != null && soundtrackHeader != null) {
                getFromTrackListing(movieUrl, f, trackTable);
            
            }
            else if (soundtrackHeader != null) {
                // 3. Find the first table that appears after this header
                Element songTable = soundtrackHeader.parent().nextElementSiblings()
                                    .select("table.wikitable").first();

                try (FileWriter fw = new FileWriter(f, true)) {
                    if (needHeader) {
                        fw.write("movieUrl,title,singers,composer,lyricists,length,source" + System.lineSeparator());
                        this.needHeader = false;
                    }
                    if (songTable != null) {                    
                        getFromWikiTable(movieUrl, songTable, fw);
                    } else {
                        getFromUnorderedList(movieUrl, soundtrackHeader, fw);
                    }
                }
            } else {
                logger.debug("[WikiScraperService] No Soundtrack header found for {}", movieUrl);
            }
        } catch (Exception e) {
            logger.error("[WikiScraperService] Error scraping {}: {}", movieUrl, e.getMessage());
        }
    }


    private void getFromTrackListing(String movieUrl, File f, Element trackTable) throws IOException {
        // 2. Find the table following this header
         // We look at the parent of the span (the H3) and find the next table           
         //trackHeader.closest("h3").nextElementSiblings().select("table.wikitable").first();
        try (FileWriter fw = new FileWriter(f, true)) {
            if (needHeader) {
                fw.write("movieUrl,title,singers,composer,lyricists,length,source" + System.lineSeparator());
                this.needHeader = false;
            }
            Elements rows = trackTable.select("tr");
            // detect header row (<th>) to map column indices by header name
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
                        title = "Could not find title";

                    if (lyricsIdx >= 0 && lyricsIdx < cells.size()) 
                        lyrics = cells.get(lyricsIdx).text();
                    else
                        lyrics = "Could not find lyrics";

                    if (musicIdx >= 0 && musicIdx < cells.size()) 
                        music = cells.get(musicIdx).text();
                    else 
                        music = "Could not find music composer";

                    if (singerIdx >= 0 && singerIdx < cells.size()) 
                        singers = cells.get(singerIdx).text();
                    else 
                        singers = "Could not find singers";

                    if (lengthIdx >= 0 && lengthIdx < cells.size()) 
                        length = cells.get(lengthIdx).text();
                    else 
                        length = "Could not find length";
                } else {
                    // fallback to common index ordering when no header detected
                    title = "Could not find title.186";
                    lyrics = "Could not find lyrics.187";
                    music = "Could not find music composer.188"; 
                    singers = "Could not find singers.189";
                    length = "Could not find length.190";   
                }

                logger.info("[WikiScraperService] Track Data - movieUrl: {}, title: {}, lyrics: {}, music: {}, singers: {}, length: {}", movieUrl, title, lyrics, music, singers, length);

                String line = escapeCsv(movieUrl) + "," + escapeCsv(title) + "," + escapeCsv(singers) + "," + escapeCsv(music) + "," + escapeCsv(lyrics) + "," + escapeCsv(length) + "," + escapeCsv("Track Listing") + System.lineSeparator();
                fw.write(line);
                logger.debug("[WikiScraperService] Wrote CSV song entry (track table): {}", line.trim());
            }
        }
    }


    private void getFromUnorderedList(String movieUrl, Element soundtrackHeader, FileWriter fw) throws IOException {
        Element songList = soundtrackHeader.parent().nextElementSiblings().select("ul").first();
        if (songList != null) {
            for (Element li : songList.select("li")) {
                String text = li.text().replaceAll("\\[.*?\\]", "").trim();
                String title = text;
                String singers = "Unknown";
                String music = "";
                String lyrics = "";
                String length = "";

                // Heuristic splits: dash, slash, or ' by '
                String[] parts = text.split("\\s*[–—-]\\s*|\\s*/\\s*|\\s+by\\s+");
                if (parts.length >= 2) {
                    title = parts[0].trim();
                    singers = parts[1].trim();
                } else {
                    // fallback: try to find quoted title or italicized
                    Element it = li.selectFirst("i");
                    if (it != null) 
                        title = it.text().trim();
                    else 
                        title = "Could not find title.222";
                }

                logger.info("[WikiScraperService] Unordered List Data - movieUrl: {}, title: {}, lyrics: {}, music: {}, singers: {}, length: {}", movieUrl, title, lyrics, music, singers, length);

                String line = escapeCsv(movieUrl) + "," + escapeCsv(title) + "," + escapeCsv(singers) + "," + escapeCsv(music) + "," + escapeCsv(lyrics) + "," + escapeCsv(length) + "," + escapeCsv("Unordered List") + System.lineSeparator();
                fw.write(line);
                logger.debug("[WikiScraperService] Wrote CSV song list entry: {}", line.trim());
            }
        } else {
            logger.debug("[WikiScraperService] No structured songs found for {}", movieUrl);
        }
    }


    private void getFromWikiTable(String movieUrl, Element songTable, FileWriter fw) throws IOException {
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
            String singers = "Unknown";
            String music = "";
            String lyrics = "";
            String length = "";

            if (headerFound) {
                if (titleIdx >= 0 && titleIdx < cells.size()) 
                    title = cells.get(titleIdx).text().replaceAll("\\[.*?\\]", "");
                else 
                    title = "title not found";

                if (singerIdx >= 0 && singerIdx < cells.size()) singers = cells.get(singerIdx).text();
                else 
                    singers = "singers not found";

                if (musicIdx >= 0 && musicIdx < cells.size()) music = cells.get(musicIdx).text();
                else 
                    music = "music not found";

                if (lyricsIdx >= 0 && lyricsIdx < cells.size()) lyrics = cells.get(lyricsIdx).text();
                else 
                    lyrics = "lyrics not found";

                if (lengthIdx >= 0 && lengthIdx < cells.size()) length = cells.get(lengthIdx).text();
                else 
                    length = "length not found";
            } else {
                title = "title not found";
                singers = "singers not found";
                music = "music not found";
                lyrics = "lyrics not found";
                length = "length not found";
            }

            logger.info("[WikiScraperService] WikiTable Data - movieUrl: {}, title: {}, lyrics: {}, music: {}, singers: {}, length: {}", movieUrl, title, lyrics, music, singers, length);

            String line = escapeCsv(movieUrl) + "," + escapeCsv(title) + "," + escapeCsv(singers) + "," + escapeCsv(music) + "," + escapeCsv(lyrics) + "," + escapeCsv(length) + "," + escapeCsv("WikiTable") + System.lineSeparator();
            fw.write(line);
            logger.debug("[WikiScraperService] Wrote CSV song entry: {}", line.trim());
        }
    }


    private Element getSoundtrackHeader(Document doc) {
        // 1. Find the Soundtrack section by its ID (standard Wiki format)
        Element soundtrackHeader = doc.getElementById("Soundtrack");

        // 2. If no ID, search for a heading containing the word "Soundtrack"
        if (soundtrackHeader == null) {
            soundtrackHeader = doc.select("h2:contains(Soundtrack), h3:contains(Soundtrack)").first();
        }
        return soundtrackHeader;
    }

    private Element retrieveTrackHeader(Document doc) {
        // TODO Auto-generated method stub
        // 1. Target the 'Track listing' sub-heading specifically
        Element trackHeader = doc.select("span#Track_listing").first();
        
        // If the ID isn't there, look for any H3 containing 'Track listing'
        if (trackHeader == null) {
            trackHeader = doc.select("h3:contains(Track listing)").first();
        }
        return trackHeader;
    }


    private String escapeCsv(String s) {
        if (s == null) return "";
        String escaped = s.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

}

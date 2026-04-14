package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.ResponseEntity;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

import com.example.dto.MusicSongDTO;
import com.example.entity.MusicSong;
import com.example.repo.MusicRepository;
import com.example.service.MockGeminiService;
import com.example.service.SemanticSearchService;
import com.example.service.WikiScraperService;


@SpringBootApplication
@ComponentScan("com.example")
@EnableJpaRepositories(basePackages = "com.example.repo")
@EntityScan("com.example.entity")
@RestController
public class DemoApplication {

    private static final Logger logger = LoggerFactory.getLogger(DemoApplication.class);

    private final WikiScraperService wikiScraperService;

    @Autowired
    private MusicRepository musicRepository;

    @Autowired
    private MockGeminiService geminiService;

    @Autowired
    private SemanticSearchService semanticSearchService;

    public DemoApplication(WikiScraperService wikiScraperService) {
        this.wikiScraperService = wikiScraperService;
    }

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
    @GetMapping("/hello")
    public String check() {
        return "Hello Prisha, welcome here.";
    }

    @GetMapping("/echo")
    public String echo(@RequestParam("input") String input) {
        return "You entered " + input;
    }

    @GetMapping("/scan")
    public Map<String, Object> scanYear(@RequestParam("year") String year) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, String>> movies = wikiScraperService.scrapeMovies("https://en.wikipedia.org/wiki/List_of_Hindi_films_of_"+year);
            response.put("status", "Scanning done");
            response.put("moviesFound", movies);
            response.put("count", movies.size());
        } catch (Exception e) {
            response.put("status", "Error during scanning: " + e.getMessage());
            response.put("moviesFound", new ArrayList<>());
            response.put("count", 0);
        }
        return response;
    }

@GetMapping("/search")
public ResponseEntity<List<MusicSongDTO>> semanticSearch(@RequestParam String query) {
    System.out.println("Inside semantic search with query: " + query);
    // 1. Convert user's search text into a vector (using the mock service)
    double[] queryVector = geminiService.getEmbedding(query);
    
    // Manual formatting to ensure NO spaces and clean brackets
    // Format your double[] as "[0.123, 0.456, ...]" before passing
    String vectorString = "[" + Arrays.stream(queryVector)
            .mapToObj(Double::toString)
            .collect(Collectors.joining(",")) + "]";
    System.out.println("Query vector string: " + vectorString);
    // 3. Query the database for the 5 most similar songs
    List<MusicSong> results = musicRepository.findTop5SemanticMatches(vectorString);
    System.out.println("Songs obtained: " + results);
    // convert to DTO
    List<MusicSongDTO> dtoResult = results.stream()
            .map(MusicSongDTO::new)
            .collect(Collectors.toList());
    return ResponseEntity.ok(dtoResult);
}    

    private void performScan(String year) throws IOException {
        logger.info("[performScan] Starting scan for year: {}", year);
        String listUrl = "https://en.wikipedia.org/wiki/List_of_Hindi_films_of_" + year;
        logger.debug("[performScan] Fetching list page: {}", listUrl);
        Document listDoc = Jsoup.connect(listUrl).userAgent("Mozilla/5.0").get();
        logger.info("[performScan] Successfully fetched list page for year {}", year);

        // collect movie links from wikitable(s)
        Set<String> movieLinks = new HashSet<>();
        Elements tables = listDoc.select("table.wikitable");
        logger.debug("[performScan] Found {} wikitable(s)", tables.size());
        for (Element table : tables) {
            Elements anchors = table.select("a[href^=/wiki/]");
            for (Element a : anchors) {
                String href = a.attr("href");
                if (!href.contains(":")) { // skip special pages
                    movieLinks.add("https://en.wikipedia.org" + href);
                }
            }
        }
        logger.info("[performScan] Collected {} movie links from tables", movieLinks.size());

        String outputFile = "songs_" + year + ".txt";
        logger.info("[performScan] Writing results to: {}", outputFile);
        try (FileWriter fw = new FileWriter(outputFile)) {
            int processedCount = 0;
            for (String movieUrl : movieLinks) {
                try {
                    logger.debug("[performScan] Processing movie: {}", movieUrl);
                    Document movieDoc = Jsoup.connect(movieUrl).userAgent("Mozilla/5.0").get();
                    String movieTitle = movieDoc.selectFirst("#firstHeading").text();
                    logger.debug("[performScan] Movie title: {}", movieTitle);

                    // find soundtrack headline
                    Element soundtrackHeadline = null;
                    for (Element span : movieDoc.select("span.mw-headline")) {
                        String text = span.text();
                        if (text != null && text.toLowerCase().contains("soundtrack")) {
                            soundtrackHeadline = span.parent();
                            logger.debug("[performScan] Found Soundtrack section for: {}", movieTitle);
                            break;
                        }
                    }

                    if (soundtrackHeadline != null) {
                        logger.info("[performScan] Extracting songs from: {}", movieTitle);
                        fw.write("=== " + movieTitle + " ===\n");
                        Element el = soundtrackHeadline.nextElementSibling();
                        while (el != null && !el.tagName().equals("h2")) {
                            if (el.tagName().equals("ul")) {
                                for (Element li : el.select("li")) {
                                    fw.write(li.text() + "\n");
                                }
                            } else if (el.tagName().equals("p")) {
                                String t = el.text().trim();
                                if (!t.isEmpty()) fw.write(t + "\n");
                            } else if (el.tagName().equals("table")) {
                                for (Element row : el.select("tr")) {
                                    String rowText = row.text().trim();
                                    if (!rowText.isEmpty()) fw.write(rowText + "\n");
                                }
                            }
                            el = el.nextElementSibling();
                        }
                        fw.write("\n");
                        processedCount++;
                    } else {
                        logger.debug("[performScan] No Soundtrack section found for: {}", movieTitle);
                    }
                } catch (Exception e) {
                    logger.warn("[performScan] Error processing movie: {} - {}", movieUrl, e.getMessage());
                }
            }
            logger.info("[performScan] Scan complete. Processed {} movies with soundtracks", processedCount);
        }
        logger.info("[performScan] Finished writing to {}", outputFile);
    }


}

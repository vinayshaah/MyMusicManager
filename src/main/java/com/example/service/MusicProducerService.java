package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


@Service
public class MusicProducerService {

    private static final Logger logger = LoggerFactory.getLogger(MusicProducerService.class);


    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public void queueMovieUrl(String url) {
        logger.info("[MusicProducerService] Adding in queue, movie: {} ->", url);
        // 1. Check Redis (Deduplication)
        // Key format: "processed:movie_url_here"
        String cacheKey = "processed:" + url;
        Boolean alreadyInQueue = redisTemplate.hasKey(cacheKey);

        if (Boolean.FALSE.equals(alreadyInQueue)) {
            // 2. Push to RabbitMQ
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.MUSIC_EXCHANGE, 
                RabbitMQConfig.ROUTING_KEY, 
                url
            );

            // 3. Save to Redis so we don't scrape it again
            // You can also set an expiration if you want to re-scrape after 30 days
            redisTemplate.opsForValue().set(cacheKey, "QUEUED");
            
            System.out.println("Queued new URL: " + url);
        } else {
            System.out.println("Skipping (already processed): " + url);
        }
    }
}
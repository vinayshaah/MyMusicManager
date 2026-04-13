package com.example.service;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String MOVIE_URL_QUEUE = "movie_url_queue";
    public static final String MUSIC_EXCHANGE = "music_exchange";
    public static final String ROUTING_KEY = "movie_routing_key";

    @Bean
    public Queue movieQueue() {
        return new Queue(MOVIE_URL_QUEUE, true); // durable = true (survives MQ restart)
    }

    @Bean
    public TopicExchange musicExchange() {
        return new TopicExchange(MUSIC_EXCHANGE);
    }

    @Bean
    public Binding binding(Queue movieQueue, TopicExchange musicExchange) {
        return BindingBuilder.bind(movieQueue).to(musicExchange).with(ROUTING_KEY);
    }
}
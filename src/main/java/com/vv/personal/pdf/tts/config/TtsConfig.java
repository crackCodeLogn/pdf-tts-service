package com.vv.personal.pdf.tts.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Vivek
 * @since 27/12/20
 */
@Configuration
public class TtsConfig {

    @Value("${tts.url:https://texttospeech.responsivevoice.org/v1/text:synthesize}")
    public String url;

    @Value("${tts.key}")
    public String key;

    @Value("${tts.gender:female}")
    public String gender;

    @Value("${tts.lng:en}")
    public String language;

    @Value("${tts.engine:g3}")
    public String engine;

    @Value("${tts.pitch:.5}")
    public Double pitch;

    @Value("${tts.rate:.5}")
    public Double rate;

    @Value("${tts.volume:1}")
    public Integer volume;

    @Value("${tts.timeout.connect:30}")
    public Integer connectTimeout;

    @Value("${tts.timeout.read:30}")
    public Integer readTimeout;

    @Value("${tts.timeout.process:60}") //seconds
    public Integer processTimeout;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(connectTimeout))
                .setReadTimeout(Duration.ofSeconds(readTimeout))
                .build();
    }

    @Bean
    public ExecutorService singleThreadExecutor() {
        return Executors.newSingleThreadExecutor();
    }

    @Bean
    public Runtime runtime() {
        return Runtime.getRuntime();
    }
}

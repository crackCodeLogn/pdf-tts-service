package com.vv.personal.pdf.tts.controller;

import com.vv.personal.pdf.tts.config.TtsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.*;

import static com.vv.personal.pdf.tts.constants.Constants.AUDIO_EXTENSION;
import static com.vv.personal.pdf.tts.constants.Constants.PLAY_AUDIO_CLIP_MPV_CMD;
import static org.springframework.http.HttpMethod.GET;

/**
 * @author Vivek
 * @since 27/12/20
 */
@RestController("TtsController")
@RequestMapping("/tts")
public class TtsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TtsController.class);

    @Autowired
    private TtsConfig ttsConfig;

    @Bean
    private UriComponentsBuilder uriComponentsBuilder() {
        return UriComponentsBuilder.fromHttpUrl(ttsConfig.url)
                .queryParam("key", ttsConfig.key)
                .queryParam("engine", ttsConfig.engine)
                .queryParam("pitch", ttsConfig.pitch)
                .queryParam("rate", ttsConfig.rate)
                .queryParam("volume", ttsConfig.volume);
    }

    @GetMapping("/convert")
    public Boolean converter(@RequestParam String text,
                             @RequestParam(defaultValue = "ru") String language,
                             @RequestParam(defaultValue = "gender") String gender) {
        String finalUrl = uriComponentsBuilder()
                .queryParam("text", text.replaceAll("%20", " "))
                .queryParam("lang", language)
                .queryParam("gender", gender)
                .build()
                .toUriString();

        LOGGER.info("Final url: {}", finalUrl);
        File file = ttsConfig.restTemplate().execute(
                finalUrl,
                GET,
                null,
                clientHttpResponse -> {
                    File ret = File.createTempFile("download", AUDIO_EXTENSION);
                    StreamUtils.copy(clientHttpResponse.getBody(), new FileOutputStream(ret));
                    return ret;
                });
        LOGGER.info("{} => {}", file.getAbsolutePath(), file.length());

        Future<Boolean> future = ttsConfig.singleThreadExecutor().submit(createTask(file));
        try {
            return future.get(ttsConfig.processTimeout, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
        }
        return false;
        /*try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(Objects.requireNonNull(file))) { //didn't work, so moved to threaded approach
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            e.printStackTrace();
        }*/
    }

    private Callable<Boolean> createTask(File audioClip) {
        return () -> {
            String cmd = String.format(PLAY_AUDIO_CLIP_MPV_CMD, audioClip.getPath());
            Runtime runtime = ttsConfig.runtime();
            try {
                Process process = runtime.exec(cmd);
                int procWaitRes = process.waitFor();
                LOGGER.info("Result from process wait: {}", procWaitRes);
                return true;
            } catch (IOException | InterruptedException e) {
                LOGGER.error("Error while running aud clip on mpv: {} => ", audioClip.getPath(), e);
            }
            return false;
        };
    }

    @EventListener(ApplicationReadyEvent.class)
    public void appUp() {
        converter("pass1 is up now", "en", "female");
        converter("pass1356 is down now", "en", "female");
    }
}

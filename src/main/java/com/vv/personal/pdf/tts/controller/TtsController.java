package com.vv.personal.pdf.tts.controller;

import com.vv.personal.pdf.tts.config.TtsConfig;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.vv.personal.pdf.tts.constants.Constants.*;
import static com.vv.personal.pdf.tts.util.StringUtil.reduceStringToOnlyCharacters;
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
                .queryParam(REQ_PARAM_TTS_KEY, ttsConfig.key)
                .queryParam(REQ_PARAM_TTS_ENGINE, ttsConfig.engine)
                .queryParam(REQ_PARAM_TTS_PITCH, ttsConfig.pitch)
                .queryParam(REQ_PARAM_TTS_RATE, ttsConfig.rate)
                .queryParam(REQ_PARAM_TTS_VOLUME, ttsConfig.volume);
    }

    @GetMapping("/convert/text/speech")
    public Boolean converter(@RequestParam String text,
                             @RequestParam(defaultValue = "en") String language,
                             @RequestParam(defaultValue = "gender") String gender,
                             @RequestParam(defaultValue = "download") String prefix,
                             @RequestParam(defaultValue = "true") Boolean playAudioOnTheFly,
                             @RequestParam(defaultValue = "/tmp") String destinationFolder) {
        String finalUrl = uriComponentsBuilder()
                .queryParam(REQ_PARAM_TTS_TEXT, text.replaceAll(SPACE_PERCENT_20, SPACE_STR))
                .queryParam(REQ_PARAM_TTS_LANG, language)
                .queryParam(REQ_PARAM_TTS_GENDER, gender)
                .build()
                .toUriString();

        LOGGER.info("Final url: {}", finalUrl);
        File file = ttsConfig.restTemplate().execute(
                finalUrl,
                GET,
                null,
                clientHttpResponse -> {
                    File ret = File.createTempFile(prefix, AUDIO_EXTENSION);
                    StreamUtils.copy(clientHttpResponse.getBody(), new FileOutputStream(ret));
                    Files.move(Path.of(ret.getPath()), Path.of(destinationFolder + "/" + ret.getName()));
                    return ret;
                });
        LOGGER.info("{} => {}", file.getAbsolutePath(), file.length());

        if (playAudioOnTheFly) {
            Future<Boolean> future = ttsConfig.singleThreadExecutor().submit(playAudioFromFile(file));
            try {
                return future.get(ttsConfig.processTimeout, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                e.printStackTrace();
            }
            return false;
        }
        return true;
        /*try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(Objects.requireNonNull(file))) { //didn't work, so moved to threaded approach
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            e.printStackTrace();
        }*/
    }

    @GetMapping("/convert/pdf/text")
    public List<String> extractTextFromPdf(@RequestParam String pdfFileLocation,
                                           @RequestParam(defaultValue = "en") String lng) {
        File pdf = new File(pdfFileLocation);
        String endingSplit = getSplitRegex(lng);
        try (PDDocument doc = PDDocument.load(pdf)) {
            LOGGER.info("PDF loaded in program, decoding in progress, total pages: {}", doc.getNumberOfPages());
            List<String> lines = Arrays.stream(new PDFTextStripper().getText(doc)
                    .split(endingSplit)) //"\\r?\\n"
                    .map(this::cleanLine)
                    .filter(line -> !line.isEmpty() && !line.toLowerCase().contains("copyright"))
                    .collect(Collectors.toList());
            lines.forEach(LOGGER::info);
            LOGGER.info("Decoding complete. Extracted {} lines", lines.size());
            return lines;
        } catch (IOException e) {
            LOGGER.error("Failed to extract text from PDF. ", e);
        }
        return new ArrayList<>();
    }

    @GetMapping("/automate/pdf/text/speech")
    public Boolean automatePdfTextToSpeech(@RequestParam String pdfFileLocation,
                                           @RequestParam(defaultValue = "en") String lng,
                                           @RequestParam(defaultValue = "true") Boolean convertAll,
                                           @RequestParam(defaultValue = "0") Integer startLineIndex,
                                           @RequestParam(defaultValue = "10") Integer endLineIndexButNotIncluded,
                                           @RequestParam(defaultValue = "true") Boolean playAudioOnTheFly,
                                           @RequestParam(defaultValue = "/tmp") String destinationFolder) {
        StopWatch stopWatch = ttsConfig.stopWatch();
        File file = new File(pdfFileLocation);
        String prefixForDownloadedSpeech = reduceStringToOnlyCharacters(file.getName());
        List<String> pdfLines = extractTextFromPdf(pdfFileLocation, lng);
        int lastLine = convertAll ? pdfLines.size() :
                (pdfLines.size() > endLineIndexButNotIncluded ? endLineIndexButNotIncluded : pdfLines.size());
        LOGGER.info("Will perform automated text to speech for pdf at '{}' until {}th line.", pdfFileLocation, lastLine);
        boolean allDone = true;
        for (int i = startLineIndex; i < lastLine; i++) {
            if (!converter(pdfLines.get(i),
                    ttsConfig.language,
                    ttsConfig.gender,
                    String.format(PREFIX_DOWNLOADED_SPEECH_LINE_PARTS, prefixForDownloadedSpeech, i),
                    playAudioOnTheFly,
                    destinationFolder)) {
                LOGGER.warn("Breaking conversion at line {} => {}, as failed to convert to speech.", i, pdfLines.get(i));
                allDone = false;
                break;
            }
        }
        stopWatch.stop();
        if (allDone) LOGGER.info("All {} lines converted to speech successfully in {}s", lastLine - startLineIndex, stopWatch.getTime(TimeUnit.SECONDS));
        else LOGGER.info("Couldn't convert all {} lines successfully in {}s", lastLine - startLineIndex, stopWatch.getTime(TimeUnit.SECONDS));
        return allDone;
    }

    @GetMapping("/automate/audio/readAloud")
    public void automateAudioReadAloud(@RequestParam String audioClipsLocation,
                                       @RequestParam(defaultValue = "true") Boolean readAllClips,
                                       @RequestParam(defaultValue = "0") Integer startClipNumber,
                                       @RequestParam(defaultValue = "10") Integer endClipNumber) {
        File audSrc = new File(audioClipsLocation);
        File[] sortedClips = new File[Objects.requireNonNull(audSrc.listFiles()).length + 1];
        Arrays.stream(Objects.requireNonNull(audSrc.listFiles())).forEach(file -> {
            if (file.getName().endsWith(".mp3") && !file.getName().contains("whole")) {
                int clipNumber = Integer.parseInt(file.getName().split("-")[1]);
                sortedClips[clipNumber] = file;
            }
        });
        Arrays.stream(sortedClips).forEach(file -> {
            if (file != null) {
                int clipNumber = Integer.parseInt(file.getName().split("-")[1]);
                if (readAllClips || clipNumber >= startClipNumber && clipNumber <= endClipNumber)
                    ttsConfig.singleThreadExecutor().submit(playAudioFromFile(file));
            }
        });
    }

    @GetMapping("/collate/audio/whole")
    public void collateWholeAudio(@RequestParam String audioClipsLocation,
                                  @RequestParam(defaultValue = "true") Boolean readAllClips,
                                  @RequestParam(defaultValue = "0") Integer startClipNumber,
                                  @RequestParam(defaultValue = "10") Integer endClipNumber) throws IOException {
        File audSrc = new File(audioClipsLocation);
        File[] sortedClips = new File[Objects.requireNonNull(audSrc.listFiles()).length + 1];
        Arrays.stream(Objects.requireNonNull(audSrc.listFiles())).forEach(file -> {
            if (file.getName().endsWith(".mp3") && !file.getName().contains("whole") && !file.getName().contains("tmp")) {
                int clipNumber = Integer.parseInt(file.getName().split("-")[1]);
                if (readAllClips || clipNumber <= endClipNumber)
                    sortedClips[clipNumber] = file;
            }
        });
        Queue<File> sortedClipsQueue = new LinkedList<>();
        Arrays.stream(sortedClips).forEach(file -> {
            if (file != null) sortedClipsQueue.add(file);
        });

        File file1 = sortedClipsQueue.peek();
        String parentPath = file1.getParent();
        String collatedAudioClip = parentPath + "/" + file1.getName().split("-")[0] + "-whole.mp3";

        FileOutputStream fileOutputStream = new FileOutputStream(collatedAudioClip);
        Vector<InputStream> inputStreams = new Vector<>();
        while (!sortedClipsQueue.isEmpty()) {
            inputStreams.add(generateFileInputStream(sortedClipsQueue.poll()));
            //LOGGER.info("First file: {}, exists: {}, reading in second file: {}", file1.getName(), file1.exists(), file2.getName());
        }
        LOGGER.info("Loaded vectorized input stream with {} clips", inputStreams.size());

        Enumeration<InputStream> enumeration = inputStreams.elements();
        SequenceInputStream sequenceInputStream = new SequenceInputStream(enumeration);
        int tmp;
        long passes = 0L;
        while (true) {
            try {
                if ((tmp = sequenceInputStream.read()) == -1) break;
                fileOutputStream.write(tmp);
            } catch (IOException e) {
                LOGGER.error("Failed to collate all audio clips. ", e);
                break;
            }
            if (passes % 100000 == 0) LOGGER.info("Collating on, pass number {}", passes);
            passes++;
        }
        sequenceInputStream.close();
        fileOutputStream.flush();
        fileOutputStream.close();
        LOGGER.info("Collation complete. Destination file: {}", collatedAudioClip);
    }

    private String getSplitRegex(String lng) {
        switch (lng) {
            case "en":
                return "\\.";
            case "hi":
                return "\\।";
        }
        return "\\r?\\n";
    }

    private String cleanLine(String line) {
        return line.trim()
                .replaceAll("\n", " ") //below for hindi
//                .replaceAll("ि", "")
//                .replaceAll("ं", "")
//                .replaceAll("ु", "")
//                .replaceAll("ू", "")
//                .replaceAll("ा", "")
//                .replaceAll("\uE771", "")
//                .replaceAll("\uE772", "")
//                .replaceAll("\uE779", "")
//                .replaceAll("\uE8C0", "")
//                .replaceAll("\uE8C4", "")
//                .replaceAll("\uE8CC", "")
//                .replaceAll("\uE8D8", "")
//                .replaceAll("\uE988", "")
                ;
    }

    private FileInputStream generateFileInputStream(File file) {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    private FileInputStream generateFileInputStream(String filePath) {
        return generateFileInputStream(new File(filePath));
    }

    private Callable<Boolean> playAudioFromFile(File audioClip) {
        return () -> {
            LOGGER.info("Playing {}", audioClip);
            String cmd = String.format(PLAY_AUDIO_CLIP_MPV_CMD_FORMAT, audioClip.getPath());
            Runtime runtime = ttsConfig.runtime();
            try {
                Process process = runtime.exec(cmd);
                int procWaitRes = process.waitFor();
                if (procWaitRes != 0) LOGGER.info("Result from process wait: {}", procWaitRes);
                return true;
            } catch (IOException | InterruptedException e) {
                LOGGER.error("Error while running aud clip on mpv: {} => ", audioClip.getPath(), e);
            }
            return false;
        };
    }

}

package com.arvatar.vortex.workflow;

import com.arvatar.vortex.dto.ASRJob;
import com.arvatar.vortex.dto.JobStatus;
import com.arvatar.vortex.dto.MinIOS3Client;
import org.json.JSONObject;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import voxel.common.v1.Types;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ASRActivitiesImpl implements ASRActivities {
    private static final Logger LOGGER = LoggerFactory.getLogger(ASRActivitiesImpl.class);

    public ASRActivitiesImpl() {
    }

    @Override
    public void processJob(ASRJob job) {
        LOGGER.info("Running ASR activity for job {}", job != null ? job.job_id : "unknown");
        if (job == null) return;
        job.started_at = Instant.now().toString();
        String video_object_key = job.video_id;
        try {
            Types.Video video = fetchVideoFromObjectStore(video_object_key);
            String videoFilePath = saveTemporaryVideoFile(video);
            String audioFilePath = extractAudionWithFFmpeg(videoFilePath);
            JSONObject transcription = new JSONObject(transcribeWithFasterWhisper(audioFilePath));
            JSONArray segments = transcription.getJSONArray("segments");

            for(int index = 0; index < segments.length(); index++){
                JSONObject segment = segments.getJSONObject(index);
                for(int wordIndex = 0; wordIndex < segment.getJSONArray("words").length(); wordIndex++){
                    String word = segment.getJSONArray("words").getJSONObject(wordIndex).get("word").toString();
                    List<String> phonemes = phonemizeWord(word);
                    segment.getJSONArray("words").getJSONObject(wordIndex).put("phonemes", phonemes);
                }
            }
            job.output = segments.toString();
            deleteTemporaryVideoFile(videoFilePath);
        }catch (Exception e){
            job.error = e.getMessage();
        }
        job.status = job.error == null ? JobStatus.ASR_FAILED : JobStatus.ASR_COMPLETED;
        updateASRJob(job);
    }

    public Types.Video fetchVideoFromObjectStore(String video_object_key){
        MinIOS3Client minIOS3Client = new MinIOS3Client();
        return minIOS3Client.fetchVideo(video_object_key);
    }

    public static String saveTemporaryVideoFile(Types.Video video) {
        byte[] videoBytes = video.getPayload().toByteArray();
        String currentDir = System.getProperty("user.dir");
        String fileName = String.join("/", currentDir, String.valueOf(Instant.now().toEpochMilli()), "video.mp4");
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get(fileName), videoBytes);
        } catch (Exception e) {
            LOGGER.error("Failed to write temporary video file to {}", fileName, e);
            throw new RuntimeException(e);
        }
        return fileName;
    }

    public static void deleteTemporaryVideoFile(String fileName){
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(fileName));
        } catch (Exception e) {
            LOGGER.error("Failed to delete temporary video file {}", fileName, e);
        }
    }

    public static String extractAudionWithFFmpeg(String videoFilePath){
        try {
            String audioFilePath = videoFilePath.replace(".mp4", ".wav");
            ProcessBuilder pb = new ProcessBuilder("ffmpeg",
                    "-i",
                    videoFilePath,
                    "-vn",
                    "-acodec",
                    "copy",
                    audioFilePath
            );
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            p.waitFor();
            return audioFilePath;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String transcribeWithFasterWhisper(String audioFilePath){
        try {
            String currentDir = System.getProperty("user.dir");
            String fasterWhisperPath = currentDir + "/faster_whisper.py";
            ProcessBuilder chmodProcessBuilder = new ProcessBuilder("chmod", "+x", fasterWhisperPath);
            chmodProcessBuilder.start().waitFor();
            ProcessBuilder whisperProcessBuilder = new ProcessBuilder(
                    "python3",
                    fasterWhisperPath,
                    "--file",
                    audioFilePath
            );
            Process process = whisperProcessBuilder.redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Faster Whisper failed with exit code " + exitCode);
            }
            return jsonBuilder.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> phonemizeWord(String word){
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "espeak-ng",
                    "-v",
                    "en-us",
                    "--ipa",
                    "-q",
                    word
            );
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            String output;
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining(" ")).trim();
            }
            process.waitFor();
            output = output.replace("'", "").replace(",", "").replaceAll("\\+s", "");
            if (output.isEmpty()) return List.of();
            List<String> phones = new ArrayList<>();
            for( int i = 0; i < output.length(); ){
                int cp = output.codePointAt(i);
                String  s = new String(Character.toChars(cp));
                phones.add(s);
                i += Character.charCount(cp);
            }
            return phones;
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public static void updateASRJob(ASRJob job){
        MinIOS3Client minIOS3Client = new MinIOS3Client();
        minIOS3Client.updateASRJob(job);
    }
}

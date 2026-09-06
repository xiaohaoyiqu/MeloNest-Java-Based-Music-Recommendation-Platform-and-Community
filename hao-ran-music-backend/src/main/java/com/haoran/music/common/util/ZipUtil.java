



package com.haoran.music.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;





@Slf4j
@Component
public class ZipUtil {









    public List<String> unzip(String zipFilePath, String destDir) throws IOException {
        List<String> extractedFiles = new ArrayList<>();


        Path destPath = Paths.get(destDir);
        if (!Files.exists(destPath)) {
            Files.createDirectories(destPath);
        }

        try (ZipFile zipFile = new ZipFile(zipFilePath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryPath = destPath.resolve(entry.getName());


                if (!entryPath.normalize().startsWith(destPath.normalize())) {
                    log.warn("event=zip_entry_rejected reason=PATH_TRAVERSAL");
                    continue;
                }

                if (entry.isDirectory()) {

                    Files.createDirectories(entryPath);
                } else {

                    if (entryPath.getParent() != null) {
                        Files.createDirectories(entryPath.getParent());
                    }


                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Files.copy(is, entryPath, StandardCopyOption.REPLACE_EXISTING);
                        extractedFiles.add(entryPath.toString());
                        log.debug("event=zip_entry_extracted");
                    }
                }
            }
        }

        log.info("event=zip_extract_completed extractedCount={}", extractedFiles.size());
        return extractedFiles;
    }









    public List<String> extractAudioFiles(String zipFilePath, String destDir) throws IOException {
        List<String> extractedFiles = unzip(zipFilePath, destDir);


        List<String> audioFiles = new ArrayList<>();
        String[] audioExtensions = {".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".wma"};

        for (String file : extractedFiles) {
            String lower = file.toLowerCase();
            for (String ext : audioExtensions) {
                if (lower.endsWith(ext)) {
                    audioFiles.add(file);
                    break;
                }
            }
        }

        log.info("event=zip_audio_extract_completed audioCount={}", audioFiles.size());
        return audioFiles;
    }









    public String extractLyricFile(String zipFilePath, String destDir) throws IOException {
        List<String> extractedFiles = unzip(zipFilePath, destDir);


        String[] lyricExtensions = {".lrc", ".txt"};

        for (String file : extractedFiles) {
            String lower = file.toLowerCase();
            for (String ext : lyricExtensions) {
                if (lower.endsWith(ext)) {
                    log.info("event=zip_lyric_extract_completed lyricCount=1");
                    return file;
                }
            }
        }

        return null;
    }








    public String readLyricFile(String lyricFilePath) throws IOException {
        StringBuilder content = new StringBuilder();

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(lyricFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        return content.toString();
    }







    public boolean isSupportedArchive(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".zip");



    }







    public boolean isAudioFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        String[] audioExtensions = {".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".wma", ".opus"};
        for (String ext : audioExtensions) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }







    public boolean isLyricFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".lrc") || lower.endsWith(".txt");
    }
}

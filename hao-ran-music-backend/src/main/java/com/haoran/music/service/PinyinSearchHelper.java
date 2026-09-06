




package com.haoran.music.service;

import com.haoran.music.entity.Song;
import com.haoran.music.util.PinyinUtil;
import com.haoran.music.vo.search.SearchResultVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;




@Component
public class PinyinSearchHelper {









    public List<Song> filterByPinyin(String pinyinInput, List<Song> candidateSongs, int limit) {
        if (pinyinInput == null || pinyinInput.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String input = pinyinInput.toLowerCase().trim();


        if (!PinyinUtil.isPinyin(input)) {
            return new ArrayList<>();
        }

        Set<Song> matchedSongs = new TreeSet<>((a, b) -> {

            int playCountCompare = Long.compare(
                b.getPlayCount() != null ? b.getPlayCount() : 0,
                a.getPlayCount() != null ? a.getPlayCount() : 0
            );
            return playCountCompare;
        });

        for (Song song : candidateSongs) {
            if (matchesPinyin(input, song)) {
                matchedSongs.add(song);
                if (matchedSongs.size() >= limit) {
                    break;
                }
            }
        }

        return new ArrayList<>(matchedSongs).stream()
                .limit(limit)
                .collect(Collectors.toList());
    }








    public boolean matchesPinyin(String pinyinInput, Song song) {
        String input = pinyinInput.toLowerCase().trim();


        if (PinyinUtil.fuzzyMatch(input, song.getName())) {
            return true;
        }


        if (song.getArtistNames() != null && PinyinUtil.fuzzyMatch(input, song.getArtistNames())) {
            return true;
        }


        if (song.getAlbumName() != null && PinyinUtil.fuzzyMatch(input, song.getAlbumName())) {
            return true;
        }

        return false;
    }








    public String[] getPinyinKeywords(Song song) {
        List<String> keywords = new ArrayList<>();


        if (song.getName() != null && PinyinUtil.containsChinese(song.getName())) {
            String fullName = PinyinUtil.toPinyin(song.getName()).replace(" ", "");
            String initial = PinyinUtil.toPinyinInitial(song.getName());
            keywords.add(fullName);
            keywords.add(initial);
        }


        if (song.getArtistNames() != null && PinyinUtil.containsChinese(song.getArtistNames())) {
            String artistPinyin = PinyinUtil.toPinyin(song.getArtistNames()).replace(" ", "");
            String artistInitial = PinyinUtil.toPinyinInitial(song.getArtistNames());
            keywords.add(artistPinyin);
            keywords.add(artistInitial);
        }

        return keywords.toArray(new String[0]);
    }







    public boolean needPinyinSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return false;
        }


        if (PinyinUtil.isPinyin(keyword) && keyword.length() >= 2) {
            return true;
        }


        if (PinyinUtil.containsChinese(keyword) && keyword.matches(".*[a-zA-Z].*")) {
            return true;
        }

        return false;
    }








    public double calculatePinyinMatchScore(String input, String target) {
        if (input == null || target == null) {
            return 0.0;
        }

        input = input.toLowerCase().trim();
        target = target.toLowerCase().trim();


        if (target.equals(input)) {
            return 1.0;
        }


        if (target.contains(input)) {
            return 0.8;
        }


        String targetPinyin = PinyinUtil.toPinyin(target).replace(" ", "");
        if (targetPinyin.contains(input)) {
            return 0.6;
        }


        String targetInitial = PinyinUtil.toPinyinInitial(target);
        if (targetInitial.contains(input)) {
            return 0.4;
        }

        return 0.0;
    }
}

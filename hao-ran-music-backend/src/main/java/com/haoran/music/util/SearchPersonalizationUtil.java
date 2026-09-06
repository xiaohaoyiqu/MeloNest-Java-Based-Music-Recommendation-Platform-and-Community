package com.haoran.music.util;

import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.Album;
import com.haoran.music.entity.Artist;
import com.haoran.music.entity.Song;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;











@Slf4j
public class SearchPersonalizationUtil {




    private static final double BASE_SCORE = 100.0;




    private static final double PREFERENCE_BOOST = 1.3;








    public static double calculateSongWeight(Song song, Map<String, Object> userPortrait) {
        if (song == null) {
            return BASE_SCORE;
        }

        if (ObjectUtils.isEmpty(userPortrait) || userPortrait.isEmpty()) {
            return BASE_SCORE;
        }

        double weight = BASE_SCORE;
        @SuppressWarnings("unchecked")
        Map<String, Object> musicPreference = (Map<String, Object>) userPortrait.get("musicPreference");

        if (musicPreference != null) {

            @SuppressWarnings("unchecked")
            List<String> favoriteGenres = (List<String>) musicPreference.get("favoriteGenres");
            if (favoriteGenres != null && ObjectUtils.isNotEmpty(song.getMainType())) {
                int genreIndex = favoriteGenres.indexOf(song.getMainType());
                if (genreIndex >= 0) {

                    double genreBoost = 1.0 + (favoriteGenres.size() - genreIndex) * 0.1;
                    weight *= genreBoost;
                }
            }


            @SuppressWarnings("unchecked")
            List<String> favoriteLanguages = (List<String>) musicPreference.get("favoriteLanguages");
            if (favoriteLanguages != null && ObjectUtils.isNotEmpty(song.getLanguage())) {
                int langIndex = favoriteLanguages.indexOf(song.getLanguage());
                if (langIndex >= 0) {

                    double langBoost = 1.0 + (favoriteLanguages.size() - langIndex) * 0.15;
                    weight *= langBoost;
                }
            }
        }

        return weight;
    }








    public static double calculateAlbumWeight(Album album, Map<String, Object> userPortrait) {
        if (album == null) {
            return BASE_SCORE;
        }

        if (ObjectUtils.isEmpty(userPortrait) || userPortrait.isEmpty()) {
            return BASE_SCORE;
        }

        double weight = BASE_SCORE;
        @SuppressWarnings("unchecked")
        Map<String, Object> musicPreference = (Map<String, Object>) userPortrait.get("musicPreference");

        if (musicPreference != null) {

            @SuppressWarnings("unchecked")
            List<String> favoriteLanguages = (List<String>) musicPreference.get("favoriteLanguages");
            if (favoriteLanguages != null && ObjectUtils.isNotEmpty(album.getLanguage())) {
                int langIndex = favoriteLanguages.indexOf(album.getLanguage());
                if (langIndex >= 0) {
                    double langBoost = 1.0 + (favoriteLanguages.size() - langIndex) * 0.15;
                    weight *= langBoost;
                }
            }
        }

        return weight;
    }








    public static double calculateArtistWeight(Artist artist, Map<String, Object> userPortrait) {
        if (artist == null) {
            return BASE_SCORE;
        }

        if (ObjectUtils.isEmpty(userPortrait) || userPortrait.isEmpty()) {
            return BASE_SCORE;
        }



        double weight = BASE_SCORE;


        if (ObjectUtils.isNotEmpty(artist.getFansCount()) && artist.getFansCount() > 0) {
            double fansBoost = 1.0 + Math.log10(artist.getFansCount() + 1) * 0.1;
            weight *= Math.min(fansBoost, 1.5);          
        }

        return weight;
    }









    public static List<String> getPersonalizedHotKeywords(
            Map<String, Object> userPortrait,
            List<String> defaultKeywords,
            int limit) {

        if (ObjectUtils.isEmpty(userPortrait) || userPortrait.isEmpty()) {
            return defaultKeywords.stream()
                    .limit(limit)
                    .collect(java.util.stream.Collectors.toList());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> musicPreference = (Map<String, Object>) userPortrait.get("musicPreference");

        if (musicPreference != null) {
            @SuppressWarnings("unchecked")
            List<String> favoriteGenres = (List<String>) musicPreference.get("favoriteGenres");

            if (favoriteGenres != null && !favoriteGenres.isEmpty()) {

                List<String> personalized = new java.util.ArrayList<>(favoriteGenres);


                for (String keyword : defaultKeywords) {
                    if (!personalized.contains(keyword)) {
                        personalized.add(keyword);
                    }
                    if (personalized.size() >= limit) {
                        break;
                    }
                }

                return personalized.stream()
                        .limit(limit)
                        .collect(java.util.stream.Collectors.toList());
            }
        }

        return defaultKeywords.stream()
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }









    public static List<String> getPersonalizedSuggestions(
            String keyword,
            Map<String, Object> userPortrait,
            List<String> defaultSuggestions) {

        if (ObjectUtils.isEmpty(keyword)) {
            return defaultSuggestions;
        }

        if (ObjectUtils.isEmpty(userPortrait) || userPortrait.isEmpty()) {
            return defaultSuggestions;
        }


        @SuppressWarnings("unchecked")
        Map<String, Object> musicPreference = (Map<String, Object>) userPortrait.get("musicPreference");

        if (musicPreference != null) {
            @SuppressWarnings("unchecked")
            List<String> favoriteGenres = (List<String>) musicPreference.get("favoriteGenres");

            if (favoriteGenres != null && !favoriteGenres.isEmpty()) {

                List<String> personalized = new java.util.ArrayList<>();


                for (String suggestion : defaultSuggestions) {
                    personalized.add(suggestion);
                    for (String genre : favoriteGenres) {
                        if (suggestion.contains(genre) || suggestion.contains(keyword)) {

                            personalized.remove(suggestion);
                            personalized.add(0, suggestion);
                            break;
                        }
                    }
                }

                return personalized;
            }
        }

        return defaultSuggestions;
    }









    public static double calculateRankScore(
            double relevanceScore,
            double popularityScore,
            double personalizationScore) {


        return relevanceScore * 0.4
                + popularityScore * 0.4
                + personalizationScore * 0.2;
    }
}

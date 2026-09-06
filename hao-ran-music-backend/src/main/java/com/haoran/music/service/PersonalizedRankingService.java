package com.haoran.music.service;

import java.util.List;
import java.util.Map;












public interface PersonalizedRankingService {








    Map<String, Object> getPersonalizedHotSongs(Long userId, Integer limit);








    Map<String, Object> getPersonalizedCreators(Long userId, Integer limit);









    Map<String, Object> getPersonalizedPlaylists(Long userId, Integer limit);








    Map<String, Object> getUserActiveTimeAnalysis(Long userId);








    Map<String, Object> getUserDeepPreferenceAnalysis(Long userId);
}

package com.haoran.music.service;

import com.haoran.music.vo.audio.PlaylistAudioAnalysis;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;







public interface PlaylistAudioAnalysisService {







    PlaylistAudioAnalysis analyzePlaylist(Long playlistId);







    List<String> generateAutoTags(Long playlistId);







    Map<String, Object> updatePlaylistAudioTags(Long playlistId);




    Map<String, Object> updatePlaylistAudioTags(Long playlistId, Long operatorId);







    Map<String, Object> getPlaylistFeatureDistribution(Long playlistId);







    Double checkPlaylistConsistency(Long playlistId);







    int batchUpdateAllPlaylists();









    List<Long> recommendPlaylistsByFeatures(Double valence, Double energy, Integer limit);






    JdbcTemplate getJdbcTemplate();
}

package com.haoran.music.service;

import java.util.List;
import java.util.Map;







public interface DJMixService {










    List<Map<String, Object>> getMixableSongs(Long songId, Integer bpmTolerance, Integer limit);










    Map<String, Object> generateMixPlaylist(Long startSongId, Integer durationMinutes, Long userId);




    Map<String, Object> generateMixPlaylist(Long startSongId, Integer durationMinutes,
                                            Long userId, List<Long> selectedSongIds);








    Map<String, Object> checkMixable(Long songId1, Long songId2);







    Map<String, Object> getSongMixInfo(Long songId);









    List<Map<String, Object>> getCompatibleKeys(Integer key, Integer mode);









    Map<String, Object> calculateBpmConversion(Double sourceBpm, Double targetBpm);
}

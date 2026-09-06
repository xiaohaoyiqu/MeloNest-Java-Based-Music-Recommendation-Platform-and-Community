package com.haoran.music.service;

import com.haoran.music.vo.recommend.RecommendVO;
import com.haoran.music.vo.recommend.RecommendedSongVO;

import java.util.List;





public interface RecommendService {







    RecommendVO getDailyRecommend(Long userId);








    RecommendVO getPersonalRecommend(Long userId, Integer limit);








    RecommendVO getDiscoverRecommend(Long userId, Integer limit);









    RecommendVO getSimilarSongs(Long userId, Long songId, Integer limit);









    RecommendVO getArtistRecommend(Long userId, Long artistId, Integer limit);








    RecommendVO getSocialRecommend(Long userId, Integer limit);






    void refreshUserRecommendProfile(Long userId);







    List<String> getUserPreferenceTags(Long userId);









    void recordUserAction(Long userId, String actionType, Long targetId, Integer targetType);










    List<RecommendedSongVO> getPersonalRecommendWithReason(Long userId, Integer limit);








    List<RecommendedSongVO> getDailyDiscoveryWithReason(Long userId, Integer limit);









    List<RecommendedSongVO> getSimilarRecommendWithReason(Long userId, Long songId, Integer limit);










    List<Long> getPersonalizedPlaylists(Long userId, Integer limit);








    List<Long> getPersonalizedAlbums(Long userId, Integer limit);








    List<Long> getPersonalizedMVs(Long userId, Integer limit);
}

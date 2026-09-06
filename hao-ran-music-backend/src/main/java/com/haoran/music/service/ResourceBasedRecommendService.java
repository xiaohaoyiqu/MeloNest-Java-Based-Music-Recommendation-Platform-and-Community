package com.haoran.music.service;

import com.haoran.music.vo.recommend.RecommendVO;
import java.util.List;
import java.util.Map;











public interface ResourceBasedRecommendService {









    RecommendVO getRecommendByRequestedSongs(Long userId, Integer limit);









    RecommendVO getHotRequestedSongsRecommend(Long userId, Integer limit);









    RecommendVO getRecommendByRequestPreference(Long userId, Integer limit);








    Map<String, Object> getUnmetRequestsAnalysis(Long userId);








    Map<String, Object> getHotRequestStatistics(Integer limit);
}

package com.haoran.music.service;

import java.util.List;
import java.util.Map;












public interface TopicRecommendService {








    List<Map<String, Object>> getRecommendedTopics(Long userId, Integer limit);









    List<Map<String, Object>> getTopicsByGenre(Long userId, String genre, Integer limit);
}





package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.MusicTopic;

import java.util.List;
import java.util.Map;




public interface MusicTopicService extends IService<MusicTopic> {







    List<Object> getHotTopics(Integer limit);









    List<Map<String, Object>> getPersonalizedTopics(Long userId, Integer limit);








    Boolean followTopic(Long topicId, Long userId);








    Boolean unfollowTopic(Long topicId, Long userId);








    Object getTopicDetail(Long topicId, Long userId);
}

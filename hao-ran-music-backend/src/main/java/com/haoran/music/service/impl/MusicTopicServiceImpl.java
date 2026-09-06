   
                      
                        
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.MusicTopic;
import com.haoran.music.entity.TopicFollow;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.MusicTopicMapper;
import com.haoran.music.mapper.TopicFollowMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.MusicTopicService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

   
           
   
@Slf4j
@Service
public class MusicTopicServiceImpl extends ServiceImpl<MusicTopicMapper, MusicTopic> implements MusicTopicService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;

    @Autowired
    private MusicTopicMapper musicTopicMapper;

    @Autowired
    private TopicFollowMapper topicFollowMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public List<Object> getHotTopics(Integer limit) {
        int resolvedLimit = normalizeLimit(limit);

        LambdaQueryWrapper<MusicTopic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MusicTopic::getIsDeleted, false)
                .eq(MusicTopic::getIsHot, true)
                .orderByDesc(MusicTopic::getPostCount)
                .orderByDesc(MusicTopic::getFollowerCount)
                .last("LIMIT " + resolvedLimit);

        List<MusicTopic> topics = musicTopicMapper.selectList(wrapper);
        return topics.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getPersonalizedTopics(Long userId, Integer limit) {
        int resolvedLimit = normalizeLimit(limit);

        LambdaQueryWrapper<MusicTopic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MusicTopic::getIsDeleted, false)
                .eq(MusicTopic::getIsHot, true)
                .orderByDesc(MusicTopic::getPostCount)
                .orderByDesc(MusicTopic::getFollowerCount)
                .last("LIMIT " + (resolvedLimit * 2));

        List<MusicTopic> topics = musicTopicMapper.selectList(wrapper);
        Set<Long> followedTopicIds = getFollowedTopicIds(userId, topics);

        List<Map<String, Object>> result = new ArrayList<>();
        for (MusicTopic topic : topics) {
            if (result.size() >= resolvedLimit) break;

            Map<String, Object> vo = convertToVO(topic);
            vo.put("isFollowed", followedTopicIds.contains(topic.getId()));

                     
            String recommendReason = generateTopicRecommendReason(topic);
            vo.put("recommendReason", recommendReason);

            result.add(vo);
        }

        log.info("个性化推荐话题: userId={}, count={}", userId, result.size());
        return result;
    }

    private int normalizeLimit(Integer limit) {
        if (ObjectUtils.isEmpty(limit) || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Set<Long> getFollowedTopicIds(Long userId, List<MusicTopic> topics) {
        if (ObjectUtils.isEmpty(userId) || topics == null || topics.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> topicIds = topics.stream()
                .map(MusicTopic::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (topicIds.isEmpty()) {
            return Collections.emptySet();
        }
        LambdaQueryWrapper<TopicFollow> followWrapper = new LambdaQueryWrapper<>();
        followWrapper.eq(TopicFollow::getUserId, userId)
                .in(TopicFollow::getTopicId, topicIds);
        return topicFollowMapper.selectList(followWrapper).stream()
                .map(TopicFollow::getTopicId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

       
               
      
                      
                   
       
    private String generateTopicRecommendReason(MusicTopic topic) {
        List<String> reasons = new ArrayList<>();

               
        Integer postCount = topic.getPostCount() != null ? topic.getPostCount() : 0;
        if (postCount >= 100) {
            reasons.add("热门话题");
        } else if (postCount >= 50) {
            reasons.add("活跃话题");
        }

               
        Integer followerCount = topic.getFollowerCount() != null ? topic.getFollowerCount() : 0;
        if (followerCount >= 100) {
            reasons.add("多人关注");
        }

               
        if (!ObjectUtils.isEmpty(topic.getCategory())) {
            reasons.add(topic.getCategory());
        }

                 
        if (reasons.isEmpty()) {
            return "推荐话题";
        } else if (reasons.size() == 1) {
            return reasons.get(0);
        } else {
            return reasons.get(0) + " · " + reasons.get(1);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean followTopic(Long topicId, Long userId) {
        if (ObjectUtils.isEmpty(topicId) || ObjectUtils.isEmpty(userId)) {
            log.warn("关注话题参数为空: topicId={}, userId={}", topicId, userId);
            return false;
        }
        User user = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(user, "关注话题");

                   
        MusicTopic topic = musicTopicMapper.selectById(topicId);
        if (ObjectUtils.isEmpty(topic) || Boolean.TRUE.equals(topic.getIsDeleted())) {
            log.warn("话题不存在或已删除: topicId={}", topicId);
            return false;
        }

        int result = topicFollowMapper.insertIgnore(topicId, userId);

                  
        if (result > 0 && UserAccountStatusUtil.canContributePublicStats(user)) {
            musicTopicMapper.adjustFollowerCount(topicId, 1);
        }

        if (result <= 0) {
            log.info("用户已关注该话题: topicId={}, userId={}", topicId, userId);
        }
        log.info("关注话题成功: topicId={}, userId={}, result={}", topicId, userId, result);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unfollowTopic(Long topicId, Long userId) {
        if (ObjectUtils.isEmpty(topicId) || ObjectUtils.isEmpty(userId)) {
            log.warn("取消关注话题参数为空: topicId={}, userId={}", topicId, userId);
            return false;
        }

        int result = topicFollowMapper.deleteByTopicAndUser(topicId, userId);

                  
        if (result > 0 && UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            musicTopicMapper.adjustFollowerCount(topicId, -1);
        }

        log.info("取消关注话题成功: topicId={}, userId={}, result={}", topicId, userId, result);
        return result > 0;
    }

    @Override
    public Object getTopicDetail(Long topicId, Long userId) {
        if (ObjectUtils.isEmpty(topicId)) {
            return null;
        }

        MusicTopic topic = musicTopicMapper.selectById(topicId);
        if (ObjectUtils.isEmpty(topic) || Boolean.TRUE.equals(topic.getIsDeleted())) {
            return null;
        }

        Map<String, Object> result = convertToVO(topic);

                      
        if (!ObjectUtils.isEmpty(userId)) {
            LambdaQueryWrapper<TopicFollow> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TopicFollow::getTopicId, topicId)
                    .eq(TopicFollow::getUserId, userId);

            Long count = topicFollowMapper.selectCount(wrapper);
            result.put("isFollowed", count != null && count > 0);
        } else {
            result.put("isFollowed", false);
        }

        return result;
    }

       
              
       
    private Map<String, Object> convertToVO(MusicTopic topic) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", topic.getId());
        vo.put("name", topic.getName());
        vo.put("description", topic.getDescription());
        vo.put("cover", topic.getCover());
        vo.put("category", topic.getCategory());
        vo.put("postCount", topic.getPostCount() != null ? topic.getPostCount() : 0);
        vo.put("followerCount", topic.getFollowerCount() != null ? topic.getFollowerCount() : 0);
        vo.put("isHot", topic.getIsHot() != null ? topic.getIsHot() : false);
        return vo;
    }
}

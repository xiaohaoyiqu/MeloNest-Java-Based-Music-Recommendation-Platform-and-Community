package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.RecommendWeightService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

   
             
  
                      
   
@Slf4j
@Service
public class RecommendWeightServiceImpl implements RecommendWeightService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserVipMapper userVipMapper;

    @Resource
    private UserCreditMapper userCreditMapper;

    @Resource
    private UserDecorationMapper userDecorationMapper;

    @Resource
    private UserFollowMapper userFollowMapper;

    @Resource
    private CreatorMapper creatorMapper;

    @Resource
    private SongMapper songMapper;

    @Resource
    private AlbumMapper albumMapper;

    @Resource
    private PlaylistMapper playlistMapper;

    @Resource
    private ListenHistoryMapper listenHistoryMapper;

    @Resource
    private SongLikeMapper songLikeMapper;
    @Resource
    private com.haoran.music.mapper.PlaylistCollaboratorMapper collaboratorMapper;

    @Resource
    private com.haoran.music.mapper.PlaylistSongMapper playlistSongMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
                
    private static final String VIP_WEIGHT_CACHE_PREFIX = "recommend_weight:vip:";
    private static final String ROLE_WEIGHT_CACHE_PREFIX = "recommend_weight:role:";
    private static final String CREDIT_WEIGHT_CACHE_PREFIX = "recommend_weight:credit:";
    private static final String DECORATION_WEIGHT_CACHE_PREFIX = "recommend_weight:decoration:";
    private static final String CREATOR_TYPE_CACHE_PREFIX = "recommend_weight:creator:";
    private static final String USER_COEFFICIENT_CACHE_PREFIX = "recommend_coefficient:";

                
    private static final long CACHE_EXPIRE_SECONDS = 3600;

                                                       

    @Override
    public Double calculateRecommendWeight(Long userId, Long contentId, Long creatorId) {
        if (ObjectUtils.isEmpty(contentId)) {
            return 0.0;
        }

                    
        Double baseScore = calculateBaseScore(contentId, "song");
        if (baseScore == null || baseScore <= 0) {
            return 0.0;
        }

                      
        Double userCoefficient = getUserRecommendCoefficient(userId);

                      
        Double socialWeight = 1.0;
        if (ObjectUtils.isNotEmpty(creatorId) && ObjectUtils.isNotEmpty(userId)) {
            socialWeight = getSocialRelationWeight(userId, creatorId);
        }

                    
        return baseScore * userCoefficient * socialWeight;
    }

    @Override
    public Double getUserRecommendCoefficient(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 1.0;        
        }

                  
        String cacheKey = USER_COEFFICIENT_CACHE_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (Double) cached;
        }

                 
        double coefficient = 1.0;

                
        coefficient *= getVipWeight(userId);

               
        coefficient *= getRoleWeight(userId);

                
        coefficient *= getCreditWeight(userId);

               
        coefficient *= getDecorationWeight(userId);

               
        redisTemplate.opsForValue().set(cacheKey, coefficient, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        return coefficient;
    }

    @Override
    public Double calculateBaseScore(Long contentId, String type) {
        if (ObjectUtils.isEmpty(contentId)) {
            return 0.0;
        }

        try {
            switch (type) {
                case "song":
                    return calculateSongBaseScore(contentId);
                case "album":
                    return calculateAlbumBaseScore(contentId);
                case "playlist":
                    return calculatePlaylistBaseScore(contentId);
                default:
                    return 0.0;
            }
        } catch (Exception e) {
            log.warn("计算基础得分失败: contentId={}, type={}", contentId, type);
            return 0.0;
        }
    }

    @Override
    public Double calculateUserInterestScore(Long userId, Long contentId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(contentId)) {
            return 0.0;
        }

        double score = 0.0;

                              
        LambdaQueryWrapper<SongLike> likeWrapper = new LambdaQueryWrapper<>();
        likeWrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getSongId, contentId)
                .eq(SongLike::getIsFavorite, 1);
        Long likeCount = songLikeMapper.selectCount(likeWrapper);
        if (likeCount != null && likeCount > 0) {
            score += 40.0;
        }

                                             
        LambdaQueryWrapper<ListenHistory> historyWrapper = new LambdaQueryWrapper<>();
        historyWrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getSongId, contentId);
        Long playCount = listenHistoryMapper.selectCount(historyWrapper);
        if (playCount != null) {
            score += Math.min(30.0, playCount / 5.0);
        }

                                      
        historyWrapper = new LambdaQueryWrapper<>();
        historyWrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getSongId, contentId)
                .ge(ListenHistory::getListenTime, LocalDateTime.now().minusDays(7))
                .orderByDesc(ListenHistory::getListenTime)
                .last("LIMIT 1");
        ListenHistory recentPlay = listenHistoryMapper.selectOne(historyWrapper);
        if (recentPlay != null) {
            score += 30.0;
        }

        return Math.min(100.0, score);
    }

    @Override
    public Double applyTimeDecay(Double score, Long contentTime, Integer halfLifeDays) {
        if (score == null || score <= 0 || ObjectUtils.isEmpty(contentTime)) {
            return score;
        }

        int halfLife = halfLifeDays != null ? halfLifeDays : 30;            
        long currentTime = System.currentTimeMillis() / 1000;           
        long timeDiff = currentTime - contentTime;          
        double daysDiff = timeDiff / (24.0 * 3600.0);         

                                 
        double decayFactor = Math.exp(-daysDiff / halfLife);

        return score * decayFactor;
    }

                                                       

    @Override
    public Double getVipWeight(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 1.0;
        }

                
        String cacheKey = VIP_WEIGHT_CACHE_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (Double) cached;
        }

                  
        LambdaQueryWrapper<UserVip> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserVip::getUserId, userId)
                .eq(UserVip::getDeleted, CommonConstants.NOT_DELETED);
        UserVip userVip = userVipMapper.selectOne(wrapper);

        double weight = 1.0;            
        if (userVip != null && userVip.getVipLevel() != null) {
            Integer vipLevel = userVip.getVipLevel();
            if (userVip.getVipExpireTime() != null && userVip.getVipExpireTime().isAfter(LocalDateTime.now())) {
                        
                if (vipLevel != null) {
                    if (vipLevel == 1) {                
                        weight = 1.1;
                    } else if (vipLevel == 2) {                  
                        weight = 1.2;
                    } else if (vipLevel == 3) {               
                        weight = 1.4;
                    } else if (vipLevel == 4) {                 
                        weight = 1.5;
                    } else {
                        weight = 1.0;         
                    }
                }
            }
        }

               
        redisTemplate.opsForValue().set(cacheKey, weight, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        return weight;
    }

    @Override
    public Double getRoleWeight(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 1.0;
        }

                
        String cacheKey = ROLE_WEIGHT_CACHE_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (Double) cached;
        }

                 
        User user = userMapper.selectById(userId);
        double weight = 1.0;
        if (user != null && user.getRole() != null) {
            switch (user.getRole()) {
                case "CREATOR":
                    weight = 1.4;
                    break;
                case "SUPER_ADMIN":
                    weight = 1.4;
                    break;
                case "ADMIN":
                    weight = 1.2;
                    break;
                case "MODERATOR":
                    weight = 1.1;
                    break;
                default:
                    weight = 1.0;
            }
        }

               
        redisTemplate.opsForValue().set(cacheKey, weight, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        return weight;
    }

    @Override
    public Double getCreditWeight(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 1.0;
        }

                
        String cacheKey = CREDIT_WEIGHT_CACHE_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (Double) cached;
        }

                
        LambdaQueryWrapper<UserCredit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCredit::getUserId, userId);
        UserCredit userCredit = userCreditMapper.selectOne(wrapper);

        double weight = 1.0;
        if (userCredit != null && userCredit.getCreditScore() != null) {
            int score = userCredit.getCreditScore();
            if (score >= 90) {
                weight = 1.1;      
            } else if (score >= 80) {
                weight = 1.0;      
            } else if (score >= 70) {
                weight = 1.0;      
            } else if (score >= 60) {
                weight = 0.8;      
            } else {
                weight = 0.5;     
            }
        }

               
        redisTemplate.opsForValue().set(cacheKey, weight, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        return weight;
    }

    @Override
    public Double getDecorationWeight(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 1.0;
        }

                
        String cacheKey = DECORATION_WEIGHT_CACHE_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (Double) cached;
        }

                         
        LambdaQueryWrapper<UserDecoration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDecoration::getUserId, userId)
                .eq(UserDecoration::getIsEquipped, 1)
                .eq(UserDecoration::getDeleted, CommonConstants.NOT_DELETED);
        List<UserDecoration> decorations = userDecorationMapper.selectList(wrapper);

        double weight = 1.0;         
        for (UserDecoration decoration : decorations) {
            if (decoration != null && decoration.getRarity() != null) {
                String rarity = decoration.getRarity();
                if ("legendary".equals(rarity)) {
                    weight = Math.max(weight, 1.3);
                } else if ("epic".equals(rarity)) {
                    weight = Math.max(weight, 1.1);
                } else if ("rare".equals(rarity)) {
                    weight = Math.max(weight, 1.0);
                }
            }
        }

               
        redisTemplate.opsForValue().set(cacheKey, weight, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        return weight;
    }

    @Override
    public Double getSocialRelationWeight(Long userId, Long creatorId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(creatorId)) {
            return 1.0;
        }

        double weight = 1.0;         

                              
        LambdaQueryWrapper<UserFollow> followWrapper1 = new LambdaQueryWrapper<>();
        followWrapper1.eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getFolloweeId, creatorId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED);
        Long count1 = userFollowMapper.selectCount(followWrapper1);

        LambdaQueryWrapper<UserFollow> followWrapper2 = new LambdaQueryWrapper<>();
        followWrapper2.eq(UserFollow::getFollowerId, creatorId)
                .eq(UserFollow::getFolloweeId, userId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED);
        Long count2 = userFollowMapper.selectCount(followWrapper2);

        if (count1 != null && count1 > 0 && count2 != null && count2 > 0) {
            weight = 1.9;      
        } else if (count1 != null && count1 > 0) {
            weight = 1.3;         
        } else if (count2 != null && count2 > 0) {
            weight = 1.1;         
        }

        return weight;
    }

    @Override
    public Double getCreatorTypeWeight(Long creatorId) {
        if (ObjectUtils.isEmpty(creatorId)) {
            return 1.0;
        }

                
        String cacheKey = CREATOR_TYPE_CACHE_PREFIX + creatorId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (Double) cached;
        }

                  
                                                 
                                       
        LambdaQueryWrapper<Creator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Creator::getUserId, creatorId)
                .eq(Creator::getDeleted, CommonConstants.NOT_DELETED);
        Creator creator = creatorMapper.selectOne(wrapper);

        double weight = 1.0;
        if (creator != null && creator.getCreatorType() != null) {
            String type = creator.getCreatorType();
            if ("signed".equals(type)) {
                weight = 1.4;         
            } else if ("independent".equals(type)) {
                weight = 1.0;         
            } else if ("external_signed".equals(type)) {
                weight = 1.1;        
            } else if ("external_independent".equals(type)) {
                weight = 0.8;        
            }
        }

               
        redisTemplate.opsForValue().set(cacheKey, weight, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        return weight;
    }

    @Override
    public Map<Long, Double> batchCalculateRecommendWeight(Long userId, List<Long> contentIds, String type) {
        Map<Long, Double> result = new HashMap<>();
        if (ObjectUtils.isEmpty(userId) || contentIds == null || contentIds.isEmpty()) {
            return result;
        }

                         
        Double userCoefficient = getUserRecommendCoefficient(userId);

                      
        for (Long contentId : contentIds) {
            Double weight = calculateRecommendWeight(userId, contentId, null);
            if (weight != null && weight > 0) {
                result.put(contentId, weight);
            }
        }

        return result;
    }

                                                       

       
               
       
    private Double calculateSongBaseScore(Long songId) {
        Song song = songMapper.selectById(songId);
        if (song == null || !CommonConstants.STATUS_NORMAL.equals(song.getStatus())) {
            return 0.0;
        }

        double score = 0.0;

                   
        if (song.getAvgRating() != null) {
            score += song.getAvgRating().doubleValue() * 0.4;
        }

                                        
        if (song.getPlayCount() != null) {
            score += Math.min(20.0, song.getPlayCount() / 100.0);
        }

                    
        if (song.getHotScore() != null) {
            score += Math.min(20.0, song.getHotScore() / 1000.0);
        }

                    
        if (song.getReleaseDate() != null) {
            long daysSinceRelease = ChronoUnit.DAYS.between(song.getReleaseDate(), LocalDateTime.now());
            if (daysSinceRelease <= 30) {
                score += 5.0;
            }
        }

                                        
        if (song.getFavoriteCount() != null) {
            score += Math.min(15.0, song.getFavoriteCount() / 10.0);
        }

        return score;
    }
       
               
       
    private Double calculateAlbumBaseScore(Long albumId) {
        Album album = albumMapper.selectById(albumId);
        if (album == null) {
            return 0.0;
        }

        double score = 0.0;

                                      
        if (album.getPlayCount() != null) {
            score += Math.min(30.0, album.getPlayCount() / 100.0);
        }

                                     
        if (album.getFavoriteCount() != null) {
            score += Math.min(40.0, album.getFavoriteCount() / 10.0);
        }

                                     
        if (album.getSongCount() != null) {
            score += Math.min(20.0, album.getSongCount() * 0.5);
        }

                               
        if (album.getCreateTime() != null) {
            long daysSinceRelease = ChronoUnit.DAYS.between(
                    album.getCreateTime(), LocalDateTime.now());
            if (daysSinceRelease <= 30) {
                score += 10.0;
            }
        }

        return score;
    }


       
               
       
    private Double calculatePlaylistBaseScore(Long playlistId) {
        Playlist playlist = playlistMapper.selectById(playlistId);
        if (playlist == null) {
            return 0.0;
        }

        double score = 0.0;

              
        if (playlist.getPlayCount() != null) {
            score += Math.min(30.0, playlist.getPlayCount() / 100.0);
        }

              
        if (playlist.getFavoriteCount() != null) {
            score += Math.min(40.0, playlist.getFavoriteCount() / 10.0);
        }

               
        if (playlist.getSongCount() != null) {
            score += Math.min(20.0, playlist.getSongCount() * 2.0);
        }

               
        if (playlist.getIsFeatured() != null && playlist.getIsFeatured() == 1) {
            score += 30.0;
        }

        return score;
    }

       
                 
                            
      
                         
                         
                              
       
    @Override
    public Double getCollaborativePlaylistWeight(Long userId, Long songId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(songId)) {
            return 1.0;
        }
        if (!UserAccountStatusUtil.canAppearInRecommendations(userId, userMapper::selectById)) {
            return 1.0;
        }

        try {
                                   
            LambdaQueryWrapper<com.haoran.music.entity.PlaylistCollaborator> collaboratorWrapper =
                    new LambdaQueryWrapper<>();
            collaboratorWrapper.eq(com.haoran.music.entity.PlaylistCollaborator::getUserId, userId)
                    .eq(com.haoran.music.entity.PlaylistCollaborator::getStatus, "accepted")
                    .eq(com.haoran.music.entity.PlaylistCollaborator::getDeleted, CommonConstants.NOT_DELETED);

            List<com.haoran.music.entity.PlaylistCollaborator> collaborators =
                    collaboratorMapper.selectList(collaboratorWrapper);

            if (collaborators.isEmpty()) {
                return 1.0;              
            }

                            
            List<Long> playlistIds = collaborators.stream()
                    .map(com.haoran.music.entity.PlaylistCollaborator::getPlaylistId)
                    .collect(Collectors.toList());
            Set<Long> publicPlaylistIds = filterPublicPlaylistIds(playlistIds);
            if (publicPlaylistIds.isEmpty()) {
                return 1.0;
            }

                               
            LambdaQueryWrapper<com.haoran.music.entity.PlaylistSong> songWrapper = new LambdaQueryWrapper<>();
            songWrapper.in(com.haoran.music.entity.PlaylistSong::getPlaylistId, publicPlaylistIds)
                    .eq(com.haoran.music.entity.PlaylistSong::getSongId, songId)
                    .eq(com.haoran.music.entity.PlaylistSong::getDeleted, CommonConstants.NOT_DELETED);

            Long count = playlistSongMapper.selectCount(songWrapper);
            if (count != null && count > 0 && canExposeSongUploader(songId)) {
                                      
                                       
                double bonus = 1.1 + Math.min(publicPlaylistIds.size() * 0.02, 0.2);
                log.debug("协作歌单推荐加成: userId={}, songId={}, collaborators={}, bonus={}",
                        userId, songId, publicPlaylistIds.size(), bonus);
                return bonus;
            }

            return 1.0;
        } catch (Exception e) {
            log.warn("获取协作歌单权重失败: userId={}, songId={}", userId, songId);
            return 1.0;
        }
    }

    private Set<Long> filterPublicPlaylistIds(List<Long> playlistIds) {
        if (playlistIds == null || playlistIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> safePlaylistIds = playlistIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (safePlaylistIds.isEmpty()) {
            return Collections.emptySet();
        }

        List<Playlist> playlists = playlistMapper.selectBatchIds(safePlaylistIds);
        Set<Long> ownerIds = playlists.stream()
                .map(Playlist::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allowedOwnerIds = UserAccountStatusUtil.filterPublicContentUserIds(
                ownerIds, ids -> userMapper.selectBatchIds(ids));

        return playlists.stream()
                .filter(playlist -> playlist != null
                        && CommonConstants.NOT_DELETED.equals(playlist.getDeleted())
                        && (playlist.getUserId() == null || allowedOwnerIds.contains(playlist.getUserId())))
                .map(Playlist::getId)
                .collect(Collectors.toSet());
    }

    private boolean canExposeSongUploader(Long songId) {
        Song song = songMapper.selectById(songId);
        return song != null
                && CommonConstants.NOT_DELETED.equals(song.getDeleted())
                && CommonConstants.STATUS_NORMAL.equals(song.getStatus())
                && (song.getUploaderId() == null
                || UserAccountStatusUtil.canExposePublicContent(song.getUploaderId(), userMapper::selectById));
    }
}

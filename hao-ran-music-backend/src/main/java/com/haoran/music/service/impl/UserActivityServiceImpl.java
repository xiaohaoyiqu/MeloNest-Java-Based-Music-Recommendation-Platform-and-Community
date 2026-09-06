package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.dto.PageResult;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.UserActivityService;
import com.haoran.music.vo.user.UserActivityVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

   
                      
                             
   
@Slf4j
@Service
public class UserActivityServiceImpl implements UserActivityService {

    @Resource
    private MusicPostMapper musicPostMapper;

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private SongLikeMapper songLikeMapper;

    @Resource
    private SongMapper songMapper;

    @Resource
    private PlaylistMapper playlistMapper;

    @Resource
    private UserActivityMapper userActivityMapper;

    @Resource
    private com.haoran.music.mapper.UserMapper userMapper;

       
               
       
    private static final Map<String, String> ACTIVITY_TYPE_MAP = new HashMap<>();

       
               
       
    private static final Map<String, String> TARGET_TYPE_MAP = new HashMap<>();

    static {
        ACTIVITY_TYPE_MAP.put("post", "发布动态");
        ACTIVITY_TYPE_MAP.put("comment", "发表评论");
        ACTIVITY_TYPE_MAP.put("like", "点赞");
        ACTIVITY_TYPE_MAP.put("favorite", "收藏");
        ACTIVITY_TYPE_MAP.put("share", "分享");

        TARGET_TYPE_MAP.put("song", "歌曲");
        TARGET_TYPE_MAP.put("album", "专辑");
        TARGET_TYPE_MAP.put("artist", "歌手");
        TARGET_TYPE_MAP.put("playlist", "歌单");
        TARGET_TYPE_MAP.put("mv", "MV");
        TARGET_TYPE_MAP.put("post", "动态");
        TARGET_TYPE_MAP.put("user", "用户");
    }

    @Override
    public PageResult<UserActivityVO> getUserActivities(Long userId, String type, Integer page, Integer size) {
        if (!UserAccountStatusUtil.canExposePublicContent(userId, userMapper::selectById)) {
            return emptyActivityPage(page, size);
        }

        List<UserActivityVO> activities = new ArrayList<>();

                      
        if ("all".equals(type) || "post".equals(type)) {
            activities.addAll(getPostActivities(userId));
        }
        if ("all".equals(type) || "comment".equals(type)) {
            activities.addAll(getCommentActivities(userId));
        }
        if ("all".equals(type) || "like".equals(type)) {
            activities.addAll(getLikeActivities(userId));
        }
        if ("all".equals(type) || "favorite".equals(type)) {
            activities.addAll(getFavoriteActivities(userId));
        }
        if ("all".equals(type) || "share".equals(type)) {
            activities.addAll(getShareActivities(userId));
        }

                
        activities.sort(Comparator.comparing(
                UserActivityVO::getCreatedTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

             
        int total = activities.size();
        int start = (page - 1) * size;
        int end = Math.min(start + size, total);
        List<UserActivityVO> pageRecords = start < total ? activities.subList(start, end) : new ArrayList<>();

                 
        for (UserActivityVO activity : pageRecords) {
            activity.setTimeDescription(formatTimeDescription(activity.getCreatedTime()));
        }

        PageResult<UserActivityVO> result = new PageResult<>();
        result.setRecords(pageRecords);
        result.setTotal((long) total);
        result.setCurrent((long) page);
        result.setSize((long) size);
        result.setPages((long) Math.ceil((double) total / size));

        return result;
    }

    @Override
    public Object getUserActivityStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        if (!UserAccountStatusUtil.canExposePublicContent(userId, userMapper::selectById)) {
            stats.put("postCount", 0);
            stats.put("commentCount", 0);
            stats.put("likeCount", 0);
            stats.put("favoriteCount", 0);
            stats.put("shareCount", 0);
            return stats;
        }

                 
        Long postCount = musicPostMapper.selectCount(
                new LambdaQueryWrapper<MusicPost>().eq(MusicPost::getUserId, userId)
        );
        stats.put("postCount", postCount != null ? postCount.intValue() : 0);

                 
        Long commentCount = commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>().eq(Comment::getUserId, userId)
        );
        stats.put("commentCount", commentCount != null ? commentCount.intValue() : 0);

                 
        Long likeCount = songLikeMapper.selectCount(
                new LambdaQueryWrapper<SongLike>()
                        .eq(SongLike::getUserId, userId)
                        .eq(SongLike::getIsLike, 1)
        );
        stats.put("likeCount", likeCount != null ? likeCount.intValue() : 0);

                                  
        Long favoriteCount = songLikeMapper.selectCount(
                new LambdaQueryWrapper<SongLike>()
                        .eq(SongLike::getUserId, userId)
                        .eq(SongLike::getIsFavorite, 1)
        );
        stats.put("favoriteCount", favoriteCount != null ? favoriteCount.intValue() : 0);

                 
        Long shareCount = userActivityMapper.selectCount(
                new LambdaQueryWrapper<UserActivity>()
                        .eq(UserActivity::getUserId, userId)
                        .eq(UserActivity::getActivityType, "share")
                        .eq(UserActivity::getDeleted, 0)
        );
        stats.put("shareCount", shareCount != null ? shareCount.intValue() : 0);

        return stats;
    }

       
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordActivity(Long userId, String activityType, String targetType, Long targetId, String content) {
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "记录活动");

        UserActivity activity = new UserActivity();
        activity.setUserId(userId);
        activity.setActivityType(activityType);
        activity.setTargetType(targetType);
        activity.setTargetId(targetId);
        activity.setContent(content);
        activity.setCreateTime(LocalDateTime.now());
        activity.setUpdateTime(LocalDateTime.now());

        userActivityMapper.insert(activity);

        log.info("记录用户活动: userId={}, type={}, target={}, id={}", userId, activityType, targetType, targetId);
    }

       
             
       
    private List<UserActivityVO> getPostActivities(Long userId) {
        List<MusicPost> posts = musicPostMapper.selectList(
                new LambdaQueryWrapper<MusicPost>()
                        .eq(MusicPost::getUserId, userId)
                        .orderByDesc(MusicPost::getCreateTime)
                        .last("LIMIT 50")
        );

        return posts.stream().map(post -> {
            UserActivityVO vo = new UserActivityVO();
            vo.setId(post.getId());
            vo.setActivityType("post");
            vo.setActivityTypeName(ACTIVITY_TYPE_MAP.get("post"));
            vo.setTargetType("post");
            vo.setTargetTypeName("动态");
            vo.setTargetId(post.getId());
            vo.setContent(post.getContent());
            vo.setCreatedTime(post.getCreateTime());

                   
            Map<String, Object> target = new HashMap<>();
            target.put("id", post.getId());
            target.put("content", post.getContent());
            target.put("images", post.getImages());
            target.put("likeCount", post.getLikeCount());
            target.put("commentCount", post.getCommentCount());
            vo.setTarget(target);

            return vo;
        }).collect(Collectors.toList());
    }

       
             
       
    private List<UserActivityVO> getCommentActivities(Long userId) {
        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getUserId, userId)
                        .orderByDesc(Comment::getCreateTime)
                        .last("LIMIT 50")
        );

        return comments.stream().map(comment -> {
            UserActivityVO vo = new UserActivityVO();
            vo.setId(comment.getId());
            vo.setActivityType("comment");
            vo.setActivityTypeName(ACTIVITY_TYPE_MAP.get("comment"));
            vo.setTargetType(determineCommentTargetType(comment));
            vo.setTargetTypeName(TARGET_TYPE_MAP.getOrDefault(determineCommentTargetType(comment), "评论"));
            vo.setTargetId(comment.getTargetId());
            vo.setContent(comment.getContent());
            vo.setCreatedTime(comment.getCreateTime());

            return vo;
        }).collect(Collectors.toList());
    }

       
             
       
    private List<UserActivityVO> getLikeActivities(Long userId) {
        List<SongLike> likes = songLikeMapper.selectList(
                new LambdaQueryWrapper<SongLike>()
                        .eq(SongLike::getUserId, userId)
                        .eq(SongLike::getIsLike, 1)
                        .orderByDesc(SongLike::getCreateTime)
                        .last("LIMIT 50")
        );

        return likes.stream().map(like -> {
            UserActivityVO vo = new UserActivityVO();
            vo.setId(like.getId());
            vo.setActivityType("like");
            vo.setActivityTypeName(ACTIVITY_TYPE_MAP.get("like"));
            vo.setTargetType("song");
            vo.setTargetTypeName(TARGET_TYPE_MAP.get("song"));
            vo.setTargetId(like.getSongId());
            vo.setCreatedTime(like.getCreateTime());

            return vo;
        }).collect(Collectors.toList());
    }

       
             
       
    private List<UserActivityVO> getFavoriteActivities(Long userId) {
        List<SongLike> favorites = songLikeMapper.selectList(
                new LambdaQueryWrapper<SongLike>()
                        .eq(SongLike::getUserId, userId)
                        .eq(SongLike::getIsFavorite, 1)
                        .orderByDesc(SongLike::getUpdateTime)
                        .last("LIMIT 50")
        );
        Map<Long, Song> songsById = songMap(favorites.stream()
                .map(SongLike::getSongId)
                .collect(Collectors.toList()));

        return favorites.stream().map(favorite -> {
            UserActivityVO vo = new UserActivityVO();
            vo.setId(favorite.getId());
            vo.setActivityType("favorite");
            vo.setActivityTypeName(ACTIVITY_TYPE_MAP.get("favorite"));
            vo.setTargetType("song");
            vo.setTargetTypeName(TARGET_TYPE_MAP.get("song"));
            vo.setTargetId(favorite.getSongId());
            vo.setCreatedTime(favorite.getUpdateTime() != null
                    ? favorite.getUpdateTime() : favorite.getCreateTime());
            vo.setTarget(songTarget(songsById.get(favorite.getSongId())));

            return vo;
        }).collect(Collectors.toList());
    }

    private List<UserActivityVO> getShareActivities(Long userId) {
        List<UserActivity> shares = userActivityMapper.selectList(
                new LambdaQueryWrapper<UserActivity>()
                        .eq(UserActivity::getUserId, userId)
                        .eq(UserActivity::getActivityType, "share")
                        .eq(UserActivity::getDeleted, 0)
                        .orderByDesc(UserActivity::getCreateTime)
                        .last("LIMIT 50")
        );
        return shares.stream().map(share -> {
            UserActivityVO vo = new UserActivityVO();
            vo.setId(share.getId());
            vo.setActivityType("share");
            vo.setActivityTypeName(ACTIVITY_TYPE_MAP.get("share"));
            vo.setTargetType(share.getTargetType());
            vo.setTargetTypeName(TARGET_TYPE_MAP.getOrDefault(share.getTargetType(), "内容"));
            vo.setTargetId(share.getTargetId());
            vo.setContent(share.getContent());
            vo.setCreatedTime(share.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    private Map<Long, Song> songMap(List<Long> songIds) {
        if (ObjectUtils.isEmpty(songIds)) {
            return Collections.emptyMap();
        }
        return songMapper.selectBatchIds(new LinkedHashSet<>(songIds)).stream()
                .collect(Collectors.toMap(Song::getId, song -> song, (left, right) -> left));
    }

    private Map<String, Object> songTarget(Song song) {
        if (song == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> target = new HashMap<>();
        target.put("id", song.getId());
        target.put("name", song.getName());
        target.put("cover", song.getCover());
        return target;
    }

       
               
                                                     
       
    private String determineCommentTargetType(Comment comment) {
        if (comment.getTargetType() != null) {
                                               
            Integer targetType = comment.getTargetType();
                                  
            switch (targetType) {
                case 1: return "song";
                case 2: return "album";
                case 3: return "playlist";
                case 4: return "mv";
                default: return "post";
            }
        }
        return "post";
    }

       
                          
      
                     
                       
                      
       
    private PageResult<UserActivityVO> emptyActivityPage(Integer page, Integer size) {
        long safePage = page == null || page <= 0 ? 1L : page;
        long safeSize = size == null || size <= 0 ? 20L : size;
        PageResult<UserActivityVO> result = new PageResult<>();
        result.setRecords(Collections.emptyList());
        result.setTotal(0L);
        result.setCurrent(safePage);
        result.setSize(safeSize);
        result.setPages(0L);
        return result;
    }

       
              
       
    private String formatTimeDescription(LocalDateTime time) {
        if (time == null) {
            return "";
        }

        LocalDateTime now = LocalDateTime.now();
        long seconds = java.time.Duration.between(time, now).getSeconds();

        if (seconds < 60) {
            return "刚刚";
        } else if (seconds < 3600) {
            return (seconds / 60) + "分钟前";
        } else if (seconds < 86400) {
            return (seconds / 3600) + "小时前";
        } else if (seconds < 604800) {
            return (seconds / 86400) + "天前";
        } else {
            return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
    }
}

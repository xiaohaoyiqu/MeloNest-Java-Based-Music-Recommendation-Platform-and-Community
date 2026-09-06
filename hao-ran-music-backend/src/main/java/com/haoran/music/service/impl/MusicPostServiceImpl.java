




package com.haoran.music.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.PublicStatsSql;
import com.haoran.music.common.enums.UserType;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.*;
import com.haoran.music.service.MusicPostService;
import com.haoran.music.service.NotificationService;
import com.haoran.music.service.UserVipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;




@Slf4j
@Service
public class MusicPostServiceImpl extends ServiceImpl<MusicPostMapper, MusicPost>
        implements MusicPostService {

    private static final int DEFAULT_POST_PAGE_SIZE = 10;
    private static final int MAX_POST_PAGE_SIZE = 50;

    private final MusicPostMapper musicPostMapper;
    private final PostLikeMapper postLikeMapper;
    private final UserFollowMapper userFollowMapper;
    private final UserMapper userMapper;
    private final SongMapper songMapper;
    private final AlbumMapper albumMapper;
    private final PlaylistMapper playlistMapper;
    private final MVMapper mvMapper;
    private final MusicTopicMapper musicTopicMapper;
    private final UserVipService userVipService;
    private final ObjectMapper objectMapper;

    @Autowired
    private NotificationService notificationService;

    public MusicPostServiceImpl(
            MusicPostMapper musicPostMapper,
            PostLikeMapper postLikeMapper,
            UserFollowMapper userFollowMapper,
            UserMapper userMapper,
            SongMapper songMapper,
            AlbumMapper albumMapper,
            PlaylistMapper playlistMapper,
            MVMapper mvMapper,
            MusicTopicMapper musicTopicMapper,
            UserVipService userVipService) {
        this.musicPostMapper = musicPostMapper;
        this.postLikeMapper = postLikeMapper;
        this.userFollowMapper = userFollowMapper;
        this.userMapper = userMapper;
        this.songMapper = songMapper;
        this.albumMapper = albumMapper;
        this.playlistMapper = playlistMapper;
        this.mvMapper = mvMapper;
        this.musicTopicMapper = musicTopicMapper;
        this.userVipService = userVipService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public IPage<Object> getPosts(String timeRange, String type, Long topicId, Long currentUserId, Integer page, Integer size) {
        if (ObjectUtils.isEmpty(page) || page <= 0) {
            page = 1;
        }
        if (ObjectUtils.isEmpty(size) || size <= 0) {
            size = DEFAULT_POST_PAGE_SIZE;
        } else {
            size = Math.min(size, MAX_POST_PAGE_SIZE);
        }

        LambdaQueryWrapper<MusicPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MusicPost::getIsDeleted, false);
        applyPublicAuthorFilter(wrapper);


        if (!ObjectUtils.isEmpty(timeRange) && !"all".equals(timeRange)) {
            LocalDateTime startTime = getTimeRangeStart(timeRange);
            if (startTime != null) {
                wrapper.ge(MusicPost::getCreateTime, startTime);
            }
        }


        if ("following".equals(type) && !ObjectUtils.isEmpty(currentUserId)) {

            LambdaQueryWrapper<UserFollow> followWrapper = new LambdaQueryWrapper<>();
            followWrapper.eq(UserFollow::getFollowerId, currentUserId)
                    .eq(UserFollow::getDeleted, 0);
            List<UserFollow> followList = userFollowMapper.selectList(followWrapper);

            if (!followList.isEmpty()) {
                List<Long> followUserIds = filterPublicSignalUserIds(followList.stream()
                        .map(UserFollow::getFolloweeId)
                        .collect(Collectors.toList()));
                wrapper.in(MusicPost::getUserId, followUserIds)
                        .and(w -> w.in(MusicPost::getVisibility, Arrays.asList("public", "followers"))
                                .or()
                                .isNull(MusicPost::getVisibility)
                                .or()
                                .eq(MusicPost::getVisibility, ""));
            } else {

                wrapper.eq(MusicPost::getId, -1L);              
            }
        } else if ("topic".equals(type) && !ObjectUtils.isEmpty(topicId)) {
            applyPublicPostVisibility(wrapper);
            MusicTopic topic = musicTopicMapper.selectById(topicId);
            if (topic == null || Boolean.TRUE.equals(topic.getIsDeleted())) {
                wrapper.eq(MusicPost::getId, -1L);
            } else {
                String topicName = topic.getName();
                if (ObjectUtils.isEmpty(topicName)) {
                    wrapper.like(MusicPost::getTopics, "\"" + topicId + "\"");
                } else {
                    wrapper.and(w -> w.like(MusicPost::getTopics, "\"" + topicId + "\"")
                            .or()
                            .like(MusicPost::getTopics, topicName));
                }
            }
        } else {

            applyPublicPostVisibility(wrapper);
        }

        wrapper.orderByDesc(MusicPost::getCreateTime);

        IPage<MusicPost> pageResult = musicPostMapper.selectPage(
                new Page<>(page, size),
                wrapper
        );


        IPage<Object> result = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        Set<Long> vipUserIds = activeVipUserIds(pageResult.getRecords());
        List<Object> records = pageResult.getRecords().stream()
                .map(post -> convertToVO(post, currentUserId, vipUserIds))
                .collect(Collectors.toList());
        result.setRecords(records);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPost(Long userId, String content, String images, String resourceType,
                           Long resourceId, String topics, String visibility, Boolean allowComment) {
        if (ObjectUtils.isEmpty(userId)) {
            log.warn("event=music_post_create_rejected reason=USER_ID_MISSING");
            return null;
        }
        ensureCurrentUserCanInteract(userId, "发布动态");

        MusicPost post = new MusicPost();
        post.setUserId(userId);

        if (ObjectUtils.isNotEmpty(content)) {
            SecurityCheckUtil.CheckResult contentCheck = SecurityCheckUtil.checkDescription(content);
            if (!contentCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, contentCheck.getMessage());
            }
            post.setContent(SecurityCheckUtil.escapeHtml(contentCheck.getCleanedValue()));
        } else {
            post.setContent(content);
        }
        post.setResourceType(resourceType);
        post.setResourceId(resourceId);
        post.setTopics(topics);
        post.setPostType(determinePostType(resourceType));
        post.setVisibility(ObjectUtils.isEmpty(visibility) ? "public" : visibility);
        post.setIsListenDiary(false);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setAllowComment(toAllowCommentValue(allowComment));
        post.setOfficialCommentClosed(false);
        post.setShareCount(0);
        post.setIsDeleted(false);
        post.setCreateTime(LocalDateTime.now());
        post.setUpdateTime(LocalDateTime.now());

        int result = musicPostMapper.insert(post);

        if (result > 0) {
            if ("public".equals(post.getVisibility()) && canPublicAuthor(userId)) {
                incrementTopicPostCounts(topics);
            }
            log.info("event=music_post_created postId={} userId={}", post.getId(), userId);
            return post.getId();
        }

        log.warn("event=music_post_create_failed userId={}", userId);
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createListenDiary(Long userId, String content, String images, String topics) {
        if (ObjectUtils.isEmpty(userId)) {
            log.warn("event=listen_diary_create_rejected reason=USER_ID_MISSING");
            return null;
        }
        ensureCurrentUserCanInteract(userId, "发布动态");

        MusicPost post = new MusicPost();
        post.setUserId(userId);

        if (ObjectUtils.isNotEmpty(content)) {
            SecurityCheckUtil.CheckResult contentCheck = SecurityCheckUtil.checkDescription(content);
            if (!contentCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, contentCheck.getMessage());
            }
            post.setContent(SecurityCheckUtil.escapeHtml(contentCheck.getCleanedValue()));
        } else {
            post.setContent(content);
        }
        post.setImages(images);
        post.setTopics(topics);
        post.setPostType("diary");
        post.setVisibility("public");
        post.setIsListenDiary(true);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setAllowComment(1);
        post.setOfficialCommentClosed(false);
        post.setShareCount(0);
        post.setIsDeleted(false);
        post.setCreateTime(LocalDateTime.now());
        post.setUpdateTime(LocalDateTime.now());

        int result = musicPostMapper.insert(post);

        if (result > 0) {
            if (canPublicAuthor(userId)) {
                incrementTopicPostCounts(topics);
            }
            log.info("event=listen_diary_created postId={} userId={}", post.getId(), userId);
            return post.getId();
        }

        log.warn("event=listen_diary_create_failed userId={}", userId);
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updatePostCommentSetting(Long postId, Long userId, Boolean allowComment) {
        if (ObjectUtils.isEmpty(postId) || ObjectUtils.isEmpty(userId)) {
            log.warn("event=music_post_comment_setting_rejected postId={} userId={} reason=PARAMETER_MISSING",
                    postId, userId);
            return false;
        }
        ensureCurrentUserCanInteract(userId, "更新动态评论设置");

        MusicPost post = musicPostMapper.selectById(postId);
        if (ObjectUtils.isEmpty(post) || Boolean.TRUE.equals(post.getIsDeleted())) {
            log.warn("event=music_post_comment_setting_rejected postId={} reason=POST_UNAVAILABLE", postId);
            return false;
        }
        if (!userId.equals(post.getUserId())) {
            log.warn("event=music_post_comment_setting_rejected postId={} userId={} reason=NOT_OWNER",
                    postId, userId);
            return false;
        }

        post.setAllowComment(toAllowCommentValue(allowComment));
        post.setUpdateTime(LocalDateTime.now());
        return musicPostMapper.updateById(post) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateOfficialCommentClosed(Long postId, Long operatorId, Boolean closed) {
        if (ObjectUtils.isEmpty(postId) || ObjectUtils.isEmpty(operatorId)) {
            log.warn("event=music_post_official_comment_setting_rejected postId={} operatorId={} "
                    + "reason=PARAMETER_MISSING", postId, operatorId);
            return false;
        }

        MusicPost post = musicPostMapper.selectById(postId);
        if (ObjectUtils.isEmpty(post) || Boolean.TRUE.equals(post.getIsDeleted())) {
            log.warn("event=music_post_official_comment_setting_rejected postId={} reason=POST_UNAVAILABLE",
                    postId);
            return false;
        }

        post.setOfficialCommentClosed(Boolean.TRUE.equals(closed));
        post.setUpdateTime(LocalDateTime.now());
        boolean success = musicPostMapper.updateById(post) > 0;
        log.info("event=music_post_official_comment_setting_updated postId={} operatorId={} "
                + "closed={} success={}", postId, operatorId, closed, success);
        return success;
    }











    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long autoCreateListenDiary(Long userId, Long songId, String songName, String artistName) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(songId)) {
            log.warn("event=listen_diary_auto_create_rejected userId={} songId={} reason=PARAMETER_MISSING",
                    userId, songId);
            return null;
        }
        if (!canPublicAuthor(userId)) {
            log.debug("event=listen_diary_auto_create_skipped userId={} songId={} reason=AUTHOR_NOT_PUBLIC",
                    userId, songId);
            return null;
        }


        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LambdaQueryWrapper<MusicPost> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(MusicPost::getUserId, userId)
                .eq(MusicPost::getIsListenDiary, true)
                .eq(MusicPost::getResourceId, songId)
                .ge(MusicPost::getCreateTime, todayStart);

        Long existingCount = musicPostMapper.selectCount(checkWrapper);
        if (existingCount != null && existingCount > 0) {
            log.debug("event=listen_diary_auto_create_skipped userId={} songId={} reason=ALREADY_CREATED",
                    userId, songId);
            return null;
        }


        String content = String.format("今天听了《%s》 - %s，感觉很不错！推荐给大家~", songName, artistName);


        String topics = null;
        try {

            topics = "{\"mood\":\"relaxed\",\"genre\":\"pop\"}";
        } catch (Exception e) {
            log.debug("event=listen_diary_topic_build_failed errorType={}",
                    e.getClass().getSimpleName());
        }


        MusicPost post = new MusicPost();
        post.setUserId(userId);

        if (ObjectUtils.isNotEmpty(content)) {
            SecurityCheckUtil.CheckResult contentCheck = SecurityCheckUtil.checkDescription(content);
            if (!contentCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, contentCheck.getMessage());
            }
            post.setContent(SecurityCheckUtil.escapeHtml(contentCheck.getCleanedValue()));
        } else {
            post.setContent(content);
        }
        post.setImages(null);
        post.setTopics(topics);
        post.setPostType("diary");
        post.setVisibility("public");
        post.setIsListenDiary(true);
        post.setResourceType("song");
        post.setResourceId(songId);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setAllowComment(1);
        post.setOfficialCommentClosed(false);
        post.setShareCount(0);
        post.setIsDeleted(false);
        post.setCreateTime(LocalDateTime.now());
        post.setUpdateTime(LocalDateTime.now());

        int result = musicPostMapper.insert(post);

        if (result > 0) {
            log.info("event=listen_diary_auto_created postId={} userId={} songId={}",
                    post.getId(), userId, songId);
            return post.getId();
        }

        log.warn("event=listen_diary_auto_create_failed userId={} songId={}", userId, songId);
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deletePost(Long postId, Long userId) {
        if (ObjectUtils.isEmpty(postId) || ObjectUtils.isEmpty(userId)) {
            log.warn("event=music_post_delete_rejected reason=PARAMETER_MISSING");
            return false;
        }

        MusicPost post = musicPostMapper.selectById(postId);
        if (ObjectUtils.isEmpty(post) || Boolean.TRUE.equals(post.getIsDeleted())) {
            log.warn("event=music_post_delete_rejected postId={} reason=POST_UNAVAILABLE", postId);
            return false;
        }


        if (!post.getUserId().equals(userId)) {
            log.warn("event=music_post_delete_rejected postId={} userId={} ownerId={} reason=NOT_OWNER",
                    postId, userId, post.getUserId());
            return false;
        }

        post.setIsDeleted(true);
        post.setUpdateTime(LocalDateTime.now());

        int result = musicPostMapper.updateById(post);
        if (result > 0 && "public".equals(post.getVisibility())) {
            decrementTopicPostCounts(post.getTopics());
        }

        log.info("event=music_post_deleted postId={} userId={} affectedRows={}", postId, userId, result);

        return result > 0;
    }









    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updatePost(Long postId, Long userId, String content) {
        if (ObjectUtils.isEmpty(postId) || ObjectUtils.isEmpty(userId)) {
            log.warn("event=music_post_update_rejected reason=PARAMETER_MISSING");
            return false;
        }

        if (ObjectUtils.isEmpty(content)) {
            log.warn("event=music_post_update_rejected reason=CONTENT_MISSING");
            return false;
        }
        ensureCurrentUserCanInteract(userId, "编辑动态");

        MusicPost post = musicPostMapper.selectById(postId);
        if (ObjectUtils.isEmpty(post) || Boolean.TRUE.equals(post.getIsDeleted())) {
            log.warn("event=music_post_update_rejected postId={} reason=POST_UNAVAILABLE", postId);
            return false;
        }


        if (!post.getUserId().equals(userId)) {
            log.warn("event=music_post_update_rejected postId={} userId={} ownerId={} reason=NOT_OWNER",
                    postId, userId, post.getUserId());
            return false;
        }


        LocalDateTime createTime = post.getCreateTime();
        if (createTime != null && createTime.plusMinutes(30).isBefore(LocalDateTime.now())) {
            log.warn("event=music_post_update_rejected postId={} reason=EDIT_WINDOW_EXPIRED", postId);
            return false;
        }


        if (ObjectUtils.isNotEmpty(content)) {
            SecurityCheckUtil.CheckResult contentCheck = SecurityCheckUtil.checkDescription(content);
            if (!contentCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, contentCheck.getMessage());
            }
            post.setContent(SecurityCheckUtil.escapeHtml(contentCheck.getCleanedValue()));
        }
        post.setUpdateTime(LocalDateTime.now());

        int result = musicPostMapper.updateById(post);

        log.info("event=music_post_updated postId={} userId={} affectedRows={}", postId, userId, result);

        return result > 0;
    }

    private int toAllowCommentValue(Boolean allowComment) {
        return Boolean.FALSE.equals(allowComment) ? 0 : 1;
    }

    private boolean isAuthorCommentAllowed(MusicPost post) {
        return post == null || post.getAllowComment() == null || post.getAllowComment() != 0;
    }

    private boolean isAdminUser(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return false;
        }
        User user = userMapper.selectById(userId);
        return user != null && UserRole.isAdmin(user.getRole());
    }


    private void applyPublicPostVisibility(LambdaQueryWrapper<MusicPost> wrapper) {
        wrapper.and(w -> w.eq(MusicPost::getVisibility, "public")
                .or()
                .isNull(MusicPost::getVisibility)
                .or()
                .eq(MusicPost::getVisibility, ""));
    }

    private void applyPostVisibility(LambdaQueryWrapper<MusicPost> wrapper, Long ownerUserId, Long currentUserId) {
        if (!ObjectUtils.isEmpty(currentUserId) && currentUserId.equals(ownerUserId)) {
            return;
        }
        applyPublicAuthorFilter(wrapper);
        if (isFollowingUser(currentUserId, ownerUserId)) {
            wrapper.and(w -> w.in(MusicPost::getVisibility, Arrays.asList("public", "followers"))
                    .or()
                    .isNull(MusicPost::getVisibility)
                    .or()
                    .eq(MusicPost::getVisibility, ""));
        } else {
            applyPublicPostVisibility(wrapper);
        }
    }

    private boolean canViewPost(MusicPost post, Long currentUserId) {
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted())) {
            return false;
        }
        if (!Objects.equals(currentUserId, post.getUserId()) && !canPublicAuthor(post.getUserId())) {
            return false;
        }
        String visibility = post.getVisibility();
        if (ObjectUtils.isEmpty(visibility) || "public".equals(visibility)) {
            return true;
        }
        if (!ObjectUtils.isEmpty(currentUserId) && currentUserId.equals(post.getUserId())) {
            return true;
        }
        return "followers".equals(visibility) && isFollowingUser(currentUserId, post.getUserId());
    }

    @Override
    public void requirePostReadable(Long postId, Long currentUserId) {
        MusicPost post = musicPostMapper.selectById(postId);
        if (!canViewPost(post, currentUserId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "动态不存在或不可见");
        }
    }

    @Override
    public void requirePostOwnerOrModerator(Long postId, Long currentUserId) {
        MusicPost post = musicPostMapper.selectById(postId);
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "动态不存在");
        }
        if (Objects.equals(post.getUserId(), currentUserId)) {
            return;
        }
        User viewer = currentUserId == null ? null : userMapper.selectById(currentUserId);
        if (!UserAccountStatusUtil.canInteract(viewer) || !UserRole.canModerate(viewer.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看视频处理状态");
        }
    }

    private void ensureCurrentUserCanInteract(Long userId, String action) {
        UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, action);
    }

    private void applyPublicAuthorFilter(LambdaQueryWrapper<MusicPost> wrapper) {
        wrapper.exists(publicUserExistsSql("user_id"));
    }

    private boolean canPublicAuthor(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return false;
        }
        return UserAccountStatusUtil.canRetainPublicContent(userId, userMapper::selectById);
    }

    private boolean canCurrentUserContributePublicStats(Long userId) {
        return UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById);
    }

    private List<Long> filterPublicSignalUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        Set<Long> allowed = users.stream()
                .filter(UserAccountStatusUtil::canContributePublicStats)
                .map(User::getId)
                .collect(Collectors.toSet());
        return userIds.stream()
                .filter(allowed::contains)
                .collect(Collectors.toList());
    }

    private IPage<Object> emptyObjectPage(Integer page, Integer size) {
        Page<Object> emptyPage = new Page<>(page, size, 0);
        emptyPage.setRecords(new ArrayList<>());
        return emptyPage;
    }

    private String publicUserExistsSql(String userIdColumn) {
        return PublicStatsSql.USER_EXISTS_PREFIX + userIdColumn + PublicStatsSql.RETAINED_PUBLIC_CONTENT_FILTER;
    }

    private boolean isFollowingUser(Long followerId, Long followeeId) {
        if (ObjectUtils.isEmpty(followerId) || ObjectUtils.isEmpty(followeeId)) {
            return false;
        }
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId)
                .eq(UserFollow::getDeleted, 0);
        Long count = userFollowMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean likePost(Long postId, Long userId) {
        if (ObjectUtils.isEmpty(postId) || ObjectUtils.isEmpty(userId)) {
            log.warn("event=music_post_like_rejected reason=PARAMETER_MISSING");
            return false;
        }
        ensureCurrentUserCanInteract(userId, "点赞动态");

        MusicPost post = musicPostMapper.selectById(postId);
        if (ObjectUtils.isEmpty(post) || Boolean.TRUE.equals(post.getIsDeleted())) {
            log.warn("event=music_post_like_rejected postId={} reason=POST_UNAVAILABLE", postId);
            return false;
        }
        if (!canViewPost(post, userId)) {
            log.warn("event=music_post_like_rejected postId={} userId={} reason=NOT_VISIBLE",
                    postId, userId);
            return false;
        }
        if (!canCurrentUserContributePublicStats(post.getUserId())) {
            log.warn("event=music_post_like_rejected postId={} authorId={} reason=AUTHOR_RESTRICTED",
                    postId, post.getUserId());
            return false;
        }

        int likeResult = postLikeMapper.insertIgnore(postId, userId);


        if (likeResult > 0 && canCurrentUserContributePublicStats(userId) && canCurrentUserContributePublicStats(post.getUserId())) {
            musicPostMapper.incrementLikeCount(postId);
        }
        if (likeResult > 0) {
            notificationService.sendLikeNotification(
                    post.getUserId(), userId, userDisplayName(userId),
                    "music-post", postId, postNotificationName(post));
        }

        log.info("event=music_post_liked postId={} userId={} inserted={}",
                postId, userId, likeResult);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unlikePost(Long postId, Long userId) {
        if (ObjectUtils.isEmpty(postId) || ObjectUtils.isEmpty(userId)) {
            log.warn("event=music_post_unlike_rejected reason=PARAMETER_MISSING");
            return false;
        }

        int result = postLikeMapper.deleteByPostAndUser(postId, userId);


        if (result > 0) {
            MusicPost post = musicPostMapper.selectById(postId);
            if (post != null
                    && canCurrentUserContributePublicStats(userId)
                    && canCurrentUserContributePublicStats(post.getUserId())) {
                musicPostMapper.decrementLikeCount(postId);
            }
            if (post != null) {
                notificationService.revokeLikeNotification(post.getUserId(), userId, postId);
            }
        }

        log.info("event=music_post_unliked postId={} userId={} affectedRows={}", postId, userId, result);

        return result > 0;
    }

    private String userDisplayName(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return "一位镇民";
        if (!ObjectUtils.isEmpty(user.getNickname())) return user.getNickname().trim();
        if (!ObjectUtils.isEmpty(user.getUsername())) return user.getUsername().trim();
        return "一位镇民";
    }

    private String postNotificationName(MusicPost post) {
        if (post == null || ObjectUtils.isEmpty(post.getContent())) return "音乐广场动态";
        String content = post.getContent().trim();
        return content.length() > 20 ? content.substring(0, 20) + "..." : content;
    }




    private String determinePostType(String resourceType) {
        if (ObjectUtils.isEmpty(resourceType)) {
            return "text";
        }

        switch (resourceType) {
            case "song":
                return "music";
            case "album":
                return "album";
            case "playlist":
                return "playlist";
            case "mv":
                return "mv";
            default:
                return "text";
        }
    }




    private LocalDateTime getTimeRangeStart(String timeRange) {
        LocalDateTime now = LocalDateTime.now();

        switch (timeRange) {
            case "today":
                return now.toLocalDate().atStartOfDay();
            case "3days":
                return now.minusDays(3);
            case "7days":
                return now.minusDays(7);
            case "30days":
                return now.minusDays(30);
            default:
                return null;
        }
    }







    private Map<String, Object> getUserInfo(Long userId, boolean isVip) {
        if (ObjectUtils.isEmpty(userId)) {
            return getAnonymousUser();
        }

        User user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            return getAnonymousUser();
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("signature", user.getIntroduction());


        userInfo.put("userName", user.getNickname());
        userInfo.put("userAvatar", user.getAvatar());

        UserType userType = UserType.fromCode(user.getUserType());
        int userLevel = 1;
        if (userType == UserType.ACTIVE) {
            userLevel = 2;        
        } else if (userType.shouldRestrict()) {
            userLevel = 0;         
        }
        userInfo.put("userLevel", userLevel);
        userInfo.put("isCreator", user.getIsCreator() != null && user.getIsCreator() == 1);
        userInfo.put("isVip", isVip);

        return userInfo;
    }






    private Map<String, Object> getAnonymousUser() {
        Map<String, Object> userInfo = new HashMap<>();
        String defaultAvatar = "https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png";

        userInfo.put("id", 0L);
        userInfo.put("nickname", "匿名用户");
        userInfo.put("avatar", defaultAvatar);
        userInfo.put("signature", "");


        userInfo.put("userName", "匿名用户");
        userInfo.put("userAvatar", defaultAvatar);
        userInfo.put("userLevel", 1);
        userInfo.put("isCreator", false);
        userInfo.put("isVip", false);

        return userInfo;
    }








    private Boolean isPostLiked(Long postId, Long userId) {
        if (ObjectUtils.isEmpty(postId) || ObjectUtils.isEmpty(userId)) {
            return false;
        }

        LambdaQueryWrapper<PostLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId);

        Long count = postLikeMapper.selectCount(wrapper);
        return count != null && count > 0;
    }








    private Map<String, Object> getMusicResource(String resourceType, Long resourceId) {
        if (ObjectUtils.isEmpty(resourceType) || ObjectUtils.isEmpty(resourceId)) {
            return null;
        }

        try {
            switch (resourceType) {
                case "song":
                    Song song = songMapper.selectById(resourceId);
                    if (song != null) {
                        Map<String, Object> resource = new HashMap<>();
                        resource.put("type", "song");
                        resource.put("id", song.getId());
                        resource.put("title", song.getName());
                        resource.put("coverUrl", song.getCover());
                        resource.put("artist", song.getArtistNames());
                        return resource;
                    }
                    break;
                case "album":
                    Album album = albumMapper.selectById(resourceId);
                    if (album != null) {
                        Map<String, Object> resource = new HashMap<>();
                        resource.put("type", "album");
                        resource.put("id", album.getId());
                        resource.put("title", album.getName());
                        resource.put("coverUrl", album.getCover());
                        resource.put("artist", album.getArtistNames());
                        return resource;
                    }
                    break;
                case "playlist":
                    Playlist playlist = playlistMapper.selectById(resourceId);
                    if (playlist != null && canPublicAuthor(playlist.getUserId())) {
                        Map<String, Object> resource = new HashMap<>();
                        resource.put("type", "playlist");
                        resource.put("id", playlist.getId());
                        resource.put("title", playlist.getName());
                        resource.put("coverUrl", playlist.getCover());
                        return resource;
                    }
                    break;
                case "mv":
                    MV mv = mvMapper.selectById(resourceId);
                    if (mv != null) {
                        Map<String, Object> resource = new HashMap<>();
                        resource.put("type", "mv");
                        resource.put("id", mv.getId());
                        resource.put("title", mv.getName());
                        resource.put("coverUrl", mv.getCover());
                        resource.put("artist", mv.getArtistNames());
                        return resource;
                    }
                    break;
            }
        } catch (Exception e) {
            log.debug("event=music_post_resource_load_failed resourceType={} resourceId={} errorType={}",
                    resourceType, resourceId, e.getClass().getSimpleName());
        }

        return null;
    }

    private Map<String, Object> getUnavailableMusicResource(String resourceType, Long resourceId) {
        Map<String, Object> resource = new HashMap<>();
        resource.put("type", resourceType);
        resource.put("id", resourceId);
        resource.put("title", "资源已不可用");
        resource.put("coverUrl", "");
        resource.put("artist", "原资源不存在或已下架");
        resource.put("unavailable", true);
        return resource;
    }







    private List<String> parseImages(String imagesJson) {
        if (ObjectUtils.isEmpty(imagesJson)) {
            return new ArrayList<>();
        }

        try {

            if (imagesJson.trim().startsWith("[")) {


                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\"");
                java.util.regex.Matcher matcher = pattern.matcher(imagesJson);
                List<String> urls = new ArrayList<>();
                while (matcher.find()) {
                    String url = matcher.group(1);

                    if (!url.matches("^(url|imageUrl|src)$") &&
                        (url.startsWith("http") || url.startsWith("/") || url.matches("^[a-zA-Z]:\\\\.*"))) {
                        urls.add(url);
                    }
                }
                return urls.isEmpty() ? Arrays.asList(imagesJson.replaceAll("[\\[\\]\"]", "").split(",")) : urls;
            }

            return Arrays.asList(imagesJson.split(","));
        } catch (Exception e) {
            log.debug("event=music_post_images_parse_failed errorType={}",
                    e.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }

    private void incrementTopicPostCounts(String topics) {
        updateTopicPostCounts(topics, 1);
    }

    private void decrementTopicPostCounts(String topics) {
        updateTopicPostCounts(topics, -1);
    }

    private void updateTopicPostCounts(String topics, int delta) {
        List<String> topicNames = parseTopics(topics).stream()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (topicNames.isEmpty()) {
            return;
        }

        for (String topicName : topicNames) {
            musicTopicMapper.adjustPostCountByName(topicName, delta);
        }
    }






    private List<String> parseTopics(String topicsJson) {
        if (ObjectUtils.isEmpty(topicsJson)) {
            return new ArrayList<>();
        }

        try {

            if (topicsJson.startsWith("[")) {
                return Arrays.asList(topicsJson.replaceAll("[\\[\\]\"]", "").split(","));
            }

            return Arrays.asList(topicsJson.split(","));
        } catch (Exception e) {
            log.debug("event=music_post_topics_parse_failed errorType={}",
                    e.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }







    private Map<String, Object> parseVideoInfo(String videoInfoJson) {
        if (ObjectUtils.isEmpty(videoInfoJson)) {
            return null;
        }
        try {
            Map<String, Object> videoInfo = objectMapper.readValue(videoInfoJson, Map.class);
            if (ObjectUtils.isEmpty(videoInfo)
                    || (!videoInfo.containsKey("baseName") && !videoInfo.containsKey("original") && !videoInfo.containsKey("720p"))) {
                return null;
            }
            videoInfo.remove("remoteOriginalPath");
            videoInfo.remove("remote480pPath");
            videoInfo.remove("remote720pPath");
            videoInfo.remove("remoteThumbnailPath");
            return videoInfo;
        } catch (Exception e) {
            log.debug("event=music_post_video_info_parse_failed errorType={}",
                    e.getClass().getSimpleName());
            return null;
        }
    }

    private String toVideoInfoJson(Map<String, Object> postData) {
        try {
            Object videoInfo = postData.get("videoInfo");
            if (ObjectUtils.isEmpty(videoInfo)) {
                Map<String, Object> generated = new HashMap<>();
                Object videoUrl = postData.get("videoUrl");
                Object thumbnailUrl = postData.get("thumbnailUrl");
                if (ObjectUtils.isNotEmpty(videoUrl)) {
                    generated.put("original", videoUrl);
                    generated.put("originalUrl", videoUrl);
                    generated.put("720p", videoUrl);
                }
                if (ObjectUtils.isNotEmpty(thumbnailUrl)) {
                    generated.put("thumbnail", thumbnailUrl);
                    generated.put("thumbnailUrl", thumbnailUrl);
                }
                copyIfPresent(postData, generated, "originalSize");
                copyIfPresent(postData, generated, "compressedSize");
                copyIfPresent(postData, generated, "duration");
                videoInfo = generated.isEmpty() ? null : generated;
            }
            return ObjectUtils.isEmpty(videoInfo) ? null : objectMapper.writeValueAsString(videoInfo);
        } catch (Exception e) {
            log.debug("event=music_post_video_info_build_failed errorType={}",
                    e.getClass().getSimpleName());
            return null;
        }
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (ObjectUtils.isNotEmpty(value)) {
            target.put(key, value);
        }
    }







    private Map<String, Object> convertToVO(MusicPost post, Long currentUserId) {
        Set<Long> vipUserIds = userVipService.getActiveVipExpirations(
                Collections.singleton(post.getUserId())).keySet();
        return convertToVO(post, currentUserId, vipUserIds);
    }

    private Map<String, Object> convertToVO(MusicPost post, Long currentUserId, Set<Long> vipUserIds) {

        Map<String, Object> userInfo = getUserInfo(post.getUserId(), vipUserIds.contains(post.getUserId()));


        List<Map<String, Object>> musicResources = new ArrayList<>();
        if (!ObjectUtils.isEmpty(post.getResourceType()) && !ObjectUtils.isEmpty(post.getResourceId())) {
            Map<String, Object> resource = getMusicResource(post.getResourceType(), post.getResourceId());
            musicResources.add(resource != null
                    ? resource
                    : getUnavailableMusicResource(post.getResourceType(), post.getResourceId()));
        }

        Map<String, Object> vo = new HashMap<>();

        vo.put("id", post.getId());
        vo.put("userId", post.getUserId());
        vo.put("content", post.getContent());


        vo.put("userName", userInfo.get("userName"));
        vo.put("userAvatar", userInfo.get("userAvatar"));
        vo.put("userLevel", userInfo.get("userLevel"));
        vo.put("isCreator", userInfo.get("isCreator"));
        vo.put("isVip", userInfo.get("isVip"));


        List<String> storedImages = parseImages(post.getImages());
        int imageCount = storedImages.size();
        try {
            if (ObjectUtils.isNotEmpty(post.getImages()) && post.getImages().trim().startsWith("[")) {
                imageCount = com.alibaba.fastjson2.JSON.parseArray(post.getImages()).size();
            }
        } catch (Exception ignored) {

        }
        List<String> controlledImages = new ArrayList<>();
        for (int index = 0; index < imageCount; index++) {
            controlledImages.add("/api/music-square/posts/" + post.getId() + "/images/" + index
                    + "/content?variant=compressed");
        }
        vo.put("images", controlledImages);
        vo.put("musicResources", musicResources);
        if ("video".equals(post.getPostType())) {
            Map<String, Object> videoInfo = parseVideoInfo(ObjectUtils.isNotEmpty(post.getVideoInfo()) ? post.getVideoInfo() : post.getTopics());
            if (videoInfo != null && "ready".equals(ObjectUtils.castString(videoInfo.get("status")))) {
                videoInfo.remove("original");
                videoInfo.put("480p", "/api/music-square/posts/videos/" + post.getId() + "/content?quality=480p");
                videoInfo.put("720p", "/api/music-square/posts/videos/" + post.getId() + "/content?quality=720p");
                videoInfo.put("thumbnail", "/api/music-square/posts/videos/" + post.getId() + "/content?quality=thumbnail");
            }
            vo.put("videoInfo", videoInfo);
            vo.put("topics", ObjectUtils.isEmpty(post.getVideoInfo()) && videoInfo != null ? new ArrayList<>() : parseTopics(post.getTopics()));
        } else {
            vo.put("topics", parseTopics(post.getTopics()));
        }


        vo.put("likeCount", post.getLikeCount() != null ? post.getLikeCount() : 0);
        vo.put("commentCount", post.getCommentCount() != null ? post.getCommentCount() : 0);
        vo.put("shareCount", post.getShareCount() != null ? post.getShareCount() : 0);
        vo.put("isLiked", isPostLiked(post.getId(), currentUserId));
        boolean authorAllowComment = isAuthorCommentAllowed(post);
        boolean officialCommentClosed = Boolean.TRUE.equals(post.getOfficialCommentClosed());
        vo.put("authorAllowComment", authorAllowComment);
        vo.put("officialCommentClosed", officialCommentClosed);
        vo.put("allowComment", authorAllowComment && !officialCommentClosed);
        vo.put("canManageComment", !ObjectUtils.isEmpty(currentUserId) && currentUserId.equals(post.getUserId()));
        vo.put("canOfficialManageComment", isAdminUser(currentUserId));


        vo.put("postType", post.getPostType());
        vo.put("visibility", post.getVisibility());
        vo.put("isListenDiary", post.getIsListenDiary());


        vo.put("createTime", post.getCreateTime());
        vo.put("updateTime", post.getUpdateTime());

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVideoPost(Long userId, Map<String, Object> postData) {
        if (ObjectUtils.isEmpty(userId)) {
            log.warn("event=video_post_create_rejected reason=USER_ID_MISSING");
            return null;
        }
        ensureCurrentUserCanInteract(userId, "发布动态");

        MusicPost post = new MusicPost();
        post.setUserId(userId);
        post.setContent((String) postData.get("content"));
        post.setTopics((String) postData.get("topics"));
        post.setVideoInfo(toVideoInfoJson(postData));
        post.setPostType("video");
        post.setVisibility("public");
        post.setIsListenDiary(false);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setAllowComment(toAllowCommentValue((Boolean) postData.get("allowComment")));
        post.setOfficialCommentClosed(false);
        post.setShareCount(0);
        post.setIsDeleted(false);
        post.setCreateTime(LocalDateTime.now());
        post.setUpdateTime(LocalDateTime.now());

        int result = musicPostMapper.insert(post);

        if (result > 0) {
            if (canPublicAuthor(userId)) {
                incrementTopicPostCounts(post.getTopics());
            }
            log.info("event=video_post_created postId={} userId={}", post.getId(), userId);
            return post.getId();
        }

        log.warn("event=video_post_create_failed userId={}", userId);
        return null;
    }












    @Override
    public IPage<Object> getUserPosts(Long userId, Long currentUserId, Integer page, Integer size) {
        if (ObjectUtils.isEmpty(page) || page <= 0) {
            page = 1;
        }
        if (ObjectUtils.isEmpty(size) || size <= 0) {
            size = DEFAULT_POST_PAGE_SIZE;
        } else {
            size = Math.min(size, MAX_POST_PAGE_SIZE);
        }
        if (!Objects.equals(userId, currentUserId) && !canPublicAuthor(userId)) {
            return emptyObjectPage(page, size);
        }

        LambdaQueryWrapper<MusicPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MusicPost::getUserId, userId)
                .eq(MusicPost::getIsDeleted, false);
        applyPostVisibility(wrapper, userId, currentUserId);
        wrapper.orderByDesc(MusicPost::getCreateTime);

        IPage<MusicPost> pageResult = musicPostMapper.selectPage(
                new Page<>(page, size),
                wrapper
        );


        IPage<Object> result = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        Set<Long> vipUserIds = activeVipUserIds(pageResult.getRecords());
        List<Object> records = pageResult.getRecords().stream()
                .map(post -> convertToVO(post, currentUserId, vipUserIds))
                .collect(Collectors.toList());
        result.setRecords(records);

        return result;
    }








    @Override
    public Map<String, Object> getUserPostStats(Long userId, Long currentUserId) {
        boolean includeAll = Objects.equals(userId, currentUserId);
        Map<String, Object> aggregate = null;
        if (includeAll || canCurrentUserContributePublicStats(userId)) {
            boolean includeFollowers = !includeAll && isFollowingUser(currentUserId, userId);
            aggregate = musicPostMapper.selectUserPostStats(userId, includeAll, includeFollowers);
        }
        Map<String, Object> stats = new HashMap<>();

        stats.put("postCount", numberValue(aggregate, "postCount"));
        stats.put("likeCount", numberValue(aggregate, "likeCount"));
        stats.put("commentCount", numberValue(aggregate, "commentCount"));
        stats.put("shareCount", numberValue(aggregate, "shareCount"));

        return stats;
    }











    @Override
    public IPage<Object> getUserPostsByType(Long userId, String postType, Long currentUserId, Integer page, Integer size) {
        if (ObjectUtils.isEmpty(page) || page <= 0) {
            page = 1;
        }
        if (ObjectUtils.isEmpty(size) || size <= 0) {
            size = DEFAULT_POST_PAGE_SIZE;
        } else {
            size = Math.min(size, MAX_POST_PAGE_SIZE);
        }
        if (!Objects.equals(userId, currentUserId) && !canPublicAuthor(userId)) {
            return emptyObjectPage(page, size);
        }

        LambdaQueryWrapper<MusicPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MusicPost::getUserId, userId)
                .eq(MusicPost::getIsDeleted, false);
        applyPostVisibility(wrapper, userId, currentUserId);


        if (!ObjectUtils.isEmpty(postType) && !"all".equals(postType)) {
            if ("video".equals(postType)) {
                wrapper.eq(MusicPost::getPostType, "video");
            } else if ("diary".equals(postType)) {
                wrapper.eq(MusicPost::getIsListenDiary, true);
            } else if ("music".equals(postType)) {
                wrapper.eq(MusicPost::getResourceType, "song");
            } else if ("album".equals(postType)) {
                wrapper.eq(MusicPost::getResourceType, "album");
            } else if ("mv".equals(postType)) {
                wrapper.eq(MusicPost::getResourceType, "mv");
            } else if ("playlist".equals(postType)) {
                wrapper.eq(MusicPost::getResourceType, "playlist");
            } else if ("text".equals(postType)) {
                wrapper.eq(MusicPost::getPostType, "text");
            }
        }

        wrapper.orderByDesc(MusicPost::getCreateTime);

        IPage<MusicPost> pageResult = musicPostMapper.selectPage(
                new Page<>(page, size),
                wrapper
        );


        IPage<Object> result = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        Set<Long> vipUserIds = activeVipUserIds(pageResult.getRecords());
        List<Object> records = pageResult.getRecords().stream()
                .map(post -> convertToVO(post, currentUserId, vipUserIds))
                .collect(Collectors.toList());
        result.setRecords(records);

        return result;
    }








    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer batchDeletePosts(List<Long> postIds, Long userId) {
        if (ObjectUtils.isEmpty(postIds) || postIds.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Long postId : postIds) {
            Boolean success = deletePost(postId, userId);
            if (Boolean.TRUE.equals(success)) {
                count++;
            }
        }

        log.info("event=music_post_batch_delete_completed userId={} deletedCount={}", userId, count);
        return count;
    }








    @Override
    public Map<String, Object> getPostDetail(Long postId, Long currentUserId) {
        MusicPost post = musicPostMapper.selectById(postId);

        if (post == null || Boolean.TRUE.equals(post.getIsDeleted()) || !canViewPost(post, currentUserId)) {
            return null;
        }

        return convertToVO(post, currentUserId);
    }

    private Set<Long> activeVipUserIds(Collection<MusicPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> userIds = posts.stream()
                .map(MusicPost::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return userVipService.getActiveVipExpirations(userIds).keySet();
    }










    @Override
    public List<Map<String, Object>> getMyVideoPosts(Long userId, Integer status) {
        if (ObjectUtils.isEmpty(userId)) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<MusicPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MusicPost::getUserId, userId)
                .eq(MusicPost::getIsDeleted, false)
                .eq(MusicPost::getPostType, "video");


        if (status != null) {
            switch (status) {
                case 0:       
                    wrapper.eq(MusicPost::getVisibility, "pending");
                    break;
                case 1:       
                    wrapper.eq(MusicPost::getVisibility, "public");
                    break;
                case 2:       
                    wrapper.eq(MusicPost::getVisibility, "private");
                    break;
            }
        }

        wrapper.orderByDesc(MusicPost::getCreateTime);

        List<MusicPost> posts = musicPostMapper.selectList(wrapper);
        return posts.stream()
                .map(post -> {
                    Map<String, Object> videoInfo = new HashMap<>();
                    videoInfo.put("postId", post.getId());
                    videoInfo.put("content", post.getContent());
                    videoInfo.put("postType", post.getPostType());
                    videoInfo.put("visibility", post.getVisibility());
                    videoInfo.put("createTime", post.getCreateTime());
                    videoInfo.put("updateTime", post.getUpdateTime());


                    Integer statusCode;
                    String statusText;
                    if ("pending".equals(post.getVisibility())) {
                        statusCode = 0;
                        statusText = "审核中";
                    } else if ("public".equals(post.getVisibility())) {
                        statusCode = 1;
                        statusText = "已发布";
                    } else {
                        statusCode = 2;
                        statusText = "未通过";
                    }
                    videoInfo.put("status", statusCode);
                    videoInfo.put("statusText", statusText);

                    return videoInfo;
                })
                .collect(Collectors.toList());
    }







    @Override
    public Map<String, Object> getVideoPostStats(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            Map<String, Object> emptyStats = new HashMap<>();
            emptyStats.put("total", 0);
            emptyStats.put("pending", 0);
            emptyStats.put("published", 0);
            emptyStats.put("rejected", 0);
            return emptyStats;
        }

        Map<String, Object> aggregate = musicPostMapper.selectVideoPostStats(userId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", numberValue(aggregate, "total"));
        stats.put("pending", numberValue(aggregate, "pending"));
        stats.put("published", numberValue(aggregate, "published"));
        stats.put("rejected", numberValue(aggregate, "rejected"));

        return stats;
    }

    private long numberValue(Map<String, Object> row, String key) {
        if (row == null || row.get(key) == null) {
            return 0L;
        }
        Object value = row.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }








    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteVideoPost(Long postId, Long userId) {
        if (ObjectUtils.isEmpty(postId) || ObjectUtils.isEmpty(userId)) {
            log.warn("event=video_post_delete_rejected reason=PARAMETER_MISSING");
            return false;
        }

        MusicPost post = musicPostMapper.selectById(postId);
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted())) {
            log.warn("event=video_post_delete_rejected postId={} reason=POST_UNAVAILABLE", postId);
            return false;
        }


        if (!"video".equals(post.getPostType())) {
            log.warn("event=video_post_delete_rejected postId={} postType={} reason=TYPE_MISMATCH",
                    postId, post.getPostType());
            return false;
        }


        if (!post.getUserId().equals(userId)) {
            log.warn("event=video_post_delete_rejected postId={} userId={} ownerId={} reason=NOT_OWNER",
                    postId, userId, post.getUserId());
            return false;
        }

        post.setIsDeleted(true);
        post.setUpdateTime(LocalDateTime.now());

        int result = musicPostMapper.updateById(post);

        log.info("event=video_post_deleted postId={} userId={} affectedRows={}", postId, userId, result);

        return result > 0;
    }
}




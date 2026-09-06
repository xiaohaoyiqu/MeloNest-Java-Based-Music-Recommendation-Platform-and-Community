package com.haoran.music.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.MusicConstants;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.dto.comment.CommentCreateDTO;
import com.haoran.music.dto.comment.CommentUserCountDTO;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.CommentService;
import com.haoran.music.service.ContentAccessService;
import com.haoran.music.service.EmojiService;
import com.haoran.music.service.MusicPostService;
import com.haoran.music.service.NotificationService;
import com.haoran.music.service.UserBlacklistService;
import com.haoran.music.service.UserStatisticsService;
import com.haoran.music.service.UserVipService;
import com.haoran.music.vo.comment.CommentVO;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

   
                      
                       
   
@Slf4j
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private CommentLikeMapper commentLikeMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private SongMapper songMapper;

    @Resource
    private AlbumMapper albumMapper;

    @Resource
    private PlaylistMapper playlistMapper;

    @Resource
    private MVMapper mvMapper;

    @Resource
    private ArtistMapper artistMapper;

    @Resource
    private MusicPostMapper musicPostMapper;

    @Resource
    private MarketplaceItemMapper marketplaceItemMapper;
    
    @Resource
    private com.haoran.music.mapper.UserFollowMapper userFollowMapper;
    @Resource
    private UserBlacklistService userBlacklistService;

    @Resource
    private NotificationService notificationService;

    @Resource
    private UserStatisticsService userStatisticsService;

    @Resource
    private UserVipService userVipService;

    @Resource
    private ContentAccessService contentAccessService;

    @Resource
    private EmojiService emojiService;

    @Resource
    private MusicPostService musicPostService;

    @Resource
    private PlaylistCollaboratorMapper playlistCollaboratorMapper;

    @Override
    public CommentVO getCommentById(Long commentId, Long userId) {
        if (ObjectUtils.isEmpty(commentId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "评论ID不能为空");
        }

        Comment comment = getById(commentId);
        if (ObjectUtils.isEmpty(comment)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        if (Integer.valueOf(6).equals(comment.getTargetType())) {
            validateTarget(comment.getTargetType(), comment.getTargetId(), userId);
        }

        CommentVO vo = convertToVO(comment, userId);

                
        IPage<CommentVO> replies = getReplies(commentId, new PageQuery(1, 10), userId);
        if (CollUtil.isNotEmpty(replies.getRecords())) {
            vo.setReplies(replies.getRecords());
        }

        return vo;
    }

    @Override
    public IPage<CommentVO> pageComments(Integer targetType, Long targetId, PageQuery pageQuery, Long userId) {
        if (Integer.valueOf(6).equals(targetType)) {
            validateTarget(targetType, targetId, userId);
        }

        Page<Comment> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());

        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getTargetType, targetType)
                .eq(Comment::getTargetId, targetId)
                .eq(Comment::getParentId, 0)
                .eq(Comment::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Comment::getDeleted, CommonConstants.NOT_DELETED);

                          
        wrapper.orderByDesc(Comment::getIsPinned, Comment::getCreateTime);

        IPage<Comment> commentPage = page(page, wrapper);

                 
        if (!commentPage.getRecords().isEmpty()) {
            return convertToVOBatch(commentPage, userId);
        }
        return commentPage.convert(comment -> new CommentVO());
    }

    @Override
    public IPage<CommentVO> getReplies(Long commentId, PageQuery pageQuery, Long userId) {
        Comment parent = getById(commentId);
        if (ObjectUtils.isNotEmpty(parent) && Integer.valueOf(6).equals(parent.getTargetType())) {
            validateTarget(parent.getTargetType(), parent.getTargetId(), userId);
        }

        Page<Comment> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());

        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getParentId, commentId)
                .eq(Comment::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Comment::getDeleted, CommonConstants.NOT_DELETED)
                .orderByAsc(Comment::getCreateTime);

        IPage<Comment> commentPage = page(page, wrapper);

                 
        if (!commentPage.getRecords().isEmpty()) {
            return convertToVOBatch(commentPage, userId);
        }
        return commentPage.convert(comment -> new CommentVO());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createComment(Long userId, CommentCreateDTO dto) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        ensureCurrentUserCanInteract(userId, "发表评论");
        boolean canContributeStats = canCurrentUserContributePublicStats(userId);

                   
        validateTarget(dto.getTargetType(), dto.getTargetId(), userId, true);
                         
        if (ObjectUtils.isNotEmpty(dto.getReplyToUserId())) {
            User replyToUser = userMapper.selectById(dto.getReplyToUserId());
            if (!UserAccountStatusUtil.canInteract(replyToUser)) {
                throw new BusinessException(ResultCode.FORBIDDEN,
                        UserAccountStatusUtil.targetUnavailableMessage(replyToUser) + "，无法回复");
            }
            Boolean isBlacklisted = userBlacklistService.isBlacklisted(userId, dto.getReplyToUserId());
            if (Boolean.TRUE.equals(isBlacklisted)) {
                throw new BusinessException("无法回复黑名单中的用户");
            }
        }

        Comment parentComment = null;
                           
        if (ObjectUtils.isNotEmpty(dto.getParentId()) && !dto.getParentId().equals(0L)) {
            parentComment = getById(dto.getParentId());
            if (ObjectUtils.isEmpty(parentComment)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "父评论不存在");
            }
                           
            if (!parentComment.getTargetType().equals(dto.getTargetType()) ||
                !parentComment.getTargetId().equals(dto.getTargetId())) {
                throw new BusinessException("父评论目标不匹配");
            }
        }

        SecurityCheckUtil.CheckResult contentCheck = SecurityCheckUtil.checkCommunityText(dto.getContent(), "评论内容");
        if (!contentCheck.isSafe()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, contentCheck.getMessage());
        }
        emojiService.requireContentEmojiAccess(contentCheck.getCleanedValue(), userId);

               
        Comment comment = new Comment();
        comment.setUserId(userId);
        comment.setTargetType(dto.getTargetType());
        comment.setTargetId(dto.getTargetId());
        comment.setParentId(ObjectUtils.isNotEmpty(dto.getParentId()) ? dto.getParentId() : 0L);
                              
        comment.setReplyUserId(dto.getReplyToUserId());
        comment.setContent(SecurityCheckUtil.escapeHtml(contentCheck.getCleanedValue()));
        comment.setLikeCount(0L);
        comment.setReplyCount(0L);
        comment.setIsPinned(CommonConstants.NO);
        comment.setStatus(CommonConstants.STATUS_NORMAL);
        comment.setEditCount(0);
        comment.setIp(UserContext.getClientIp());                                                   

        save(comment);

                    
        if (ObjectUtils.isNotEmpty(dto.getParentId()) && !dto.getParentId().equals(0L)) {
            if (ObjectUtils.isNotEmpty(parentComment)
                    && canContributeStats
                    && canContributeCommentStats(parentComment)) {
                commentMapper.adjustReplyCount(parentComment.getId(), 1);
            }
        }

                                       
        if ((ObjectUtils.isEmpty(dto.getParentId()) || dto.getParentId().equals(0L))
                && canContributeStats
                && canContributeTargetStats(dto.getTargetType(), dto.getTargetId())) {
            updateTargetCommentCount(dto.getTargetType(), dto.getTargetId(), 1);
        }

        if (canContributeStats) {
            userStatisticsService.incrementInteraction(userId, "comment", 1);
        }
        Long recipientId = resolveCommentRecipient(dto, parentComment);
        notificationService.sendCommentNotification(
                recipientId,
                userId,
                userDisplayName(userId),
                comment.getId(),
                notificationResourceType(dto.getTargetType()),
                dto.getTargetId(),
                getTargetName(dto.getTargetType(), dto.getTargetId()),
                parentComment != null);
        log.info("用户发表评论: userId={}, targetType={}, targetId={}", userId, dto.getTargetType(), dto.getTargetId());

        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteComment(Long userId, Long commentId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(commentId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        Comment comment = getById(commentId);
        if (ObjectUtils.isEmpty(comment)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }

                         
        if (!userId.equals(comment.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除此评论");
        }

               
        removeById(commentId);

        boolean canContributeStats = canCurrentUserContributePublicStats(userId);

                    
        if (ObjectUtils.isNotEmpty(comment.getParentId()) && !comment.getParentId().equals(0L)) {
            Comment parentComment = getById(comment.getParentId());
            if (ObjectUtils.isNotEmpty(parentComment)
                    && canContributeStats
                    && canContributeCommentStats(parentComment)) {
                commentMapper.adjustReplyCount(parentComment.getId(), -1);
            }
        }

                                       
        if ((ObjectUtils.isEmpty(comment.getParentId()) || comment.getParentId().equals(0L))
                && canContributeStats
                && canContributeTargetStats(comment.getTargetType(), comment.getTargetId())) {
            updateTargetCommentCount(comment.getTargetType(), comment.getTargetId(), -1);
        }

                     
        commentLikeMapper.deleteDeletedHistoryByComment(commentId);
        LambdaQueryWrapper<CommentLike> likeWrapper = new LambdaQueryWrapper<>();
        likeWrapper.eq(CommentLike::getCommentId, commentId);
                                       
        commentLikeMapper.delete(likeWrapper);

                              
        try {
            int revokedCount = notificationService.revokeCommentNotifications(commentId);
            log.info("删除评论，已撤回相关通知: commentId={}, revokedCount={}", commentId, revokedCount);
        } catch (Exception e) {
            log.warn("撤回评论通知失败: userId={}, commentId={}", userId, commentId);
        }

        log.info("用户删除评论: userId={}, commentId={}", userId, commentId);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean likeComment(Long userId, Long commentId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(commentId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        ensureCurrentUserCanInteract(userId, "点赞评论");

        Comment comment = getById(commentId);
        if (ObjectUtils.isEmpty(comment)
                || !CommonConstants.STATUS_NORMAL.equals(comment.getStatus())
                || CommonConstants.DELETED.equals(comment.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        User commentAuthor = userMapper.selectById(comment.getUserId());
        if (!UserAccountStatusUtil.canInteract(commentAuthor)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    UserAccountStatusUtil.targetUnavailableMessage(commentAuthor) + "，无法点赞");
        }

        commentLikeMapper.deleteDeletedHistory(userId, commentId);
        int inserted = commentLikeMapper.insertIgnoreActive(IdWorker.getId(), userId, commentId);
        if (inserted <= 0) {
            throw new BusinessException("已经点赞过该评论");
        }

        if (canCurrentUserContributePublicStats(userId) && canContributeCommentStats(comment)) {
            if (commentMapper.adjustLikeCount(commentId, 1) <= 0) {
                throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
            }

            userStatisticsService.incrementInteraction(userId, "like", 1);
        }
        notificationService.sendCommentLikeNotification(
                comment.getUserId(),
                userId,
                userDisplayName(userId),
                commentId,
                notificationResourceType(comment.getTargetType()),
                comment.getTargetId(),
                getTargetName(comment.getTargetType(), comment.getTargetId()));
        log.info("用户点赞评论: userId={}, commentId={}", userId, commentId);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unlikeComment(Long userId, Long commentId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(commentId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        ensureCurrentUserCanInteract(userId, "取消点赞");

        commentLikeMapper.deleteDeletedHistory(userId, commentId);
        int deleted = commentLikeMapper.deleteActiveLike(userId, commentId);
        if (deleted <= 0) {
            throw new BusinessException("未点赞过该评论");
        }

                  
        Comment comment = getById(commentId);
        if (ObjectUtils.isNotEmpty(comment)) {
            if (canCurrentUserContributePublicStats(userId) && canContributeCommentStats(comment)) {
                commentMapper.adjustLikeCount(commentId, -1);
            }

                     
            try {
                notificationService.revokeLikeNotification(comment.getUserId(), userId, commentId);
                log.info("取消点赞评论，已撤回通知: userId={}, commentId={}", userId, commentId);
            } catch (Exception e) {
                log.warn("撤回点赞通知失败: userId={}, commentId={}", userId, commentId);
            }
        }

        log.info("用户取消点赞评论: userId={}, commentId={}", userId, commentId);

        return true;
    }

    @Override
    public List<CommentVO> getHotComments(Integer targetType, Integer limit, Long userId) {
        int actualLimit = limit != null && limit > 0 ? Math.min(limit, 100) : 20;
        int queryLimit = Integer.valueOf(6).equals(targetType) ? Math.min(actualLimit * 5, 500) : actualLimit;
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getTargetType, targetType)
                .eq(Comment::getParentId, 0)
                .eq(Comment::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Comment::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Comment::getLikeCount, Comment::getReplyCount, Comment::getCreateTime)
                .last("LIMIT " + queryLimit);

        List<Comment> comments = list(wrapper);

        if (Integer.valueOf(6).equals(targetType)) {
            comments = comments.stream()
                    .filter(comment -> isTargetReadable(comment, userId))
                    .limit(actualLimit)
                    .collect(Collectors.toList());
        }

        if (comments.isEmpty()) {
            return new ArrayList<>();
        }

                 
        return convertToVOBatch(comments, userId);
    }

    @Override
    public IPage<CommentVO> getUserComments(Long userId, PageQuery pageQuery) {
        Page<Comment> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());

        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getUserId, userId)
                .eq(Comment::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Comment::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Comment::getCreateTime);

        IPage<Comment> commentPage = page(page, wrapper);

                 
        if (!commentPage.getRecords().isEmpty()) {
            return convertToVOBatch(commentPage, userId);
        }
        return commentPage.convert(comment -> new CommentVO());
    }

       
                       
      
                                
                           
                     
       
    private IPage<CommentVO> convertToVOBatch(IPage<Comment> commentPage, Long userId) {
        List<Comment> comments = commentPage.getRecords();
        if (comments.isEmpty()) {
            return commentPage.convert(comment -> new CommentVO());
        }

        List<CommentVO> voList = convertToVOBatch(comments, userId);

        Page<CommentVO> voPage = new Page<>(commentPage.getCurrent(), commentPage.getSize(), commentPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

       
                         
      
                           
                           
                   
       
    private List<CommentVO> convertToVOBatch(List<Comment> comments, Long userId) {
                     
        if (ObjectUtils.isNotEmpty(userId)) {
            List<Long> blacklistedUserIds = userBlacklistService.getBlacklistedUserIds(userId);
            if (CollUtil.isNotEmpty(blacklistedUserIds)) {
                comments = comments.stream()
                    .filter(comment -> !blacklistedUserIds.contains(comment.getUserId()))
                    .collect(Collectors.toList());
                if (comments.isEmpty()) {
                    return new ArrayList<>();
                }
            }
        }

        if (comments.isEmpty()) {
            return new ArrayList<>();
        }

                               
        Set<Long> userIds = new HashSet<>(comments.stream()
                .map(Comment::getUserId)
                .collect(Collectors.toSet()));
                    
        for (Comment comment : comments) {
            if (ObjectUtils.isNotEmpty(comment.getReplyUserId())) {
                userIds.add(comment.getReplyUserId());
            }
        }
        Map<Long, User> userMap = getUserInfos(userIds);
        Map<Long, java.time.LocalDateTime> vipExpirations = userVipService.getActiveVipExpirations(userIds);

                             
        Map<Integer, Set<Long>> targetIdsByType = new HashMap<>();
        for (Comment comment : comments) {
            targetIdsByType.computeIfAbsent(comment.getTargetType(), k -> new HashSet<>())
                    .add(comment.getTargetId());
        }
        Map<String, TargetInfo> targetInfoMap = getTargetInfos(targetIdsByType);

                      
        Set<Long> likedCommentIds = Collections.emptySet();
        if (ObjectUtils.isNotEmpty(userId)) {
            List<Long> commentIds = comments.stream()
                    .map(Comment::getId)
                    .collect(Collectors.toList());
            likedCommentIds = getLikedCommentIds(userId, commentIds);
        }

                   
        List<CommentVO> voList = new ArrayList<>();
        for (Comment comment : comments) {
            CommentVO vo = BeanUtil.copyProperties(comment, CommentVO.class);

                     
            User user = userMap.get(comment.getUserId());
            if (user != null) {
                vo.setUsername(user.getNickname());
                vo.setUserAvatar(user.getAvatar());
                           
                setUserRoleInfo(vo, user, vipExpirations.containsKey(user.getId()));
            }

                     
            String targetKey = comment.getTargetType() + ":" + comment.getTargetId();
            TargetInfo targetInfo = targetInfoMap.get(targetKey);
            if (targetInfo != null) {
                vo.setTargetName(targetInfo.getName());
                vo.setTargetCover(targetInfo.getCover());
            }

                     
            vo.setIsLiked(likedCommentIds.contains(comment.getId()));

                       
            int editCount = comment.getEditCount() != null ? comment.getEditCount() : 0;
            vo.setEditCount(editCount);
            vo.setRemainingEditCount(Math.max(0, 3 - editCount));

                        
            if (ObjectUtils.isNotEmpty(comment.getReplyUserId())) {
                User replyUser = userMap.get(comment.getReplyUserId());
                if (replyUser != null) {
                    vo.setReplyToUsername(replyUser.getNickname());
                }
            }

                                          
            boolean canEdit = userId != null && userId.equals(comment.getUserId()) &&
                    System.currentTimeMillis() - comment.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() < 30 * 60 * 1000 &&
                    editCount < 3;
            vo.setCanEdit(canEdit);

                   
            vo.setIp(maskIp(comment.getIp()));

            voList.add(vo);
        }

        return voList;
    }

       
               
      
                            
                              
       
    private Map<Long, User> getUserInfos(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(User::getId, userIds)
                    .eq(User::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(User::getDeleted, CommonConstants.NOT_DELETED)
                    .select(User::getId, User::getNickname, User::getAvatar,
                            User::getRole, User::getIsModerator, User::getIsOfficial);

            List<User> users = userMapper.selectList(wrapper);
            return users.stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        } catch (Exception e) {
            log.warn("批量获取用户信息失败: userIds={}", userIds);
            return Collections.emptyMap();
        }
    }

       
               
      
                                               
                                      
       
    private Map<String, TargetInfo> getTargetInfos(Map<Integer, Set<Long>> targetIdsByType) {
        if (targetIdsByType == null || targetIdsByType.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, TargetInfo> result = new HashMap<>();

        try {
                                   
            if (targetIdsByType.containsKey(1)) {
                Set<Long> songIds = targetIdsByType.get(1);
                if (!songIds.isEmpty()) {
                    LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
                    wrapper.in(Song::getId, songIds)
                            .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                            .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                            .select(Song::getId, Song::getName, Song::getCover);

                    List<Song> songs = songMapper.selectList(wrapper);
                    for (Song song : songs) {
                        String key = "1:" + song.getId();
                        result.put(key, new TargetInfo(song.getName(), song.getCover()));
                    }
                }
            }

                                   
            if (targetIdsByType.containsKey(2)) {
                Set<Long> albumIds = targetIdsByType.get(2);
                if (!albumIds.isEmpty()) {
                    LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
                    wrapper.in(Album::getId, albumIds)
                            .eq(Album::getStatus, CommonConstants.STATUS_NORMAL)
                            .eq(Album::getDeleted, CommonConstants.NOT_DELETED)
                            .select(Album::getId, Album::getName, Album::getCover);

                    List<Album> albums = albumMapper.selectList(wrapper);
                    for (Album album : albums) {
                        String key = "2:" + album.getId();
                        result.put(key, new TargetInfo(album.getName(), album.getCover()));
                    }
                }
            }

                                   
            if (targetIdsByType.containsKey(3)) {
                Set<Long> playlistIds = targetIdsByType.get(3);
                if (!playlistIds.isEmpty()) {
                    LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
                    wrapper.in(Playlist::getId, playlistIds)
                            .eq(Playlist::getStatus, CommonConstants.STATUS_NORMAL)
                            .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                            .select(Playlist::getId, Playlist::getName, Playlist::getCover);

                    List<Playlist> playlists = playlistMapper.selectList(wrapper);
                    for (Playlist playlist : playlists) {
                        String key = "3:" + playlist.getId();
                        result.put(key, new TargetInfo(playlist.getName(), playlist.getCover()));
                    }
                }
            }

                                   
            if (targetIdsByType.containsKey(4)) {
                Set<Long> mvIds = targetIdsByType.get(4);
                if (!mvIds.isEmpty()) {
                    LambdaQueryWrapper<MV> wrapper = new LambdaQueryWrapper<>();
                    wrapper.in(MV::getId, mvIds)
                            .eq(MV::getStatus, CommonConstants.STATUS_NORMAL)
                            .eq(MV::getDeleted, CommonConstants.NOT_DELETED)
                            .select(MV::getId, MV::getName, MV::getCover);

                    List<MV> mvs = mvMapper.selectList(wrapper);
                    for (MV mv : mvs) {
                        String key = "4:" + mv.getId();
                        result.put(key, new TargetInfo(mv.getName(), mv.getCover()));
                    }
                }
            }

                                   
            if (targetIdsByType.containsKey(5)) {
                Set<Long> artistIds = targetIdsByType.get(5);
                if (!artistIds.isEmpty()) {
                    LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
                    wrapper.in(Artist::getId, artistIds)
                            .eq(Artist::getStatus, CommonConstants.STATUS_NORMAL)
                            .eq(Artist::getDeleted, CommonConstants.NOT_DELETED)
                            .select(Artist::getId, Artist::getName, Artist::getAvatar);

                    List<Artist> artists = artistMapper.selectList(wrapper);
                    for (Artist artist : artists) {
                        String key = "5:" + artist.getId();
                        result.put(key, new TargetInfo(artist.getName(), artist.getAvatar()));
                    }
                }
            }


                                       
            if (targetIdsByType.containsKey(6)) {
                Set<Long> postIds = targetIdsByType.get(6);
                if (!postIds.isEmpty()) {
                    LambdaQueryWrapper<MusicPost> wrapper = new LambdaQueryWrapper<>();
                    wrapper.in(MusicPost::getId, postIds)
                            .eq(MusicPost::getIsDeleted, false)
                            .select(MusicPost::getId, MusicPost::getContent, MusicPost::getImages);

                    List<MusicPost> posts = musicPostMapper.selectList(wrapper);
                    for (MusicPost post : posts) {
                        String key = "6:" + post.getId();
                        result.put(key, new TargetInfo(getPostPreviewName(post), getPostPreviewCover(post)));
                    }
                }
            }

        } catch (Exception e) {
            log.warn("批量获取目标信息失败: targetIdsByType={}", targetIdsByType);
        }

        return result;
    }

       
                      
      
                         
                               
                         
       
    private Set<Long> getLikedCommentIds(Long userId, List<Long> commentIds) {
        if (userId == null || commentIds == null || commentIds.isEmpty()) {
            return Collections.emptySet();
        }

        try {
            LambdaQueryWrapper<CommentLike> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(CommentLike::getUserId, userId)
                    .in(CommentLike::getCommentId, commentIds)
                    .eq(CommentLike::getIsLike, CommonConstants.YES)
                    .eq(CommentLike::getDeleted, CommonConstants.NOT_DELETED)
                    .select(CommentLike::getCommentId);

            List<CommentLike> commentLikes = commentLikeMapper.selectList(wrapper);
            return commentLikes.stream()
                    .map(CommentLike::getCommentId)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("批量获取点赞状态失败: userId={}, commentIds={}", userId, commentIds);
            return Collections.emptySet();
        }
    }

       
              
       
    @lombok.Getter
    private static class TargetInfo {
        private final String name;
        private final String cover;

        public TargetInfo(String name, String cover) {
            this.name = name;
            this.cover = cover;
        }
    }

    private String getPostPreviewName(MusicPost post) {
        if (ObjectUtils.isEmpty(post)) {
            return null;
        }
        String content = post.getContent();
        if (StrUtil.isBlank(content)) {
            return "音乐广场动态";
        }
        return content.length() > 20 ? content.substring(0, 20) + "..." : content;
    }

    private String getPostPreviewCover(MusicPost post) {
        if (ObjectUtils.isEmpty(post)) {
            return null;
        }
        String images = post.getImages();
        if (StrUtil.isBlank(images)) {
            return null;
        }
        return "/api/music-square/posts/" + post.getId()
                + "/images/0/content?variant=compressed";
    }

    private boolean canViewPost(MusicPost post, Long userId) {
        if (post == null || post.getId() == null) {
            return false;
        }
        try {
            musicPostService.requirePostReadable(post.getId(), userId);
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    private boolean isTargetReadable(Comment comment, Long userId) {
        try {
            validateTarget(comment.getTargetType(), comment.getTargetId(), userId);
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    private boolean isPostCommentAllowed(MusicPost post) {
        if (post == null) {
            return false;
        }
        boolean authorAllowComment = post.getAllowComment() == null || post.getAllowComment() != 0;
        return authorAllowComment && !Boolean.TRUE.equals(post.getOfficialCommentClosed());
    }

       
                                                                                
                                                                               
       
    private void requireTargetOwnerCanReceiveInteraction(Long ownerId, String action) {
        if (ownerId == null) {
            return;
        }
        User owner = userMapper.selectById(ownerId);
        if (!UserAccountStatusUtil.canInteract(owner)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    UserAccountStatusUtil.targetUnavailableMessage(owner) + "，无法" + action);
        }
    }

    private void ensureCurrentUserCanInteract(Long userId, String action) {
        User user = userMapper.selectById(userId);
        if (!UserAccountStatusUtil.canInteract(user)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    UserAccountStatusUtil.currentUnavailableMessage(user) + "，无法" + action);
        }
    }

    private boolean canCurrentUserContributePublicStats(Long userId) {
        return UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById);
    }

    private boolean canContributeCommentStats(Comment comment) {
        return comment != null
                && UserAccountStatusUtil.canContributePublicStats(comment.getUserId(), userMapper::selectById);
    }

    private boolean canContributeTargetStats(Integer targetType, Long targetId) {
        if (targetType == null || targetId == null) {
            return false;
        }
        switch (targetType) {
            case 1:
                Song song = songMapper.selectById(targetId);
                return song != null
                        && (song.getUploaderId() == null
                        || UserAccountStatusUtil.canExposePublicContent(song.getUploaderId(), userMapper::selectById));
            case 3:
                Playlist playlist = playlistMapper.selectById(targetId);
                return playlist != null
                        && UserAccountStatusUtil.canExposePublicContent(playlist.getUserId(), userMapper::selectById);
            case 6:
                MusicPost post = musicPostMapper.selectById(targetId);
                return post != null
                        && UserAccountStatusUtil.canExposePublicContent(post.getUserId(), userMapper::selectById);
            case 7:
                MarketplaceItem marketplaceItem = marketplaceItemMapper.selectById(targetId);
                return marketplaceItem != null && !Boolean.TRUE.equals(marketplaceItem.getIsDeleted())
                        && UserAccountStatusUtil.canExposePublicContent(
                        marketplaceItem.getSellerId(), userMapper::selectById);
            case 2:
            case 4:
            case 5:
                return true;
            default:
                return false;
        }
    }

       
               
      
                             
                             
       
    private void validateTarget(Integer targetType, Long targetId, Long userId) {
        validateTarget(targetType, targetId, userId, false);
    }

    private void validateTarget(Integer targetType, Long targetId, Long userId, boolean requireCommentOpen) {
        switch (targetType) {
            case 1:      
                Song song = songMapper.selectById(targetId);
                if (ObjectUtils.isEmpty(song)) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
                }
                if (requireCommentOpen) {
                    contentAccessService.requireSongAccess(song, userId);
                } else {
                    contentAccessService.requireSongMetadataAccess(song, userId);
                }
                if (requireCommentOpen) {
                    requireTargetOwnerCanReceiveInteraction(song.getUploaderId(), "评论");
                }
                break;
            case 2:      
                Album album = albumMapper.selectById(targetId);
                if (ObjectUtils.isEmpty(album)) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "专辑不存在");
                }
                if (requireCommentOpen) {
                    contentAccessService.requireAlbumAccess(album, userId);
                } else {
                    contentAccessService.requireAlbumMetadataAccess(album, userId);
                }
                break;
            case 3:      
                Playlist playlist = playlistMapper.selectById(targetId);
                if (ObjectUtils.isEmpty(playlist)) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
                }
                if (requireCommentOpen) {
                    contentAccessService.requirePlaylistAccess(playlist, userId);
                } else {
                    contentAccessService.requirePlaylistMetadataAccess(playlist, userId);
                }
                if (!Integer.valueOf(1).equals(playlist.getIsPublic())
                        && !Objects.equals(playlist.getUserId(), userId)
                        && playlistCollaboratorMapper.selectAccepted(playlist.getId(), userId) == null) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该歌单评论");
                }
                if (requireCommentOpen) {
                    requireTargetOwnerCanReceiveInteraction(playlist.getUserId(), "评论");
                }
                break;
            case 4:      
                MV mv = mvMapper.selectById(targetId);
                if (ObjectUtils.isEmpty(mv)) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "MV不存在");
                }
                if (requireCommentOpen) {
                    contentAccessService.requireMvAccess(mv, userId);
                } else {
                    contentAccessService.requireMvMetadataAccess(mv, userId);
                }
                break;
            case 5:      
                Artist artist = artistMapper.selectById(targetId);
                if (ObjectUtils.isEmpty(artist)) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "歌手不存在");
                }
                if (!CommonConstants.STATUS_NORMAL.equals(artist.getStatus())) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "歌手已下架");
                }
                break;
            case 6:          
                musicPostService.requirePostReadable(targetId, userId);
                MusicPost post = musicPostMapper.selectById(targetId);
                if (requireCommentOpen && post != null) {
                    requireTargetOwnerCanReceiveInteraction(post.getUserId(), "评论");
                }
                if (requireCommentOpen && !isPostCommentAllowed(post)) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "评论区已关闭");
                }
                break;
            case 7:             
                MarketplaceItem marketplaceItem = marketplaceItemMapper.selectById(targetId);
                if (ObjectUtils.isEmpty(marketplaceItem) || Boolean.TRUE.equals(marketplaceItem.getIsDeleted())) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "商品帖不存在或已删除");
                }
                if (!UserAccountStatusUtil.canExposePublicContent(
                        marketplaceItem.getSellerId(), userMapper::selectById)) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "商品帖当前不可访问");
                }
                if (requireCommentOpen) {
                    requireTargetOwnerCanReceiveInteraction(marketplaceItem.getSellerId(), "评论");
                }
                break;
            default:
                throw new BusinessException(ResultCode.PARAM_ERROR, "无效的目标类型");
        }
    }

       
                 
      
                             
                             
                            
       
    private void updateTargetCommentCount(Integer targetType, Long targetId, int delta) {
        switch (targetType) {
            case 1:      
                songMapper.adjustCommentCount(targetId, delta);
                break;
            case 2:      
                albumMapper.adjustCommentCount(targetId, delta);
                break;
            case 3:      
                            
                break;
            case 4:      
                mvMapper.adjustCommentCount(targetId, delta);
                break;
            case 5:      
                artistMapper.adjustCommentCount(targetId, delta);
                break;
            case 6:            
                musicPostMapper.adjustCommentCount(targetId, delta);
                break;
            case 7:                   
                break;
        }
    }

       
                        
      
                          
                          
                   
       
    private CommentVO convertToVO(Comment comment, Long userId) {
        CommentVO vo = BeanUtil.copyProperties(comment, CommentVO.class);

                 
        User user = userMapper.selectById(comment.getUserId());
        if (ObjectUtils.isNotEmpty(user)) {
            vo.setUsername(user.getNickname());
            vo.setUserAvatar(user.getAvatar());
                       
            setUserRoleInfo(vo, user, Boolean.TRUE.equals(userVipService.isVip(user.getId())));
        }

                 
        vo.setTargetName(getTargetName(comment.getTargetType(), comment.getTargetId()));
        vo.setTargetCover(getTargetCover(comment.getTargetType(), comment.getTargetId()));

                 
        if (ObjectUtils.isNotEmpty(userId)) {
            LambdaQueryWrapper<CommentLike> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(CommentLike::getUserId, userId)
                    .eq(CommentLike::getCommentId, comment.getId())
                    .eq(CommentLike::getIsLike, CommonConstants.YES)
                    .eq(CommentLike::getDeleted, CommonConstants.NOT_DELETED);

            Long count = commentLikeMapper.selectCount(wrapper);
            vo.setIsLiked(count != null && count > 0);
        }

                   
        int editCount = comment.getEditCount() != null ? comment.getEditCount() : 0;
        vo.setEditCount(editCount);
        vo.setRemainingEditCount(Math.max(0, 3 - editCount));

                                                       
        boolean canEdit = userId != null && userId.equals(comment.getUserId()) &&
                System.currentTimeMillis() - comment.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() < 30 * 60 * 1000 &&
                editCount < 3;
        vo.setCanEdit(canEdit);

               
        vo.setIp(maskIp(comment.getIp()));

        return vo;
    }

       
             
      
                             
                             
                   
       
    private String getTargetName(Integer targetType, Long targetId) {
        switch (targetType) {
            case 1:
                Song song = songMapper.selectById(targetId);
                return ObjectUtils.isNotEmpty(song) ? song.getName() : null;
            case 2:
                Album album = albumMapper.selectById(targetId);
                return ObjectUtils.isNotEmpty(album) ? album.getName() : null;
            case 3:
                Playlist playlist = playlistMapper.selectById(targetId);
                return ObjectUtils.isNotEmpty(playlist) ? playlist.getName() : null;
            case 4:
                MV mv = mvMapper.selectById(targetId);
                return ObjectUtils.isNotEmpty(mv) ? mv.getName() : null;
            case 5:
                Artist artist = artistMapper.selectById(targetId);
                return ObjectUtils.isNotEmpty(artist) ? artist.getName() : null;
            case 6:
                MusicPost post = musicPostMapper.selectById(targetId);
                return getPostPreviewName(post);
            case 7:
                MarketplaceItem marketplaceItem = marketplaceItemMapper.selectById(targetId);
                return ObjectUtils.isNotEmpty(marketplaceItem) ? marketplaceItem.getTitle() : null;
            default:
                return null;
        }
    }

       
             
      
                             
                             
                   
       
    private String getTargetCover(Integer targetType, Long targetId) {
        switch (targetType) {
            case 1:
                Song song = songMapper.selectById(targetId);
                return ObjectUtils.isNotEmpty(song) ? song.getCover() : null;
            case 2:
                Album album = albumMapper.selectById(targetId);
                return ObjectUtils.isNotEmpty(album) ? album.getCover() : null;
            case 3:
                Playlist playlist = playlistMapper.selectById(targetId);
                return ObjectUtils.isNotEmpty(playlist) ? playlist.getCover() : null;
            case 4:
                MV mv = mvMapper.selectById(targetId);
                return ObjectUtils.isNotEmpty(mv) ? mv.getCover() : null;
            case 5:
                Artist artist = artistMapper.selectById(targetId);
                return ObjectUtils.isNotEmpty(artist) ? artist.getAvatar() : null;
            case 6:
                MusicPost post = musicPostMapper.selectById(targetId);
                return getPostPreviewCover(post);
            case 7:
                MarketplaceItem marketplaceItem = marketplaceItemMapper.selectById(targetId);
                return ObjectUtils.isNotEmpty(marketplaceItem) ? marketplaceItem.getResourceCover() : null;
            default:
                return null;
        }
    }

       
               
      
                       
                       
       
    private void setUserRoleInfo(CommentVO vo, User user, boolean isVip) {
        if (ObjectUtils.isEmpty(user)) {
            return;
        }

                 
        String role = user.getRole();
        if (StrUtil.isNotBlank(role)) {
            vo.setUserRole(role);
        } else {
                         
            if (user.getIsModerator() != null && user.getIsModerator() == 1) {
                vo.setUserRole("moderator");
            } else {
                vo.setUserRole("user");
            }
        }

        vo.setIsVip(isVip);

                   
        vo.setIsOfficial(user.getIsOfficial() != null && user.getIsOfficial() == 1);
    }

       
           
      
                     
                     
       
    private String maskIp(String ip) {
        if (StrUtil.isBlank(ip)) {
            return "未知";
        }
                 
        String[] parts = ip.split("\\.");
        if (parts.length >= 4) {
            parts[3] = "***";
            return String.join(".", parts);
        }
        return "***.***.***";
    }

    @Resource
    private com.haoran.music.mapper.CommentEditHistoryMapper commentEditHistoryMapper;

    @Resource
    private com.haoran.music.mapper.ReportMapper reportMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean editComment(Long userId, Long commentId, String newContent) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(commentId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        ensureCurrentUserCanInteract(userId, "编辑评论");

        if (StrUtil.isBlank(newContent) || StrUtil.isBlank(newContent.trim())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "评论内容不能为空");
        }

        SecurityCheckUtil.CheckResult contentCheck = SecurityCheckUtil.checkCommunityText(newContent, "评论内容");
        if (!contentCheck.isSafe()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, contentCheck.getMessage());
        }
        emojiService.requireContentEmojiAccess(contentCheck.getCleanedValue(), userId);

               
        Comment comment = getById(commentId);
        if (ObjectUtils.isEmpty(comment)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }

                         
        if (!userId.equals(comment.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权编辑此评论");
        }

                   
        int currentEditCount = comment.getEditCount() != null ? comment.getEditCount() : 0;
        if (currentEditCount >= 3) {
            throw new BusinessException("编辑次数已达上限（3次）");
        }

                       
        User user = userMapper.selectById(userId);
        String editorNickname = user != null ? user.getNickname() : "未知用户";

                 
        CommentEditHistory history = new CommentEditHistory();
        history.setCommentId(commentId);
        history.setOriginalContent(comment.getContent());
        history.setEditedContent(SecurityCheckUtil.escapeHtml(contentCheck.getCleanedValue()));
        history.setEditorId(userId);
        history.setEditorNickname(editorNickname);
        history.setEditorIp(UserContext.getClientIp());                                                   
        history.setEditTime(java.time.LocalDateTime.now());
        commentEditHistoryMapper.insert(history);

                 
        comment.setContent(SecurityCheckUtil.escapeHtml(contentCheck.getCleanedValue()));
        comment.setEditCount(currentEditCount + 1);
        updateById(comment);

        log.info("用户编辑评论: userId={}, commentId={}, editCount={}", userId, commentId, currentEditCount + 1);

        return true;
    }

    @Override
    public List<com.haoran.music.dto.comment.CommentEditHistoryVO> getCommentEditHistory(Long commentId, Long userId) {
        if (ObjectUtils.isEmpty(commentId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "评论ID不能为空");
        }

        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || CommonConstants.DELETED.equals(comment.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        validateTarget(comment.getTargetType(), comment.getTargetId(), userId);

        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CommentEditHistory> wrapper =
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(CommentEditHistory::getCommentId, commentId)
                .eq(CommentEditHistory::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(CommentEditHistory::getEditTime);

        List<CommentEditHistory> histories = commentEditHistoryMapper.selectList(wrapper);

        return histories.stream().map(history -> {
            com.haoran.music.dto.comment.CommentEditHistoryVO vo = new com.haoran.music.dto.comment.CommentEditHistoryVO();
            vo.setId(history.getId());
            vo.setContent(history.getEditedContent());
            vo.setEditTime(history.getEditTime());
            vo.setEditorName(history.getEditorNickname());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean reportComment(Long userId, Long commentId, String reason, String description) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(commentId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        if (StrUtil.isBlank(reason)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "举报理由不能为空");
        }

                   
        Comment comment = getById(commentId);
        if (ObjectUtils.isEmpty(comment)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }

                   
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Report> wrapper =
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(Report::getReporterId, userId)
                .eq(Report::getTargetType, "comment")
                .eq(Report::getTargetId, commentId)
                .ne(Report::getStatus, "closed");

        Long existingCount = reportMapper.selectCount(wrapper);
        if (existingCount != null && existingCount > 0) {
            throw new BusinessException("您已经举报过该评论，请勿重复举报");
        }

                 
        Report report = new Report();
        report.setReporterId(userId);
        report.setTargetType("comment");
        report.setTargetId(commentId);
        report.setReportType("other");               
        report.setReason(reason);
        report.setDescription(description);
        report.setStatus("pending");
        report.setIsRewarded(0);

        reportMapper.insert(report);

        log.info("用户举报评论: userId={}, commentId={}, reason={}", userId, commentId, reason);

        return true;
    }                                                            

       
                       
       
    @Override
    public List<CommentVO> getQualityComments(Integer targetType, Long targetId, Integer limit, Long userId) {
        if (ObjectUtils.isEmpty(targetType) || ObjectUtils.isEmpty(targetId)) {
            return new ArrayList<>();
        }
        validateTarget(targetType, targetId, userId);

        int actualLimit = limit != null && limit > 0 ? Math.min(limit, 100) : 20;

        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getTargetType, targetType)
                .eq(Comment::getTargetId, targetId)
                .eq(Comment::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Comment::getDeleted, CommonConstants.NOT_DELETED)
                .isNull(Comment::getParentId);

        List<Comment> allComments = list(wrapper);
        if (CollUtil.isEmpty(allComments)) {
            return new ArrayList<>();
        }

        Set<Long> followingUserIds = new HashSet<>();
        if (ObjectUtils.isNotEmpty(userId)) {
            followingUserIds = getFollowingUserIds(userId);
        }

        Set<Long> commentUserIds = allComments.stream()
                .map(Comment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> scoringUserMap = userMapper.selectBatchIds(commentUserIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        Set<Long> vipUserIds = userVipService.getActiveVipExpirations(commentUserIds).keySet();

        Map<Comment, Double> scoreMap = new HashMap<>();
        for (Comment comment : allComments) {
            double score = calculateCommentQualityScore(
                    comment, scoringUserMap.get(comment.getUserId()), userId, followingUserIds,
                    vipUserIds.contains(comment.getUserId()));
            scoreMap.put(comment, score);
        }

        List<Comment> topComments = scoreMap.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(actualLimit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return batchConvertToVO(topComments, userId);
    }

       
             
       
    @Override
    public List<CommentVO> getFriendComments(Integer targetType, Long targetId, Integer limit, Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }

        if (ObjectUtils.isEmpty(targetType) || ObjectUtils.isEmpty(targetId)) {
            return new ArrayList<>();
        }
        validateTarget(targetType, targetId, userId);

        int actualLimit = limit != null && limit > 0 ? Math.min(limit, 50) : 10;

        Set<Long> followingUserIds = getFollowingUserIds(userId);
        if (CollUtil.isEmpty(followingUserIds)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getTargetType, targetType)
                .eq(Comment::getTargetId, targetId)
                .eq(Comment::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Comment::getDeleted, CommonConstants.NOT_DELETED)
                .in(Comment::getUserId, followingUserIds)
                .isNull(Comment::getParentId)
                .orderByDesc(Comment::getCreateTime)
                .last("LIMIT " + actualLimit);

        List<Comment> friendComments = list(wrapper);
        if (CollUtil.isEmpty(friendComments)) {
            return new ArrayList<>();
        }

        Map<Long, Integer> intimacyMap = getUserIntimacyMap(userId, followingUserIds);
        friendComments.sort((c1, c2) -> {
            int intimacy1 = intimacyMap.getOrDefault(c1.getUserId(), 0);
            int intimacy2 = intimacyMap.getOrDefault(c2.getUserId(), 0);
            if (intimacy1 != intimacy2) {
                return Integer.compare(intimacy2, intimacy1);
            }
            return Long.compare(
                c1.getLikeCount() != null ? c1.getLikeCount() : 0,
                c2.getLikeCount() != null ? c2.getLikeCount() : 0
            );
        });

        return batchConvertToVO(friendComments, userId);
    }

       
                
       
    @Override
    public List<com.haoran.music.vo.user.UserVO> getCommentRecommendedUsers(
            Integer targetType, Long targetId, Integer limit, Long userId) {
        if (ObjectUtils.isEmpty(targetType) || ObjectUtils.isEmpty(targetId)) {
            return new ArrayList<>();
        }
        validateTarget(targetType, targetId, userId);

        int actualLimit = limit != null && limit > 0 ? Math.min(limit, 30) : 10;

        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getTargetType, targetType)
                .eq(Comment::getTargetId, targetId)
                .eq(Comment::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Comment::getDeleted, CommonConstants.NOT_DELETED)
                .select(Comment::getUserId)
                .groupBy(Comment::getUserId);

        List<Comment> comments = list(wrapper);
        if (CollUtil.isEmpty(comments)) {
            return new ArrayList<>();
        }

        Set<Long> userIds = comments.stream()
                .map(Comment::getUserId)
                .collect(Collectors.toSet());

        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        Set<Long> vipUserIds = userVipService.getActiveVipExpirations(userIds).keySet();
        Map<Long, Long> commentCountMap = commentMapper.selectUserCommentCounts(userIds, targetType, targetId)
                .stream()
                .collect(Collectors.toMap(
                        CommentUserCountDTO::getUserId,
                        count -> count.getCommentCount() == null ? 0L : count.getCommentCount(),
                        (left, right) -> left));

        Map<Long, Double> userScoreMap = new HashMap<>();
        for (Long uid : userIds) {
            double score = calculateUserQualityScore(
                    userMap.get(uid), commentCountMap.getOrDefault(uid, 0L), vipUserIds.contains(uid));
            userScoreMap.put(uid, score);
        }

        List<Long> sortedUserIds = userScoreMap.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(sortedUserIds)) {
            return new ArrayList<>();
        }

        List<Long> topUserIds = new ArrayList<>();
        for (Long uid : sortedUserIds) {
            User user = userMap.get(uid);
            if (!UserAccountStatusUtil.canAppearInRecommendations(user)) {
                continue;
            }
            topUserIds.add(uid);
            if (topUserIds.size() >= actualLimit) {
                break;
            }
        }

        if (CollUtil.isEmpty(topUserIds)) {
            return new ArrayList<>();
        }

        List<User> users = topUserIds.stream()
                .map(userMap::get)
                .filter(UserAccountStatusUtil::canAppearInRecommendations)
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(users)) {
            return new ArrayList<>();
        }

        return users.stream()
                .map(user -> {
                    com.haoran.music.vo.user.UserVO vo = BeanUtil.copyProperties(user, com.haoran.music.vo.user.UserVO.class);
                    vo.setPhone(null);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private double calculateCommentQualityScore(Comment comment,
                                                User user,
                                                Long currentUserId,
                                                Set<Long> followingUserIds,
                                                boolean isVip) {
        double baseScore = (comment.getLikeCount() != null ? comment.getLikeCount() : 0) * 1.0
                         + (comment.getReplyCount() != null ? comment.getReplyCount() : 0) * 1.5;

        if (user == null) {
            return baseScore;
        }

        double userWeight = calculateUserWeight(user, isVip);

        double contentQuality = 1.0;
        if (StrUtil.isNotBlank(comment.getContent())) {
            int contentLength = comment.getContent().length();
            contentQuality = Math.min(1.2, 1.0 + contentLength / 500.0);
        }

        long daysSinceComment = 0;
        if (comment.getCreateTime() != null) {
            daysSinceComment = java.time.temporal.ChronoUnit.DAYS.between(
                comment.getCreateTime(),
                java.time.LocalDateTime.now()
            );
        }
        double timeDecay = Math.exp(-daysSinceComment / 7.0);

        double friendBonus = 0.0;
        if (ObjectUtils.isNotEmpty(currentUserId) && followingUserIds.contains(comment.getUserId())) {
            friendBonus = 30.0;
        }

        return baseScore * userWeight * contentQuality * timeDecay + friendBonus;
    }

    private double calculateUserWeight(User user, boolean isVip) {
        double vipWeight = isVip ? 1.2 : 1.0;

        double creditWeight = 1.0;
        if (user.getCreditScore() != null) {
            int credit = user.getCreditScore();
            if (credit >= 90) creditWeight = 1.1;
            else if (credit >= 80) creditWeight = 1.0;
            else if (credit >= 60) creditWeight = 0.9;
            else creditWeight = 0.7;
        }

        double roleWeight = 1.0;
        if ("ADMIN".equals(user.getRole())) {
            roleWeight = 1.2;
        } else if ("CREATOR".equals(user.getRole())) {
            roleWeight = 1.1;
        }

        return vipWeight * creditWeight * roleWeight;
    }

    private double calculateUserQualityScore(User user, Long commentCount, boolean isVip) {
        if (user == null) {
            return 0.0;
        }

        double score = 0.0;

        if (user.getCreditScore() != null) {
            score += Math.min(30, user.getCreditScore() * 0.3);
        }

        if (isVip) {
            score += 15.0;
        }

        score += Math.min(30, (commentCount == null ? 0L : commentCount) * 5);

        if ("ADMIN".equals(user.getRole())) {
            score += 10.0;
        } else if ("CREATOR".equals(user.getRole())) {
            score += 7.0;
        }

        if (user.getFansCount() != null && user.getFansCount() > 0) {
            score += Math.min(15, Math.log10(user.getFansCount() + 1) * 3);
        }

        return score;
    }

    private Set<Long> getFollowingUserIds(Long userId) {
        List<Long> followeeIds = userFollowMapper.selectFolloweeIds(userId);
        if (CollUtil.isEmpty(followeeIds)) {
            return new HashSet<>();
        }
        return followeeIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Long resolveCommentRecipient(CommentCreateDTO request, Comment parentComment) {
        if (request.getReplyToUserId() != null) {
            return request.getReplyToUserId();
        }
        if (parentComment != null) {
            return parentComment.getUserId();
        }
        switch (request.getTargetType()) {
            case 1:
                Song song = songMapper.selectById(request.getTargetId());
                return song == null ? null : song.getUploaderId();
            case 3:
                Playlist playlist = playlistMapper.selectById(request.getTargetId());
                return playlist == null ? null : playlist.getUserId();
            case 6:
                MusicPost post = musicPostMapper.selectById(request.getTargetId());
                return post == null ? null : post.getUserId();
            case 7:
                MarketplaceItem item = marketplaceItemMapper.selectById(request.getTargetId());
                return item == null ? null : item.getSellerId();
            default:
                return null;
        }
    }

    private String notificationResourceType(Integer targetType) {
        switch (targetType) {
            case 1: return "song";
            case 2: return "album";
            case 3: return "playlist";
            case 4: return "mv";
            case 5: return "artist";
            case 6: return "music-post";
            case 7: return "marketplace-item";
            default: return null;
        }
    }

    private String userDisplayName(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return "一位镇民";
        if (StrUtil.isNotBlank(user.getNickname())) return user.getNickname().trim();
        if (StrUtil.isNotBlank(user.getUsername())) return user.getUsername().trim();
        return "一位镇民";
    }

    private Map<Long, Integer> getUserIntimacyMap(Long userId, Set<Long> friendIds) {
        Map<Long, Integer> intimacyMap = new HashMap<>();
        if (CollUtil.isEmpty(friendIds)) {
            return intimacyMap;
        }

        Set<Long> mutualFollowerIds = new HashSet<>(userFollowMapper.selectMutualFollowerIds(userId, friendIds));
        Map<Long, Long> interactionCounts = commentMapper.selectUserCommentCounts(friendIds, 4, userId)
                .stream()
                .collect(Collectors.toMap(
                        CommentUserCountDTO::getUserId,
                        count -> count.getCommentCount() == null ? 0L : count.getCommentCount(),
                        (left, right) -> left));

        for (Long friendId : friendIds) {
            int intimacy = 10;
            if (mutualFollowerIds.contains(friendId)) {
                intimacy += 20;
            }
            long interactCount = interactionCounts.getOrDefault(friendId, 0L);
            intimacy += (int) Math.min(20L, interactCount * 2L);
            intimacyMap.put(friendId, intimacy);
        }

        return intimacyMap;
    }

    private List<CommentVO> batchConvertToVO(List<Comment> comments, Long userId) {
        if (CollUtil.isEmpty(comments)) {
            return new ArrayList<>();
        }

        Set<Long> userIds = comments.stream()
                .map(Comment::getUserId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = new HashMap<>();
        if (CollUtil.isNotEmpty(userIds)) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        }

        Set<Long> commentIds = comments.stream()
                .map(Comment::getId)
                .collect(Collectors.toSet());
        Set<Long> likedCommentIds = new HashSet<>();
        if (ObjectUtils.isNotEmpty(userId) && CollUtil.isNotEmpty(commentIds)) {
            LambdaQueryWrapper<CommentLike> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(CommentLike::getUserId, userId)
                    .in(CommentLike::getCommentId, commentIds);
            List<CommentLike> likes = commentLikeMapper.selectList(wrapper);
            likedCommentIds = likes.stream()
                    .map(CommentLike::getCommentId)
                    .collect(Collectors.toSet());
        }

        final Set<Long> finalLikedCommentIds = likedCommentIds;
        final Map<Long, User> finalUserMap = userMap;

        return comments.stream().map(comment -> {
            CommentVO vo = BeanUtil.copyProperties(comment, CommentVO.class);

            User user = finalUserMap.get(comment.getUserId());
            if (user != null) {
                vo.setUsername(user.getNickname());
                vo.setUserAvatar(user.getAvatar());
            }

            vo.setIsLiked(finalLikedCommentIds.contains(comment.getId()));

            return vo;
        }).collect(Collectors.toList());
    }

}

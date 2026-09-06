package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.SocialRecommendService;
import com.haoran.music.vo.recommend.RecommendVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

   
             
  
                      
   
@Slf4j
@Service
public class SocialRecommendServiceImpl implements SocialRecommendService {

    private final UserFollowMapper userFollowMapper;
    private final SongMapper songMapper;
    private final SongLikeMapper songLikeMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final UserFriendMapper userFriendMapper;

    private static final double FRIENDS_LIKE_WEIGHT = 0.4;
    private static final double FOLLOWING_CONTENT_WEIGHT = 0.3;
    private static final double INTERACTION_WEIGHT = 0.3;

    public SocialRecommendServiceImpl(UserFollowMapper userFollowMapper,
                                      SongMapper songMapper,
                                      SongLikeMapper songLikeMapper,
                                      CommentMapper commentMapper,
                                      UserMapper userMapper,
                                      PlaylistMapper playlistMapper,
                                      PlaylistSongMapper playlistSongMapper,
                                      UserFriendMapper userFriendMapper) {
        this.userFollowMapper = userFollowMapper;
        this.songMapper = songMapper;
        this.songLikeMapper = songLikeMapper;
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.playlistMapper = playlistMapper;
        this.playlistSongMapper = playlistSongMapper;
        this.userFriendMapper = userFriendMapper;
    }

    @Override
    public RecommendVO getSocialBasedRecommend(Long userId, Integer limit) {
        int actualLimit = limit != null && limit > 0 ? limit : 20;

        RecommendVO result = new RecommendVO();
        result.setSource("social");
        result.setSourceName("社交推荐");

        return getMixedSocialRecommend(userId, actualLimit);
    }

    @Override
    public RecommendVO getFriendsLikedRecommend(Long userId, Integer limit) {
        int actualLimit = limit != null && limit > 0 ? limit : 20;

        RecommendVO result = new RecommendVO();
        result.setSource("friends_like");
        result.setSourceName("好友喜欢的");

        Set<Long> friendIds = getMutualFriends(userId);

        if (friendIds.isEmpty()) {
            result.setSongs(new ArrayList<>());
            return result;
        }

        Map<Long, Integer> songScoreMap = new HashMap<>();
        for (Long friendId : friendIds) {
            List<Long> friendLikedSongIds = getFriendLikedSongs(friendId);
            for (Long songId : friendLikedSongIds) {
                songScoreMap.merge(songId, 1, Integer::sum);
            }
        }

        Set<Long> userSongIds = getUserLikedSongs(userId);

                                  
        List<Long> targetSongIds = songScoreMap.entrySet().stream()
                .filter(e -> !userSongIds.contains(e.getKey()))
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(actualLimit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        List<RecommendVO.SongSimpleVO> songs = batchConvertToSimpleVO(targetSongIds);

        result.setSongs(songs);
        return result;
    }

    @Override
    public RecommendVO getFollowingUsersContent(Long userId, Integer limit) {
        int actualLimit = limit != null && limit > 0 ? limit : 20;

        RecommendVO result = new RecommendVO();
        result.setSource("following_content");
        result.setSourceName("关注用户的内容");

        Set<Long> followingIds = getFollowingUsers(userId);

        if (followingIds.isEmpty()) {
            result.setSongs(new ArrayList<>());
            return result;
        }

        List<Long> playlistIds = getFollowingUsersPlaylists(followingIds);

        Set<Long> songIds = new HashSet<>();
        for (Long playlistId : playlistIds) {
            songIds.addAll(getSongsFromPlaylist(playlistId));
        }

        List<Long> targetSongIds = songIds.stream()
                .limit(actualLimit)
                .collect(Collectors.toList());
        List<RecommendVO.SongSimpleVO> songs = batchConvertToSimpleVO(targetSongIds);

        result.setSongs(songs);
        return result;
    }

    @Override
    public RecommendVO getSocialInteractionRecommend(Long userId, Integer limit) {
        int actualLimit = limit != null && limit > 0 ? limit : 20;

        RecommendVO result = new RecommendVO();
        result.setSource("social_interaction");
        result.setSourceName("社交互动推荐");

        Set<Long> friendIds = getMutualFriends(userId);
        Map<Long, Integer> songScoreMap = new HashMap<>();

        for (Long friendId : friendIds) {
            List<Long> commentedSongs = getFriendCommentedSongs(friendId);
            for (Long songId : commentedSongs) {
                songScoreMap.merge(songId, 2, Integer::sum);
            }

            List<Long> likedSongs = getFriendLikedSongs(friendId);
            for (Long songId : likedSongs) {
                songScoreMap.merge(songId, 1, Integer::sum);
            }
        }

        List<Long> targetSongIds = songScoreMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(actualLimit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        List<RecommendVO.SongSimpleVO> songs = batchConvertToSimpleVO(targetSongIds);

        result.setSongs(songs);
        return result;
    }

    @Override
    public RecommendVO getMixedSocialRecommend(Long userId, Integer limit) {
        int actualLimit = limit != null && limit > 0 ? limit : 20;

        RecommendVO result = new RecommendVO();
        result.setSource("mixed_social");
        result.setSourceName("混合社交推荐");

        Map<Long, Double> songScoreMap = new HashMap<>();

        RecommendVO friendsLiked = getFriendsLikedRecommend(userId, actualLimit);
        addSongsWithWeight(songScoreMap, friendsLiked, FRIENDS_LIKE_WEIGHT);

        RecommendVO followingContent = getFollowingUsersContent(userId, actualLimit);
        addSongsWithWeight(songScoreMap, followingContent, FOLLOWING_CONTENT_WEIGHT);

        RecommendVO interactionRec = getSocialInteractionRecommend(userId, actualLimit);
        addSongsWithWeight(songScoreMap, interactionRec, INTERACTION_WEIGHT);

        List<Long> targetSongIds = songScoreMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(actualLimit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        List<RecommendVO.SongSimpleVO> songs = batchConvertToSimpleVO(targetSongIds);

        result.setSongs(songs);
        return result;
    }

    @Override
    public String getSocialRecommendReason(Long userId, Long contentId, String contentType) {
        Set<Long> friendIds = getMutualFriends(userId);

        if ("song".equals(contentType) && !friendIds.isEmpty()) {
                          
            LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(SongLike::getUserId, friendIds)
                    .eq(SongLike::getSongId, contentId)
                    .eq(SongLike::getIsFavorite, 1)
                    .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED);

            SongLike like = songLikeMapper.selectOne(wrapper);
            if (like != null) {
                User friend = userMapper.selectById(like.getUserId());
                String friendName = friend != null ? friend.getNickname() : "好友";
                return "你的好友 " + friendName + " 也喜欢这首歌";
            }
        }

        return "社交推荐";
    }

    @Override
    public Integer calculateSocialIntimacy(Long userId1, Long userId2) {
        if (ObjectUtils.isEmpty(userId1) || ObjectUtils.isEmpty(userId2)) {
            return 0;
        }

        if (userId1.equals(userId2)) {
            return 100;
        }

        int intimacyScore = 0;

        boolean isFollowing = isFollowing(userId1, userId2);
        boolean isFollowed = isFollowing(userId2, userId1);
        if (isFollowing && isFollowed) {
            intimacyScore += 40;
        } else if (isFollowing || isFollowed) {
            intimacyScore += 20;
        }

        int commonLikedSongs = getCommonLikedSongs(userId1, userId2);
        intimacyScore += Math.min(commonLikedSongs * 3, 30);

        int commonCommentedSongs = getCommonCommentedSongs(userId1, userId2);
        intimacyScore += Math.min(commonCommentedSongs * 5, 20);

        int commonFollowing = getCommonFollowing(userId1, userId2);
        intimacyScore += Math.min(commonFollowing * 2, 10);

        return Math.min(intimacyScore, 100);
    }

    @Override
    public Object getUserSocialPortrait(Long userId) {
        Map<String, Object> portrait = new HashMap<>();

        int friendsCount = getMutualFriends(userId).size();
        portrait.put("friendsCount", friendsCount);

        int followingCount = getFollowingUsers(userId).size();
        portrait.put("followingCount", followingCount);

        int followersCount = getFollowers(userId).size();
        portrait.put("followersCount", followersCount);

        Set<String> friendLikedGenres = getFriendLikedGenres(userId);
        portrait.put("friendLikedGenres", friendLikedGenres);

        int socialActivityScore = calculateSocialActivityScore(userId);
        portrait.put("socialActivityScore", socialActivityScore);

        return portrait;
    }

    private Set<Long> getMutualFriends(Long userId) {
        Set<Long> friends = new HashSet<>();

        Set<Long> following = getFollowingUsers(userId);
        Set<Long> followers = getFollowers(userId);
        friends.addAll(following);
        friends.retainAll(followers);

        Set<Long> acceptedFriends = getAcceptedFriends(userId);
        friends.addAll(acceptedFriends);

        return friends;
    }

    private Set<Long> getAcceptedFriends(Long userId) {
        LambdaQueryWrapper<com.haoran.music.entity.UserFriend> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(com.haoran.music.entity.UserFriend::getUserId, userId)
                         .or()
                         .eq(com.haoran.music.entity.UserFriend::getFriendId, userId))
               .eq(com.haoran.music.entity.UserFriend::getStatus, "accepted")
               .eq(com.haoran.music.entity.UserFriend::getDeleted, 0);

        List<com.haoran.music.entity.UserFriend> friendRelations = userFriendMapper.selectList(wrapper);
        Set<Long> friendIds = new HashSet<>();

        for (com.haoran.music.entity.UserFriend uf : friendRelations) {
            if (userId.equals(uf.getUserId())) {
                friendIds.add(uf.getFriendId());
            } else {
                friendIds.add(uf.getUserId());
            }
        }

        return filterEligibleUserIds(friendIds);
    }

    private Set<Long> getFollowingUsers(Long userId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED);

        Set<Long> userIds = userFollowMapper.selectList(wrapper).stream()
                .map(UserFollow::getFolloweeId)
                .collect(Collectors.toSet());
        return filterEligibleUserIds(userIds);
    }

    private Set<Long> getFollowers(Long userId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFolloweeId, userId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED);

        Set<Long> userIds = userFollowMapper.selectList(wrapper).stream()
                .map(UserFollow::getFollowerId)
                .collect(Collectors.toSet());
        return filterEligibleUserIds(userIds);
    }

    private boolean isFollowing(Long followerId, Long followeeId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED);

        return userFollowMapper.selectCount(wrapper) > 0;
    }

    private List<Long> getFriendLikedSongs(Long friendId) {
        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, friendId)
                .eq(SongLike::getIsFavorite, 1)
                .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED);

        return songLikeMapper.selectList(wrapper).stream()
                .map(SongLike::getSongId)
                .collect(Collectors.toList());
    }

    private boolean isFriendLikedSong(Long friendId, Long songId) {
        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, friendId)
                .eq(SongLike::getSongId, songId)
                .eq(SongLike::getIsFavorite, 1)
                .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED);

        return songLikeMapper.selectCount(wrapper) > 0;
    }

    private Set<Long> filterEligibleUserIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> safeUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (safeUserIds.isEmpty()) {
            return Collections.emptySet();
        }

        return userMapper.selectBatchIds(safeUserIds).stream()
                .filter(UserAccountStatusUtil::canAppearInRecommendations)
                .map(User::getId)
                .collect(Collectors.toSet());
    }

    private List<Long> getFriendCommentedSongs(Long friendId) {
        return commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getUserId, friendId)
                        .eq(Comment::getTargetType, 1)
                        .eq(Comment::getDeleted, CommonConstants.NOT_DELETED)
        ).stream()
                .map(Comment::getTargetId)
                .collect(Collectors.toList());
    }

    private Set<Long> getUserLikedSongs(Long userId) {
        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getIsFavorite, 1)
                .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED);

        return songLikeMapper.selectList(wrapper).stream()
                .map(SongLike::getSongId)
                .collect(Collectors.toSet());
    }

    private List<Long> getFollowingUsersPlaylists(Set<Long> followingIds) {
        if (followingIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Playlist::getUserId, followingIds)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                .eq(Playlist::getIsPublic, 1);

        return playlistMapper.selectList(wrapper).stream()
                .map(Playlist::getId)
                .collect(Collectors.toList());
    }

    private List<Long> getSongsFromPlaylist(Long playlistId) {
        if (playlistId == null) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<com.haoran.music.entity.PlaylistSong> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.haoran.music.entity.PlaylistSong::getPlaylistId, playlistId)
                .eq(com.haoran.music.entity.PlaylistSong::getDeleted, CommonConstants.NOT_DELETED)
                .orderByAsc(com.haoran.music.entity.PlaylistSong::getAddTime)
                .last("LIMIT 100");

        return playlistSongMapper.selectList(wrapper).stream()
                .map(com.haoran.music.entity.PlaylistSong::getSongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private int getCommonLikedSongs(Long userId1, Long userId2) {
        Set<Long> user1Liked = getUserLikedSongs(userId1);
        Set<Long> user2Liked = getUserLikedSongs(userId2);
        user1Liked.retainAll(user2Liked);
        return user1Liked.size();
    }

    private int getCommonCommentedSongs(Long userId1, Long userId2) {
        Set<Long> user1Commented = new HashSet<>();
        Set<Long> user2Commented = new HashSet<>();

        user1Commented.addAll(getFriendCommentedSongs(userId1));
        user2Commented.addAll(getFriendCommentedSongs(userId2));

        user1Commented.retainAll(user2Commented);
        return user1Commented.size();
    }

    private int getCommonFollowing(Long userId1, Long userId2) {
        Set<Long> user1Following = getFollowingUsers(userId1);
        Set<Long> user2Following = getFollowingUsers(userId2);
        user1Following.retainAll(user2Following);
        return user1Following.size();
    }

       
                       
       
    private Set<String> getFriendLikedGenres(Long userId) {
        Set<Long> friendIds = getMutualFriends(userId);

        if (friendIds.isEmpty()) {
            return new HashSet<>();
        }

                      
        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SongLike::getUserId, friendIds)
                .eq(SongLike::getIsFavorite, 1)
                .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED)
                .select(SongLike::getSongId);

        List<SongLike> likes = songLikeMapper.selectList(wrapper);
        Set<Long> songIds = likes.stream()
                .map(SongLike::getSongId)
                .collect(Collectors.toSet());

        if (songIds.isEmpty()) {
            return new HashSet<>();
        }

                      
        LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<>();
        songWrapper.in(Song::getId, songIds)
                .isNotNull(Song::getMainType)
                .select(Song::getId, Song::getMainType);

        List<Song> songs = songMapper.selectList(songWrapper);
        return songs.stream()
                .map(Song::getMainType)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toSet());
    }

    private int calculateSocialActivityScore(Long userId) {
        int score = 0;

        int followingCount = getFollowingUsers(userId).size();
        score += Math.min(followingCount, 20);

        Long commentCount = commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getUserId, userId)
                        .eq(Comment::getDeleted, CommonConstants.NOT_DELETED)
        );
        score += Math.min(commentCount.intValue() * 2, 30);

        int friendsCount = getMutualFriends(userId).size();
        score += Math.min(friendsCount * 2, 20);

        Long likeCount = songLikeMapper.selectCount(
                new LambdaQueryWrapper<SongLike>()
                        .eq(SongLike::getUserId, userId)
                        .eq(SongLike::getIsFavorite, 1)
                        .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED)
        );
        score += Math.min(likeCount.intValue(), 30);

        return Math.min(score, 100);
    }

    private void addSongsWithWeight(Map<Long, Double> scoreMap, RecommendVO recommend, double weight) {
        if (recommend == null || recommend.getSongs() == null) {
            return;
        }

        for (int i = 0; i < recommend.getSongs().size(); i++) {
            Long songId = recommend.getSongs().get(i).getId();
            double positionScore = (recommend.getSongs().size() - i) * weight;
            scoreMap.merge(songId, positionScore, Double::sum);
        }
    }

       
                                 
       
    private List<RecommendVO.SongSimpleVO> batchConvertToSimpleVO(List<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return new ArrayList<>();
        }

               
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getId, songIds)
                .eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0);

        List<Song> songs = songMapper.selectList(wrapper);
        Map<Long, Song> songMap = songs.stream()
                .collect(Collectors.toMap(Song::getId, s -> s));

        return songIds.stream()
                .map(id -> convertSongToSimpleVO(songMap.get(id)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

       
                      
       
    private RecommendVO.SongSimpleVO convertSongToSimpleVO(Song song) {
        if (song == null) {
            return null;
        }

        RecommendVO.SongSimpleVO vo = new RecommendVO.SongSimpleVO();
        vo.setId(song.getId());
        vo.setName(song.getName());
        vo.setArtistNames(song.getArtistNames());
        vo.setArtistId(song.getArtistId());
        vo.setAlbumName(song.getAlbumName());
        vo.setAlbumId(song.getAlbumId());
        vo.setDuration(song.getDuration());
        vo.setCover(song.getCover());
        return vo;
    }

}

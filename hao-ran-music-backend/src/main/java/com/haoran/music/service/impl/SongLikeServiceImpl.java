package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.MusicConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.SongLike;
import com.haoran.music.entity.User;
import com.haoran.music.entity.Playlist;
import com.haoran.music.entity.PlaylistSong;
import com.haoran.music.mapper.SongLikeMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.PlaylistMapper;
import com.haoran.music.mapper.PlaylistSongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.FavoriteCollectionItemMapper;
import com.haoran.music.service.SongLikeService;
import com.haoran.music.service.SongService;
import com.haoran.music.service.FavoriteHistoryService;
import com.haoran.music.service.UserStatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;





@Slf4j
@Service
public class SongLikeServiceImpl extends ServiceImpl<SongLikeMapper, SongLike> implements SongLikeService {

    @Autowired
    private SongMapper songMapper;

    @Autowired
    private SongService songService;

    @Autowired
    private FavoriteHistoryService favoriteHistoryService;

    @Autowired
    private UserStatisticsService userStatisticsService;

    @Autowired
    private PlaylistMapper playlistMapper;

    @Autowired
    private PlaylistSongMapper playlistSongMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired(required = false)
    private FavoriteCollectionItemMapper favoriteCollectionItemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean likeSong(Long userId, Long songId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(songId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID和歌曲ID不能为空");
        }
        User user = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(user, "点赞歌曲");


        Song song = songMapper.selectById(songId);
        if (ObjectUtils.isEmpty(song)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        requireSongOwnerCanReceiveInteraction(song, "点赞歌曲");


        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getSongId, songId);
        SongLike songLike = getOne(wrapper);

        if (ObjectUtils.isEmpty(songLike)) {

            songLike = new SongLike();
            songLike.setUserId(userId);
            songLike.setSongId(songId);
            songLike.setIsLike(1);
            songLike.setIsFavorite(0);
            save(songLike);
        } else if (songLike.getIsLike() == 0) {

            songLike.setIsLike(1);
            updateById(songLike);
        } else {

            return true;
        }

        if (canContributeSongStats(user, song)) {

            song.setLikeCount(safeLong(song.getLikeCount()) + 1);
            songMapper.updateById(song);


            updateHotScore(songId);
            userStatisticsService.incrementInteraction(userId, "like", 1);
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unlikeSong(Long userId, Long songId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(songId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID和歌曲ID不能为空");
        }


        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getSongId, songId);
        SongLike songLike = getOne(wrapper);

        if (ObjectUtils.isNotEmpty(songLike) && songLike.getIsLike() == 1) {

            songLike.setIsLike(0);
            updateById(songLike);

            User user = userMapper.selectById(userId);


            Song song = songMapper.selectById(songId);
            if (ObjectUtils.isNotEmpty(song) && canContributeSongStats(user, song) && safeLong(song.getLikeCount()) > 0) {
                song.setLikeCount(safeLong(song.getLikeCount()) - 1);
                songMapper.updateById(song);
                updateHotScore(songId);
            }
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean favoriteSong(Long userId, Long songId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(songId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID和歌曲ID不能为空");
        }
        User user = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(user, "收藏歌曲");


        Song song = songMapper.selectById(songId);
        if (ObjectUtils.isEmpty(song)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        requireSongOwnerCanReceiveInteraction(song, "收藏歌曲");


        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getSongId, songId);
        SongLike songLike = getOne(wrapper);

        if (ObjectUtils.isEmpty(songLike)) {

            songLike = new SongLike();
            songLike.setUserId(userId);
            songLike.setSongId(songId);
            songLike.setIsFavorite(1);
            songLike.setIsLike(0);
            save(songLike);
        } else if (songLike.getIsFavorite() == 0) {

            songLike.setIsFavorite(1);
            updateById(songLike);
        } else {

            return true;
        }

        boolean canContributeStats = canContributeSongStats(user, song);
        if (canContributeStats) {

            song.setFavoriteCount(safeLong(song.getFavoriteCount()) + 1);
            songMapper.updateById(song);


            updateHotScore(songId);
        }


        favoriteHistoryService.recordFavoriteAction(userId, songId, 1);


        addSongToFavoritePlaylist(userId, songId);
        if (canContributeStats) {
            userStatisticsService.incrementInteraction(userId, "favorite", 1);
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unfavoriteSong(Long userId, Long songId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(songId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID和歌曲ID不能为空");
        }


        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getSongId, songId);
        SongLike songLike = getOne(wrapper);

        if (ObjectUtils.isNotEmpty(songLike) && songLike.getIsFavorite() == 1) {

            songLike.setIsFavorite(0);
            updateById(songLike);

            User user = userMapper.selectById(userId);


            Song song = songMapper.selectById(songId);
            boolean canContributeStats = ObjectUtils.isNotEmpty(song) && canContributeSongStats(user, song);
            if (canContributeStats && safeLong(song.getFavoriteCount()) > 0) {
                song.setFavoriteCount(safeLong(song.getFavoriteCount()) - 1);
                songMapper.updateById(song);
                updateHotScore(songId);
            }


            favoriteHistoryService.recordFavoriteAction(userId, songId, 2);


            removeSongFromFavoritePlaylist(userId, songId);
            if (canContributeStats) {
                userStatisticsService.incrementInteraction(userId, "unfavorite", 1);
            }
        }

        if (favoriteCollectionItemMapper != null) {
            favoriteCollectionItemMapper.deleteResource(userId, "song", songId);
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean toggleLike(Long userId, Long songId) {
        Boolean isLiked = isLiked(userId, songId);
        if (isLiked) {
            unlikeSong(userId, songId);
            return false;
        } else {
            likeSong(userId, songId);
            return true;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean toggleFavorite(Long userId, Long songId) {
        Boolean isFavorited = isFavorited(userId, songId);
        if (isFavorited) {
            unfavoriteSong(userId, songId);
            return false;
        } else {
            favoriteSong(userId, songId);
            return true;
        }
    }

    @Override
    public Boolean isLiked(Long userId, Long songId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(songId)) {
            return false;
        }

        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getSongId, songId)
                .eq(SongLike::getIsLike, 1);
        return count(wrapper) > 0;
    }

    @Override
    public Boolean isFavorited(Long userId, Long songId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(songId)) {
            return false;
        }

        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getSongId, songId)
                .eq(SongLike::getIsFavorite, 1);
        return count(wrapper) > 0;
    }

    @Override
    public IPage<SongLike> getFavoriteSongs(Long userId, com.haoran.music.common.dto.PageQuery pageQuery) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }

        Page<SongLike> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());

        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getIsFavorite, 1)
                .orderByDesc(SongLike::getCreateTime);

        return page(page, wrapper);
    }

    @Override
    public Integer getFavoriteCount(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 0;
        }

        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getIsFavorite, 1);
        return Math.toIntExact(count(wrapper));
    }

    @Override
    public Map<Long, Map<String, Boolean>> getBatchSongStatus(Long userId, List<Long> songIds) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(songIds)) {
            return new HashMap<>();
        }

        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .in(SongLike::getSongId, songIds);

        List<SongLike> songLikes = list(wrapper);

        Map<Long, Map<String, Boolean>> result = new HashMap<>();
        for (Long songId : songIds) {
            Map<String, Boolean> status = new HashMap<>();
            status.put("isLiked", false);
            status.put("isFavorited", false);
            result.put(songId, status);
        }

        for (SongLike songLike : songLikes) {
            Map<String, Boolean> status = result.get(songLike.getSongId());
            if (status != null) {
                status.put("isLiked", songLike.getIsLike() == 1);
                status.put("isFavorited", songLike.getIsFavorite() == 1);
            }
        }

        return result;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private boolean canContributeSongStats(User user, Song song) {
        if (!UserAccountStatusUtil.canContributePublicStats(user)) {
            return false;
        }
        if (song == null || song.getUploaderId() == null) {
            return true;
        }
        return UserAccountStatusUtil.canExposePublicContent(song.getUploaderId(), userMapper::selectById);
    }





    private void requireSongOwnerCanReceiveInteraction(Song song, String action) {
        if (song == null || song.getUploaderId() == null) {
            return;
        }
        User owner = userMapper.selectById(song.getUploaderId());
        if (!UserAccountStatusUtil.canInteract(owner)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    UserAccountStatusUtil.targetUnavailableMessage(owner) + "，无法" + action);
        }
    }







    private void updateHotScore(Long songId) {
        Song song = songMapper.selectById(songId);
        if (ObjectUtils.isEmpty(song)) {
            return;
        }


        int favoriteCount = song.getFavoriteCount() != null ? song.getFavoriteCount().intValue() : 0;
        int commentCount = song.getCommentCount() != null ? song.getCommentCount().intValue() : 0;
        int likeCount = song.getLikeCount() != null ? song.getLikeCount().intValue() : 0;
        int replyCount = song.getReplyCount() != null ? song.getReplyCount().intValue() : 0;

        long hotScore = favoriteCount * 5L + commentCount * 3L + likeCount * 2L + replyCount * 1L;
        song.setHotScore((int) hotScore);
        songMapper.updateById(song);
    }







    private void addSongToFavoritePlaylist(Long userId, Long songId) {
        try {

            LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Playlist::getUserId, userId)
                    .eq(Playlist::getType, MusicConstants.PlaylistType.FAVORITE)
                    .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED);

            Playlist favoritePlaylist = playlistMapper.selectOne(wrapper);


            if (ObjectUtils.isEmpty(favoritePlaylist)) {
                favoritePlaylist = new Playlist();
                favoritePlaylist.setUserId(userId);
                favoritePlaylist.setName("我喜爱的音乐");
                favoritePlaylist.setType(MusicConstants.PlaylistType.FAVORITE);
                favoritePlaylist.setCover("/images/default-base.png");
                favoritePlaylist.setDescription("我喜爱的音乐收藏");
                favoritePlaylist.setIsPublic(CommonConstants.PUBLIC_PRIVATE);
                favoritePlaylist.setSongCount(0L);
                favoritePlaylist.setPlayCount(0L);
                favoritePlaylist.setFavoriteCount(0L);
                favoritePlaylist.setStatus(CommonConstants.STATUS_NORMAL);
                favoritePlaylist.setDeleted(CommonConstants.NOT_DELETED);
                playlistMapper.insert(favoritePlaylist);
            }



            String deleteOldSql = "DELETE FROM playlist_song WHERE playlist_id = ? AND song_id = ? AND deleted = 1";
            int cleanedCount = jdbcTemplate.update(deleteOldSql, favoritePlaylist.getId(), songId);
            if (cleanedCount > 0) {
                log.info("【数据留存】清理旧的历史记录: playlistId={}, songId={}, cleanedCount={}",
                    favoritePlaylist.getId(), songId, cleanedCount);
            }


            LambdaQueryWrapper<PlaylistSong> existWrapper = new LambdaQueryWrapper<>();
            existWrapper.eq(PlaylistSong::getPlaylistId, favoritePlaylist.getId())
                    .eq(PlaylistSong::getSongId, songId)
                    .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED);

            PlaylistSong existSong = playlistSongMapper.selectOne(existWrapper);
            if (ObjectUtils.isNotEmpty(existSong)) {

                return;
            }


            LambdaQueryWrapper<PlaylistSong> maxSortWrapper = new LambdaQueryWrapper<>();
            maxSortWrapper.eq(PlaylistSong::getPlaylistId, favoritePlaylist.getId())
                    .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED)
                    .orderByDesc(PlaylistSong::getSortOrder)
                    .last("LIMIT 1");

            PlaylistSong maxSort = playlistSongMapper.selectOne(maxSortWrapper);
            int nextSortOrder = (maxSort != null) ? maxSort.getSortOrder() + 1 : 0;


            PlaylistSong playlistSong = new PlaylistSong();
            playlistSong.setPlaylistId(favoritePlaylist.getId());
            playlistSong.setSongId(songId);
            playlistSong.setSortOrder(nextSortOrder);
            playlistSong.setDeleted(CommonConstants.NOT_DELETED);
            playlistSongMapper.insert(playlistSong);


            favoritePlaylist.setSongCount(favoritePlaylist.getSongCount() + 1);


            Song song = songMapper.selectById(songId);
            if (ObjectUtils.isNotEmpty(song) && ObjectUtils.isNotEmpty(song.getCover())) {
                favoritePlaylist.setCover(song.getCover());
                log.info("已更新收藏歌单封面: playlistId={}, cover={}", favoritePlaylist.getId(), song.getCover());
            }

            playlistMapper.updateById(favoritePlaylist);

            log.info("已添加歌曲到收藏歌单: userId={}, songId={}, playlistId={}", userId, songId, favoritePlaylist.getId());
        } catch (Exception e) {
            log.error("添加歌曲到收藏歌单失败: userId={}, songId={}, error={}", userId, songId, e.getClass().getSimpleName());
        }
    }








    private void removeSongFromFavoritePlaylist(Long userId, Long songId) {
        try {

            LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Playlist::getUserId, userId)
                    .eq(Playlist::getType, MusicConstants.PlaylistType.FAVORITE)
                    .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED);

            Playlist favoritePlaylist = playlistMapper.selectOne(wrapper);
            if (ObjectUtils.isEmpty(favoritePlaylist)) {
                return;
            }


            LambdaQueryWrapper<PlaylistSong> existWrapper = new LambdaQueryWrapper<>();
            existWrapper.eq(PlaylistSong::getPlaylistId, favoritePlaylist.getId())
                    .eq(PlaylistSong::getSongId, songId)
                    .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED);

            PlaylistSong activeSong = playlistSongMapper.selectOne(existWrapper);
            if (ObjectUtils.isEmpty(activeSong)) {

                return;
            }


            LambdaQueryWrapper<PlaylistSong> deletedWrapper = new LambdaQueryWrapper<>();
            deletedWrapper.eq(PlaylistSong::getPlaylistId, favoritePlaylist.getId())
                    .eq(PlaylistSong::getSongId, songId)
                    .eq(PlaylistSong::getDeleted, CommonConstants.DELETED);

            PlaylistSong deletedSong = playlistSongMapper.selectOne(deletedWrapper);

            if (ObjectUtils.isNotEmpty(deletedSong)) {


                String physicalDeleteSql = "DELETE FROM playlist_song WHERE id = ?";
                jdbcTemplate.update(physicalDeleteSql, deletedSong.getId());
                log.info("【数据留存】物理删除旧的历史记录: playlistId={}, songId={}, deletedId={}",
                    favoritePlaylist.getId(), songId, deletedSong.getId());
            }



            playlistSongMapper.deleteById(activeSong.getId());
            log.info("【数据留存】逻辑删除歌单歌曲记录: playlistId={}, songId={}, id={}",
                favoritePlaylist.getId(), songId, activeSong.getId());


            Long newCount = Math.max(0L, favoritePlaylist.getSongCount() - 1);
            favoritePlaylist.setSongCount(newCount);


            if (newCount > 0) {

                LambdaQueryWrapper<PlaylistSong> firstSongWrapper = new LambdaQueryWrapper<>();
                firstSongWrapper.eq(PlaylistSong::getPlaylistId, favoritePlaylist.getId())
                        .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED)
                        .orderByAsc(PlaylistSong::getSortOrder)
                        .last("LIMIT 1");

                PlaylistSong firstSong = playlistSongMapper.selectOne(firstSongWrapper);
                if (ObjectUtils.isNotEmpty(firstSong)) {
                    Song song = songMapper.selectById(firstSong.getSongId());
                    if (ObjectUtils.isNotEmpty(song) && ObjectUtils.isNotEmpty(song.getCover())) {
                        favoritePlaylist.setCover(song.getCover());
                        log.info("已更新收藏歌单封面: playlistId={}, cover={}", favoritePlaylist.getId(), song.getCover());
                    }
                }
            } else {

                favoritePlaylist.setCover("/images/default-base.png");
                log.info("收藏歌单为空，使用默认封面: playlistId={}", favoritePlaylist.getId());
            }

            playlistMapper.updateById(favoritePlaylist);

            log.info("已从收藏歌单移除歌曲: userId={}, songId={}, playlistId={}",
                    userId, songId, favoritePlaylist.getId());
        } catch (Exception e) {
            log.error("从收藏歌单移除歌曲失败: userId={}, songId={}, error={}", userId, songId, e.getClass().getSimpleName());
        }
    }








    @Override
    public Set<Long> getFavoriteSongIdsBatch(Long userId, List<Long> songIds) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(songIds)) {
            return Collections.emptySet();
        }

        try {

            LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SongLike::getUserId, userId)
                    .in(SongLike::getSongId, songIds)
                    .eq(SongLike::getIsFavorite, 1)
                    .select(SongLike::getSongId);

            List<SongLike> songLikes = list(wrapper);
            return songLikes.stream()
                    .map(SongLike::getSongId)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("批量查询收藏状态失败: userId={}, songIds={}", userId, songIds);
            return Collections.emptySet();
        }
    }
}

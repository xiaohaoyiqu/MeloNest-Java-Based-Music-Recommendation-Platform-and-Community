package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.MusicConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.SyncFavoriteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;





@Slf4j
@Service
public class SyncFavoriteServiceImpl implements SyncFavoriteService {

    @Autowired
    private SongLikeMapper songLikeMapper;

    @Autowired
    private PlaylistMapper playlistMapper;

    @Autowired
    private PlaylistSongMapper playlistSongMapper;

    @Autowired
    private SongMapper songMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> syncFavoriteData(Long userId) {

        List<Long> userIds = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(userId)) {
            userIds.add(userId);
        } else {
            LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
            wrapper.select(SongLike::getUserId)
                    .eq(SongLike::getIsFavorite, 1)
                    .groupBy(SongLike::getUserId);
            List<SongLike> songLikes = songLikeMapper.selectList(wrapper);
            userIds = songLikes.stream()
                    .map(SongLike::getUserId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }

        long totalSynced = 0;
        for (Long uid : userIds) {
            totalSynced += syncUserFavoriteData(uid);
        }

        log.info("event=favorite_reconciliation_completed userCount={} changedRows={}",
                userIds.size(), totalSynced);
        return Result.success(totalSynced);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> fixFavoritePlaylist(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return Result.error(ResultCode.PARAM_ERROR);
        }

        long synced = syncUserFavoriteData(userId);
        return Result.success(synced);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Integer> updateAllPlaylistSongCount() {
        String sql = "UPDATE playlist p "
                + "LEFT JOIN ("
                + "  SELECT playlist_id, COUNT(*) AS song_count "
                + "  FROM playlist_song ps "
                + "  INNER JOIN song s ON s.id = ps.song_id AND s.status = 1 AND s.deleted = 0 "
                + "  WHERE ps.deleted = 0 "
                + "  GROUP BY playlist_id"
                + ") ps ON ps.playlist_id = p.id "
                + "SET p.song_count = COALESCE(ps.song_count, 0), p.update_time = NOW() "
                + "WHERE p.deleted = 0 "
                + "AND (p.song_count IS NULL OR p.song_count <> COALESCE(ps.song_count, 0))";
        int updated = jdbcTemplate.update(sql);
        log.info("event=playlist_song_count_reconciled changedPlaylists={}", updated);
        return Result.success(updated);
    }







    private long syncUserFavoriteData(Long userId) {
        long syncedCount = 0;


        LambdaQueryWrapper<Playlist> playlistWrapper = new LambdaQueryWrapper<>();
        playlistWrapper.eq(Playlist::getUserId, userId)
                .eq(Playlist::getType, MusicConstants.PlaylistType.FAVORITE)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED);

        Playlist favoritePlaylist = playlistMapper.selectOne(playlistWrapper);


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
            requireSingleWrite(playlistMapper.insert(favoritePlaylist), "创建收藏歌单失败");
            if (favoritePlaylist.getId() == null) {
                throw new BusinessException("创建收藏歌单后未返回主键");
            }
            log.info("创建收藏歌单: userId={}, playlistId={}", userId, favoritePlaylist.getId());
        }


        LambdaQueryWrapper<SongLike> likeWrapper = new LambdaQueryWrapper<>();
        likeWrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getIsFavorite, 1)
                .select(SongLike::getSongId);

        List<SongLike> songLikes = songLikeMapper.selectList(likeWrapper);
        Set<Long> favoriteSongIds = songLikes.stream()
                .map(SongLike::getSongId)
                .collect(Collectors.toSet());

        log.info("用户收藏歌曲数(从song_like): userId={}, count={}", userId, favoriteSongIds.size());


        LambdaQueryWrapper<PlaylistSong> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(PlaylistSong::getPlaylistId, favoritePlaylist.getId())
                .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED);

        List<PlaylistSong> existingSongs = playlistSongMapper.selectList(existingWrapper);
        Set<Long> existingSongIds = existingSongs.stream()
                .map(PlaylistSong::getSongId)
                .collect(Collectors.toSet());

        log.info("歌单中现有歌曲数(从playlist_song): userId={}, count={}", userId, existingSongIds.size());


        Set<Long> toAdd = new HashSet<>(favoriteSongIds);
        toAdd.removeAll(existingSongIds);


        Set<Long> toRemove = new HashSet<>(existingSongIds);
        toRemove.removeAll(favoriteSongIds);

        log.info("需要同步: userId={}, toAdd={}, toRemove={}", userId, toAdd.size(), toRemove.size());


        int maxSortOrder = existingSongs.isEmpty() ? 0
            : existingSongs.stream()
                .mapToInt(PlaylistSong::getSortOrder)
                .max()
                .orElse(0);

        for (Long songId : toAdd) {

            String deleteOldSql = "DELETE FROM playlist_song WHERE playlist_id = ? AND song_id = ? AND deleted = 1";
            jdbcTemplate.update(deleteOldSql, favoritePlaylist.getId(), songId);


            LambdaQueryWrapper<PlaylistSong> checkWrapper = new LambdaQueryWrapper<>();
            checkWrapper.eq(PlaylistSong::getPlaylistId, favoritePlaylist.getId())
                    .eq(PlaylistSong::getSongId, songId)
                    .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED);

            if (playlistSongMapper.selectCount(checkWrapper) == 0) {
                PlaylistSong playlistSong = new PlaylistSong();
                playlistSong.setPlaylistId(favoritePlaylist.getId());
                playlistSong.setSongId(songId);
                playlistSong.setSortOrder(++maxSortOrder);
                playlistSong.setDeleted(CommonConstants.NOT_DELETED);
                requireSingleWrite(playlistSongMapper.insert(playlistSong), "添加收藏歌曲失败");
                syncedCount++;
                log.info("添加歌曲到收藏歌单: userId={}, songId={}", userId, songId);
            }
        }


        for (Long songId : toRemove) {
            LambdaQueryWrapper<PlaylistSong> removeWrapper = new LambdaQueryWrapper<>();
            removeWrapper.eq(PlaylistSong::getPlaylistId, favoritePlaylist.getId())
                    .eq(PlaylistSong::getSongId, songId)
                    .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED);

            PlaylistSong toDelete = playlistSongMapper.selectOne(removeWrapper);
            if (ObjectUtils.isNotEmpty(toDelete)) {

                LambdaQueryWrapper<PlaylistSong> deletedWrapper = new LambdaQueryWrapper<>();
                deletedWrapper.eq(PlaylistSong::getPlaylistId, favoritePlaylist.getId())
                        .eq(PlaylistSong::getSongId, songId)
                        .eq(PlaylistSong::getDeleted, CommonConstants.DELETED);

                PlaylistSong deleted = playlistSongMapper.selectOne(deletedWrapper);
                if (ObjectUtils.isNotEmpty(deleted)) {

                    String physicalDeleteSql = "DELETE FROM playlist_song WHERE id = ?";
                    requireSingleWrite(jdbcTemplate.update(physicalDeleteSql, deleted.getId()),
                            "清理收藏歌曲历史记录失败");
                }


                requireSingleWrite(playlistSongMapper.deleteById(toDelete.getId()), "移除收藏歌曲失败");
                syncedCount++;
                log.info("从收藏歌单移除歌曲: userId={}, songId={}", userId, songId);
            }
        }


        long newCount = playlistSongMapper.countVisibleSongs(
                favoritePlaylist.getId(), false, Collections.emptySet());
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
                }
            }
        } else {
            favoritePlaylist.setCover("/images/default-base.png");
        }

        requireSingleWrite(playlistMapper.update(null,
                new LambdaUpdateWrapper<Playlist>()
                        .eq(Playlist::getId, favoritePlaylist.getId())
                        .eq(Playlist::getUserId, userId)
                        .eq(Playlist::getType, MusicConstants.PlaylistType.FAVORITE)
                        .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                        .set(Playlist::getSongCount, newCount)
                        .set(Playlist::getCover, favoritePlaylist.getCover())),
                "更新收藏歌单汇总失败");

        log.info("用户收藏数据同步完成: userId={}, synced={}, newCount={}", userId, syncedCount, newCount);
        return syncedCount;
    }

    private void requireSingleWrite(int affected, String message) {
        if (affected != 1) {
            throw new BusinessException(message);
        }
    }
}

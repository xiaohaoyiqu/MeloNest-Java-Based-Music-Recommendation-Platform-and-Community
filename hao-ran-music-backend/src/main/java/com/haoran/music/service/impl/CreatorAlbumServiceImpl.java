package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.CreatorAlbum;
import com.haoran.music.entity.CreatorAlbumSong;
import com.haoran.music.entity.Song;
import com.haoran.music.mapper.CreatorAlbumMapper;
import com.haoran.music.mapper.CreatorAlbumSongMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.service.CreatorAlbumService;
import com.haoran.music.service.CreatorEligibilityService;
import com.haoran.music.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

   
                      
                          
   
@Slf4j
@Service
public class CreatorAlbumServiceImpl extends ServiceImpl<CreatorAlbumMapper, CreatorAlbum> implements CreatorAlbumService {

    @Resource
    private CreatorAlbumSongMapper albumSongMapper;

    @Resource
    private CreatorEligibilityService creatorEligibilityService;

    @Resource
    private PermissionService permissionService;

    @Resource
    private SongMapper songMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
public Long createAlbum(Long userId, CreatorAlbum album) {
        creatorEligibilityService.requireEligible(userId, "创建创作者专辑");
                    
        SecurityCheckUtil.CheckResult nameCheck = SecurityCheckUtil.checkAlbumName(album.getAlbumName());
        if (!nameCheck.isSafe()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, nameCheck.getMessage());
        }
        
                    
        if (ObjectUtils.isNotEmpty(album.getDescription())) {
            SecurityCheckUtil.CheckResult descCheck = SecurityCheckUtil.checkDescription(album.getDescription());
            if (!descCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, descCheck.getMessage());
            }
        }

        album.setUserId(userId);
        album.setStatus(0);        
        album.setSongCount(0);
        album.setTotalDuration(0);
        album.setSongCount(0);
        album.setTotalDuration(0);

        save(album);
        log.info("创建专辑成功, albumId: {}, userId: {}", album.getId(), userId);
        return album.getId();
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAlbum(Long albumId, Long userId, CreatorAlbum album) {
        creatorEligibilityService.requireEligible(userId, "修改创作者专辑");
        if (ObjectUtils.isEmpty(albumId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "专辑ID不能为空");
        }

        CreatorAlbum existingAlbum = getById(albumId);
        if (existingAlbum == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "专辑不存在");
        }

        if (!existingAlbum.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限修改此专辑");
        }

                    
        if (ObjectUtils.isNotEmpty(album.getAlbumName())) {
            SecurityCheckUtil.CheckResult nameCheck = SecurityCheckUtil.checkAlbumName(album.getAlbumName());
            if (!nameCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, nameCheck.getMessage());
            }
        }
        
                    
        if (ObjectUtils.isNotEmpty(album.getDescription())) {
            SecurityCheckUtil.CheckResult descCheck = SecurityCheckUtil.checkDescription(album.getDescription());
            if (!descCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, descCheck.getMessage());
            }
        }

        album.setId(albumId);
        updateById(album);
        log.info("更新专辑成功, albumId: {}, userId: {}", albumId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishAlbum(Long albumId, Long userId) {
                                                                               
                                                                              
                                                                             
        throw new BusinessException(ResultCode.FORBIDDEN,
                "旧版创作者专辑不支持直接发布，请通过作品投稿审核发布");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAlbum(Long albumId, Long userId) {
        creatorEligibilityService.requireEligible(userId, "删除创作者专辑");
        if (ObjectUtils.isEmpty(albumId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "专辑ID不能为空");
        }

        CreatorAlbum album = getById(albumId);
        if (album == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "专辑不存在");
        }

        if (!album.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限删除此专辑");
        }

                   
        albumSongMapper.delete(new LambdaQueryWrapper<CreatorAlbumSong>()
                .eq(CreatorAlbumSong::getAlbumId, albumId));

               
        removeById(albumId);
        log.info("删除专辑成功, albumId: {}, userId: {}", albumId, userId);
    }

    @Override
    public CreatorAlbum getAlbumDetail(Long albumId, Long viewerId) {
        if (ObjectUtils.isEmpty(albumId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "专辑ID不能为空");
        }

        CreatorAlbum album = getById(albumId);
        if (album == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "专辑不存在");
        }
        requireAlbumVisible(album, viewerId);

        return album;
    }

    @Override
    public IPage<CreatorAlbum> getMyAlbums(Long userId, PageQuery pageQuery) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }

        Page<CreatorAlbum> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());

        return page(page, new LambdaQueryWrapper<CreatorAlbum>()
                .eq(CreatorAlbum::getUserId, userId)
                .orderByDesc(CreatorAlbum::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSongsToAlbum(Long albumId, Long userId, List<Long> songIds) {
        creatorEligibilityService.requireEligible(userId, "管理创作者专辑歌曲");
        if (ObjectUtils.isEmpty(albumId) || ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "参数不能为空");
        }
        if (songIds == null || songIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲ID不能为空");
        }
        if (songIds.size() > 100) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多添加100首歌曲");
        }
        LinkedHashSet<Long> distinctSongIds = new LinkedHashSet<>();
        for (Long songId : songIds) {
            if (songId == null || songId <= 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲ID不合法");
            }
            distinctSongIds.add(songId);
        }

        Map<Long, Song> songsById = songMapper.selectBatchIds(distinctSongIds).stream()
                .collect(Collectors.toMap(Song::getId, Function.identity()));
        for (Long songId : distinctSongIds) {
            Song song = songsById.get(songId);
            if (song == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲不存在: " + songId);
            }
            if (!userId.equals(song.getUploaderId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能添加本人上传的歌曲");
            }
        }

        CreatorAlbum album = getById(albumId);
        if (album == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "专辑不存在");
        }

        if (!album.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限操作此专辑");
        }

        Set<Long> existingSongIds = albumSongMapper.selectList(new LambdaQueryWrapper<CreatorAlbumSong>()
                        .eq(CreatorAlbumSong::getAlbumId, albumId)
                        .in(CreatorAlbumSong::getSongId, distinctSongIds))
                .stream()
                .map(CreatorAlbumSong::getSongId)
                .collect(Collectors.toSet());
        List<Long> newSongIds = distinctSongIds.stream()
                .filter(songId -> !existingSongIds.contains(songId))
                .collect(Collectors.toList());

        int currentSongCount = album.getSongCount() == null ? 0 : album.getSongCount();
        int position = currentSongCount + 1;
        for (Long songId : newSongIds) {
            CreatorAlbumSong albumSong = new CreatorAlbumSong();
            albumSong.setAlbumId(albumId);
            albumSong.setSongId(songId);
            albumSong.setPosition(position++);
            albumSong.setIsSingle(0);
            albumSong.setSource("creator_upload");
            albumSongMapper.insert(albumSong);
        }

                 
        if (!newSongIds.isEmpty()) {
            album.setSongCount(currentSongCount + newSongIds.size());
            updateById(album);
        }

        log.info("添加歌曲到专辑成功, albumId: {}, songCount: {}", albumId, newSongIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSongFromAlbum(Long albumId, Long songId, Long userId) {
        creatorEligibilityService.requireEligible(userId, "管理创作者专辑歌曲");
        if (ObjectUtils.isEmpty(albumId) || ObjectUtils.isEmpty(songId) || ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "参数不能为空");
        }

        CreatorAlbum album = getById(albumId);
        if (album == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "专辑不存在");
        }

        if (!album.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限操作此专辑");
        }

        albumSongMapper.delete(new LambdaQueryWrapper<CreatorAlbumSong>()
                .eq(CreatorAlbumSong::getAlbumId, albumId)
                .eq(CreatorAlbumSong::getSongId, songId));

                 
        if (album.getSongCount() > 0) {
            album.setSongCount(album.getSongCount() - 1);
            updateById(album);
        }

        log.info("从专辑移除歌曲成功, albumId: {}, songId: {}", albumId, songId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSongPosition(Long albumId, Long songId, Integer newPosition, Long userId) {
        creatorEligibilityService.requireEligible(userId, "调整创作者专辑曲序");
        if (ObjectUtils.isEmpty(albumId) || ObjectUtils.isEmpty(songId) || ObjectUtils.isEmpty(newPosition)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "参数不能为空");
        }

        CreatorAlbum album = getById(albumId);
        if (album == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "专辑不存在");
        }

        if (!album.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限操作此专辑");
        }

        CreatorAlbumSong albumSong = albumSongMapper.selectOne(new LambdaQueryWrapper<CreatorAlbumSong>()
                .eq(CreatorAlbumSong::getAlbumId, albumId)
                .eq(CreatorAlbumSong::getSongId, songId));

        if (albumSong != null) {
            albumSong.setPosition(newPosition);
            albumSongMapper.updateById(albumSong);
        }

        log.info("更新歌曲位置成功, albumId: {}, songId: {}, newPosition: {}", albumId, songId, newPosition);
    }

    @Override
    public List<CreatorAlbumSong> getAlbumSongs(Long albumId, Long viewerId) {
        if (ObjectUtils.isEmpty(albumId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "专辑ID不能为空");
        }

        CreatorAlbum album = getById(albumId);
        if (album == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "专辑不存在");
        }
        requireAlbumVisible(album, viewerId);

        return albumSongMapper.selectList(new LambdaQueryWrapper<CreatorAlbumSong>()
                .eq(CreatorAlbumSong::getAlbumId, albumId)
                .orderByAsc(CreatorAlbumSong::getPosition));
    }

    private void requireAlbumVisible(CreatorAlbum album, Long viewerId) {
        if (Integer.valueOf(1).equals(album.getStatus())) {
            return;
        }
        if (viewerId != null && viewerId.equals(album.getUserId())) {
            return;
        }
        if (viewerId != null && permissionService.isModerator(viewerId)) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权限查看未发布专辑");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadSongToAlbum(Long albumId, Long userId, CreatorAlbumSong albumSong) {
        creatorEligibilityService.requireEligible(userId, "上传创作者专辑歌曲");
        if (ObjectUtils.isEmpty(albumId) || ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "参数不能为空");
        }

        CreatorAlbum album = getById(albumId);
        if (album == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "专辑不存在");
        }

        if (!album.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限操作此专辑");
        }

        albumSong.setAlbumId(albumId);
        albumSong.setIsSingle(0);
        albumSong.setSource("creator_upload");

        albumSongMapper.insert(albumSong);

                       
        if (albumSong.getDuration() != null) {
            album.setTotalDuration(album.getTotalDuration() + albumSong.getDuration());
        }
        album.setSongCount(album.getSongCount() + 1);
        updateById(album);

        log.info("上传歌曲到专辑成功, albumId: {}, songId: {}", albumId, albumSong.getId());
        return albumSong.getId();
    }
}

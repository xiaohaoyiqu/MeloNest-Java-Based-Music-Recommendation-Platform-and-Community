package com.haoran.music.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ConvertHelper;
import com.haoran.music.common.util.CatalogSearchInput;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.common.util.UrlHelper;
import com.haoran.music.dto.song.SongVO;
import com.haoran.music.entity.Album;
import com.haoran.music.entity.AlbumFavorite;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.*;
import com.haoran.music.service.AlbumService;
import com.haoran.music.service.ContentAccessService;
import com.haoran.music.service.search.SearchIndexService;
import com.haoran.music.vo.album.AlbumVO;
import com.haoran.music.common.enums.CountryCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

   
                      
                       
   
@Slf4j
@Service
public class AlbumServiceImpl extends ServiceImpl<AlbumMapper, Album> implements AlbumService {

    @Resource
    private SongMapper songMapper;

    @Resource
    private PlaylistMapper playlistMapper;

    @Resource
    private PlaylistSongMapper playlistSongMapper;

    @Resource
    private ArtistMapper artistMapper;

    @Resource
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Resource
    private AlbumFavoriteMapper albumFavoriteMapper;

    @Autowired(required = false)
    private FavoriteCollectionItemMapper favoriteCollectionItemMapper;

    @Resource
    private SearchIndexService searchIndexService;

    @Resource
    private com.haoran.music.mapper.UserMapper userMapper;

    @Resource
    private ContentAccessService contentAccessService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Album album) {
        boolean result = super.save(album);
        if (result && album != null) {
            searchIndexService.sync("album", album.getId());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(Album album) {
        boolean result = super.updateById(album);
        if (result && album != null && album.getId() != null) {
            searchIndexService.sync("album", album.getId());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(java.io.Serializable id) {
        boolean result = super.removeById(id);
        if (result && id != null) {
            searchIndexService.sync("album", Long.valueOf(String.valueOf(id)));
        }
        return result;
    }

    @Override
    public AlbumVO getAlbumById(Long albumId, Long userId) {
        if (ObjectUtils.isEmpty(albumId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "专辑ID不能为空");
        }

        Album album = getById(albumId);
        if (ObjectUtils.isEmpty(album)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "专辑不存在");
        }

        contentAccessService.requireAlbumMetadataAccess(album, userId);

        AlbumVO vo = convertToVO(album);

                   
        LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<>();
        songWrapper.eq(Song::getAlbumId, albumId)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .orderByAsc(Song::getAlbumTrackNo, Song::getReleaseDate,
                        Song::getCreateTime, Song::getId);

        List<Song> songs = filterPublicUploaderSongs(songMapper.selectList(songWrapper));
        if (songs.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "专辑暂无可公开歌曲");
        }

                    
        List<AlbumVO.SongSimpleVO> songSimpleList = ConvertHelper.toVOList(songs, song -> {
            AlbumVO.SongSimpleVO simpleVO = new AlbumVO.SongSimpleVO();
            simpleVO.setId(song.getId());
            simpleVO.setName(song.getName());
            simpleVO.setDuration(song.getDuration());
            simpleVO.setMainType(song.getMainType());
            simpleVO.setCover(song.getCover());
            simpleVO.setUrlStandard(UrlHelper.buildRelativeAudioUrl(song.getUrlStandard()));
            simpleVO.setUrlHigh(UrlHelper.buildRelativeAudioUrl(song.getUrlHigh()));
            simpleVO.setUrlLossless(UrlHelper.buildRelativeAudioUrl(song.getUrlLossless()));
            simpleVO.setSizeStandard(song.getSizeStandard());
            simpleVO.setSizeHigh(song.getSizeHigh());
            simpleVO.setSizeLossless(song.getSizeLossless());
            simpleVO.setAlbumId(albumId);
            simpleVO.setAlbumName(album.getName());
            simpleVO.setAlbumTrackNo(song.getAlbumTrackNo());
            simpleVO.setIsSingle(song.getIsSingle());
            simpleVO.setArtistIds(song.getArtistIds());
            simpleVO.setArtistNames(song.getArtistNames());

            return simpleVO;
        });

        vo.setSongs(songSimpleList);

        if (ObjectUtils.isNotEmpty(userId)) {
            vo.setIsFavorite(checkIsFavorite(userId, albumId));
        }

        return vo;
    }

       
                        
      
                            
                           
                       
                        
                                         
                         
                     
       
    @Override
    public IPage<AlbumVO> pageAlbums(PageQuery pageQuery, String keyword, String language, String area, String genre, String sortBy, Long userId) {
        Page<Album> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        String safeKeyword = CatalogSearchInput.normalizeForLike(keyword);

        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Album::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Album::getDeleted, CommonConstants.NOT_DELETED);

        if (StrUtil.isNotBlank(safeKeyword)) {
            wrapper.and(group -> group.like(Album::getName, safeKeyword)
                    .or()
                    .like(Album::getArtistNames, safeKeyword));
        }

               
        if (StrUtil.isNotBlank(language)) {
            wrapper.eq(Album::getLanguage, language);
        }

                                    
        if (StrUtil.isNotBlank(area)) {
                                               
            if ("内地".equals(area) || "港台".equals(area)) {
                List<String> chineseCountries = CountryCode.getNamesByRegion(area);
                wrapper.and(group -> group.eq(Album::getRegion, area)
                        .or(!chineseCountries.isEmpty())
                        .in(!chineseCountries.isEmpty(), Album::getCountry, chineseCountries));
            } else if ("欧美".equals(area)) {
                List<String> westernCountries = CountryCode.getWesternNames();
                wrapper.and(group -> group.eq(Album::getRegion, area)
                        .or(!westernCountries.isEmpty())
                        .in(!westernCountries.isEmpty(), Album::getCountry, westernCountries));
            } else {
                wrapper.and(group -> group.eq(Album::getRegion, area)
                        .or()
                        .eq(Album::getCountry, area));
            }
        }

               
        if (StrUtil.isNotBlank(genre)) {
            wrapper.like(Album::getGenres, genre);
        }

                                 
        if ("new".equals(sortBy)) {
                           
            wrapper.orderByDesc(Album::getReleaseDate);
        } else if ("hot".equals(sortBy)) {
                              
            wrapper.orderByDesc(Album::getPlayCount, Album::getFavoriteCount);
        } else {
                   
            handleSort(wrapper, pageQuery.getSortField(), pageQuery.getSortOrder());
        }

        IPage<Album> albumPage = page(page, wrapper);
        albumPage.setRecords(filterAlbumsWithPublicSongs(albumPage.getRecords()));

                   
        Set<Long> favoriteAlbumIds = Collections.emptySet();
        if (ObjectUtils.isNotEmpty(userId) && !albumPage.getRecords().isEmpty()) {
            favoriteAlbumIds = getFavoriteAlbumIds(userId, albumPage.getRecords());
        }

                                     
        IPage<AlbumVO> voPage = ConvertHelper.toVOPage(albumPage, this::convertToVO);
        ConvertHelper.setFieldFromSet(voPage.getRecords(), AlbumVO::getId, favoriteAlbumIds, AlbumVO::setIsFavorite);

        return voPage;
    }

    @Override
    public List<AlbumVO> getAlbumsByArtist(Long artistId, Long userId) {
        if (ObjectUtils.isEmpty(artistId)) {
            return new ArrayList<>();
        }

                               
        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(artist -> artist.eq(Album::getArtistId, artistId)
                        .or()
                        .apply("FIND_IN_SET({0}, artist_ids) > 0", artistId))
                .eq(Album::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Album::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Album::getReleaseDate);

        List<Album> albums = filterAlbumsWithPublicSongs(list(wrapper));

                   
        Set<Long> favoriteAlbumIds = Collections.emptySet();
        if (ObjectUtils.isNotEmpty(userId) && !albums.isEmpty()) {
            favoriteAlbumIds = getFavoriteAlbumIds(userId, albums);
        }

                                     
        List<AlbumVO> voList = ConvertHelper.toVOList(albums, this::convertToVO);
        ConvertHelper.setFieldFromSet(voList, AlbumVO::getId, favoriteAlbumIds, AlbumVO::setIsFavorite);

        return voList;
    }

    @Override
    public IPage<AlbumVO> getNewAlbums(PageQuery pageQuery, Long userId) {
        Page<Album> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());

                        
        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Album::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Album::getDeleted, CommonConstants.NOT_DELETED)
                .ge(Album::getReleaseDate, java.time.LocalDate.now().minusDays(30))
                .orderByDesc(Album::getReleaseDate);

        IPage<Album> albumPage = page(page, wrapper);
        albumPage.setRecords(filterAlbumsWithPublicSongs(albumPage.getRecords()));

                   
        Set<Long> favoriteAlbumIds = Collections.emptySet();
        if (ObjectUtils.isNotEmpty(userId) && !albumPage.getRecords().isEmpty()) {
            favoriteAlbumIds = getFavoriteAlbumIds(userId, albumPage.getRecords());
        }

                                     
        IPage<AlbumVO> voPage = ConvertHelper.toVOPage(albumPage, this::convertToVO);
        ConvertHelper.setFieldFromSet(voPage.getRecords(), AlbumVO::getId, favoriteAlbumIds, AlbumVO::setIsFavorite);

        return voPage;
    }

    @Override
    public List<AlbumVO> getHotAlbums(String type, Integer limit, Long userId) {
        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Album::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Album::getDeleted, CommonConstants.NOT_DELETED);
        
                                     
        if (StrUtil.isNotBlank(type)) {
            wrapper.eq(Album::getType, type);
        }
        
        int actualLimit = limit != null && limit > 0 ? limit : 20;
        wrapper.orderByDesc(Album::getPlayCount, Album::getFavoriteCount)
                .last("LIMIT " + expandedPublicQueryLimit(actualLimit));

        List<Album> albums = filterAlbumsWithPublicSongs(list(wrapper)).stream()
                .limit(actualLimit)
                .collect(Collectors.toList());

                   
        Set<Long> favoriteAlbumIds = Collections.emptySet();
        if (ObjectUtils.isNotEmpty(userId) && !albums.isEmpty()) {
            favoriteAlbumIds = getFavoriteAlbumIds(userId, albums);
        }

                                     
        List<AlbumVO> voList = ConvertHelper.toVOList(albums, this::convertToVO);
        ConvertHelper.setFieldFromSet(voList, AlbumVO::getId, favoriteAlbumIds, AlbumVO::setIsFavorite);

        return voList;
    }

       
           
      
             
                               
                            
                                            
      
                         
                          
                   
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean favoriteAlbum(Long userId, Long albumId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(albumId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        User user = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(user, "收藏专辑");

                   
        Album album = getById(albumId);
        if (ObjectUtils.isEmpty(album)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "专辑不存在");
        }

                           
        LambdaQueryWrapper<AlbumFavorite> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.eq(AlbumFavorite::getUserId, userId)
                .eq(AlbumFavorite::getAlbumId, albumId)
                .eq(AlbumFavorite::getDeleted, CommonConstants.NOT_DELETED);

        AlbumFavorite activeRecord = albumFavoriteMapper.selectOne(activeWrapper);
        if (activeRecord != null) {
                       
            log.debug("专辑已收藏，跳过: userId={}, albumId={}", userId, albumId);
            return true;
        }

                                  
        LambdaQueryWrapper<AlbumFavorite> deletedWrapper = new LambdaQueryWrapper<>();
        deletedWrapper.eq(AlbumFavorite::getUserId, userId)
                .eq(AlbumFavorite::getAlbumId, albumId)
                .eq(AlbumFavorite::getDeleted, CommonConstants.DELETED);

        AlbumFavorite deletedRecord = albumFavoriteMapper.selectOne(deletedWrapper);

        if (deletedRecord != null) {
                                               
            String restoreSql = "UPDATE album_favorite SET deleted = 0, update_time = NOW() WHERE id = ?";
            jdbcTemplate.update(restoreSql, deletedRecord.getId());
            log.info("恢复专辑收藏记录: userId={}, albumId={}, id={}", userId, albumId, deletedRecord.getId());
        } else {
                          
            AlbumFavorite albumFavorite = new AlbumFavorite();
            albumFavorite.setUserId(userId);
            albumFavorite.setAlbumId(albumId);
            albumFavorite.setDeleted(CommonConstants.NOT_DELETED);
            albumFavoriteMapper.insert(albumFavorite);
            log.info("创建专辑收藏记录: userId={}, albumId={}", userId, albumId);
        }

        if (UserAccountStatusUtil.canContributePublicStats(user)) {
                      
            album.setFavoriteCount((album.getFavoriteCount() != null ? album.getFavoriteCount() : 0) + 1);
            updateById(album);
        }

        log.info("用户收藏专辑: userId={}, albumId={}", userId, albumId);
        return true;
    }

       
             
      
             
                        
                            
                             
                     
      
                         
                          
                   
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unfavoriteAlbum(Long userId, Long albumId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(albumId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

                             
        LambdaQueryWrapper<AlbumFavorite> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.eq(AlbumFavorite::getUserId, userId)
                .eq(AlbumFavorite::getAlbumId, albumId)
                .eq(AlbumFavorite::getDeleted, CommonConstants.NOT_DELETED);

        AlbumFavorite activeRecord = albumFavoriteMapper.selectOne(activeWrapper);
        if (activeRecord == null) {
            removeFavoriteGrouping(userId, "album", albumId);
                       
            log.debug("专辑未收藏，跳过: userId={}, albumId={}", userId, albumId);
            return true;
        }

                                       
        LambdaQueryWrapper<AlbumFavorite> deletedWrapper = new LambdaQueryWrapper<>();
        deletedWrapper.eq(AlbumFavorite::getUserId, userId)
                .eq(AlbumFavorite::getAlbumId, albumId)
                .eq(AlbumFavorite::getDeleted, CommonConstants.DELETED);

        AlbumFavorite deletedRecord = albumFavoriteMapper.selectOne(deletedWrapper);

        if (deletedRecord != null) {
                                
            String physicalDeleteSql = "DELETE FROM album_favorite WHERE id = ?";
            jdbcTemplate.update(physicalDeleteSql, deletedRecord.getId());
            log.info("物理删除旧的历史记录: userId={}, albumId={}, deletedId={}", userId, albumId, deletedRecord.getId());
        }

                                                      
        albumFavoriteMapper.deleteById(activeRecord.getId());
        removeFavoriteGrouping(userId, "album", albumId);
        log.info("逻辑删除专辑收藏记录: userId={}, albumId={}, id={}", userId, albumId, activeRecord.getId());

                  
        Album album = getById(albumId);
        if (ObjectUtils.isNotEmpty(album)
                && UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            album.setFavoriteCount(Math.max(0, (album.getFavoriteCount() != null ? album.getFavoriteCount() : 0) - 1));
            updateById(album);
        }

        log.info("用户取消收藏专辑: userId={}, albumId={}", userId, albumId);
        return true;
    }

    private void removeFavoriteGrouping(Long userId, String resourceType, Long resourceId) {
        if (favoriteCollectionItemMapper != null) {
            favoriteCollectionItemMapper.deleteResource(userId, resourceType, resourceId);
        }
    }

    @Override
    public List<SongVO> getAlbumSongs(Long albumId, Long userId) {
        if (ObjectUtils.isEmpty(albumId)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getAlbumId, albumId)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .orderByAsc(Song::getAlbumTrackNo, Song::getReleaseDate,
                        Song::getCreateTime, Song::getId);

        List<Song> songs = filterPublicUploaderSongs(songMapper.selectList(wrapper));

                   
        Set<Long> favoriteSongIds = Collections.emptySet();
        if (ObjectUtils.isNotEmpty(userId) && !songs.isEmpty()) {
            favoriteSongIds = getFavoriteSongIdsForSongs(userId, songs);
        }

                                     
        List<SongVO> voList = ConvertHelper.toVOList(songs, this::convertSongToVO);
        ConvertHelper.setFieldFromSet(voList, SongVO::getId, favoriteSongIds, SongVO::setIsFavorite);

        return voList;
    }

       
                  
      
                         
                      
       
    @Override
    public List<AlbumVO> getUserFavoriteAlbums(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }

                                         
        LambdaQueryWrapper<AlbumFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlbumFavorite::getUserId, userId)
                .eq(AlbumFavorite::getDeleted, 0)
                .orderByDesc(AlbumFavorite::getCreateTime);

        List<AlbumFavorite> favorites = albumFavoriteMapper.selectList(wrapper);
        if (CollUtil.isEmpty(favorites)) {
            return new ArrayList<>();
        }

                 
        List<Long> albumIds = favorites.stream()
                .map(AlbumFavorite::getAlbumId)
                .collect(Collectors.toList());

                   
        LambdaQueryWrapper<Album> albumWrapper = new LambdaQueryWrapper<>();
        albumWrapper.in(Album::getId, albumIds)
                .eq(Album::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Album::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Album::getFavoriteCount);

        List<Album> albums = filterAlbumsWithPublicSongs(list(albumWrapper));

                          
        List<AlbumVO> voList = ConvertHelper.toVOList(albums, this::convertToVO);
        voList.forEach(vo -> vo.setIsFavorite(true));

        return voList;
    }

       
                      
      
                         
                         
                        
       
    private Set<Long> getFavoriteAlbumIds(Long userId, List<Album> albums) {
        if (ObjectUtils.isEmpty(userId) || albums.isEmpty()) {
            return Collections.emptySet();
        }

                                   
        List<Long> albumIds = ConvertHelper.extractIds(albums, Album::getId);

        LambdaQueryWrapper<AlbumFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlbumFavorite::getUserId, userId)
                .in(AlbumFavorite::getAlbumId, albumIds)
                .eq(AlbumFavorite::getDeleted, CommonConstants.NOT_DELETED)
                .select(AlbumFavorite::getAlbumId);

        List<AlbumFavorite> favorites = albumFavoriteMapper.selectList(wrapper);
        return ConvertHelper.extractIdSet(favorites, AlbumFavorite::getAlbumId);
    }

       
                                
      
                         
                        
                        
       
    private Set<Long> getFavoriteSongIdsForSongs(Long userId, List<Song> songs) {
        Long favoritePlaylistId = getFavoritePlaylistId(userId);
        if (favoritePlaylistId == null) {
            return Collections.emptySet();
        }

        if (songs.isEmpty()) {
            return Collections.emptySet();
        }

                              
        List<Long> songIds = ConvertHelper.extractIds(songs, Song::getId);

        LambdaQueryWrapper<com.haoran.music.entity.PlaylistSong> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.haoran.music.entity.PlaylistSong::getPlaylistId, favoritePlaylistId)
                .in(com.haoran.music.entity.PlaylistSong::getSongId, songIds)
                .eq(com.haoran.music.entity.PlaylistSong::getDeleted, CommonConstants.NOT_DELETED)
                .select(com.haoran.music.entity.PlaylistSong::getSongId);

        List<com.haoran.music.entity.PlaylistSong> playlistSongs = playlistSongMapper.selectList(wrapper);

        return ConvertHelper.extractIdSet(playlistSongs,
                com.haoran.music.entity.PlaylistSong::getSongId);
    }

       
                 
      
                         
                     
       
    private Long getFavoritePlaylistId(Long userId) {
        LambdaQueryWrapper<com.haoran.music.entity.Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.haoran.music.entity.Playlist::getUserId, userId)
                .eq(com.haoran.music.entity.Playlist::getType, 0)
                .eq(com.haoran.music.entity.Playlist::getDeleted, CommonConstants.NOT_DELETED)
                .select(com.haoran.music.entity.Playlist::getId);

        com.haoran.music.entity.Playlist favoritePlaylist = playlistMapper.selectOne(wrapper);
        return favoritePlaylist != null ? favoritePlaylist.getId() : null;
    }

       
                              
      
                          
                          
                                 
       
    private Boolean checkIsFavorite(Long userId, Long albumId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(albumId)) {
            return false;
        }

                                   
        LambdaQueryWrapper<AlbumFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlbumFavorite::getUserId, userId)
                .eq(AlbumFavorite::getAlbumId, albumId)
                .eq(AlbumFavorite::getDeleted, CommonConstants.NOT_DELETED);

        Long count = albumFavoriteMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

       
           
      
                            
                            
                            
       
    private void handleSort(LambdaQueryWrapper<Album> wrapper, String sortField, String sortOrder) {
        if (StrUtil.isBlank(sortField)) {
            wrapper.orderByDesc(Album::getCreateTime);
            return;
        }

        boolean isAsc = !"desc".equalsIgnoreCase(sortOrder);

        switch (sortField) {
            case "time":
                wrapper.orderBy(true, isAsc, Album::getCreateTime);
                break;
            case "hot":
                wrapper.orderBy(true, isAsc, Album::getPlayCount);
                break;
            case "name":
                wrapper.orderBy(true, isAsc, Album::getName);
                break;
            case "releaseTime":
                wrapper.orderBy(true, isAsc, Album::getReleaseDate);
                break;
            case "playCount":
                wrapper.orderBy(true, isAsc, Album::getPlayCount);
                break;
            default:
                wrapper.orderByDesc(Album::getCreateTime);
                break;
        }
    }

       
            
      
                        
                   
       
    private AlbumVO convertToVO(Album album) {
        AlbumVO vo = BeanUtil.copyProperties(album, AlbumVO.class);
                                    
                     
        if (ObjectUtils.isNotEmpty(vo.getCover())) {
            vo.setCover(UrlHelper.buildRelativeCoverUrl(vo.getCover()));
        } else {
            vo.setCover("/default-cover.png");
        }

                                
           
        if (StrUtil.isNotBlank(album.getArtistIds())) {
            String[] ids = album.getArtistIds().split(",");
            if (ids.length > 0) {
                try {
                    Long artistId = Long.valueOf(ids[0].trim());
                    vo.setArtistId(artistId);
                              
                    com.haoran.music.entity.Artist artist = artistMapper.selectById(artistId);
                    if (artist != null) {
                        if (StrUtil.isNotBlank(artist.getAvatar())) {
                            vo.setArtistAvatar(UrlHelper.buildRelativeCoverUrl(artist.getAvatar()));
                        } else {
                            vo.setArtistAvatar("/default-avatar.png");
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析artistId失败: artistIds={}", album.getArtistIds());
                }
            }
        }

        return vo;
    }

       
                   
      
                       
                   
       
    private SongVO convertSongToVO(Song song) {
        SongVO vo = BeanUtil.copyProperties(song, SongVO.class);
                                    
        if (ObjectUtils.isNotEmpty(vo.getCover())) {
            vo.setCover(UrlHelper.buildRelativeCoverUrl(vo.getCover()));
        }
        return vo;
    }

       
                   
      
                        
                    
       
    @Override
    public List<AlbumVO> getNewAlbums(Integer limit) {
        int actualLimit = limit != null ? limit : 50;

        LocalDate newAlbumDate = LocalDate.now().minusDays(CommonConstants.NEW_SONG_DAYS);
        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Album::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Album::getDeleted, CommonConstants.NOT_DELETED)
                .ge(Album::getReleaseDate, newAlbumDate)
                .orderByDesc(Album::getReleaseDate, Album::getCreateTime)
                .last("LIMIT " + expandedPublicQueryLimit(actualLimit));

        List<Album> albums = filterAlbumsWithPublicSongs(list(wrapper)).stream()
                .limit(actualLimit)
                .collect(Collectors.toList());
        return ConvertHelper.toVOList(albums, this::convertToVO);
    }

       
               
                            
      
                          
                          
                     
       
    @Override
    public List<AlbumVO> getSimilarAlbums(Long albumId, Integer limit) {
        if (ObjectUtils.isEmpty(albumId)) {
            return new ArrayList<>();
        }

        int actualLimit = limit != null && limit > 0 ? limit : 10;

                  
        Album originalAlbum = getById(albumId);
        if (originalAlbum == null) {
            return new ArrayList<>();
        }

        Set<Long> albumIds = new LinkedHashSet<>();

                            
        if (StrUtil.isNotBlank(originalAlbum.getLanguage()) && albumIds.size() < actualLimit) {
            LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Album::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Album::getDeleted, CommonConstants.NOT_DELETED)
                    .ne(Album::getId, albumId)
                    .eq(Album::getLanguage, originalAlbum.getLanguage())
                    .orderByDesc(Album::getFavoriteCount, Album::getReleaseDate)
                    .last("LIMIT " + expandedPublicQueryLimit(actualLimit - albumIds.size()));

            List<Album> sameLanguageAlbums = filterAlbumsWithPublicSongs(list(wrapper));
            for (Album a : sameLanguageAlbums) {
                if (albumIds.size() >= actualLimit) break;
                albumIds.add(a.getId());
            }
        }

                            
        if (StrUtil.isNotBlank(originalAlbum.getGenres()) && albumIds.size() < actualLimit) {
            String[] genres = originalAlbum.getGenres().split(",");
            for (String genre : genres) {
                if (albumIds.size() >= actualLimit) break;

                LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Album::getStatus, CommonConstants.STATUS_NORMAL)
                        .eq(Album::getDeleted, CommonConstants.NOT_DELETED)
                        .ne(Album::getId, albumId)
                        .notIn(Album::getId, albumIds)
                        .like(Album::getGenres, genre.trim())
                        .orderByDesc(Album::getFavoriteCount)
                        .last("LIMIT " + expandedPublicQueryLimit(actualLimit - albumIds.size()));

                List<Album> sameGenreAlbums = filterAlbumsWithPublicSongs(list(wrapper));
                for (Album a : sameGenreAlbums) {
                    if (albumIds.size() >= actualLimit) break;
                    albumIds.add(a.getId());
                }
            }
        }

                               
        if (StrUtil.isNotBlank(originalAlbum.getArtistIds()) && albumIds.size() < actualLimit) {
            String[] artistIds = originalAlbum.getArtistIds().split(",");
            for (String artistId : artistIds) {
                if (albumIds.size() >= actualLimit) break;

                LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Album::getStatus, CommonConstants.STATUS_NORMAL)
                        .eq(Album::getDeleted, CommonConstants.NOT_DELETED)
                        .ne(Album::getId, albumId)
                        .notIn(Album::getId, albumIds)
                        .apply("FIND_IN_SET({0}, artist_ids) > 0", artistId.trim())
                        .orderByDesc(Album::getReleaseDate)
                        .last("LIMIT " + expandedPublicQueryLimit(Math.min(3, actualLimit - albumIds.size())));

                List<Album> artistAlbums = filterAlbumsWithPublicSongs(list(wrapper));
                for (Album a : artistAlbums) {
                    if (albumIds.size() >= actualLimit) break;
                    albumIds.add(a.getId());
                }
            }
        }

        if (albumIds.isEmpty()) {
            return new ArrayList<>();
        }

                 
        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Album::getId, albumIds)
                .eq(Album::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Album::getDeleted, CommonConstants.NOT_DELETED);

        List<Album> albums = filterAlbumsWithPublicSongs(list(wrapper));
        return ConvertHelper.toVOList(albums, this::convertToVO);
    }

       
                   
      
                          
                          
                        
       
    @Override
    public List<AlbumVO> getArtistOtherAlbums(Long albumId, Integer limit) {
        if (ObjectUtils.isEmpty(albumId)) {
            return new ArrayList<>();
        }

        int actualLimit = limit != null && limit > 0 ? limit : 10;

                  
        Album originalAlbum = getById(albumId);
        if (originalAlbum == null || StrUtil.isBlank(originalAlbum.getArtistIds())) {
            return new ArrayList<>();
        }

                           
        String[] artistIds = originalAlbum.getArtistIds().split(",");
        if (artistIds.length == 0) {
            return new ArrayList<>();
        }

        Long primaryArtistId;
        try {
            primaryArtistId = Long.valueOf(artistIds[0].trim());
        } catch (NumberFormatException e) {
            return new ArrayList<>();
        }

                      
        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Album::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Album::getDeleted, CommonConstants.NOT_DELETED)
                .ne(Album::getId, albumId)
                .apply("FIND_IN_SET({0}, artist_ids) > 0", primaryArtistId)
                .orderByDesc(Album::getReleaseDate, Album::getFavoriteCount)
                .last("LIMIT " + expandedPublicQueryLimit(actualLimit));

        List<Album> albums = filterAlbumsWithPublicSongs(list(wrapper)).stream()
                .limit(actualLimit)
                .collect(Collectors.toList());
        return ConvertHelper.toVOList(albums, this::convertToVO);
    }

    private int expandedPublicQueryLimit(int limit) {
        return limit > 0 ? Math.min(limit * 3, 300) : limit;
    }

    private List<Album> filterAlbumsWithPublicSongs(List<Album> albums) {
        if (albums == null || albums.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> albumIds = albums.stream()
                .map(Album::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> publicAlbumIds = getAlbumIdsWithPublicSongs(albumIds);
        return albums.stream()
                .filter(album -> publicAlbumIds.contains(album.getId()))
                .collect(Collectors.toList());
    }

    private Set<Long> getAlbumIdsWithPublicSongs(Collection<Long> albumIds) {
        if (albumIds == null || albumIds.isEmpty()) {
            return Collections.emptySet();
        }

        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getAlbumId, albumIds)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .select(Song::getAlbumId, Song::getUploaderId, Song::getStatus, Song::getDeleted);

        return filterPublicUploaderSongs(songMapper.selectList(wrapper)).stream()
                .map(Song::getAlbumId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private List<Song> filterPublicUploaderSongs(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> uploaderIds = songs.stream()
                .map(Song::getUploaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allowedUploaderIds = UserAccountStatusUtil.filterPublicContentUserIds(
                uploaderIds,
                ids -> userMapper.selectBatchIds(ids)
        );

        return songs.stream()
                .filter(song -> CommonConstants.STATUS_NORMAL.equals(song.getStatus()))
                .filter(song -> CommonConstants.NOT_DELETED.equals(song.getDeleted()))
                .filter(song -> song.getUploaderId() == null || allowedUploaderIds.contains(song.getUploaderId()))
                .collect(Collectors.toList());
    }
}

   
                      
   
package com.haoran.music.service.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.MusicConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UrlHelper;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.vo.search.SearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MysqlSearchEngineAdapter implements SearchEngineAdapter {

    @Resource
    private SongMapper songMapper;

    @Resource
    private AlbumMapper albumMapper;

    @Resource
    private ArtistMapper artistMapper;

    @Resource
    private PlaylistMapper playlistMapper;

    @Resource
    private MVMapper mvMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PlaylistSongMapper playlistSongMapper;

    private static final int SEARCH_LIMIT = 20;

    @Override
    public String engineName() {
        return "mysql";
    }

    @Override
    public SearchResultVO search(String keyword, Long userId) {
        SearchResultVO result = new SearchResultVO();
        result.setKeyword(keyword);
        result.setSongs(searchSongsInternal(keyword, userId));
        result.setAlbums(searchAlbumsInternal(keyword, userId));
        result.setArtists(searchArtistsInternal(keyword, userId));
        result.setPlaylists(searchPlaylistsInternal(keyword, userId));
        result.setMvs(searchMvsInternal(keyword, userId));
        return result;
    }

    @Override
    public SearchResultVO searchSongs(String keyword, Long userId, Integer page, Integer size) {
        return searchSongs(keyword, userId, page, size, "all");
    }

    @Override
    public SearchResultVO searchSongs(String keyword, Long userId, Integer page, Integer size, String field) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        PageData<SearchResultVO.SongSimpleVO> pageData = searchSongsPage(keyword, userId, currentPage, pageSize, field);

        SearchResultVO result = new SearchResultVO();
        result.setKeyword(keyword);
        result.setSongs(pageData.records);
        applyPagination(result, currentPage, pageSize, pageData.total);
        return result;
    }

    @Override
    public SearchResultVO searchAlbums(String keyword, Long userId, Integer page, Integer size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        PageData<SearchResultVO.AlbumSimpleVO> pageData = searchAlbumsPage(keyword, currentPage, pageSize);

        SearchResultVO result = new SearchResultVO();
        result.setKeyword(keyword);
        result.setAlbums(pageData.records);
        applyPagination(result, currentPage, pageSize, pageData.total);
        return result;
    }

    @Override
    public SearchResultVO searchArtists(String keyword, Long userId, Integer page, Integer size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        PageData<SearchResultVO.ArtistSimpleVO> pageData = searchArtistsPage(keyword, currentPage, pageSize);

        SearchResultVO result = new SearchResultVO();
        result.setKeyword(keyword);
        result.setArtists(pageData.records);
        applyPagination(result, currentPage, pageSize, pageData.total);
        return result;
    }

    @Override
    public SearchResultVO searchPlaylists(String keyword, Long userId, Integer page, Integer size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        PageData<SearchResultVO.PlaylistSimpleVO> pageData = searchPlaylistsPage(keyword, currentPage, pageSize);

        SearchResultVO result = new SearchResultVO();
        result.setKeyword(keyword);
        result.setPlaylists(pageData.records);
        applyPagination(result, currentPage, pageSize, pageData.total);
        return result;
    }

    @Override
    public SearchResultVO searchMvs(String keyword, Long userId, Integer page, Integer size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        PageData<SearchResultVO.MvSimpleVO> pageData = searchMvsPage(keyword, currentPage, pageSize);

        SearchResultVO result = new SearchResultVO();
        result.setKeyword(keyword);
        result.setMvs(pageData.records);
        applyPagination(result, currentPage, pageSize, pageData.total);
        return result;
    }

    @Override
    public SearchResultVO searchUsers(String keyword, Long userId, Integer page, Integer size) {
        int currentPage = normalizePage(page);
        int pageSize = normalizeSize(size);
        PageData<SearchResultVO.UserSimpleVO> pageData = searchUsersPage(keyword, currentPage, pageSize);

        SearchResultVO result = new SearchResultVO();
        result.setKeyword(keyword);
        result.setUsers(pageData.records);
        applyPagination(result, currentPage, pageSize, pageData.total);
        return result;
    }
    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return SEARCH_LIMIT;
        }
        return Math.min(size, 100);
    }

    private String buildLimitClause(int page, int size) {
        long offset = (long) (page - 1) * size;
        return "LIMIT " + offset + ", " + size;
    }

    private void applyPagination(SearchResultVO result, int page, int size, long total) {
        result.setPage(page);
        result.setSize(size);
        result.setTotal(total);
        result.setPages(total <= 0 ? 0 : (int) Math.ceil((double) total / size));
    }

    private static class PageData<T> {
        private final List<T> records;
        private final long total;

        private PageData(List<T> records, long total) {
            this.records = records;
            this.total = total;
        }

        private static <T> PageData<T> empty() {
            return new PageData<>(Collections.emptyList(), 0L);
        }
    }

       
                 
       
    private List<SearchResultVO.SongSimpleVO> searchSongsInternal(String keyword, Long userId) {
        return searchSongsPage(keyword, userId, 1, SEARCH_LIMIT).records;
    }

    private PageData<SearchResultVO.SongSimpleVO> searchSongsPage(String keyword, Long userId, int page, int size) {
        return searchSongsPage(keyword, userId, page, size, "all");
    }

    private PageData<SearchResultVO.SongSimpleVO> searchSongsPage(String keyword, Long userId, int page, int size, String field) {
        Long total = songMapper.selectCount(buildSongSearchWrapper(keyword, field));
        if (total == null || total == 0) {
            return PageData.empty();
        }

        LambdaQueryWrapper<Song> wrapper = buildSongSearchWrapper(keyword, field);
        wrapper.orderByDesc(Song::getPlayCount, Song::getFavoriteCount)
                .last(buildLimitClause(page, candidateSize(size)));
        List<Song> songList = filterPublicUploaderSongs(songMapper.selectList(wrapper)).stream()
                .limit(size)
                .collect(Collectors.toList());

                              
        Set<Long> favoriteSongIds = Collections.emptySet();
        if (ObjectUtils.isNotEmpty(userId)) {
            favoriteSongIds = getFavoriteSongIds(userId, songList);
        }

        List<SearchResultVO.SongSimpleVO> voList = new ArrayList<>();
        for (Song song : songList) {
            SearchResultVO.SongSimpleVO vo = new SearchResultVO.SongSimpleVO();
            vo.setId(song.getId());
            vo.setName(song.getName());
            vo.setArtistNames(song.getArtistNames());
            vo.setAlbumName(song.getAlbumName());
            vo.setDuration(song.getDuration());
            vo.setMainType(song.getMainType());
            vo.setCover(UrlHelper.buildRelativeCoverUrl(song.getCover()));
            vo.setUrlStandard(UrlHelper.buildRelativeAudioUrl(song.getUrlStandard()));
            vo.setUrlHigh(UrlHelper.buildRelativeAudioUrl(song.getUrlHigh()));
            vo.setUrlLossless(UrlHelper.buildRelativeAudioUrl(song.getUrlLossless()));
                     
            vo.setVersionType(song.getVersionType());
            vo.setVersionName(song.getVersionName());
                     
            vo.setLanguage("中文");
                               
            vo.setIsFavorite(favoriteSongIds.contains(song.getId()));
            voList.add(vo);
        }

        return new PageData<>(voList, total);
    }

    private LambdaQueryWrapper<Song> buildSongSearchWrapper(String keyword) {
        return buildSongSearchWrapper(keyword, "all");
    }

    private LambdaQueryWrapper<Song> buildSongSearchWrapper(String keyword, String field) {
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<Song>()
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED);
        if ("title".equals(field)) {
            return wrapper.like(Song::getName, keyword);
        }
        if ("artist".equals(field)) {
            return wrapper.like(Song::getArtistNames, keyword);
        }
        if ("album".equals(field)) {
            return wrapper.like(Song::getAlbumName, keyword);
        }
        return wrapper.and(w -> w.like(Song::getName, keyword)
                .or()
                .like(Song::getArtistNames, keyword)
                .or()
                .like(Song::getAlbumName, keyword));
    }

       
                      
      
                         
                        
                        
       
    private Set<Long> getFavoriteSongIds(Long userId, List<Song> songs) {
                    
        Long favoritePlaylistId = getFavoritePlaylistId(userId);
        if (favoritePlaylistId == null) {
            return Collections.emptySet();
        }

                       
        if (songs.isEmpty()) {
            return Collections.emptySet();
        }

                   
        List<Long> songIds = songs.stream()
                .map(Song::getId)
                .collect(Collectors.toList());

                         
        LambdaQueryWrapper<PlaylistSong> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistSong::getPlaylistId, favoritePlaylistId)
                .in(PlaylistSong::getSongId, songIds)
                .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED)
                .select(PlaylistSong::getSongId);

        List<PlaylistSong> playlistSongs = playlistSongMapper.selectList(wrapper);

                      
        return playlistSongs.stream()
                .map(PlaylistSong::getSongId)
                .collect(Collectors.toSet());
    }

       
                 
       
    private Long getFavoritePlaylistId(Long userId) {
        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Playlist::getUserId, userId)
                .eq(Playlist::getType, MusicConstants.PlaylistType.FAVORITE)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                .select(Playlist::getId);

        Playlist favoritePlaylist = playlistMapper.selectOne(wrapper);
        return favoritePlaylist != null ? favoritePlaylist.getId() : null;
    }

       
                 
       
    private List<SearchResultVO.AlbumSimpleVO> searchAlbumsInternal(String keyword, Long userId) {
        return searchAlbumsPage(keyword, 1, SEARCH_LIMIT).records;
    }

    private PageData<SearchResultVO.AlbumSimpleVO> searchAlbumsPage(String keyword, int page, int size) {
        Long total = albumMapper.selectCount(buildAlbumSearchWrapper(keyword));
        if (total == null || total == 0) {
            return PageData.empty();
        }

        LambdaQueryWrapper<Album> wrapper = buildAlbumSearchWrapper(keyword);
        wrapper.orderByDesc(Album::getPlayCount, Album::getFavoriteCount)
                .last(buildLimitClause(page, candidateSize(size)));
        List<Album> albumList = filterAlbumsWithPublicSongs(albumMapper.selectList(wrapper)).stream()
                .limit(size)
                .collect(Collectors.toList());

        List<SearchResultVO.AlbumSimpleVO> voList = new ArrayList<>();
        for (Album album : albumList) {
            SearchResultVO.AlbumSimpleVO vo = new SearchResultVO.AlbumSimpleVO();
            vo.setId(album.getId());
            vo.setName(album.getName());
            vo.setArtistNames(album.getArtistNames());
            vo.setReleaseDate(album.getReleaseDate() != null ? album.getReleaseDate().toString() : null);
            vo.setCover(album.getCover());
            vo.setLanguage(album.getLanguage());
            voList.add(vo);
        }

        return new PageData<>(voList, total);
    }

    private LambdaQueryWrapper<Album> buildAlbumSearchWrapper(String keyword) {
        return new LambdaQueryWrapper<Album>()
                .eq(Album::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Album::getDeleted, CommonConstants.NOT_DELETED)
                .and(w -> w.like(Album::getName, keyword)
                        .or()
                        .like(Album::getArtistNames, keyword));
    }

       
                 
       
    private List<SearchResultVO.ArtistSimpleVO> searchArtistsInternal(String keyword, Long userId) {
        return searchArtistsPage(keyword, 1, SEARCH_LIMIT).records;
    }

    private PageData<SearchResultVO.ArtistSimpleVO> searchArtistsPage(String keyword, int page, int size) {
        Long total = artistMapper.selectCount(buildArtistSearchWrapper(keyword));
        if (total == null || total == 0) {
            return PageData.empty();
        }

        LambdaQueryWrapper<Artist> wrapper = buildArtistSearchWrapper(keyword);
        wrapper.orderByDesc(Artist::getFansCount)
                .last(buildLimitClause(page, size));
        List<Artist> artistList = artistMapper.selectList(wrapper);

        List<SearchResultVO.ArtistSimpleVO> voList = new ArrayList<>();
        for (Artist artist : artistList) {
            SearchResultVO.ArtistSimpleVO vo = new SearchResultVO.ArtistSimpleVO();
            vo.setId(artist.getId());
            vo.setName(artist.getName());
            vo.setType(artist.getType());
            vo.setAvatar(artist.getAvatar());
            voList.add(vo);
        }

        return new PageData<>(voList, total);
    }

    private LambdaQueryWrapper<Artist> buildArtistSearchWrapper(String keyword) {
        return new LambdaQueryWrapper<Artist>()
                .eq(Artist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Artist::getDeleted, CommonConstants.NOT_DELETED)
                .like(Artist::getName, keyword);
    }

       
                 
       
    private List<SearchResultVO.PlaylistSimpleVO> searchPlaylistsInternal(String keyword, Long userId) {
        return searchPlaylistsPage(keyword, 1, SEARCH_LIMIT).records;
    }

    private PageData<SearchResultVO.PlaylistSimpleVO> searchPlaylistsPage(String keyword, int page, int size) {
        Long total = playlistMapper.selectCount(buildPlaylistSearchWrapper(keyword));
        if (total == null || total == 0) {
            return PageData.empty();
        }

        LambdaQueryWrapper<Playlist> wrapper = buildPlaylistSearchWrapper(keyword);
        wrapper.orderByDesc(Playlist::getPlayCount, Playlist::getFavoriteCount)
                .last(buildLimitClause(page, candidateSize(size)));
        List<Playlist> playlistList = filterPublicCreatorPlaylists(playlistMapper.selectList(wrapper)).stream()
                .limit(size)
                .collect(Collectors.toList());

                   
        Map<Long, String> userNameMap = Collections.emptyMap();
        if (!playlistList.isEmpty()) {
            Set<Long> userIds = playlistList.stream()
                    .map(Playlist::getUserId)
                    .collect(Collectors.toSet());
            userNameMap = getUserNames(userIds);
        }

        List<SearchResultVO.PlaylistSimpleVO> voList = new ArrayList<>();
        for (Playlist playlist : playlistList) {
            SearchResultVO.PlaylistSimpleVO vo = new SearchResultVO.PlaylistSimpleVO();
            vo.setId(playlist.getId());
            vo.setName(playlist.getName());
            vo.setCreatorName(userNameMap.get(playlist.getUserId()));
            vo.setSongCount(playlist.getSongCount() != null ? playlist.getSongCount().intValue() : 0);
            vo.setCover(playlist.getCover());
            voList.add(vo);
        }

        return new PageData<>(voList, total);
    }

    private LambdaQueryWrapper<Playlist> buildPlaylistSearchWrapper(String keyword) {
        return new LambdaQueryWrapper<Playlist>()
                .eq(Playlist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                .eq(Playlist::getIsPublic, CommonConstants.PUBLIC_PUBLIC)
                .eq(Playlist::getType, MusicConstants.PlaylistType.CUSTOM)
                .like(Playlist::getName, keyword);
    }

       
                 
      
                            
                             
       
    private Map<Long, String> getUserNames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
                     
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(User::getId, userIds)
                    .eq(User::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(User::getDeleted, CommonConstants.NOT_DELETED)
                    .select(User::getId, User::getNickname);

            List<User> users = userMapper.selectList(wrapper);

                             
            return users.stream()
                    .filter(UserAccountStatusUtil::canExposePublicContent)
                    .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));
        } catch (Exception e) {
            log.warn("批量获取用户名称失败: userIds={}", userIds);
            return Collections.emptyMap();
        }
    }

       
                 
       
    private List<SearchResultVO.MvSimpleVO> searchMvsInternal(String keyword, Long userId) {
        return searchMvsPage(keyword, 1, SEARCH_LIMIT).records;
    }

    private PageData<SearchResultVO.MvSimpleVO> searchMvsPage(String keyword, int page, int size) {
        Long total = mvMapper.selectCount(buildMvSearchWrapper(keyword));
        if (total == null || total == 0) {
            return PageData.empty();
        }

        LambdaQueryWrapper<MV> wrapper = buildMvSearchWrapper(keyword);
        wrapper.orderByDesc(MV::getPlayCount)
                .last(buildLimitClause(page, candidateSize(size)));
        List<MV> mvList = filterPublicMvs(mvMapper.selectList(wrapper)).stream()
                .limit(size)
                .collect(Collectors.toList());

                      
        Map<Long, String> songLanguageMap = getSongLanguageMap(mvList);

        List<SearchResultVO.MvSimpleVO> voList = new ArrayList<>();
        for (MV mv : mvList) {
            SearchResultVO.MvSimpleVO vo = new SearchResultVO.MvSimpleVO();
            vo.setId(mv.getId());
            vo.setName(mv.getName());
            vo.setArtistNames(mv.getArtistNames());
            vo.setDuration(mv.getDuration());
            vo.setPlayCount(mv.getPlayCount());
            vo.setCover(mv.getCover());
                        
            if (mv.getSongId() != null && songLanguageMap.containsKey(mv.getSongId())) {
                vo.setSongLanguage(songLanguageMap.get(mv.getSongId()));
            }
            voList.add(vo);
        }

        return new PageData<>(voList, total);
    }

    private LambdaQueryWrapper<MV> buildMvSearchWrapper(String keyword) {
        return new LambdaQueryWrapper<MV>()
                .eq(MV::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(MV::getDeleted, CommonConstants.NOT_DELETED)
                .and(w -> w.like(MV::getName, keyword)
                        .or()
                        .like(MV::getArtistNames, keyword));
    }

       
                    
      
                         
                            
       
    private Map<Long, String> getSongLanguageMap(List<MV> mvList) {
                         
        Set<Long> songIds = mvList.stream()
                .map(MV::getSongId)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toSet());

        if (songIds.isEmpty()) {
            return Collections.emptyMap();
        }

                   
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Song::getId, Song::getLanguage)
                .in(Song::getId, songIds)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED);

        List<Song> songs = songMapper.selectList(wrapper);

               
        return songs.stream()
                .collect(Collectors.toMap(Song::getId, Song::getLanguage, (a, b) -> a));
    }

    private int candidateSize(int size) {
        long expanded = Math.max((long) size * 3L, (long) size + 10L);
        return (int) Math.min(expanded, 300L);
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
                uploaderIds, ids -> userMapper.selectBatchIds(ids));
        return songs.stream()
                .filter(song -> song.getUploaderId() == null || allowedUploaderIds.contains(song.getUploaderId()))
                .collect(Collectors.toList());
    }

    private List<Playlist> filterPublicCreatorPlaylists(List<Playlist> playlists) {
        if (playlists == null || playlists.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = playlists.stream()
                .map(Playlist::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allowedUserIds = UserAccountStatusUtil.filterPublicContentUserIds(
                userIds, ids -> userMapper.selectBatchIds(ids));
        return playlists.stream()
                .filter(playlist -> playlist.getUserId() != null && allowedUserIds.contains(playlist.getUserId()))
                .collect(Collectors.toList());
    }

    private List<Album> filterAlbumsWithPublicSongs(List<Album> albums) {
        if (albums == null || albums.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> albumIds = albums.stream()
                .map(Album::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (albumIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getAlbumId, albumIds)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .select(Song::getAlbumId, Song::getUploaderId);
        Set<Long> allowedAlbumIds = filterPublicUploaderSongs(songMapper.selectList(wrapper)).stream()
                .map(Song::getAlbumId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return albums.stream()
                .filter(album -> allowedAlbumIds.contains(album.getId()))
                .collect(Collectors.toList());
    }

    private List<MV> filterPublicMvs(List<MV> mvs) {
        if (mvs == null || mvs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> songIds = mvs.stream()
                .map(MV::getSongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (songIds.isEmpty()) {
            return mvs;
        }
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Song::getId, songIds)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .select(Song::getId, Song::getUploaderId);
        Set<Long> allowedSongIds = filterPublicUploaderSongs(songMapper.selectList(wrapper)).stream()
                .map(Song::getId)
                .collect(Collectors.toSet());
        return mvs.stream()
                .filter(mv -> mv.getSongId() == null || allowedSongIds.contains(mv.getSongId()))
                .collect(Collectors.toList());
    }

       
                 
       
    private List<SearchResultVO.UserSimpleVO> searchUsersInternal(String keyword, Long userId) {
        return searchUsersPage(keyword, 1, SEARCH_LIMIT).records;
    }

    private PageData<SearchResultVO.UserSimpleVO> searchUsersPage(String keyword, int page, int size) {
        Long total = userMapper.selectCount(buildUserSearchWrapper(keyword));
        if (total == null || total == 0) {
            return PageData.empty();
        }

        LambdaQueryWrapper<User> wrapper = buildUserSearchWrapper(keyword);
        wrapper.orderByDesc(User::getFansCount)
                .last(buildLimitClause(page, size));
        List<User> userList = userMapper.selectList(wrapper);

        List<SearchResultVO.UserSimpleVO> voList = new ArrayList<>();
        for (User user : userList) {
            SearchResultVO.UserSimpleVO vo = new SearchResultVO.UserSimpleVO();
            vo.setId(user.getId());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setFansCount(user.getFansCount());
            vo.setSignature(user.getIntroduction());
            voList.add(vo);
        }

        return new PageData<>(voList, total);
    }

    private LambdaQueryWrapper<User> buildUserSearchWrapper(String keyword) {
        return UserAccountStatusUtil.publicStatsUserQuery()
                .and(w -> w.like(User::getNickname, keyword)
                        .or()
                        .like(User::getUsername, keyword));
    }
}

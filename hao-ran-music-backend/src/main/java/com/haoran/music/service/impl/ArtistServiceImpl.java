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
import com.haoran.music.common.util.*;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.AlbumMapper;
import com.haoran.music.mapper.ArtistMapper;
import com.haoran.music.mapper.ArtistClaimMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.SongArtistMapper;
import com.haoran.music.mapper.SubjectFollowMapper;
import com.haoran.music.service.ArtistService;
import com.haoran.music.service.search.SearchIndexService;
import com.haoran.music.vo.artist.ArtistVO;
import com.haoran.music.vo.album.AlbumVO;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;





@Slf4j
@Service
public class ArtistServiceImpl extends ServiceImpl<ArtistMapper, Artist> implements ArtistService {

    @Resource
    private SongMapper songMapper;

    @Resource
    private SongArtistMapper songArtistMapper;

    @Resource
    private AlbumMapper albumMapper;

    @Resource
    private SubjectFollowMapper subjectFollowMapper;

    @Resource
    private ArtistClaimMapper artistClaimMapper;

    @Resource
    private com.haoran.music.mapper.UserMapper userMapper;

    @Resource
    private SearchIndexService searchIndexService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Artist artist) {
        boolean result = super.save(artist);
        if (result && artist != null) {
            searchIndexService.sync("artist", artist.getId());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(Artist artist) {
        boolean result = super.updateById(artist);
        if (result && artist != null && artist.getId() != null) {
            searchIndexService.sync("artist", artist.getId());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(java.io.Serializable id) {
        boolean result = super.removeById(id);
        if (result && id != null) {
            searchIndexService.sync("artist", Long.valueOf(String.valueOf(id)));
        }
        return result;
    }

    @Override
    public ArtistVO getArtistById(Long artistId, Long userId) {
        if (ObjectUtils.isEmpty(artistId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌手ID不能为空");
        }

        Artist artist = getById(artistId);
        if (ObjectUtils.isEmpty(artist)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌手不存在");
        }


        if (!CommonConstants.STATUS_NORMAL.equals(artist.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "歌手已禁用");
        }

        ArtistVO vo = convertToVO(artist);
        enrichProfileIdentity(vo, artist);


        List<Song> hotSongs = getHotSongsByArtistId(artistId, 5);
        List<AlbumVO.SongSimpleVO> songSimpleList = ConvertHelper.toVOList(hotSongs, song -> {
            AlbumVO.SongSimpleVO simpleVO = new AlbumVO.SongSimpleVO();
            simpleVO.setId(song.getId());
            simpleVO.setName(song.getName());
            simpleVO.setDuration(song.getDuration());
            simpleVO.setMainType(song.getMainType());
            simpleVO.setCover(UrlHelper.buildRelativeCoverUrl(song.getCover()));
            simpleVO.setAlbumId(song.getAlbumId());
            simpleVO.setUrlStandard(UrlHelper.buildRelativeAudioUrl(song.getUrlStandard()));
            simpleVO.setUrlHigh(UrlHelper.buildRelativeAudioUrl(song.getUrlHigh()));
            simpleVO.setUrlLossless(UrlHelper.buildRelativeAudioUrl(song.getUrlLossless()));


            String albumName = song.getAlbumName();
            if (song.getIsSingle() != null && song.getIsSingle() == 1) {
                if (StrUtil.isBlank(albumName) || albumName.contains("Singles")) {
                    albumName = "单曲";
                }
            }
            simpleVO.setAlbumName(albumName);


            simpleVO.setArtistIds(song.getArtistIds());
            simpleVO.setArtistNames(song.getArtistNames());


            simpleVO.setVersionType(song.getVersionType());
            simpleVO.setVersionName(song.getVersionName());


            simpleVO.setLanguage(song.getLanguage());

            return simpleVO;
        });
        vo.setHotSongs(songSimpleList);


        LambdaQueryWrapper<Album> albumWrapper = new LambdaQueryWrapper<>();
        albumWrapper.apply("FIND_IN_SET({0}, artist_ids) > 0", artistId)
                .eq(Album::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Album::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Album::getReleaseDate)
                .last("LIMIT 10");

        List<Album> albums = filterAlbumsWithPublicSongs(albumMapper.selectList(albumWrapper)).stream()
                .limit(10)
                .collect(Collectors.toList());
        List<AlbumVO.AlbumSimpleVO> albumSimpleList = ConvertHelper.toVOList(albums, album -> {
            AlbumVO.AlbumSimpleVO simpleVO = new AlbumVO.AlbumSimpleVO();
            simpleVO.setId(album.getId());
            simpleVO.setName(album.getName());
            simpleVO.setCover(UrlHelper.buildRelativeCoverUrl(album.getCover()));
            simpleVO.setReleaseDate(album.getReleaseDate());
            return simpleVO;
        });
        vo.setAlbums(albumSimpleList);


        if (ObjectUtils.isNotEmpty(userId)) {
            vo.setIsFollow(checkIsFollow(userId, artistId));
        }

        return vo;
    }












    @Override
    public IPage<ArtistVO> pageArtists(PageQuery pageQuery, String area, String keyword, String initial, String sortBy, Long userId) {
        Page<Artist> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        String safeKeyword = CatalogSearchInput.normalizeForLike(keyword);

        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Artist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Artist::getDeleted, CommonConstants.NOT_DELETED);


        if (StrUtil.isNotBlank(area)) {
            String normalizedArea = normalizeArtistArea(area);
            if ("内地".equals(normalizedArea)) {
                wrapper.in(Artist::getArea, "内地", "中国", "CN", "zh");
            } else if ("港台".equals(normalizedArea)) {
                wrapper.in(Artist::getArea, "港台", "香港", "台湾", "中国香港", "中国台湾", "HK", "TW");
            } else if ("欧美".equals(normalizedArea)) {
                wrapper.in(Artist::getArea, "欧美", "美国", "英国", "法国", "德国", "加拿大", "意大利", "US", "GB", "en");
            } else {
                wrapper.eq(Artist::getArea, normalizedArea);
            }
        }


        if (StrUtil.isNotBlank(safeKeyword)) {
            wrapper.and(w -> w.like(Artist::getName, safeKeyword)
                    .or()
                    .like(Artist::getFirstLetter, safeKeyword));
        }


        if (StrUtil.isNotBlank(initial) && !"热门".equals(initial)) {
            if ("*".equals(initial)) {

                wrapper.apply("(first_letter IS NULL OR first_letter = '' OR UPPER(first_letter) NOT REGEXP '^[A-Z]$')");
            } else {
                wrapper.eq(Artist::getFirstLetter, initial.toUpperCase());
            }
        }


        if (StrUtil.isNotBlank(sortBy)) {
            handleSortByParam(wrapper, sortBy);
        } else {

            handleSort(wrapper, pageQuery.getSortField(), pageQuery.getSortOrder());
        }

        IPage<Artist> artistPage = page(page, wrapper);


        Set<Long> followedArtistIds = Collections.emptySet();
        if (ObjectUtils.isNotEmpty(userId) && !artistPage.getRecords().isEmpty()) {
            followedArtistIds = getFollowedArtistIds(userId, artistPage.getRecords());
        }


        IPage<ArtistVO> voPage = ConvertHelper.toVOPage(artistPage, this::convertToVO);
        ConvertHelper.setFieldFromSet(voPage.getRecords(), ArtistVO::getId, followedArtistIds, ArtistVO::setIsFollow);

        return voPage;
    }

    private String normalizeArtistArea(String area) {
        if ("zh".equalsIgnoreCase(area)) return "内地";
        if ("tw".equalsIgnoreCase(area) || "hk".equalsIgnoreCase(area)) return "港台";
        if ("en".equalsIgnoreCase(area)) return "欧美";
        if ("ja".equalsIgnoreCase(area)) return "日本";
        if ("ko".equalsIgnoreCase(area)) return "韩国";
        return area;
    }

    @Override
    public List<ArtistVO> getArtistsByLetter(String letter, Long userId) {
        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Artist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Artist::getDeleted, CommonConstants.NOT_DELETED);

        if (StrUtil.isNotBlank(letter) && !"*".equals(letter)) {
            wrapper.eq(Artist::getFirstLetter, letter.toUpperCase());
        } else if ("*".equals(letter)) {

            wrapper.apply("(first_letter IS NULL OR first_letter = '' OR UPPER(first_letter) NOT REGEXP '^[A-Z]$')");
        }

        wrapper.orderByAsc(Artist::getFirstLetter, Artist::getName);

        List<Artist> artists = list(wrapper);


        Set<Long> followedArtistIds = Collections.emptySet();
        if (ObjectUtils.isNotEmpty(userId) && !artists.isEmpty()) {
            followedArtistIds = getFollowedArtistIds(userId, artists);
        }


        List<ArtistVO> voList = ConvertHelper.toVOList(artists, this::convertToVO);
        ConvertHelper.setFieldFromSet(voList, ArtistVO::getId, followedArtistIds, ArtistVO::setIsFollow);

        return voList;
    }

    @Override
    public List<ArtistVO> getHotArtists(Integer limit, Long userId) {
        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Artist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Artist::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Artist::getFansCount)
                .last("LIMIT " + (limit != null ? limit : 50));

        List<Artist> artists = list(wrapper);


        Set<Long> followedArtistIds = Collections.emptySet();
        if (ObjectUtils.isNotEmpty(userId) && !artists.isEmpty()) {
            followedArtistIds = getFollowedArtistIds(userId, artists);
        }


        List<ArtistVO> voList = ConvertHelper.toVOList(artists, this::convertToVO);
        ConvertHelper.setFieldFromSet(voList, ArtistVO::getId, followedArtistIds, ArtistVO::setIsFollow);

        return voList;
    }

    @Override
    public IPage<ArtistVO> searchArtists(String keyword, PageQuery pageQuery, Long userId) {
        if (StrUtil.isBlank(keyword)) {
            return ConvertHelper.emptyPage(pageQuery.getPage(), pageQuery.getSize(), ArtistVO.class);
        }

        Page<Artist> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());

        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Artist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Artist::getDeleted, CommonConstants.NOT_DELETED)
                .and(w -> w.like(Artist::getName, keyword)
                        .or()
                        .like(Artist::getFirstLetter, keyword));

        wrapper.orderByDesc(Artist::getFansCount);

        IPage<Artist> artistPage = page(page, wrapper);


        Set<Long> followedArtistIds = Collections.emptySet();
        if (ObjectUtils.isNotEmpty(userId) && !artistPage.getRecords().isEmpty()) {
            followedArtistIds = getFollowedArtistIds(userId, artistPage.getRecords());
        }


        IPage<ArtistVO> voPage = ConvertHelper.toVOPage(artistPage, this::convertToVO);
        ConvertHelper.setFieldFromSet(voPage.getRecords(), ArtistVO::getId, followedArtistIds, ArtistVO::setIsFollow);

        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean followArtist(Long userId, Long artistId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(artistId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        User user = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(user, "关注歌手");


        Artist artist = getById(artistId);
        if (ObjectUtils.isEmpty(artist)
                || !CommonConstants.STATUS_NORMAL.equals(artist.getStatus())
                || !CommonConstants.NOT_DELETED.equals(artist.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌手不存在");
        }

        boolean contributesPublicStats = UserAccountStatusUtil.canContributePublicStats(user);
        int changed = subjectFollowMapper.activate(
                userId, "artist_profile", artistId, contributesPublicStats);
        if (changed == 0) {
            throw new BusinessException("已经关注过该歌手");
        }

        if (contributesPublicStats) {
            if (baseMapper.adjustFansCount(artistId, 1) != 1) {
                throw new BusinessException("歌手粉丝数状态已变化，请刷新后重试");
            }
        }

        log.info("event=artist_followed userId={} artistId={}", userId, artistId);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unfollowArtist(Long userId, Long artistId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(artistId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        SubjectFollow subjectFollow = subjectFollowMapper.selectActiveForUpdate(
                userId, "artist_profile", artistId);
        if (ObjectUtils.isEmpty(subjectFollow)) {
            throw new BusinessException("未关注该歌手");
        }

        if (subjectFollowMapper.cancel(subjectFollow.getId()) != 1) {
            throw new BusinessException("关注状态已变更，请刷新后重试");
        }
        if (Boolean.TRUE.equals(subjectFollow.getContributesPublicStats())) {
            if (baseMapper.adjustFansCount(artistId, -1) != 1) {
                throw new BusinessException("歌手粉丝数状态已变化，请刷新后重试");
            }
        }

        log.info("event=artist_unfollowed userId={} artistId={}", userId, artistId);

        return true;
    }

    @Override
    public List<String> getArtistLetters() {
        List<String> letters = new ArrayList<>();

        for (char c = 'A'; c <= 'Z'; c++) {
            letters.add(String.valueOf(c));
        }

        letters.add("*");
        return letters;
    }

    @Override
    public List<AlbumVO.SongSimpleVO> getArtistHotSongs(Long artistId, Integer limit, Long userId) {
        if (ObjectUtils.isEmpty(artistId)) {
            return new ArrayList<>();
        }


        int actualLimit = limit != null ? limit : 10;
        List<Song> songs = filterPublicUploaderSongs(
                songMapper.selectHotSongsByArtistId(artistId, expandedPublicQueryLimit(actualLimit))
        ).stream()
                .limit(actualLimit)
                .collect(Collectors.toList());


        for (Song song : songs) {
            if (song.getIsSingle() != null && song.getIsSingle() == 1) {
                if (StrUtil.isNotBlank(song.getAlbumName()) &&
                    !song.getAlbumName().contains("Singles")) {


                } else {

                    song.setAlbumName("单曲");
                }
            }

        }


        return ConvertHelper.toVOList(songs, song -> {
            AlbumVO.SongSimpleVO vo = BeanUtil.copyProperties(song, AlbumVO.SongSimpleVO.class);
            vo.setCover(UrlHelper.buildRelativeCoverUrl(song.getCover()));
            vo.setUrlStandard(UrlHelper.buildRelativeAudioUrl(song.getUrlStandard()));
            vo.setUrlHigh(UrlHelper.buildRelativeAudioUrl(song.getUrlHigh()));
            vo.setUrlLossless(UrlHelper.buildRelativeAudioUrl(song.getUrlLossless()));
            return vo;
        });
    }








    @Override
    public List<ArtistVO> getArtistList(String area, Long userId) {
        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Artist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Artist::getDeleted, CommonConstants.NOT_DELETED);


        if (StrUtil.isNotBlank(area)) {
            wrapper.eq(Artist::getArea, area);
        }


        wrapper.orderByDesc(Artist::getFansCount)
                .orderByAsc(Artist::getName);

        List<Artist> artists = list(wrapper);


        Set<Long> followedArtistIds = Collections.emptySet();
        if (ObjectUtils.isNotEmpty(userId) && !artists.isEmpty()) {
            followedArtistIds = getFollowedArtistIds(userId, artists);
        }


        List<ArtistVO> voList = ConvertHelper.toVOList(artists, this::convertToVO);
        ConvertHelper.setFieldFromSet(voList, ArtistVO::getId, followedArtistIds, ArtistVO::setIsFollow);

        return voList;
    }








    private Set<Long> getFollowedArtistIds(Long userId, List<Artist> artists) {
        if (artists.isEmpty()) {
            return Collections.emptySet();
        }


        List<Long> artistIds = ConvertHelper.extractIds(artists, Artist::getId);

        return new HashSet<>(subjectFollowMapper.selectActiveTargetIds(
                userId, "artist_profile", artistIds));
    }








    private Boolean checkIsFollow(Long userId, Long artistId) {
        return !subjectFollowMapper.selectActiveTargetIds(
                userId, "artist_profile", Collections.singletonList(artistId)).isEmpty();
    }








    private void handleSort(LambdaQueryWrapper<Artist> wrapper, String sortField, String sortOrder) {
        if (StrUtil.isBlank(sortField)) {
            wrapper.orderByDesc(Artist::getFansCount);
            return;
        }

        boolean isAsc = !"desc".equalsIgnoreCase(sortOrder);

        switch (sortField) {
            case "name":
                wrapper.orderBy(true, isAsc, Artist::getName);
                break;
            case "hot":
                wrapper.orderBy(true, isAsc, Artist::getFansCount);
                break;
            case "time":
                wrapper.orderBy(true, isAsc, Artist::getCreateTime);
                break;
            default:
                wrapper.orderByDesc(Artist::getFansCount);
                break;
        }
    }







    private void handleSortByParam(LambdaQueryWrapper<Artist> wrapper, String sortBy) {
        if (StrUtil.isBlank(sortBy) || "hot".equals(sortBy)) {
            wrapper.orderByDesc(Artist::getFansCount);
        } else if ("name".equals(sortBy)) {
            wrapper.orderByAsc(Artist::getName);
        } else if ("time".equals(sortBy)) {
            wrapper.orderByDesc(Artist::getCreateTime);
        } else {
            wrapper.orderByDesc(Artist::getFansCount);
        }
    }







    private ArtistVO convertToVO(Artist artist) {
        ArtistVO vo = BeanUtil.copyProperties(artist, ArtistVO.class);
        String artistKind = resolveArtistKind(artist);
        vo.setArtistKind(artistKind);
        vo.setArtistKindName("group".equals(artistKind) ? "乐队 / 组合"
                : "person".equals(artistKind) ? "个人音乐人" : "类型待补充");


        if (ObjectUtils.isNotEmpty(vo.getAvatar())) {
            vo.setAvatar(UrlHelper.buildRelativeCoverUrl(vo.getAvatar()));
        } else {
            vo.setAvatar("/default-avatar.png");
        }
        if (ObjectUtils.isNotEmpty(vo.getCover())) {
            vo.setCover(UrlHelper.buildRelativeCoverUrl(vo.getCover()));
        } else {
            vo.setCover("/default-cover.png");
        }


        vo.setGender(artist.getGender());
        return vo;
    }

    private void enrichProfileIdentity(ArtistVO vo, Artist artist) {
        List<ArtistClaim> claims = artistClaimMapper.selectList(new LambdaQueryWrapper<ArtistClaim>()
                .eq(ArtistClaim::getArtistId, artist.getId())
                .eq(ArtistClaim::getStatus, "approved")
                .orderByDesc(ArtistClaim::getUpdateTime)
                .last("LIMIT 1"));
        boolean creatorOwned = CollUtil.isNotEmpty(claims);
        vo.setIsCreator(creatorOwned);
        vo.setProfileSource(creatorOwned ? "town_creator" : "catalog");
        vo.setProfileSourceName(creatorOwned ? "小镇创作者" : "曲库收录");
    }

    static String resolveArtistKind(Artist artist) {
        if (artist == null) {
            return "unknown";
        }
        if (Integer.valueOf(2).equals(artist.getType()) || Integer.valueOf(3).equals(artist.getGender())) {
            return "group";
        }
        if (Integer.valueOf(1).equals(artist.getType())
                || Integer.valueOf(1).equals(artist.getGender())
                || Integer.valueOf(2).equals(artist.getGender())) {
            return "person";
        }
        return "unknown";
    }








    private List<Song> getHotSongsByArtistId(Long artistId, Integer limit) {
        int actualLimit = limit != null && limit > 0 ? limit : 10;
        List<Song> songs = filterPublicUploaderSongs(
                songMapper.selectHotSongsByArtistId(artistId, expandedPublicQueryLimit(actualLimit))
        ).stream()
                .limit(actualLimit)
                .collect(Collectors.toList());





        for (Song song : songs) {
            if (song.getIsSingle() != null && song.getIsSingle() == 1) {

                if (StrUtil.isNotBlank(song.getAlbumName()) &&
                    !song.getAlbumName().contains("Singles")) {


                } else {

                    song.setAlbumName("单曲");
                }
            }

        }
        return songs;
    }




    @Override
    public void updateArtistCount(Long artistId) {
        if (ObjectUtils.isEmpty(artistId)) {
            return;
        }

        Artist artist = getById(artistId);
        if (ObjectUtils.isEmpty(artist)) {
            return;
        }

        Long songCount = songArtistMapper.countSongByArtistId(artistId);
        Long albumCount = songArtistMapper.countAlbumByArtistId(artistId);

        artist.setSongCount(songCount != null ? songCount : 0L);
        artist.setAlbumCount(albumCount != null ? albumCount : 0L);
        updateById(artist);

        log.debug("event=artist_counts_updated artistId={} songCount={} albumCount={}",
                artistId, artist.getSongCount(), artist.getAlbumCount());
    }

    @Override
    public void syncAllArtistCount() {
        int updated = songArtistMapper.syncAllArtistSongAlbumCounts();
        log.info("event=artist_counts_reconciled updatedCount={}", updated);
    }





    @Override
    public void batchUpdateArtistCount(List<Long> artistIds) {
        if (CollUtil.isEmpty(artistIds)) {
            return;
        }


        List<Long> uniqueIds = artistIds.stream().distinct().collect(Collectors.toList());

        for (Long artistId : uniqueIds) {
            try {
                updateArtistCount(artistId);
            } catch (Exception e) {
                log.error("event=artist_count_update_failed artistId={} errorType={}",
                        artistId, e.getClass().getSimpleName());
            }
        }

        log.info("event=artist_count_batch_completed artistCount={}", uniqueIds.size());
    }








    @Override
    public List<ArtistVO> getArtistsByCategory(String category, Integer limit) {
        int actualLimit = limit != null ? limit : 50;

        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Artist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Artist::getDeleted, CommonConstants.NOT_DELETED);


        if (StrUtil.isNotBlank(category)) {
            switch (category) {
                case "chinese_male":
                    wrapper.eq(Artist::getArea, "zh")
                           .in(Artist::getType, Arrays.asList("male_singer", "band", "composer"));
                    break;
                case "chinese_female":
                    wrapper.eq(Artist::getArea, "zh")
                           .in(Artist::getType, Arrays.asList("female_singer", "band"));
                    break;
                case "western":
                    wrapper.in(Artist::getArea, Arrays.asList("us", "uk", "fr", "de"));
                    break;
                case "asian":
                    wrapper.in(Artist::getArea, Arrays.asList("jp", "kr", "th", "other"));
                    break;
                default:

                    break;
            }
        }

        wrapper.orderByDesc(Artist::getHotScore, Artist::getSongCount)
                .last("LIMIT " + actualLimit);

        List<Artist> artists = list(wrapper);
        return ConvertHelper.toVOList(artists, this::convertToVO);
    }


















    @Override
    public List<ArtistVO> getHotCreators(Integer limit, Long userId) {
        int actualLimit = limit != null ? limit : 50;

        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Artist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Artist::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Artist::getPlayCount)
                .last("LIMIT " + actualLimit);

        List<Artist> artists = list(wrapper);
        return ConvertHelper.toVOList(artists, this::convertToVO);
    }








    @Override
    public List<ArtistVO> getNewCreators(Integer limit) {
        int actualLimit = limit != null ? limit : 50;

        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Artist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Artist::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Artist::getCreateTime)
                .last("LIMIT " + actualLimit);

        List<Artist> artists = list(wrapper);
        return ConvertHelper.toVOList(artists, this::convertToVO);
    }








    @Override
    public List<ArtistVO> getActiveCreators(Integer limit) {
        int actualLimit = limit != null ? limit : 50;

        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Artist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Artist::getDeleted, CommonConstants.NOT_DELETED)
                .gt(Artist::getSongCount, 0)
                .orderByDesc(Artist::getSongCount, Artist::getPlayCount)
                .last("LIMIT " + actualLimit);

        List<Artist> artists = list(wrapper);
        return ConvertHelper.toVOList(artists, this::convertToVO);
    }









    @Override
    public List<ArtistVO> getSimilarArtists(Long artistId, Integer limit) {
        if (ObjectUtils.isEmpty(artistId)) {
            return new ArrayList<>();
        }

        int actualLimit = limit != null && limit > 0 ? limit : 10;


        Artist originalArtist = getById(artistId);
        if (originalArtist == null) {
            return new ArrayList<>();
        }

        Set<Long> artistIds = new LinkedHashSet<>();


        if (StrUtil.isNotBlank(originalArtist.getArea()) && artistIds.size() < actualLimit) {
            LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Artist::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Artist::getDeleted, CommonConstants.NOT_DELETED)
                    .ne(Artist::getId, artistId)
                    .eq(Artist::getArea, originalArtist.getArea())
                    .orderByDesc(Artist::getFansCount, Artist::getPlayCount)
                    .last("LIMIT " + (actualLimit - artistIds.size()));

            List<Artist> sameAreaArtists = list(wrapper);
            for (Artist a : sameAreaArtists) {
                artistIds.add(a.getId());
            }
        }


        if (originalArtist.getType() != null && artistIds.size() < actualLimit) {
            LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Artist::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Artist::getDeleted, CommonConstants.NOT_DELETED)
                    .ne(Artist::getId, artistId)
                    .notIn(Artist::getId, artistIds)
                    .eq(Artist::getType, originalArtist.getType())
                    .orderByDesc(Artist::getFansCount)
                    .last("LIMIT " + (actualLimit - artistIds.size()));

            List<Artist> sameTypeArtists = list(wrapper);
            for (Artist a : sameTypeArtists) {
                artistIds.add(a.getId());
            }
        }

        if (artistIds.isEmpty()) {
            return new ArrayList<>();
        }


        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Artist::getId, artistIds)
                .eq(Artist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Artist::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Artist::getFansCount);

        List<Artist> artists = list(wrapper);
        return ConvertHelper.toVOList(artists, this::convertToVO);
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

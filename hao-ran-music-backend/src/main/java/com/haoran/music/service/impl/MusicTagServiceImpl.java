package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.dto.PageResult;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.dto.song.SongVO;
import com.haoran.music.entity.MusicTag;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.SongTagRelation;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserTagPreference;
import com.haoran.music.mapper.MusicTagMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.SongTagRelationMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserTagPreferenceMapper;
import com.haoran.music.service.MusicTagService;
import com.haoran.music.vo.tag.MusicTagVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;





@Slf4j
@Service
public class MusicTagServiceImpl implements MusicTagService {

    @Resource
    private MusicTagMapper musicTagMapper;

    @Resource
    private SongTagRelationMapper songTagRelationMapper;

    @Resource
    private UserTagPreferenceMapper userTagPreferenceMapper;

    @Resource
    private SongMapper songMapper;

    @Resource
    private UserMapper userMapper;




    private static final Map<String, String> CATEGORY_NAME_MAP = new HashMap<>();

    static {
        CATEGORY_NAME_MAP.put("mood", "情绪");
        CATEGORY_NAME_MAP.put("scene", "场景");
        CATEGORY_NAME_MAP.put("style", "风格");
        CATEGORY_NAME_MAP.put("decade", "年代");
        CATEGORY_NAME_MAP.put("language", "语言");
    }

    @Override
    public Map<String, List<MusicTagVO>> getTagList() {
        List<MusicTag> tags = musicTagMapper.selectList(
                new LambdaQueryWrapper<MusicTag>()
                        .orderByAsc(MusicTag::getSortOrder)
        );

        Map<String, List<MusicTagVO>> result = new LinkedHashMap<>();
        for (String category : Arrays.asList("mood", "scene", "style", "decade", "language")) {
            String categoryName = CATEGORY_NAME_MAP.getOrDefault(category, category);
            List<MusicTagVO> categoryTags = tags.stream()
                    .filter(tag -> category.equals(tag.getCategory()))
                    .map(this::convertToVO)
                    .collect(Collectors.toList());
            result.put(categoryName, categoryTags);
        }

        return result;
    }

    @Override
    public List<MusicTagVO> getHotTags(Integer limit) {
        List<MusicTag> tags = musicTagMapper.selectList(
                new LambdaQueryWrapper<MusicTag>()
                        .eq(MusicTag::getIsHot, 1)
                        .orderByDesc(MusicTag::getUseCount)
                        .last("LIMIT " + limit)
        );

        return tags.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }



    @Override
    public PageResult<SongVO> searchSongsByTags(List<Long> tagIds, Integer page, Integer size) {
        if (ObjectUtils.isEmpty(tagIds)) {
            PageResult<SongVO> result = new PageResult<>();
            result.setRecords(new ArrayList<>());
            result.setTotal(0L);
            result.setCurrent((long) page);
            result.setSize((long) size);
            result.setPages(0L);
            return result;
        }


        List<SongTagRelation> relations = songTagRelationMapper.selectList(
                new LambdaQueryWrapper<SongTagRelation>()
                        .in(SongTagRelation::getTagId, tagIds)
        );

        if (ObjectUtils.isEmpty(relations)) {
            PageResult<SongVO> result = new PageResult<>();
            result.setRecords(new ArrayList<>());
            result.setTotal(0L);
            result.setCurrent((long) page);
            result.setSize((long) size);
            result.setPages(0L);
            return result;
        }


        Map<Long, Integer> songTagCount = new HashMap<>();
        for (SongTagRelation relation : relations) {
            Long songId = relation.getSongId();
            songTagCount.put(songId, songTagCount.getOrDefault(songId, 0) + 1);
        }


        List<Long> songIds = new ArrayList<>(songTagCount.keySet());
        songIds.sort((a, b) -> songTagCount.get(b).compareTo(songTagCount.get(a)));


        int total = songIds.size();
        int start = (page - 1) * size;
        int end = Math.min(start + size, total);
        List<Long> pagedSongIds = start < total ? songIds.subList(start, end) : new ArrayList<>();

        if (ObjectUtils.isEmpty(pagedSongIds)) {
            PageResult<SongVO> result = new PageResult<>();
            result.setRecords(new ArrayList<>());
            result.setTotal((long) total);
            result.setCurrent((long) page);
            result.setSize((long) size);
            result.setPages((long) Math.ceil((double) total / size));
            return result;
        }


        List<Song> songs = filterPublicSongs(songMapper.selectBatchIds(pagedSongIds));


        List<SongVO> records = new ArrayList<>();
        Map<Long, Song> songMap = songs.stream().collect(Collectors.toMap(Song::getId, s -> s));
        for (Long songId : pagedSongIds) {
            Song song = songMap.get(songId);
            if (song != null) {
                SongVO vo = convertToSongVO(song);
                vo.setMatchTagCount(songTagCount.get(songId));
                records.add(vo);
            }
        }

        PageResult<SongVO> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal((long) total);
        result.setCurrent((long) page);
        result.setSize((long) size);
        result.setPages((long) Math.ceil((double) total / size));
        return result;
    }

    @Override
    public List<MusicTagVO> getSongTags(Long songId) {
        List<SongTagRelation> relations = songTagRelationMapper.selectList(
                new LambdaQueryWrapper<SongTagRelation>()
                        .eq(SongTagRelation::getSongId, songId)
        );

        if (ObjectUtils.isEmpty(relations)) {
            return new ArrayList<>();
        }

        List<Long> tagIds = relations.stream()
                .map(SongTagRelation::getTagId)
                .collect(Collectors.toList());

        List<MusicTag> tags = musicTagMapper.selectBatchIds(tagIds);

        return tags.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSongTag(Long songId, Long tagId) {
        validateTagMutationTargets(songId, tagId);


        Long count = songTagRelationMapper.selectCount(
                new LambdaQueryWrapper<SongTagRelation>()
                        .eq(SongTagRelation::getSongId, songId)
                        .eq(SongTagRelation::getTagId, tagId)
        );

        if (count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该标签已存在");
        }

        SongTagRelation relation = new SongTagRelation();
        relation.setSongId(songId);
        relation.setTagId(tagId);
        relation.setSource("admin");
        requireSingleWrite(songTagRelationMapper.insert(relation), "歌曲标签关联新增失败");


        LambdaUpdateWrapper<MusicTag> counterWrapper = new LambdaUpdateWrapper<>();
        counterWrapper.eq(MusicTag::getId, tagId)
                .eq(MusicTag::getDeleted, 0)
                .setSql("use_count = COALESCE(use_count, 0) + 1");
        requireSingleWrite(musicTagMapper.update(null, counterWrapper), "音乐标签使用次数更新失败");
        log.info("[MusicTag] action=add songId={} tagId={}", songId, tagId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSongTag(Long songId, Long tagId) {
        validateTagMutationTargets(songId, tagId);
        int deleted = songTagRelationMapper.delete(
                new LambdaQueryWrapper<SongTagRelation>()
                        .eq(SongTagRelation::getSongId, songId)
                        .eq(SongTagRelation::getTagId, tagId)
        );
        if (deleted != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲标签关联不存在或状态已变化");
        }

        LambdaUpdateWrapper<MusicTag> counterWrapper = new LambdaUpdateWrapper<>();
        counterWrapper.eq(MusicTag::getId, tagId)
                .eq(MusicTag::getDeleted, 0)
                .setSql("use_count = GREATEST(COALESCE(use_count, 0) - 1, 0)");
        requireSingleWrite(musicTagMapper.update(null, counterWrapper), "音乐标签使用次数更新失败");
        log.info("[MusicTag] action=remove songId={} tagId={}", songId, tagId);
    }



    @Override
    public List<MusicTagVO> getUserTagPreference() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            log.warn("[MusicTagService] 用户未登录，返回空标签偏好");
            return new ArrayList<>();
        }

        List<UserTagPreference> preferences = userTagPreferenceMapper.selectList(
                new LambdaQueryWrapper<UserTagPreference>()
                        .eq(UserTagPreference::getUserId, userId)
                        .orderByDesc(UserTagPreference::getScore)
                        .last("LIMIT 10")
        );

        if (ObjectUtils.isEmpty(preferences)) {
            return new ArrayList<>();
        }

        List<Long> tagIds = preferences.stream()
                .map(UserTagPreference::getTagId)
                .collect(Collectors.toList());

        List<MusicTag> tags = musicTagMapper.selectBatchIds(tagIds);

        return tags.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }



    @Override
    public List<SongVO> recommendByTags(Integer limit) {
        int actualLimit = limit != null && limit > 0 ? limit : 20;
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            log.warn("[MusicTagService] 用户未登录，返回热门歌曲作为推荐");

            List<Song> hotSongs = songMapper.selectList(
                    new LambdaQueryWrapper<Song>()
                            .eq(Song::getStatus, 1)
                            .eq(Song::getDeleted, 0)
                            .orderByDesc(Song::getPlayCount)
                            .last("LIMIT " + candidateLimit(actualLimit))
            );
            return filterPublicSongs(hotSongs).stream()
                    .limit(actualLimit)
                    .map(this::convertToSongVO)
                    .collect(Collectors.toList());
        }


        List<UserTagPreference> preferences = userTagPreferenceMapper.selectList(
                new LambdaQueryWrapper<UserTagPreference>()
                        .eq(UserTagPreference::getUserId, userId)
                        .orderByDesc(UserTagPreference::getScore)
                        .last("LIMIT 5")
        );

        if (ObjectUtils.isEmpty(preferences)) {

            List<Song> hotSongs = songMapper.selectList(
                    new LambdaQueryWrapper<Song>()
                            .eq(Song::getStatus, 1)
                            .eq(Song::getDeleted, 0)
                            .orderByDesc(Song::getPlayCount)
                            .last("LIMIT " + candidateLimit(actualLimit))
            );
            return filterPublicSongs(hotSongs).stream()
                    .limit(actualLimit)
                    .map(this::convertToSongVO)
                    .collect(Collectors.toList());
        }


        List<Long> preferredTagIds = preferences.stream()
                .map(UserTagPreference::getTagId)
                .collect(Collectors.toList());

        List<SongTagRelation> relations = songTagRelationMapper.selectList(
                new LambdaQueryWrapper<SongTagRelation>()
                        .in(SongTagRelation::getTagId, preferredTagIds)
        );

        if (ObjectUtils.isEmpty(relations)) {
            return new ArrayList<>();
        }


        Map<Long, Double> songScore = new HashMap<>();
        for (SongTagRelation relation : relations) {
            Long songId = relation.getSongId();
            Double score = preferences.stream()
                    .filter(p -> p.getTagId().equals(relation.getTagId()))
                    .findFirst()
                    .map(UserTagPreference::getScore)
                    .orElse(BigDecimal.ONE)
                    .doubleValue();
            songScore.put(songId, songScore.getOrDefault(songId, 0.0) + score);
        }


        List<Long> recommendedSongIds = songScore.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(candidateLimit(actualLimit))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());


        List<Song> songs = filterPublicSongs(songMapper.selectBatchIds(recommendedSongIds));


        Map<Long, Song> songMap = songs.stream().collect(Collectors.toMap(Song::getId, s -> s));
        List<SongVO> result = new ArrayList<>();
        for (Long songId : recommendedSongIds) {
            Song song = songMap.get(songId);
            if (song != null) {
                SongVO vo = convertToSongVO(song);
                vo.setRecommendReason("基于你的听歌偏好推荐");
                result.add(vo);
                if (result.size() >= actualLimit) {
                    break;
                }
            }
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserTagPreference(Long userId, Long songId) {

        List<SongTagRelation> relations = songTagRelationMapper.selectList(
                new LambdaQueryWrapper<SongTagRelation>()
                        .eq(SongTagRelation::getSongId, songId)
        );

        if (ObjectUtils.isEmpty(relations)) {
            return;
        }

        for (SongTagRelation relation : relations) {
            Long tagId = relation.getTagId();

            UserTagPreference preference = userTagPreferenceMapper.selectOne(
                    new LambdaQueryWrapper<UserTagPreference>()
                            .eq(UserTagPreference::getUserId, userId)
                            .eq(UserTagPreference::getTagId, tagId)
            );

            if (preference == null) {
                preference = new UserTagPreference();
                preference.setUserId(userId);
                preference.setTagId(tagId);
                preference.setScore(BigDecimal.ONE);
                preference.setPlayCount(1);
                preference.setLastPlayTime(LocalDateTime.now());
                userTagPreferenceMapper.insert(preference);
            } else {

                BigDecimal newScore = preference.getScore().add(BigDecimal.valueOf(0.1));
                preference.setScore(newScore);
                preference.setPlayCount(preference.getPlayCount() + 1);
                preference.setLastPlayTime(LocalDateTime.now());
                userTagPreferenceMapper.updateById(preference);
            }
        }
    }




    private MusicTagVO convertToVO(MusicTag entity) {
        MusicTagVO vo = new MusicTagVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setCategoryName(CATEGORY_NAME_MAP.getOrDefault(entity.getCategory(), entity.getCategory()));
        vo.setIsHot(entity.getIsHot() != null && entity.getIsHot() == 1);
        return vo;
    }




    private SongVO convertToSongVO(Song song) {
        SongVO vo = new SongVO();
        BeanUtils.copyProperties(song, vo);
        return vo;
    }

    private List<Song> filterPublicSongs(List<Song> songs) {
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
                .filter(song -> song != null
                        && Integer.valueOf(1).equals(song.getStatus())
                        && Integer.valueOf(0).equals(song.getDeleted())
                        && (song.getUploaderId() == null || allowedUploaderIds.contains(song.getUploaderId())))
                .collect(Collectors.toList());
    }

    private int candidateLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 20;
        }
        long expanded = Math.max((long) limit * 3L, (long) limit + 10L);
        return (int) Math.min(expanded, 300L);
    }

    private void validateTagMutationTargets(Long songId, Long tagId) {
        if (songId == null || songId <= 0 || tagId == null || tagId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲ID和标签ID必须为正数");
        }
        Song song = songMapper.selectByIdForUpdate(songId);
        if (song == null || !Integer.valueOf(1).equals(song.getStatus())
                || !Integer.valueOf(0).equals(song.getDeleted())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲不存在或不可用");
        }
        MusicTag tag = musicTagMapper.selectById(tagId);
        if (tag == null || !Integer.valueOf(0).equals(tag.getDeleted())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "音乐标签不存在或不可用");
        }
    }

    private void requireSingleWrite(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}

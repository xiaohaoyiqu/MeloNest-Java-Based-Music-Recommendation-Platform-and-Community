   
                      
                        
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.SongVote;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.SongVoteMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.SongVoteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

   
           
   
@Slf4j
@Service
public class SongVoteServiceImpl extends ServiceImpl<SongVoteMapper, SongVote> implements SongVoteService {

    @Autowired
    private SongVoteMapper songVoteMapper;

    @Autowired
    private SongMapper songMapper;

    @Autowired
    private UserMapper userMapper;

               
    private static final int MAX_DAILY_VOTES = 10;
    private static final int DEFAULT_LIST_LIMIT = 10;
    private static final int MAX_LIST_LIMIT = 20;
    private static final int VOTE_CANDIDATE_MULTIPLIER = 20;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer vote(Long songId, Long userId) {
        if (ObjectUtils.isEmpty(songId) || ObjectUtils.isEmpty(userId)) {
            log.warn("投票参数为空: songId={}, userId={}", songId, userId);
            return 0;
        }
        User lockedUser = userMapper.selectByIdForUpdate(userId);
        UserAccountStatusUtil.requireCanInteract(lockedUser, "投票");
        Song song = songMapper.selectById(songId);
        if (!isVoteEligible(song)) {
            log.warn("歌曲不存在或当前不可投票: songId={}", songId);
            return 0;
        }

        LocalDate today = LocalDate.now();

                        
        LambdaQueryWrapper<SongVote> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(SongVote::getSongId, songId)
                .eq(SongVote::getUserId, userId)
                .eq(SongVote::getVoteDate, today);

        SongVote existingVote = songVoteMapper.selectOne(checkWrapper);
        if (existingVote != null) {
            log.info("重复投票按幂等成功处理: songId={}, userId={}", songId, userId);
            return 1;
        }

                   
        LambdaQueryWrapper<SongVote> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(SongVote::getUserId, userId)
                .eq(SongVote::getVoteDate, today);

        List<SongVote> todayVotes = songVoteMapper.selectList(countWrapper);
        int totalTodayVotes = todayVotes.size();

        if (totalTodayVotes >= MAX_DAILY_VOTES) {
            log.warn("今日投票次数已达上限: userId={}, count={}", userId, totalTodayVotes);
            return 0;
        }

                   
        SongVote songVote = new SongVote();
        songVote.setSongId(songId);
        songVote.setUserId(userId);
        songVote.setVoteDate(today);
        songVote.setVoteCount(1);
        songVote.setCreateTime(LocalDateTime.now());

        int result = songVoteMapper.insert(songVote);

        log.info("投票成功: songId={}, userId={}, result={}", songId, userId, result);

        return result > 0 ? 1 : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unvote(Long songId, Long userId) {
        if (ObjectUtils.isEmpty(songId) || ObjectUtils.isEmpty(userId)) {
            log.warn("取消投票参数为空: songId={}, userId={}", songId, userId);
            return false;
        }

        UserAccountStatusUtil.requireCanInteract(userMapper.selectByIdForUpdate(userId), "取消投票");
        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<SongVote> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongVote::getSongId, songId)
                .eq(SongVote::getUserId, userId)
                .eq(SongVote::getVoteDate, today);

        int result = songVoteMapper.delete(wrapper);

        log.info("取消投票成功: songId={}, userId={}, result={}", songId, userId, result);

        return result > 0;
    }

    @Override
    public List<Map<String, Object>> getHotVotedSongs(Integer limit, Long userId) {
        return buildVoteSongList(limit, userId);
    }

    @Override
    public Map<String, Object> getTodayVoteStats(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            Map<String, Object> result = new HashMap<>();
            result.put("remainingCount", MAX_DAILY_VOTES);
            result.put("todayVoted", 0);
            result.put("maxDaily", MAX_DAILY_VOTES);
            return result;
        }

        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<SongVote> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongVote::getUserId, userId)
                .eq(SongVote::getVoteDate, today);

        List<SongVote> todayVotes = songVoteMapper.selectList(wrapper);
        int totalTodayVotes = todayVotes.size();

        int remainingCount = Math.max(0, MAX_DAILY_VOTES - totalTodayVotes);

        Map<String, Object> result = new HashMap<>();
        result.put("remainingCount", remainingCount);
        result.put("todayVoted", totalTodayVotes);
        result.put("maxDaily", MAX_DAILY_VOTES);

        return result;
    }

    @Override
    public Boolean hasVoted(Long songId, Long userId) {
        if (ObjectUtils.isEmpty(songId) || ObjectUtils.isEmpty(userId)) {
            return false;
        }

        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<SongVote> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongVote::getSongId, songId)
                .eq(SongVote::getUserId, userId)
                .eq(SongVote::getVoteDate, today);

        Long count = songVoteMapper.selectCount(wrapper);

        return count != null && count > 0;
    }

    @Override
    public List<Map<String, Object>> getPersonalizedVotedSongs(Long userId, Integer limit) {
        return buildVoteSongList(limit, userId);
    }

    private List<Map<String, Object>> buildVoteSongList(Integer limit, Long userId) {
        int resolvedLimit = ObjectUtils.isEmpty(limit) || limit <= 0
                ? DEFAULT_LIST_LIMIT
                : Math.min(limit, MAX_LIST_LIMIT);

        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
        LambdaQueryWrapper<SongVote> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(SongVote::getVoteDate, sevenDaysAgo)
                .orderByDesc(SongVote::getVoteCount)
                .last("LIMIT " + (resolvedLimit * VOTE_CANDIDATE_MULTIPLIER));

        List<SongVote> votes = songVoteMapper.selectList(wrapper);
        Set<Long> eligibleUserIds = getEligibleRecommendationUserIds(votes.stream()
                .map(SongVote::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        Map<Long, Integer> songVoteMap = new HashMap<>();
        for (SongVote vote : votes) {
            if (vote == null || vote.getSongId() == null || !eligibleUserIds.contains(vote.getUserId())) {
                continue;
            }
            Long songId = vote.getSongId();
            Integer count = vote.getVoteCount() != null ? vote.getVoteCount() : 0;
            songVoteMap.put(songId, songVoteMap.getOrDefault(songId, 0) + count);
        }

        List<Map.Entry<Long, Integer>> topEntries = songVoteMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(resolvedLimit)
                .collect(Collectors.toList());
        if (topEntries.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> songIds = topEntries.stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        Map<Long, Song> songMap = songMapper.selectBatchIds(songIds).stream()
                .collect(Collectors.toMap(Song::getId, song -> song, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : topEntries) {
            Song song = songMap.get(entry.getKey());
            if (!isVoteEligible(song)) {
                continue;
            }
            result.add(buildVoteSongVO(song, entry.getValue(), userId));
        }
        return result;
    }

    private boolean isVoteEligible(Song song) {
        return song != null
                && !Integer.valueOf(1).equals(song.getDeleted())
                && Integer.valueOf(1).equals(song.getStatus())
                && "published".equalsIgnoreCase(song.getPublishStatus())
                && "approved".equalsIgnoreCase(song.getReviewStatus());
    }

    private Map<String, Object> buildVoteSongVO(Song song, Integer voteCount, Long userId) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("songId", song.getId());
        vo.put("songName", firstNotBlank(song.getName(), "未知歌曲"));
        vo.put("artistName", firstNotBlank(song.getArtistNames(), "未知歌手"));
        vo.put("coverUrl", song.getCover());
        vo.put("cover", song.getCover());
        vo.put("voteCount", voteCount != null ? voteCount : 0);
        vo.put("genre", firstNotBlank(song.getMainGenre(), song.getMainType(), "推荐"));
        vo.put("isVoted", !ObjectUtils.isEmpty(userId) && hasVoted(song.getId(), userId));
        return vo;
    }

    private Set<Long> getEligibleRecommendationUserIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptySet();
        }

        return userMapper.selectBatchIds(userIds).stream()
                .filter(UserAccountStatusUtil::canAppearInRecommendations)
                .map(User::getId)
                .collect(Collectors.toSet());
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }
}

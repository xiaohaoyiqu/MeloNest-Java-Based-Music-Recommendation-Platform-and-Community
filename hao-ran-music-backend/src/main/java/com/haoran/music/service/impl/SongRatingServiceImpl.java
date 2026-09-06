package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.UserAccountPolicyConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.dto.song.SongRatingDTO;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.SongRating;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.SongRatingMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.SongRatingService;
import com.haoran.music.service.RecommendService;
import com.haoran.music.vo.song.SongRatingVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;





@Slf4j
@Service
public class SongRatingServiceImpl implements SongRatingService {

    @Resource
    private SongRatingMapper songRatingMapper;

    @Resource
    private SongMapper songMapper;

    @Resource
    private RecommendService recommendService;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private UserMapper userMapper;

    @Resource
    private com.haoran.music.service.UserVipService userVipService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SongRatingVO rateSong(Long userId, SongRatingDTO dto) {
        User currentUser = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(currentUser, "评分歌曲");


        validateRatingValue(dto.getRating());


        checkOperationLimit(userId, currentUser, dto.getSongId(), dto.getRating());


        Song song = songMapper.selectById(dto.getSongId());
        if (ObjectUtils.isEmpty(song)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }


        LambdaQueryWrapper<SongRating> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongRating::getUserId, userId)
                .eq(SongRating::getSongId, dto.getSongId());

        SongRating existingRating = songRatingMapper.selectOne(wrapper);

        if (ObjectUtils.isNotEmpty(existingRating)) {

            checkModifyCooldown(userId, dto.getSongId());


            existingRating.setRating(dto.getRating());
            existingRating.setUpdateTime(LocalDateTime.now());
            songRatingMapper.updateById(existingRating);


            recordModifyTime(userId, dto.getSongId());

            log.info("用户修改评分: userId={}, songId={}, oldRating={}, newRating={}",
                userId, dto.getSongId(), existingRating.getRating(), dto.getRating());
        } else {

            SongRating songRating = new SongRating();
            songRating.setUserId(userId);
            songRating.setSongId(dto.getSongId());
            songRating.setRating(dto.getRating());
            songRating.setCreateTime(LocalDateTime.now());
            songRatingMapper.insert(songRating);

            log.info("用户评分歌曲: userId={}, songId={}, rating={}", userId, dto.getSongId(), dto.getRating());
        }


        try {
            recommendService.recordUserAction(userId, "rate", dto.getSongId(), 1);
        } catch (Exception e) {
            log.warn("记录评分行为失败: {}", e.getClass().getSimpleName());
        }


        recordOperation(userId);

        return getSongRating(dto.getSongId(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRateSong(Long userId, Map<Long, Integer> ratings) {
        User currentUser = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(currentUser, "批量评分歌曲");


        if (ratings.size() > CommonConstants.RATING_BATCH_MAX) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "批量评分最多" + CommonConstants.RATING_BATCH_MAX + "首歌曲");
        }


        for (Map.Entry<Long, Integer> entry : ratings.entrySet()) {
            validateRatingValue(entry.getValue());
        }


        int dailyCount = getDailyCount(userId);
        int maxDaily = getMaxDailyRatings(currentUser);
        int remaining = maxDaily - dailyCount;

        if (ratings.size() > remaining) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                "今日剩余评分次数为" + remaining + "次，无法批量评分" + ratings.size() + "首歌曲");
        }


        for (Map.Entry<Long, Integer> entry : ratings.entrySet()) {
            Long songId = entry.getKey();
            Integer rating = entry.getValue();


            Song song = songMapper.selectById(songId);
            if (ObjectUtils.isEmpty(song)) {
                log.warn("批量评分跳过不存在的歌曲: songId={}", songId);
                continue;
            }


            LambdaQueryWrapper<SongRating> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SongRating::getUserId, userId)
                    .eq(SongRating::getSongId, songId);

            SongRating existingRating = songRatingMapper.selectOne(wrapper);

            if (ObjectUtils.isNotEmpty(existingRating)) {
                existingRating.setRating(rating);
                existingRating.setUpdateTime(LocalDateTime.now());
                songRatingMapper.updateById(existingRating);
            } else {
                SongRating newRating = new SongRating();
                newRating.setUserId(userId);
                newRating.setSongId(songId);
                newRating.setRating(rating);
                newRating.setCreateTime(LocalDateTime.now());
                songRatingMapper.insert(newRating);
            }


            recordOperation(userId);


            try {
                recommendService.recordUserAction(userId, "rate", songId, 1);
            } catch (Exception e) {
                log.warn("记录批量评分行为失败: songId={}, error={}", songId, e.getClass().getSimpleName());
            }
        }

        log.info("用户批量评分完成: userId={}, count={}", userId, ratings.size());
    }

    @Override
    public SongRatingVO getSongRating(Long songId, Long userId) {
        SongRatingVO vo = new SongRatingVO();
        vo.setSongId(songId);

        boolean canExposeRatedSong = canExposeRatedSong(songId);


        Double avgRating = canExposeRatedSong ? songRatingMapper.getAvgRatingBySongId(songId) : 0.0;
        vo.setAvgRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);


        Integer ratingCount = canExposeRatedSong ? songRatingMapper.getRatingCountBySongId(songId) : 0;
        vo.setRatingCount(ratingCount != null ? ratingCount : 0);


        Integer userRating = null;
        if (userId != null) {
            userRating = songRatingMapper.getUserRating(userId, songId);
        }
        vo.setUserRating(userRating);


        Map<String, Object> distribution = canExposeRatedSong ? getRatingDistribution(songId) : new HashMap<>();
        SongRatingVO.RatingDistribution dist = new SongRatingVO.RatingDistribution();
        dist.setFiveStar((Integer) distribution.getOrDefault("5", 0));
        dist.setFourStar((Integer) distribution.getOrDefault("4", 0));
        dist.setThreeStar((Integer) distribution.getOrDefault("3", 0));
        dist.setTwoStar((Integer) distribution.getOrDefault("2", 0));
        dist.setOneStar((Integer) distribution.getOrDefault("1", 0));
        vo.setDistribution(dist);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRating(Long userId, Long songId) {

        checkOperationLimit(userId, userMapper.selectById(userId), songId, null);

        LambdaQueryWrapper<SongRating> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongRating::getUserId, userId)
                .eq(SongRating::getSongId, songId);

        SongRating rating = songRatingMapper.selectOne(wrapper);
        if (ObjectUtils.isNotEmpty(rating)) {
            songRatingMapper.deleteById(rating.getId());
            log.info("用户删除评分: userId={}, songId={}", userId, songId);


            String cacheKey = "song:rating:" + songId;
            redisUtils.delete(cacheKey);
            log.info("清除歌曲评分缓存: songId={}", songId);


            String modifyKey = CommonConstants.RATING_SONG_MODIFY_KEY + userId + ":" + songId;
            redisUtils.delete(modifyKey);


            recordOperation(userId);
        }
    }

    @Override
    public Object getUserRatings(Long userId) {
        LambdaQueryWrapper<SongRating> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongRating::getUserId, userId)
                .orderByDesc(SongRating::getCreateTime);

        return songRatingMapper.selectList(wrapper);
    }

    @Override
    public Map<String, Object> getUserRatingStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();


        LambdaQueryWrapper<SongRating> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongRating::getUserId, userId);

        List<SongRating> ratings = songRatingMapper.selectList(wrapper);


        Map<Integer, Integer> distribution = new HashMap<>();
        distribution.put(1, 0);
        distribution.put(2, 0);
        distribution.put(3, 0);
        distribution.put(4, 0);
        distribution.put(5, 0);

        int totalScore = 0;
        for (SongRating rating : ratings) {
            distribution.put(rating.getRating(), distribution.getOrDefault(rating.getRating(), 0) + 1);
            totalScore += rating.getRating();
        }

        stats.put("totalRatings", ratings.size());
        stats.put("averageRating", ratings.isEmpty() ? 0 : Math.round((double) totalScore / ratings.size() * 10.0) / 10.0);
        stats.put("distribution", distribution);


        int dailyCount = getDailyCount(userId);
        int maxDaily = getMaxDailyRatings(userId);
        stats.put("dailyUsed", dailyCount);
        stats.put("dailyRemaining", Math.max(0, maxDaily - dailyCount));
        stats.put("dailyMax", maxDaily);

        return stats;
    }







    private void validateRatingValue(Integer rating) {
        if (ObjectUtils.isEmpty(rating) ||
            rating < CommonConstants.RATING_MIN ||
            rating > CommonConstants.RATING_MAX) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                CommonConstants.ERROR_RATING_OUT_OF_RANGE);
        }
    }









    private void checkOperationLimit(Long userId, User currentUser, Long songId, Integer rating) {
        long currentTime = System.currentTimeMillis();


        String cooldownKey = CommonConstants.RATING_COOLDOWN_KEY + userId;
        Object lastOperationTime = redisUtils.get(cooldownKey);

        if (lastOperationTime != null) {
            long lastTime = Long.parseLong(lastOperationTime.toString());
            long elapsedSeconds = (currentTime - lastTime) / 1000;

            if (elapsedSeconds < CommonConstants.RATING_COOLDOWN_SECONDS) {
                int remainingSeconds = CommonConstants.RATING_COOLDOWN_SECONDS - (int) elapsedSeconds;
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    CommonConstants.ERROR_RATING_TOO_FREQUENT + "，请" + remainingSeconds + "秒后再试");
            }
        }


        int dailyCount = getDailyCount(userId);
        int maxDaily = getMaxDailyRatings(currentUser);

        if (dailyCount >= maxDaily) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                CommonConstants.ERROR_RATING_DAILY_LIMIT);
        }


        if (rating != null && songId != null) {
            checkAnomalousRating(userId, rating);
        }
    }








    private void checkModifyCooldown(Long userId, Long songId) {
        String modifyKey = CommonConstants.RATING_SONG_MODIFY_KEY + userId + ":" + songId;
        Object lastModifyTime = redisUtils.get(modifyKey);

        if (lastModifyTime != null) {
            long lastTime = Long.parseLong(lastModifyTime.toString());
            long elapsedHours = (System.currentTimeMillis() - lastTime) / (1000 * 60 * 60);

            if (elapsedHours < CommonConstants.RATING_MODIFY_COOLDOWN_HOURS) {
                int remainingHours = CommonConstants.RATING_MODIFY_COOLDOWN_HOURS - (int) elapsedHours;
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    CommonConstants.ERROR_RATING_MODIFY_COOLDOWN + "，请" + remainingHours + "小时后再试");
            }
        }
    }







    private void checkAnomalousRating(Long userId, Integer rating) {

        LambdaQueryWrapper<SongRating> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongRating::getUserId, userId)
                .orderByDesc(SongRating::getCreateTime)
                .last("LIMIT 10");

        List<SongRating> recentRatings = songRatingMapper.selectList(wrapper);

        if (recentRatings.size() >= CommonConstants.RATING_ANOMALY_THRESHOLD) {

            boolean allSame = true;
            Integer firstRating = recentRatings.get(0).getRating();

            for (SongRating r : recentRatings) {
                if (!r.getRating().equals(firstRating)) {
                    allSame = false;
                    break;
                }
            }

            if (allSame && rating.equals(firstRating)) {
                log.warn("检测到异常评分行为: userId={}, 重复评分={}", userId, rating);

            }
        }
    }







    private void recordModifyTime(Long userId, Long songId) {
        String modifyKey = CommonConstants.RATING_SONG_MODIFY_KEY + userId + ":" + songId;
        long currentTime = System.currentTimeMillis();


        redisUtils.set(modifyKey, currentTime,
            CommonConstants.RATING_MODIFY_COOLDOWN_HOURS + 1, TimeUnit.HOURS);
    }






    private void recordOperation(Long userId) {
        long currentTime = System.currentTimeMillis();


        String cooldownKey = CommonConstants.RATING_COOLDOWN_KEY + userId;
        redisUtils.set(cooldownKey, currentTime,
            CommonConstants.RATING_COOLDOWN_SECONDS + 1, TimeUnit.SECONDS);


        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String dailyKey = CommonConstants.RATING_DAILY_KEY + userId + ":" + today;


        long endOfToday = LocalDate.now().plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.of("+8"));
        long currentSeconds = currentTime / 1000;
        long secondsUntilTomorrow = endOfToday - currentSeconds;

        Long newCount = redisUtils.increment(dailyKey);


        if (newCount != null && newCount == 1) {
            redisUtils.expire(dailyKey, secondsUntilTomorrow, TimeUnit.SECONDS);
        }

        log.info("用户评分操作记录: userId={}, dailyCount={}", userId, newCount);
    }







    private int getDailyCount(Long userId) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String dailyKey = CommonConstants.RATING_DAILY_KEY + userId + ":" + today;

        Object dailyCount = redisUtils.get(dailyKey);
        return dailyCount != null ? Integer.parseInt(dailyCount.toString()) : 0;
    }







    private int getMaxDailyRatings(Long userId) {
        return getMaxDailyRatings(userMapper.selectById(userId));
    }







    private int getMaxDailyRatings(User currentUser) {

        int maxDaily = CommonConstants.RATING_DAILY_MAX_NORMAL;

        try {
            if (ObjectUtils.isNotEmpty(currentUser)) {

                if (Boolean.TRUE.equals(userVipService.isVip(currentUser.getId()))) {
                    maxDaily = CommonConstants.RATING_DAILY_MAX_VIP;
                }


                if (currentUser.getCreditScore() != null &&
                    currentUser.getCreditScore() >= UserAccountPolicyConstants.CREDIT_SCORE_GOOD_MIN &&
                    maxDaily < CommonConstants.RATING_DAILY_MAX_EXCELLENT) {
                    maxDaily = CommonConstants.RATING_DAILY_MAX_EXCELLENT;
                }
            }
        } catch (Exception e) {
            log.warn("获取用户VIP状态失败，使用默认限制: userId={}",
                currentUser != null ? currentUser.getId() : null);
        }

        return maxDaily;
    }







    private Map<String, Object> getRatingDistribution(Long songId) {
        Map<String, Object> distribution = new HashMap<>();

        List<Map<String, Object>> rows = songRatingMapper.getRatingDistributionBySongId(songId);
        for (Map<String, Object> row : rows) {
            Integer star = toInteger(row.get("rating") != null ? row.get("rating") : row.get("RATING"));
            Integer count = toInteger(row.get("count") != null ? row.get("count") : row.get("COUNT"));
            if (star != null && count != null) {
                distribution.put(star.toString(), count);
            }
        }

        return distribution;
    }

    private boolean canExposeRatedSong(Long songId) {
        if (songId == null) {
            return false;
        }
        Song song = songMapper.selectById(songId);
        if (song == null) {
            return false;
        }
        if (song.getUploaderId() == null) {
            return true;
        }
        User uploader = userMapper.selectById(song.getUploaderId());
        return UserAccountStatusUtil.canExposePublicContent(uploader);
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return value != null ? Integer.valueOf(value.toString()) : null;
    }
}

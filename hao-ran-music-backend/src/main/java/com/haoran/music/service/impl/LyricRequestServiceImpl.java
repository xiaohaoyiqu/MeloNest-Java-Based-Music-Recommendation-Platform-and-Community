package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.config.LyricRequestConfig;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.dto.lyric.LyricRequestDTO;
import com.haoran.music.entity.Lyric;
import com.haoran.music.entity.LyricRequest;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.LyricMapper;
import com.haoran.music.mapper.LyricRequestMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.LyricRequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;





@Slf4j
@Service
public class LyricRequestServiceImpl extends ServiceImpl<LyricRequestMapper, LyricRequest> implements LyricRequestService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_LYRIC_CONTENT_LENGTH = 20000;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    @Autowired
    private LyricMapper lyricMapper;

    @Autowired
    private SongMapper songMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private LyricRequestConfig lyricRequestConfig;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitRequest(Long userId, LyricRequestDTO dto) {
        User user = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(user, "提交歌词修正");

        reserveSubmitQuota(userId, dto.getSongId());
        boolean saved = false;
        try {
            Song song = songMapper.selectById(dto.getSongId());
            if (song == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
            }

            Integer changeType = normalizeChangeType(dto);
            String originalLyric = normalizeOriginalContent(dto.getOriginalLyric(), changeType);
            String correctedLyric = requireContent(dto.getCorrectedLyric(), "修正后歌词");
            String changeDescription = normalizeDescription(dto);

            LyricRequest request = new LyricRequest();
            request.setUserId(userId);
            request.setUserName(user.getUsername());
            request.setSongId(dto.getSongId());
            request.setSongName(StrUtil.blankToDefault(dto.getSongName(), song.getName()));
            request.setOriginalLyric(originalLyric);
            request.setCorrectedLyric(correctedLyric);
            request.setChangeDescription(changeDescription);
            request.setChangeType(changeType);
            request.setStatus(0);
            request.setIsApplied(CommonConstants.NO);
            request.setCreateTime(LocalDateTime.now());
            request.setUpdateTime(LocalDateTime.now());
            request.setDeleted(CommonConstants.NOT_DELETED);

            save(request);
            saved = true;

            log.info("event=lyric_correction_submitted userId={} songId={} requestId={}",
                    userId, dto.getSongId(), request.getId());
            return request.getId();
        } finally {
            if (!saved) {
                releaseSubmitQuota(userId, dto.getSongId());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewRequest(Long requestId, Long reviewerId, Integer status, String reviewReason) {
        if (requestId == null || reviewerId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审核参数不能为空");
        }
        if (!CommonConstants.YES.equals(status) && !Integer.valueOf(2).equals(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审核状态只能是通过或退回");
        }
        String safeReason = StrUtil.trim(reviewReason);
        if (safeReason != null && safeReason.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审核意见不能超过500个字符");
        }
        if (Integer.valueOf(2).equals(status) && StrUtil.isBlank(safeReason)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "退回时请写明原因");
        }
        LyricRequest request = getById(requestId);
        if (request == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "纠错申请不存在");
        }

        int updated = baseMapper.update(null, new LambdaUpdateWrapper<LyricRequest>()
                .eq(LyricRequest::getId, requestId)
                .eq(LyricRequest::getStatus, 0)
                .eq(LyricRequest::getDeleted, CommonConstants.NOT_DELETED)
                .set(LyricRequest::getStatus, status)
                .set(LyricRequest::getReviewerId, reviewerId)
                .set(LyricRequest::getReviewTime, LocalDateTime.now())
                .set(LyricRequest::getReviewReason, safeReason)
                .set(LyricRequest::getUpdateTime, LocalDateTime.now()));
        if (updated != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "这份申请已经处理过了");
        }

        log.info("event=lyric_correction_reviewed requestId={} reviewerId={} status={}",
                requestId, reviewerId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyRequest(Long requestId, Long operatorId) {
        if (requestId == null || operatorId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "应用参数不能为空");
        }
        LyricRequest request = getById(requestId);
        if (request == null || !CommonConstants.YES.equals(request.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只能应用已审核通过的纠错");
        }
        if (CommonConstants.YES.equals(request.getIsApplied())) {
            return;
        }

        int claimed = baseMapper.update(null, new LambdaUpdateWrapper<LyricRequest>()
                .eq(LyricRequest::getId, requestId)
                .eq(LyricRequest::getStatus, CommonConstants.YES)
                .eq(LyricRequest::getIsApplied, CommonConstants.NO)
                .eq(LyricRequest::getDeleted, CommonConstants.NOT_DELETED)
                .set(LyricRequest::getIsApplied, CommonConstants.YES)
                .set(LyricRequest::getAppliedTime, LocalDateTime.now())
                .set(LyricRequest::getUpdateTime, LocalDateTime.now()));
        if (claimed != 1) {
            return;
        }

        Song song = songMapper.selectById(request.getSongId());
        if (song == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        String lyricLanguage = StrUtil.blankToDefault(song.getLyricLanguage(),
                StrUtil.blankToDefault(song.getLanguage(), CommonConstants.DEFAULT_LYRIC_LANGUAGE)).trim();

        LambdaQueryWrapper<Lyric> wrapper = new LambdaQueryWrapper<Lyric>()
                .eq(Lyric::getSongId, request.getSongId())
                .eq(Lyric::getLyricType, CommonConstants.LYRIC_TYPE_ORIGINAL)
                .eq(Lyric::getLanguage, lyricLanguage)
                .eq(Lyric::getStatus, CommonConstants.STATUS_NORMAL)
                .orderByDesc(Lyric::getCreateTime)
                .last("LIMIT 1");
        Lyric lyric = lyricMapper.selectOne(wrapper);

        if (lyric != null) {
            if (!StrUtil.equals(lyric.getContent(), request.getOriginalLyric())) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "正式歌词已经更新，请按新版本重新提交修正");
            }
            lyric.setContent(request.getCorrectedLyric());
            lyric.setSource(CommonConstants.LYRIC_SOURCE_ADMIN);
            lyric.setUpdateTime(LocalDateTime.now());
            lyricMapper.updateById(lyric);
        } else {
            lyric = new Lyric();
            lyric.setSongId(request.getSongId());
            lyric.setLyricType(CommonConstants.LYRIC_TYPE_ORIGINAL);
            lyric.setLanguage(lyricLanguage);
            lyric.setContent(request.getCorrectedLyric());
            lyric.setSource(CommonConstants.LYRIC_SOURCE_ADMIN);
            lyric.setCreatorId(request.getReviewerId());
            lyric.setStatus(CommonConstants.STATUS_NORMAL);
            lyric.setDeleted(CommonConstants.NOT_DELETED);
            lyric.setCreateTime(LocalDateTime.now());
            lyric.setUpdateTime(LocalDateTime.now());
            lyricMapper.insert(lyric);
        }

        song.setHasLyric(CommonConstants.YES);
        if (StrUtil.isBlank(song.getLyricLanguage())) {
            song.setLyricLanguage(lyricLanguage);
        }
        songMapper.updateById(song);

        log.info("event=lyric_correction_applied requestId={} songId={} operatorId={}",
                request.getId(), request.getSongId(), operatorId);
    }

    @Override
    public IPage<LyricRequest> pageRequests(Integer current, Integer size, Integer status) {
        int safeCurrent = current == null || current <= 0 ? 1 : current;
        int safeSize = size == null || size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        Page<LyricRequest> page = new Page<>(safeCurrent, safeSize);
        LambdaQueryWrapper<LyricRequest> wrapper = new LambdaQueryWrapper<LyricRequest>()
                .eq(LyricRequest::getDeleted, 0)
                .orderByDesc(LyricRequest::getCreateTime);

        if (status != null && status != 0 && status != 1 && status != 2) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申请状态参数不合法");
        }
        if (status != null) {
            wrapper.eq(LyricRequest::getStatus, status);
        }

        return page(page, wrapper);
    }

    @Override
    public List<LyricRequest> getSongRequests(Long songId) {
        return list(
                new LambdaQueryWrapper<LyricRequest>()
                        .eq(LyricRequest::getSongId, songId)
                        .eq(LyricRequest::getDeleted, 0)
                        .orderByDesc(LyricRequest::getCreateTime)
                        .last("LIMIT 100")
        );
    }

    @Override
    public Boolean canSubmit(Long userId, Long songId) {
        if (userId == null || songId == null) {
            return false;
        }
        String submitKey = lyricRequestConfig.getSubmitPrefix() + songId + ":" + userId;
        String count = redisTemplate.opsForValue().get(submitKey);
        if (StrUtil.isBlank(count)) {
            return true;
        }
        try {
            return Integer.parseInt(count) < lyricRequestConfig.getSubmitLimitPerSong();
        } catch (NumberFormatException e) {
            redisTemplate.delete(submitKey);
            return true;
        }
    }

    private String requireContent(String content, String fieldName) {
        if (StrUtil.isBlank(content)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, fieldName + "不能为空");
        }
        String trimmed = content.trim();
        if (trimmed.length() > MAX_LYRIC_CONTENT_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, fieldName + "不能超过" + MAX_LYRIC_CONTENT_LENGTH + "个字符");
        }
        return trimmed;
    }

    private String normalizeOriginalContent(String content, Integer changeType) {
        if (StrUtil.isBlank(content)) {
            if (Integer.valueOf(2).equals(changeType)) {
                return "";
            }
            throw new BusinessException(ResultCode.PARAM_ERROR, "原始歌词不能为空");
        }
        return requireContent(content, "原始歌词");
    }

    private void reserveSubmitQuota(Long userId, Long songId) {
        if (userId == null || songId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID和歌曲ID不能为空");
        }
        String submitKey = submitKey(userId, songId);
        Long count = redisTemplate.opsForValue().increment(submitKey);
        if (count != null && count == 1) {
            redisTemplate.expire(submitKey,
                    lyricRequestConfig.getSubmitLimitPeriodHours(), TimeUnit.HOURS);
        }
        if (count == null || count > lyricRequestConfig.getSubmitLimitPerSong()) {
            if (count != null) {
                redisTemplate.opsForValue().decrement(submitKey);
            }
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "同一首歌每天最多提交" + lyricRequestConfig.getSubmitLimitPerSong() + "次修正");
        }
    }

    private void releaseSubmitQuota(Long userId, Long songId) {
        String submitKey = submitKey(userId, songId);
        Long remaining = redisTemplate.opsForValue().decrement(submitKey);
        if (remaining != null && remaining <= 0) {
            redisTemplate.delete(submitKey);
        }
    }

    private String submitKey(Long userId, Long songId) {
        return lyricRequestConfig.getSubmitPrefix() + songId + ":" + userId;
    }

    private String normalizeDescription(LyricRequestDTO dto) {
        String description = StrUtil.blankToDefault(dto.getChangeDescription(), dto.getDescription());
        if (StrUtil.isBlank(description)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "修改说明不能为空");
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "修改说明不能超过" + MAX_DESCRIPTION_LENGTH + "个字符");
        }
        return trimmed;
    }

    private Integer normalizeChangeType(LyricRequestDTO dto) {
        if (dto.getChangeType() != null) {
            int changeType = dto.getChangeType();
            if (changeType < 1 || changeType > 4) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "修改类型参数不合法");
            }
            return changeType;
        }
        String correctionType = dto.getCorrectionType();
        if (StrUtil.isBlank(correctionType)) {
            return 1;
        }
        switch (correctionType.trim().toLowerCase()) {
            case "typo":
            case "text":
            case "1":
                return 1;
            case "add":
            case "missing":
            case "2":
                return 2;
            case "format":
            case "3":
                return 3;
            case "timeline":
            case "time":
            case "4":
                return 4;
            default:
                return 1;
        }
    }
}

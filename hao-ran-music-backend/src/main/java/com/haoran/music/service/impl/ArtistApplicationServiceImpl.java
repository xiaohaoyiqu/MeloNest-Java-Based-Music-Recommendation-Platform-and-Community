package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.dto.artist.ArtistApplicationDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.config.ArtistApplicationConfig;
import com.haoran.music.entity.ArtistApplication;
import com.haoran.music.mapper.ArtistApplicationMapper;
import com.haoran.music.service.ArtistApplicationService;
import com.haoran.music.service.CreatorEligibilityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;





@Slf4j
@Service
public class ArtistApplicationServiceImpl extends ServiceImpl<ArtistApplicationMapper, ArtistApplication> implements ArtistApplicationService {

    @Autowired
    private ArtistApplicationMapper applicationMapper;

    @Autowired
    private CreatorEligibilityService creatorEligibilityService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ArtistApplicationConfig artistApplicationConfig;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitApplication(Long userId, ArtistApplicationDTO dto) {

        if (!canApply(userId)) {
            throw new RuntimeException("application rate limit reached: " + artistApplicationConfig.getMaxAppliesPerPeriod());
        }

        ArtistApplication application = new ArtistApplication();
        application.setUserId(userId);
        application.setRealName(dto.getRealName());
        application.setPhone(dto.getPhone());
        application.setEmail(dto.getEmail());
        application.setIntroduction(dto.getIntroduction());
        application.setDemoWorks(dto.getDemoWorks());
        application.setApplicationType(dto.getApplicationType());
        application.setStatus(0);       
        application.setCreateTime(LocalDateTime.now());

        applicationMapper.insert(application);


        String countKey = artistApplicationConfig.getApplyCountPrefix() + userId;
        Long count = redisTemplate.opsForValue().increment(countKey);
        if (count != null && count == 1) {
            redisTemplate.expire(countKey, artistApplicationConfig.getApplyLimitDays(), TimeUnit.DAYS);
        }


        String lastApplyKey = artistApplicationConfig.getApplyPrefix() + userId;
        redisTemplate.opsForValue().set(lastApplyKey, String.valueOf(System.currentTimeMillis()), 
                artistApplicationConfig.getApplyLimitDays(), TimeUnit.DAYS);

        log.info("event=artist_application_submitted userId={}", userId);
        return application.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewApplication(Long applicationId, Long reviewerId, Integer status, String reviewReason) {
        if (!Integer.valueOf(1).equals(status) && !Integer.valueOf(2).equals(status)) {
            throw new IllegalArgumentException("审核状态仅支持通过或拒绝");
        }
        ArtistApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new RuntimeException("申请记录不存在");
        }

        if (!Integer.valueOf(0).equals(application.getStatus())) {
            throw new IllegalStateException("申请已处理，请勿重复审核");
        }
        if (applicationMapper.reviewPending(applicationId, reviewerId, status, reviewReason) != 1) {
            throw new IllegalStateException("申请已被其他审核人处理");
        }

        if (status.equals(1)) {
            creatorEligibilityService.activate(
                    application.getUserId(),
                    toCreatorType(application.getApplicationType()),
                    reviewerId,
                    "artist application approved",
                    null);
        }

        log.info("event=artist_application_reviewed applicationId={} reviewerId={} status={}",
                applicationId, reviewerId, status);
    }

    @Override
    public ArtistApplication getUserApplication(Long userId) {
        return applicationMapper.selectOne(
            new LambdaQueryWrapper<ArtistApplication>()
                .eq(ArtistApplication::getUserId, userId)
                .eq(ArtistApplication::getDeleted, 0)
                .orderByDesc(ArtistApplication::getCreateTime)

        );
    }

    @Override
    public IPage<ArtistApplication> pageApplications(Integer current, Integer size, Integer status) {
        Page<ArtistApplication> page = new Page<>(current, size);
        LambdaQueryWrapper<ArtistApplication> wrapper = new LambdaQueryWrapper<ArtistApplication>()
                .eq(ArtistApplication::getDeleted, 0)
                .orderByDesc(ArtistApplication::getCreateTime);

        if (status != null) {
            wrapper.eq(ArtistApplication::getStatus, status);
        }

        return applicationMapper.selectPage(page, wrapper);
    }

    @Override
    public Boolean canApply(Long userId) {

        String countKey = artistApplicationConfig.getApplyCountPrefix() + userId;
        String count = redisTemplate.opsForValue().get(countKey);
        if (count != null && Integer.parseInt(count) >= artistApplicationConfig.getMaxAppliesPerPeriod()) {
            return false;
        }


        ArtistApplication existing = getUserApplication(userId);
        if (existing != null && (existing.getStatus().equals(0) || existing.getStatus().equals(1))) {
            return false;
        }

        return true;
    }

    private String toCreatorType(Integer applicationType) {
        if (Integer.valueOf(1).equals(applicationType)) {
            return "singer";
        }
        if (Integer.valueOf(2).equals(applicationType)) {
            return "producer";
        }
        if (Integer.valueOf(3).equals(applicationType)) {
            return "lyricist";
        }
        return "independent";
    }
}

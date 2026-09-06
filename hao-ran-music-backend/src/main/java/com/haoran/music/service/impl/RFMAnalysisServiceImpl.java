package com.haoran.music.service.impl;

import com.alibaba.fastjson2.JSON;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.dto.user.RfmAggregateDTO;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.RfmAnalysisMapper;
import com.haoran.music.service.RFMAnalysisService;
import com.haoran.music.vo.user.RFMVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;





@Slf4j
@Service
public class RFMAnalysisServiceImpl implements RFMAnalysisService {

    @Autowired
    private RfmAnalysisMapper rfmAnalysisMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final int RFM_BATCH_SIZE = 500;




    private static final Integer RFM_CACHE_HOURS = 6;




    private static final Map<Integer, Integer> R_SCORE_STANDARD = new LinkedHashMap<Integer, Integer>() {{
        put(1, 7);             
        put(2, 14);             
        put(3, 30);             
        put(4, 60);             
        put(5, 999);             
    }};




    private static final Map<Integer, Integer> F_SCORE_STANDARD = new LinkedHashMap<Integer, Integer>() {{
        put(20, 5);              
        put(10, 4);               
        put(5, 3);               
        put(2, 2);              
        put(0, 1);              
    }};




    private static final Map<BigDecimal, Integer> M_SCORE_STANDARD = new LinkedHashMap<BigDecimal, Integer>() {{
        put(new BigDecimal("500"), 5);               
        put(new BigDecimal("200"), 4);                 
        put(new BigDecimal("100"), 3);                 
        put(new BigDecimal("30"), 2);                 
        put(BigDecimal.ZERO, 1);                    
    }};

    @Override
    public RFMVO calculateUserRFM(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return null;
        }


        String cacheKey = "rfm:user:" + userId;
        RFMVO cached = (RFMVO) redisTemplate.opsForValue().get(cacheKey);
        if (ObjectUtils.isNotEmpty(cached)) {
            return cached;
        }

        List<RfmAggregateDTO> aggregates = rfmAnalysisMapper.selectAggregates(Collections.singletonList(userId));
        if (aggregates == null || aggregates.isEmpty()) {
            return null;
        }

        RFMVO rfm = buildRfm(aggregates.get(0));


        cacheRfm(rfm);

        log.info("用户RFM分析完成: userId={}, segment={}, score={}", userId, rfm.getSegmentType(), rfm.getTotalScore());

        return rfm;
    }

    @Override
    public List<RFMVO> batchCalculateUserRFM(List<Long> userIds) {
        if (ObjectUtils.isEmpty(userIds)) {
            return new ArrayList<>();
        }

        List<Long> distinctUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, RFMVO> rfmByUserId = rfmAnalysisMapper.selectAggregates(distinctUserIds).stream()
                .map(this::buildRfm)
                .peek(this::cacheRfm)
                .collect(Collectors.toMap(RFMVO::getUserId, rfm -> rfm, (left, right) -> left));

        return userIds.stream()
                .map(rfmByUserId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Integer> getSegmentStatistics() {
        Map<String, Integer> stats = new LinkedHashMap<>();


        String cacheKey = "rfm:stats:segments";
        Map<String, Integer> cached = (Map<String, Integer>) redisTemplate.opsForValue().get(cacheKey);
        if (ObjectUtils.isNotEmpty(cached)) {
            return cached;
        }


        for (RFMVO.UserSegment segment : RFMVO.UserSegment.values()) {
            stats.put(segment.getCode(), 0);
        }

        Long lastUserId = 0L;
        while (true) {
            List<Long> userIds = userMapper.selectActiveUserIdsAfter(lastUserId, RFM_BATCH_SIZE);
            if (userIds == null || userIds.isEmpty()) {
                break;
            }

            for (RfmAggregateDTO aggregate : rfmAnalysisMapper.selectAggregates(userIds)) {
                RFMVO rfm = buildRfm(aggregate);
                if (ObjectUtils.isNotEmpty(rfm) && ObjectUtils.isNotEmpty(rfm.getSegmentType())) {
                    stats.merge(rfm.getSegmentType(), 1, Integer::sum);
                }
            }

            lastUserId = userIds.get(userIds.size() - 1);
        }


        redisTemplate.opsForValue().set(cacheKey, stats, 1, TimeUnit.HOURS);

        return stats;
    }

    @Override
    public List<RFMVO> getUsersBySegment(String segmentType, Integer page, Integer size) {
        if (ObjectUtils.isEmpty(segmentType)) {
            return new ArrayList<>();
        }


        int pageNum = ObjectUtils.isNotEmpty(page) ? Math.max(1, page) : 1;
        int pageSize = ObjectUtils.isNotEmpty(size) ? Math.min(Math.max(1, size), 100) : 20;
        long offset = (long) (pageNum - 1) * pageSize;
        long matchedCount = 0;
        List<RFMVO> result = new ArrayList<>(pageSize);

        Long lastUserId = 0L;
        while (result.size() < pageSize) {
            List<Long> userIds = userMapper.selectActiveUserIdsAfter(lastUserId, RFM_BATCH_SIZE);
            if (userIds == null || userIds.isEmpty()) {
                break;
            }

            for (RfmAggregateDTO aggregate : rfmAnalysisMapper.selectAggregates(userIds)) {
                RFMVO rfm = buildRfm(aggregate);
                if (!segmentType.equals(rfm.getSegmentType())) {
                    continue;
                }
                if (matchedCount++ < offset) {
                    continue;
                }
                result.add(rfm);
                if (result.size() >= pageSize) {
                    break;
                }
            }

            lastUserId = userIds.get(userIds.size() - 1);
        }

        return result;
    }

    @Override
    public void refreshUserRFMCache(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return;
        }

        String cacheKey = "rfm:user:" + userId;
        redisTemplate.delete(cacheKey);

        log.info("刷新用户RFM缓存: userId={}", userId);
    }

    @Override
    public Map<String, Object> getSegmentDistribution() {
        Map<String, Object> distribution = new LinkedHashMap<>();

        Map<String, Integer> stats = getSegmentStatistics();
        Integer total = stats.values().stream().mapToInt(Integer::intValue).sum();

        distribution.put("total", total);
        distribution.put("segments", stats);


        Map<String, BigDecimal> percentages = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            BigDecimal percentage = total > 0
                    ? new BigDecimal(entry.getValue()).divide(new BigDecimal(total), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                    : BigDecimal.ZERO;
            percentages.put(entry.getKey(), percentage);
        }
        distribution.put("percentages", percentages);

        return distribution;
    }

    @Override
    public Integer predictChurnProbability(Long userId) {
        RFMVO rfm = calculateUserRFM(userId);
        if (ObjectUtils.isEmpty(rfm)) {
            return 50;         
        }





        Integer rScore = rfm.getRecencyScore();
        Integer baseProbability;
        switch (rScore) {
            case 5:
                baseProbability = 5;
                break;
            case 4:
                baseProbability = 15;
                break;
            case 3:
                baseProbability = 40;
                break;
            case 2:
                baseProbability = 70;
                break;
            case 1:
                baseProbability = 90;
                break;
            default:
                baseProbability = 50;
                break;
        }

        if (rfm.getFrequencyScore() >= 4) {
            baseProbability = baseProbability + 10;
        }


        if (rfm.getMonetaryScore() >= 4) {
            baseProbability = baseProbability - 10;
        }

        return Math.min(100, Math.max(0, baseProbability));
    }

    @Override
    public String getSegmentAdvice(String segmentType) {
        if (ObjectUtils.isEmpty(segmentType)) {
            return "未知的用户分层";
        }

        for (RFMVO.UserSegment segment : RFMVO.UserSegment.values()) {
            if (segment.getCode().equals(segmentType)) {
                return segment.getAdvice();
            }
        }

        return "未找到对应分层的建议";
    }

    @Override
    public Map<String, Object> exportRFMReport() {
        Map<String, Object> report = new LinkedHashMap<>();

        report.put("generateTime", LocalDateTime.now());
        report.put("segmentStatistics", getSegmentStatistics());
        report.put("segmentDistribution", getSegmentDistribution());


        Map<String, List<RFMVO>> segmentDetails = new LinkedHashMap<>();
        for (RFMVO.UserSegment segment : RFMVO.UserSegment.values()) {
            segmentDetails.put(segment.getCode(), new ArrayList<>());
        }

        Long lastUserId = 0L;
        boolean allSamplesCollected = false;
        while (!allSamplesCollected) {
            List<Long> userIds = userMapper.selectActiveUserIdsAfter(lastUserId, RFM_BATCH_SIZE);
            if (userIds == null || userIds.isEmpty()) {
                break;
            }

            for (RfmAggregateDTO aggregate : rfmAnalysisMapper.selectAggregates(userIds)) {
                RFMVO rfm = buildRfm(aggregate);
                List<RFMVO> samples = segmentDetails.get(rfm.getSegmentType());
                if (samples != null && samples.size() < 10) {
                    samples.add(rfm);
                }
            }

            allSamplesCollected = segmentDetails.values().stream().allMatch(samples -> samples.size() >= 10);
            lastUserId = userIds.get(userIds.size() - 1);
        }
        report.put("segmentDetails", segmentDetails);

        return report;
    }

    private RFMVO buildRfm(RfmAggregateDTO aggregate) {
        RFMVO rfm = new RFMVO();
        rfm.setUserId(aggregate.getUserId());
        rfm.setUsername(aggregate.getUsername());
        rfm.setCalculateTime(LocalDateTime.now());

        User user = new User();
        user.setStatus(aggregate.getUserStatus());
        user.setIsBanned(aggregate.getUserBanned());
        user.setUserType(aggregate.getUserType());
        user.setRiskScore(aggregate.getRiskScore());
        user.setCreditScore(aggregate.getCreditScore());
        user.setCreatorStatus(aggregate.getCreatorStatus());
        rfm.setInteractionRestricted(!UserAccountStatusUtil.canInteract(user));
        rfm.setPublicFlowRestricted(!UserAccountStatusUtil.canContributePublicStats(user));


        LocalDateTime lastActiveTime = null;
        if (aggregate.getLastVipTime() != null) {
            lastActiveTime = aggregate.getLastVipTime();
        }
        if (aggregate.getLastCheckinTime() != null) {
            if (lastActiveTime == null || aggregate.getLastCheckinTime().isAfter(lastActiveTime)) {
                lastActiveTime = aggregate.getLastCheckinTime();
            }
        }

        rfm.setLastActiveTime(lastActiveTime);


        if (lastActiveTime != null) {
            long days = ChronoUnit.DAYS.between(lastActiveTime, LocalDateTime.now());
            rfm.setRecency((int) days);


            int rScore = 5;
            for (Map.Entry<Integer, Integer> entry : R_SCORE_STANDARD.entrySet()) {
                if (days <= entry.getValue()) {
                    rScore = 6 - entry.getKey();
                    break;
                }
            }
            rfm.setRecencyScore(rScore);
        } else {
            rfm.setRecency(999);
            rfm.setRecencyScore(1);
        }



        int vipCount = aggregate.getVipCount() == null ? 0 : aggregate.getVipCount();
        int checkinCount = aggregate.getCheckinCount() == null ? 0 : aggregate.getCheckinCount();
        int totalFrequency = vipCount * 5 + checkinCount;

        rfm.setTotalPurchaseCount(vipCount);
        rfm.setTotalActiveDays(checkinCount);
        rfm.setFrequency(totalFrequency);


        int fScore = 1;
        for (Map.Entry<Integer, Integer> entry : F_SCORE_STANDARD.entrySet()) {
            if (totalFrequency >= entry.getKey()) {
                fScore = entry.getValue();
                break;
            }
        }
        rfm.setFrequencyScore(fScore);

        BigDecimal totalAmount = aggregate.getVipAmount() == null
                ? BigDecimal.ZERO : aggregate.getVipAmount();
        rfm.setTotalPurchaseAmount(totalAmount);
        rfm.setMonetary(totalAmount);


        int mScore = 1;
        for (Map.Entry<BigDecimal, Integer> entry : M_SCORE_STANDARD.entrySet()) {
            if (totalAmount.compareTo(entry.getKey()) >= 0) {
                mScore = entry.getValue();
                break;
            }
        }
        rfm.setMonetaryScore(mScore);
        rfm.setTotalScore(rfm.getRecencyScore() + rfm.getFrequencyScore() + rfm.getMonetaryScore());
        determineSegment(rfm);
        return rfm;
    }

    private void cacheRfm(RFMVO rfm) {
        if (rfm != null && rfm.getUserId() != null) {
            redisTemplate.opsForValue().set("rfm:user:" + rfm.getUserId(),
                    rfm, RFM_CACHE_HOURS, TimeUnit.HOURS);
        }
    }




    private void determineSegment(RFMVO rfm) {

        int r = rfm.getRecencyScore();              
        int f = rfm.getFrequencyScore();              
        int m = rfm.getMonetaryScore();               

        String segmentType;
        String segmentDescription;
        String operationalAdvice;


        boolean highR = r >= 4;
        boolean highF = f >= 4;
        boolean highM = m >= 4;

        if (highR && highF && highM) {

            segmentType = RFMVO.UserSegment.IMPORTANT_VALUE.getCode();
            segmentDescription = RFMVO.UserSegment.IMPORTANT_VALUE.getName();
            operationalAdvice = RFMVO.UserSegment.IMPORTANT_VALUE.getAdvice();
        } else if (!highR && highF && highM) {

            segmentType = RFMVO.UserSegment.IMPORTANT_RETENTION.getCode();
            segmentDescription = RFMVO.UserSegment.IMPORTANT_RETENTION.getName();
            operationalAdvice = RFMVO.UserSegment.IMPORTANT_RETENTION.getAdvice();
        } else if (highR && !highF && highM) {

            segmentType = RFMVO.UserSegment.IMPORTANT_DEVELOPMENT.getCode();
            segmentDescription = RFMVO.UserSegment.IMPORTANT_DEVELOPMENT.getName();
            operationalAdvice = RFMVO.UserSegment.IMPORTANT_DEVELOPMENT.getAdvice();
        } else if (!highR && !highF && highM) {

            segmentType = RFMVO.UserSegment.IMPORTANT_WIN_BACK.getCode();
            segmentDescription = RFMVO.UserSegment.IMPORTANT_WIN_BACK.getName();
            operationalAdvice = RFMVO.UserSegment.IMPORTANT_WIN_BACK.getAdvice();
        } else if (highR && highF && !highM) {

            segmentType = RFMVO.UserSegment.GENERAL_VALUE.getCode();
            segmentDescription = RFMVO.UserSegment.GENERAL_VALUE.getName();
            operationalAdvice = RFMVO.UserSegment.GENERAL_VALUE.getAdvice();
        } else if (!highR && highF && !highM) {

            segmentType = RFMVO.UserSegment.GENERAL_RETENTION.getCode();
            segmentDescription = RFMVO.UserSegment.GENERAL_RETENTION.getName();
            operationalAdvice = RFMVO.UserSegment.GENERAL_RETENTION.getAdvice();
        } else if (highR && !highF && !highM) {

            segmentType = RFMVO.UserSegment.GENERAL_DEVELOPMENT.getCode();
            segmentDescription = RFMVO.UserSegment.GENERAL_DEVELOPMENT.getName();
            operationalAdvice = RFMVO.UserSegment.GENERAL_DEVELOPMENT.getAdvice();
        } else {

            segmentType = RFMVO.UserSegment.CHURNED.getCode();
            segmentDescription = RFMVO.UserSegment.CHURNED.getName();
            operationalAdvice = RFMVO.UserSegment.CHURNED.getAdvice();
        }

        rfm.setSegmentType(segmentType);
        rfm.setSegmentDescription(segmentDescription);
        rfm.setOperationalAdvice(operationalAdvice);
    }
}

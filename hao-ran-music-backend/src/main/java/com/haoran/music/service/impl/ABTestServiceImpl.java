package com.haoran.music.service.impl;

import com.alibaba.fastjson2.JSON;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.service.ABTestService;
import com.haoran.music.vo.experiment.ABTestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;





@Slf4j
@Service
public class ABTestServiceImpl implements ABTestService {

    private final RedisTemplate<String, Object> redisTemplate;

    public ABTestServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }




    private static final String EXPERIMENT_ID_COUNTER = "ab:test:id:counter";

    @Override
    public Long createExperiment(String experimentName, String description, String experimentType, Map<String, Integer> trafficAllocation) {
        if (ObjectUtils.isEmpty(experimentName) || ObjectUtils.isEmpty(experimentType)) {
            return null;
        }


        Long experimentId = redisTemplate.opsForValue().increment(EXPERIMENT_ID_COUNTER);


        String configKey = "ab:test:config:" + experimentId;
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("experimentId", experimentId);
        config.put("experimentName", experimentName);
        config.put("description", description);
        config.put("experimentType", experimentType);
        config.put("trafficAllocation", ObjectUtils.isNotEmpty(trafficAllocation) ? trafficAllocation : getDefaultTrafficAllocation());
        config.put("status", ABTestVO.ExperimentStatus.DRAFT.getCode());
        config.put("createTime", LocalDateTime.now());
        config.put("variants", new LinkedHashMap<>());

        redisTemplate.opsForValue().set(configKey, config, 365, TimeUnit.DAYS);


        redisTemplate.opsForSet().add("ab:test:list", experimentId);

        log.info("event=ab_test_created experimentId={}", experimentId);

        return experimentId;
    }

    @Override
    public ABTestVO getExperiment(Long experimentId) {
        if (ObjectUtils.isEmpty(experimentId)) {
            return null;
        }

        String configKey = "ab:test:config:" + experimentId;
        Map<String, Object> config = (Map<String, Object>) redisTemplate.opsForValue().get(configKey);

        if (ObjectUtils.isEmpty(config)) {
            return null;
        }

        return convertMapToVO(config);
    }

    @Override
    public List<ABTestVO> getAllExperiments(String status) {
        List<ABTestVO> experiments = new ArrayList<>();

        Set<Object> experimentIds = redisTemplate.opsForSet().members("ab:test:list");
        if (ObjectUtils.isEmpty(experimentIds)) {
            return experiments;
        }

        for (Object id : experimentIds) {
            Long experimentId = Long.parseLong(id.toString());
            ABTestVO experiment = getExperiment(experimentId);
            if (ObjectUtils.isNotEmpty(experiment)) {
                if (ObjectUtils.isEmpty(status) || status.equals(experiment.getStatus())) {
                    experiments.add(experiment);
                }
            }
        }

        return experiments.stream()
                .sorted(Comparator.comparing(ABTestVO::getCreateTime).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public Boolean startExperiment(Long experimentId) {
        if (ObjectUtils.isEmpty(experimentId)) {
            return false;
        }

        String configKey = "ab:test:config:" + experimentId;
        Map<String, Object> config = (Map<String, Object>) redisTemplate.opsForValue().get(configKey);

        if (ObjectUtils.isEmpty(config)) {
            return false;
        }

        config.put("status", ABTestVO.ExperimentStatus.RUNNING.getCode());
        config.put("startTime", LocalDateTime.now());

        redisTemplate.opsForValue().set(configKey, config, 365, TimeUnit.DAYS);

        log.info("启动A/B测试实验: experimentId={}", experimentId);

        return true;
    }

    @Override
    public Boolean stopExperiment(Long experimentId) {
        if (ObjectUtils.isEmpty(experimentId)) {
            return false;
        }

        String configKey = "ab:test:config:" + experimentId;
        Map<String, Object> config = (Map<String, Object>) redisTemplate.opsForValue().get(configKey);

        if (ObjectUtils.isEmpty(config)) {
            return false;
        }

        config.put("status", ABTestVO.ExperimentStatus.COMPLETED.getCode());
        config.put("endTime", LocalDateTime.now());

        redisTemplate.opsForValue().set(configKey, config, 365, TimeUnit.DAYS);

        log.info("停止A/B测试实验: experimentId={}", experimentId);

        return true;
    }

    @Override
    public Boolean deleteExperiment(Long experimentId) {
        if (ObjectUtils.isEmpty(experimentId)) {
            return false;
        }

        String configKey = "ab:test:config:" + experimentId;
        redisTemplate.delete(configKey);

        redisTemplate.opsForSet().remove("ab:test:list", experimentId);

        log.info("删除A/B测试实验: experimentId={}", experimentId);

        return true;
    }

    @Override
    public String assignUserToVariant(Long userId, Long experimentId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(experimentId)) {
            return null;
        }


        String assignmentKey = "ab:test:assignment:" + experimentId + ":" + userId;
        Object existingAssignment = redisTemplate.opsForValue().get(assignmentKey);
        if (ObjectUtils.isNotEmpty(existingAssignment)) {
            return existingAssignment.toString();
        }


        ABTestVO experiment = getExperiment(experimentId);
        if (ObjectUtils.isEmpty(experiment) || ObjectUtils.isEmpty(experiment.getTrafficAllocation())) {
            return "A";        
        }


        Map<String, Integer> allocation = experiment.getTrafficAllocation();
        String variant = assignVariantByHash(userId, allocation);


        redisTemplate.opsForValue().set(assignmentKey, variant, 30, TimeUnit.DAYS);


        String variantUsersKey = "ab:test:variant:" + experimentId + ":" + variant + ":users";
        redisTemplate.opsForSet().add(variantUsersKey, userId);
        redisTemplate.expire(variantUsersKey, 30, TimeUnit.DAYS);

        log.debug("分配用户到实验组: userId={}, experimentId={}, variant={}", userId, experimentId, variant);

        return variant;
    }

    @Override
    public Map<Long, String> batchAssignUsersToVariant(List<Long> userIds, Long experimentId) {
        if (ObjectUtils.isEmpty(userIds) || ObjectUtils.isEmpty(experimentId)) {
            return new LinkedHashMap<>();
        }

        Map<Long, String> assignments = new LinkedHashMap<>();
        for (Long userId : userIds) {
            String variant = assignUserToVariant(userId, experimentId);
            assignments.put(userId, variant);
        }

        return assignments;
    }

    @Override
    public Boolean trackExperimentEvent(Long userId, Long experimentId, String eventType, Map<String, Object> eventData) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(experimentId) || ObjectUtils.isEmpty(eventType)) {
            return false;
        }


        String variant = assignUserToVariant(userId, experimentId);
        if (ObjectUtils.isEmpty(variant)) {
            return false;
        }


        String eventKey = "ab:test:events:" + experimentId + ":" + variant + ":" + eventType;
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("userId", userId);
        event.put("eventType", eventType);
        event.put("eventData", ObjectUtils.isNotEmpty(eventData) ? eventData : new LinkedHashMap<>());
        event.put("timestamp", LocalDateTime.now());

        redisTemplate.opsForList().rightPush(eventKey, JSON.toJSONString(event));
        redisTemplate.expire(eventKey, 30, TimeUnit.DAYS);


        String countKey = "ab:test:counts:" + experimentId + ":" + variant + ":" + eventType;
        redisTemplate.opsForValue().increment(countKey);
        redisTemplate.expire(countKey, 30, TimeUnit.DAYS);

        log.debug("记录A/B测试事件: userId={}, experimentId={}, variant={}, type={}", userId, experimentId, variant, eventType);

        return true;
    }

    @Override
    public ABTestVO analyzeExperiment(Long experimentId) {
        ABTestVO experiment = getExperiment(experimentId);
        if (ObjectUtils.isEmpty(experiment)) {
            return null;
        }


        ABTestVO.StatisticalSignificance significance = calculateSignificance(experimentId);
        experiment.setSignificance(significance);


        if (ObjectUtils.isNotEmpty(significance) && Boolean.TRUE.equals(significance.getIsSignificant())) {
            experiment.setConclusion("实验结果显著，建议采用" + (significance.getRelativeLift().compareTo(BigDecimal.ZERO) > 0 ? "实验组" : "对照组") + "方案");
        } else {
            experiment.setConclusion("实验结果不显著，需要更多数据或延长实验时间");
        }

        return experiment;
    }

    @Override
    public ABTestVO.StatisticalSignificance calculateSignificance(Long experimentId) {
        if (ObjectUtils.isEmpty(experimentId)) {
            return null;
        }

        ABTestVO.StatisticalSignificance significance = new ABTestVO.StatisticalSignificance();

        try {

            long aConversions = getEventCount(experimentId, "A", "conversion");
            long aUsers = getVariantUserCount(experimentId, "A");
            long bConversions = getEventCount(experimentId, "B", "conversion");
            long bUsers = getVariantUserCount(experimentId, "B");

            if (aUsers == 0 || bUsers == 0) {
                significance.setIsSignificant(false);
                significance.setPValue(BigDecimal.ONE);
                significance.setConfidence(BigDecimal.ZERO);
                return significance;
            }


            BigDecimal aRate = new BigDecimal(aConversions).divide(new BigDecimal(aUsers), 4, RoundingMode.HALF_UP);
            BigDecimal bRate = new BigDecimal(bConversions).divide(new BigDecimal(bUsers), 4, RoundingMode.HALF_UP);


            BigDecimal relativeLift = bRate.subtract(aRate).divide(aRate, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            significance.setRelativeLift(relativeLift);


            BigDecimal absoluteDiff = bRate.subtract(aRate);
            significance.setAbsoluteDifference(absoluteDiff);


            double pooledRate = (double) (aConversions + bConversions) / (aUsers + bUsers);
            double se = Math.sqrt(pooledRate * (1 - pooledRate) * (1.0 / aUsers + 1.0 / bUsers));
            double zScore = se > 0 ? (bRate.doubleValue() - aRate.doubleValue()) / se : 0;


            double pValue = 1 - Math.abs(zScore) / 5;           
            pValue = Math.max(0, Math.min(1, pValue));

            significance.setPValue(BigDecimal.valueOf(pValue));
            significance.setConfidence(BigDecimal.valueOf(1 - pValue).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));
            significance.setIsSignificant(pValue < 0.05);

            if (Boolean.TRUE.equals(significance.getIsSignificant())) {
                significance.setConclusion("在95%置信水平下，两组差异显著");
            } else {
                significance.setConclusion("在95%置信水平下，两组差异不显著");
            }

        } catch (Exception e) {
            log.warn("计算统计显著性失败: experimentId={}", experimentId);
            significance.setIsSignificant(false);
            significance.setPValue(BigDecimal.ONE);
        }

        return significance;
    }

    @Override
    public Map<String, Object> generateExperimentReport(Long experimentId) {
        Map<String, Object> report = new LinkedHashMap<>();

        ABTestVO experiment = analyzeExperiment(experimentId);
        if (ObjectUtils.isEmpty(experiment)) {
            return report;
        }

        report.put("experiment", experiment);
        report.put("generateTime", LocalDateTime.now());
        report.put("recommendation", experiment.getConclusion());

        return report;
    }

    @Override
    public List<ABTestVO> getExperimentList(Integer page, Integer size, String status) {
        int pageNum = ObjectUtils.isNotEmpty(page) ? page : 1;
        int pageSize = ObjectUtils.isNotEmpty(size) ? size : 20;

        List<ABTestVO> allExperiments = getAllExperiments(status);

        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, allExperiments.size());

        if (start >= allExperiments.size()) {
            return new ArrayList<>();
        }

        return allExperiments.subList(start, end);
    }

    @Override
    public Boolean updateExperimentConfig(Long experimentId, Map<String, Object> config) {
        if (ObjectUtils.isEmpty(experimentId) || ObjectUtils.isEmpty(config)) {
            return false;
        }

        String configKey = "ab:test:config:" + experimentId;
        Map<String, Object> currentConfig = (Map<String, Object>) redisTemplate.opsForValue().get(configKey);

        if (ObjectUtils.isEmpty(currentConfig)) {
            return false;
        }


        currentConfig.putAll(config);
        currentConfig.put("updateTime", LocalDateTime.now());

        redisTemplate.opsForValue().set(configKey, currentConfig, 365, TimeUnit.DAYS);

        log.info("更新A/B测试配置: experimentId={}", experimentId);

        return true;
    }




    private String assignVariantByHash(Long userId, Map<String, Integer> allocation) {
        int hash = userId.hashCode();
        int total = allocation.values().stream().mapToInt(Integer::intValue).sum();
        int value = Math.abs(hash) % total;

        int cumulative = 0;
        for (Map.Entry<String, Integer> entry : allocation.entrySet()) {
            cumulative += entry.getValue();
            if (value < cumulative) {
                return entry.getKey();
            }
        }

        return "A";        
    }




    private Map<String, Integer> getDefaultTrafficAllocation() {
        Map<String, Integer> allocation = new LinkedHashMap<>();
        allocation.put("A", 50);
        allocation.put("B", 50);
        return allocation;
    }




    private long getEventCount(Long experimentId, String variant, String eventType) {
        String countKey = "ab:test:counts:" + experimentId + ":" + variant + ":" + eventType;
        Object count = redisTemplate.opsForValue().get(countKey);
        return ObjectUtils.isNotEmpty(count) ? Long.parseLong(count.toString()) : 0;
    }




    private long getVariantUserCount(Long experimentId, String variant) {
        String variantUsersKey = "ab:test:variant:" + experimentId + ":" + variant + ":users";
        Long size = redisTemplate.opsForSet().size(variantUsersKey);
        return ObjectUtils.isNotEmpty(size) ? size : 0;
    }




    private ABTestVO convertMapToVO(Map<String, Object> config) {
        ABTestVO vo = new ABTestVO();
        vo.setExperimentId(((Number) config.get("experimentId")).longValue());
        vo.setExperimentName((String) config.get("experimentName"));
        vo.setDescription((String) config.get("description"));
        vo.setExperimentType((String) config.get("experimentType"));
        vo.setStatus((String) config.get("status"));
        vo.setTrafficAllocation((Map<String, Integer>) config.get("trafficAllocation"));
        vo.setStartTime((LocalDateTime) config.get("startTime"));
        vo.setEndTime((LocalDateTime) config.get("endTime"));
        vo.setCreateTime((LocalDateTime) config.get("createTime"));


        for (ABTestVO.ExperimentStatus status : ABTestVO.ExperimentStatus.values()) {
            if (status.getCode().equals(vo.getStatus())) {
                vo.setStatusDescription(status.getDescription());
                break;
            }
        }

        return vo;
    }
}

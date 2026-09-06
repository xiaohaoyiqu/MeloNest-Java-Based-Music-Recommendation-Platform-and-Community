package com.haoran.music.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.entity.AppealRecord;
import com.haoran.music.mapper.AppealRecordMapper;
import com.haoran.music.service.AppealRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

   
                      
                        
   
@Slf4j
@Service
public class AppealRecordServiceImpl extends ServiceImpl<AppealRecordMapper, AppealRecord> implements AppealRecordService {

    @Override
    public List<AppealRecord> getRecordsByAppealId(Long appealId) {
        if (ObjectUtil.isEmpty(appealId)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AppealRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppealRecord::getAppealId, appealId)
                .orderByDesc(AppealRecord::getCreateTime);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public List<AppealRecord> getRecordsByOperatorId(Long operatorId) {
        if (ObjectUtil.isEmpty(operatorId)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AppealRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppealRecord::getOperatorId, operatorId)
                .orderByDesc(AppealRecord::getCreateTime);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public IPage<AppealRecord> pageRecords(Long appealId, Long operatorId, String operatorType, String action, Long page, Long size) {
        Page<AppealRecord> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 20);

        LambdaQueryWrapper<AppealRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ObjectUtil.isNotNull(appealId), AppealRecord::getAppealId, appealId)
                .eq(ObjectUtil.isNotNull(operatorId), AppealRecord::getOperatorId, operatorId)
                .eq(StrUtil.isNotBlank(operatorType), AppealRecord::getOperatorType, operatorType)
                .eq(StrUtil.isNotBlank(action), AppealRecord::getAction, action)
                .orderByDesc(AppealRecord::getCreateTime);

        return baseMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Long recordAction(Long appealId, Long operatorId, String operatorType, String action, String actionRemark) {
        AppealRecord record = new AppealRecord();
        record.setAppealId(appealId);
        record.setOperatorId(operatorId);
        record.setOperatorType(operatorType);
        record.setAction(action);
        record.setActionRemark(actionRemark);
        record.setCreateTime(LocalDateTime.now());

        baseMapper.insert(record);
        log.info("申诉操作记录已保存: appealId={}, action={}, operatorId={}", appealId, action, operatorId);
        return record.getId();
    }

    @Override
    public AppealRecord getLatestRecord(Long appealId) {
        if (ObjectUtil.isEmpty(appealId)) {
            return null;
        }
        LambdaQueryWrapper<AppealRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppealRecord::getAppealId, appealId)
                .orderByDesc(AppealRecord::getCreateTime)
                .last("LIMIT 1");
        return baseMapper.selectOne(wrapper);
    }
}

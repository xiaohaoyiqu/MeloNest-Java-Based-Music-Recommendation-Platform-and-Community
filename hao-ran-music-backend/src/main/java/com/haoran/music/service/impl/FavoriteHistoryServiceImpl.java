package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.FavoriteHistory;
import com.haoran.music.entity.Song;
import com.haoran.music.mapper.FavoriteHistoryMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.service.FavoriteHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

   
                      
                                                         
   
@Slf4j
@Service
public class FavoriteHistoryServiceImpl extends ServiceImpl<FavoriteHistoryMapper, FavoriteHistory> implements FavoriteHistoryService {

    @Resource
    private SongMapper songMapper;

    public FavoriteHistoryServiceImpl() {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean recordFavoriteAction(Long userId, Long songId, Integer actionType) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(songId) || ObjectUtils.isEmpty(actionType)) {
            log.warn("recordFavoriteAction: userId, songId, or actionType is empty");
            return false;
        }

        try {
                                         
            Song song = songMapper.selectById(songId);
            if (ObjectUtils.isEmpty(song)) {
                log.warn("recordFavoriteAction: Song not found, songId={}", songId);
                return false;
            }

                                    
            FavoriteHistory history = new FavoriteHistory();
            history.setUserId(userId);
            history.setSongId(songId);
            history.setActionType(actionType);
            history.setActionTime(LocalDateTime.now());
            history.setSongName(song.getName());
            history.setArtistNames(song.getArtistNames());
            history.setCover(song.getCover());

            save(history);
            log.info("recordFavoriteAction: Recorded userId={}, songId={}, actionType={}",
                    userId, songId, actionType);
            return true;
        } catch (Exception e) {
            log.error("recordFavoriteAction: Error recording favorite action");
            return false;
        }
    }

    @Override
    public IPage<FavoriteHistory> getFavoriteHistory(Long userId, PageQuery pageQuery) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "User ID cannot be empty");
        }

        Page<FavoriteHistory> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());

        LambdaQueryWrapper<FavoriteHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FavoriteHistory::getUserId, userId)
                .orderByDesc(FavoriteHistory::getActionTime);

        return page(page, wrapper);
    }

    @Override
    public Map<String, Object> getFavoriteStatistics(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "User ID cannot be empty");
        }

        LambdaQueryWrapper<FavoriteHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FavoriteHistory::getUserId, userId);

        List<FavoriteHistory> histories = list(wrapper);

        int favoriteCount = 0;
        int unfavoriteCount = 0;

        for (FavoriteHistory history : histories) {
            if (history.getActionType() == 1) {
                favoriteCount++;
            } else if (history.getActionType() == 2) {
                unfavoriteCount++;
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", histories.size());
        stats.put("favoriteCount", favoriteCount);
        stats.put("unfavoriteCount", unfavoriteCount);

        return stats;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean clearFavoriteHistory(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "User ID cannot be empty");
        }

        LambdaQueryWrapper<FavoriteHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FavoriteHistory::getUserId, userId);
        remove(wrapper);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteFavoriteHistory(Long userId, Long id) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(id)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "User ID and record ID cannot be empty");
        }

                           
        FavoriteHistory history = getById(id);
        if (ObjectUtils.isEmpty(history)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "History record not found");
        }

        if (!history.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "No permission to delete this record");
        }

        removeById(id);
        return true;
    }
}

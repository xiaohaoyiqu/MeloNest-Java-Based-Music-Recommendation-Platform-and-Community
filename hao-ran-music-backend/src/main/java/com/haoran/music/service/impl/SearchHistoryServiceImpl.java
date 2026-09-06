package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.SearchHistory;
import com.haoran.music.mapper.SearchHistoryMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.SearchHistoryService;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;





@Slf4j
@Service
public class SearchHistoryServiceImpl extends ServiceImpl<SearchHistoryMapper, SearchHistory> implements SearchHistoryService {

    @Resource
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean addSearchHistory(Long userId, String keyword, Integer searchType, Integer resultCount) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(keyword)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID和关键词不能为空");
        }
        if (!UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            return true;
        }


        LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SearchHistory::getUserId, userId)
                .eq(SearchHistory::getKeyword, keyword)
                .eq(SearchHistory::getSearchType, searchType)
                .orderByDesc(SearchHistory::getCreateTime)
                .last("LIMIT 1");
        SearchHistory existing = getOne(wrapper);

        if (ObjectUtils.isNotEmpty(existing)) {

            existing.setCreateTime(LocalDateTime.now());
            existing.setResultCount(resultCount);
            updateById(existing);
        } else {

            SearchHistory history = new SearchHistory();
            history.setUserId(userId);
            history.setKeyword(keyword.trim());
            history.setSearchType(searchType);
            history.setResultCount(resultCount);
            save(history);
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean clearSearchHistory(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }

        LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SearchHistory::getUserId, userId);
        remove(wrapper);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteSearchHistory(Long userId, Long id) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(id)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID和记录ID不能为空");
        }


        SearchHistory history = getById(id);
        if (ObjectUtils.isEmpty(history)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "搜索记录不存在");
        }

        if (!history.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除该记录");
        }

        removeById(id);
        return true;
    }

    @Override
    public IPage<SearchHistory> getSearchHistory(Long userId, PageQuery pageQuery) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }

        Page<SearchHistory> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());

        LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SearchHistory::getUserId, userId)
                .orderByDesc(SearchHistory::getCreateTime);

        return page(page, wrapper);
    }

    @Override
    public List<String> getHotKeywords(Integer limit) {
        int safeLimit = ObjectUtils.isEmpty(limit) || limit <= 0 ? 10 : limit;


        LocalDateTime since = LocalDateTime.now().minusDays(7);

        LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(SearchHistory::getCreateTime, since)
                .orderByDesc(SearchHistory::getCreateTime)
                .last("LIMIT " + hotKeywordCandidateLimit(safeLimit));

        List<SearchHistory> histories = list(wrapper);
        Set<Long> userIds = histories.stream()
                .map(SearchHistory::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allowedUserIds = UserAccountStatusUtil.filterPublicStatsUserIds(
                userIds,
                ids -> userMapper.selectBatchIds(ids)
        );

        Map<String, Long> keywordCount = new HashMap<>();
        for (SearchHistory history : histories) {
            if (!allowedUserIds.contains(history.getUserId())) {
                continue;
            }
            String keyword = history.getKeyword();
            keywordCount.put(keyword, keywordCount.getOrDefault(keyword, 0L) + 1);
        }


        return keywordCount.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(safeLimit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private int hotKeywordCandidateLimit(int limit) {
        return Math.min(Math.max(limit * 100, limit), 5000);
    }

    @Override
    public List<String> getSearchSuggestions(Long userId, String keyword, Integer limit) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }

        if (ObjectUtils.isEmpty(limit)) {
            limit = 10;
        }

        if (ObjectUtils.isEmpty(keyword)) {

            return getRecentSearch(userId, limit).stream()
                    .map(SearchHistory::getKeyword)
                    .collect(Collectors.toList());
        }

        LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SearchHistory::getUserId, userId)
                .like(SearchHistory::getKeyword, keyword.trim())
                .groupBy(SearchHistory::getKeyword)
                .orderByDesc(SearchHistory::getCreateTime)
                .last("LIMIT " + limit);

        return list(wrapper).stream()
                .map(SearchHistory::getKeyword)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<SearchHistory> getRecentSearch(Long userId, Integer limit) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }

        if (ObjectUtils.isEmpty(limit)) {
            limit = 10;
        }

        LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SearchHistory::getUserId, userId)
                .orderByDesc(SearchHistory::getCreateTime)
                .last("LIMIT " + limit);

        return list(wrapper);
    }
}

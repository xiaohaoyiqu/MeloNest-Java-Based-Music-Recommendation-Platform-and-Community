




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.haoran.music.entity.UserQuickPhrase;
import com.haoran.music.entity.User;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.mapper.UserQuickPhraseMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.UserQuickPhraseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;




@Slf4j
@Service
public class UserQuickPhraseServiceImpl implements UserQuickPhraseService {

    private static final int MAX_PHRASE_COUNT = 10;
    private static final int MAX_PHRASE_LENGTH = 200;

    @Resource
    private UserQuickPhraseMapper userQuickPhraseMapper;

    @Resource
    private UserMapper userMapper;







    @Override
    public List<UserQuickPhrase> getUserPhrases(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<UserQuickPhrase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserQuickPhrase::getUserId, userId)
                .orderByAsc(UserQuickPhrase::getSortOrder)
                .orderByAsc(UserQuickPhrase::getCreateTime);

        return userQuickPhraseMapper.selectList(wrapper);
    }








    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserQuickPhrase addPhrase(Long userId, String phrase) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(phrase)) {
            throw new IllegalArgumentException("用户ID和常用语内容不能为空");
        }


        String trimmedPhrase = normalizePhrase(phrase);
        lockUserForWrite(userId);


        LambdaQueryWrapper<UserQuickPhrase> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(UserQuickPhrase::getUserId, userId)
                .eq(UserQuickPhrase::getPhrase, trimmedPhrase);
        Long count = userQuickPhraseMapper.selectCount(checkWrapper);
        if (count > 0) {
            throw new IllegalArgumentException("该常用语已存在");
        }


        LambdaQueryWrapper<UserQuickPhrase> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(UserQuickPhrase::getUserId, userId);
        Long currentCount = userQuickPhraseMapper.selectCount(countWrapper);
        if (currentCount >= MAX_PHRASE_COUNT) {
            throw new IllegalArgumentException("常用语数量已达上限（10条）");
        }


        LambdaQueryWrapper<UserQuickPhrase> maxSortWrapper = new LambdaQueryWrapper<>();
        maxSortWrapper.eq(UserQuickPhrase::getUserId, userId)
                .orderByDesc(UserQuickPhrase::getSortOrder)
                .last("LIMIT 1");
        UserQuickPhrase lastPhrase = userQuickPhraseMapper.selectOne(maxSortWrapper);
        Integer nextSort = (lastPhrase != null) ? lastPhrase.getSortOrder() + 1 : 0;


        UserQuickPhrase newPhrase = new UserQuickPhrase();
        newPhrase.setUserId(userId);
        newPhrase.setPhrase(trimmedPhrase);
        newPhrase.setSortOrder(nextSort);
        newPhrase.setCreateTime(LocalDateTime.now());
        newPhrase.setUpdateTime(LocalDateTime.now());

        requireSingleWrite(userQuickPhraseMapper.insert(newPhrase), "常用语新增失败");
        log.info("[UserQuickPhrase] userId={} action=add phraseId={}", userId, newPhrase.getId());

        return newPhrase;
    }









    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePhrase(Long phraseId, Long userId, String newPhrase) {
        if (ObjectUtils.isEmpty(phraseId) || ObjectUtils.isEmpty(userId)) {
            throw new IllegalArgumentException("常用语ID和用户ID不能为空");
        }

        String trimmedPhrase = normalizePhrase(newPhrase);
        lockUserForWrite(userId);


        LambdaQueryWrapper<UserQuickPhrase> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.eq(UserQuickPhrase::getId, phraseId)
                .eq(UserQuickPhrase::getUserId, userId);
        UserQuickPhrase existPhrase = userQuickPhraseMapper.selectOne(ownerWrapper);
        if (existPhrase == null) {
            throw new IllegalArgumentException("常用语不存在或无权修改");
        }


        LambdaQueryWrapper<UserQuickPhrase> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(UserQuickPhrase::getUserId, userId)
                .eq(UserQuickPhrase::getPhrase, trimmedPhrase)
                .ne(UserQuickPhrase::getId, phraseId);
        Long count = userQuickPhraseMapper.selectCount(checkWrapper);
        if (count > 0) {
            throw new IllegalArgumentException("该常用语已存在");
        }


        LambdaUpdateWrapper<UserQuickPhrase> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserQuickPhrase::getId, phraseId)
                .eq(UserQuickPhrase::getUserId, userId)
                .set(UserQuickPhrase::getPhrase, trimmedPhrase)
                .set(UserQuickPhrase::getUpdateTime, LocalDateTime.now());

        int rows = userQuickPhraseMapper.update(null, updateWrapper);
        requireSingleWrite(rows, "常用语更新失败");
        log.info("[UserQuickPhrase] userId={} action=update phraseId={}", userId, phraseId);

        return true;
    }








    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePhrase(Long phraseId, Long userId) {
        if (ObjectUtils.isEmpty(phraseId) || ObjectUtils.isEmpty(userId)) {
            return false;
        }
        lockUserForWrite(userId);

        LambdaQueryWrapper<UserQuickPhrase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserQuickPhrase::getId, phraseId)
                .eq(UserQuickPhrase::getUserId, userId);
        int rows = userQuickPhraseMapper.delete(wrapper);
        if (rows > 1) {
            throw new IllegalStateException("常用语删除结果异常");
        }
        log.info("[UserQuickPhrase] userId={} action=delete phraseId={} deleted={}",
                userId, phraseId, rows == 1);
        return rows == 1;
    }








    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSortOrder(Long userId, List<Long> phraseIds) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(phraseIds)) {
            throw new IllegalArgumentException("用户ID和常用语ID列表不能为空");
        }

        if (phraseIds.size() > MAX_PHRASE_COUNT) {
            throw new IllegalArgumentException("常用语数量不能超过10条");
        }

        Set<Long> uniqueIds = new HashSet<>();
        for (Long phraseId : phraseIds) {
            if (phraseId == null || phraseId <= 0 || !uniqueIds.add(phraseId)) {
                throw new IllegalArgumentException("常用语ID列表包含无效或重复项");
            }
        }
        lockUserForWrite(userId);

        LambdaQueryWrapper<UserQuickPhrase> ownershipWrapper = new LambdaQueryWrapper<>();
        ownershipWrapper.eq(UserQuickPhrase::getUserId, userId)
                .in(UserQuickPhrase::getId, phraseIds);
        List<UserQuickPhrase> ownedPhrases = userQuickPhraseMapper.selectList(ownershipWrapper);
        if (ownedPhrases == null || ownedPhrases.size() != phraseIds.size()) {
            throw new IllegalArgumentException("常用语不存在或不属于当前用户");
        }


        LocalDateTime updateTime = LocalDateTime.now();
        for (int i = 0; i < phraseIds.size(); i++) {
            Long phraseId = phraseIds.get(i);
            LambdaUpdateWrapper<UserQuickPhrase> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(UserQuickPhrase::getId, phraseId)
                    .eq(UserQuickPhrase::getUserId, userId)
                    .set(UserQuickPhrase::getSortOrder, i)
                    .set(UserQuickPhrase::getUpdateTime, updateTime);
            requireSingleWrite(userQuickPhraseMapper.update(null, updateWrapper), "常用语排序更新失败");
        }

        log.info("[UserQuickPhrase] userId={} action=sort phraseCount={}", userId, phraseIds.size());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceUserPhrases(Long userId, List<String> phrases) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        List<String> normalizedPhrases = normalizePhrases(phrases);
        lockUserForWrite(userId);

        LambdaQueryWrapper<UserQuickPhrase> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(UserQuickPhrase::getUserId, userId);
        userQuickPhraseMapper.delete(deleteWrapper);

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < normalizedPhrases.size(); i++) {
            UserQuickPhrase phrase = new UserQuickPhrase();
            phrase.setUserId(userId);
            phrase.setPhrase(normalizedPhrases.get(i));
            phrase.setSortOrder(i);
            phrase.setCreateTime(now);
            phrase.setUpdateTime(now);
            requireSingleWrite(userQuickPhraseMapper.insert(phrase), "常用语批量保存失败");
        }
        log.info("[UserQuickPhrase] userId={} action=replace phraseCount={}",
                userId, normalizedPhrases.size());
    }







    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearUserPhrases(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 0;
        }
        lockUserForWrite(userId);

        LambdaQueryWrapper<UserQuickPhrase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserQuickPhrase::getUserId, userId);

        int count = userQuickPhraseMapper.delete(wrapper);
        log.info("[UserQuickPhrase] userId={} action=clear deletedCount={}", userId, count);

        return count;
    }

    private List<String> normalizePhrases(List<String> phrases) {
        if (phrases == null) {
            throw new IllegalArgumentException("常用语列表不能为空");
        }
        if (phrases.size() > MAX_PHRASE_COUNT) {
            throw new IllegalArgumentException("常用语数量不能超过10条");
        }
        List<String> normalized = new ArrayList<>(phrases.size());
        Set<String> unique = new HashSet<>();
        for (String phrase : phrases) {
            String value = normalizePhrase(phrase);
            if (!unique.add(value)) {
                throw new IllegalArgumentException("常用语内容不能重复");
            }
            normalized.add(value);
        }
        return normalized;
    }

    private String normalizePhrase(String phrase) {
        if (phrase == null) {
            throw new IllegalArgumentException("常用语内容不能为空");
        }
        String normalized = phrase.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("常用语内容不能为空");
        }
        if (normalized.length() > MAX_PHRASE_LENGTH) {
            throw new IllegalArgumentException("常用语内容不能超过200个字符");
        }
        return normalized;
    }

    private void requireSingleWrite(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }

    private void lockUserForWrite(Long userId) {
        User user = userMapper.selectByIdForUpdate(userId);
        UserAccountStatusUtil.requireCanInteract(user, "修改常用语");
    }
}

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.entity.AlbumFavorite;
import com.haoran.music.entity.FavoriteCollectionGroup;
import com.haoran.music.entity.MvFavorite;
import com.haoran.music.entity.PlaylistFavorite;
import com.haoran.music.entity.SongLike;
import com.haoran.music.mapper.AlbumFavoriteMapper;
import com.haoran.music.mapper.FavoriteCollectionGroupMapper;
import com.haoran.music.mapper.FavoriteCollectionItemMapper;
import com.haoran.music.mapper.MvFavoriteMapper;
import com.haoran.music.mapper.PlaylistFavoriteMapper;
import com.haoran.music.mapper.SongLikeMapper;
import com.haoran.music.service.FavoriteCollectionService;
import com.haoran.music.vo.favorite.FavoriteGroupStateVO;
import com.haoran.music.vo.favorite.FavoriteGroupVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Set;

                                        
@Slf4j
@Service
public class FavoriteCollectionServiceImpl implements FavoriteCollectionService {
    private static final int MAX_GROUPS = 20;
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList("song", "album", "mv", "playlist"));
    private static final Set<String> RESERVED_NAMES = new HashSet<>(Arrays.asList("全部收藏", "未分组"));

    @Resource
    private FavoriteCollectionGroupMapper groupMapper;
    @Resource
    private FavoriteCollectionItemMapper itemMapper;
    @Resource
    private SongLikeMapper songLikeMapper;
    @Resource
    private AlbumFavoriteMapper albumFavoriteMapper;
    @Resource
    private MvFavoriteMapper mvFavoriteMapper;
    @Resource
    private PlaylistFavoriteMapper playlistFavoriteMapper;

    @Override
    public FavoriteGroupStateVO getState(Long userId) {
        requireUser(userId);
        FavoriteGroupStateVO state = new FavoriteGroupStateVO();
        state.setGroups(groupMapper.selectGroups(userId));
        state.setItems(itemMapper.selectUserItems(userId));
        return state;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public FavoriteGroupVO createGroup(Long userId, String name) {
        requireUser(userId);
        String normalizedName = normalizeName(name);
        List<FavoriteCollectionGroup> current = groupMapper.selectOwnedForUpdate(userId);
        if (current.size() >= MAX_GROUPS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "收藏分组最多可以建20个");
        }

        FavoriteCollectionGroup group = new FavoriteCollectionGroup();
        group.setUserId(userId);
        group.setName(normalizedName);
        Integer maxOrder = groupMapper.selectMaxSortOrder(userId);
        group.setSortOrder((maxOrder == null ? 0 : maxOrder) + 1);
        group.setCreateTime(LocalDateTime.now());
        group.setUpdateTime(LocalDateTime.now());
        try {
            groupMapper.insert(group);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "这个分组已经在收藏架上了");
        }
        log.info("event=favorite_group_created userId={} groupId={}", userId, group.getId());
        return groupMapper.selectGroups(userId).stream()
                .filter(item -> group.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("分组创建后读取失败"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameGroup(Long userId, Long groupId, String name) {
        requireOwnedGroup(userId, groupId);
        String normalizedName = normalizeName(name);
        try {
            if (groupMapper.updateOwnedName(userId, groupId, normalizedName) != 1) {
                throw new BusinessException(ResultCode.NOT_FOUND, "收藏分组不存在");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "这个分组名称已经用过了");
        }
        log.info("event=favorite_group_renamed userId={} groupId={}", userId, groupId);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void reorderGroups(Long userId, List<Long> groupIds) {
        requireUser(userId);
        if (groupIds == null || groupIds.isEmpty() || groupIds.size() > MAX_GROUPS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分组顺序不正确");
        }
        List<FavoriteCollectionGroup> ownedGroups = groupMapper.selectOwnedForUpdate(userId);
        Map<Long, FavoriteCollectionGroup> ownedById = new LinkedHashMap<>();
        for (FavoriteCollectionGroup group : ownedGroups) ownedById.put(group.getId(), group);
        Set<Long> requested = new HashSet<>(groupIds);
        if (requested.size() != groupIds.size() || requested.contains(null)
                || requested.size() != ownedById.size() || !requested.equals(ownedById.keySet())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请刷新后再调整分组顺序");
        }
        for (int index = 0; index < groupIds.size(); index++) {
            if (groupMapper.updateOwnedSortOrder(userId, groupIds.get(index), index + 1) != 1) {
                throw new BusinessException("分组顺序保存失败");
            }
        }
        log.info("event=favorite_groups_reordered userId={} groupCount={}", userId, groupIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long userId, Long groupId) {
        requireUser(userId);
        if (groupId == null) throw new BusinessException(ResultCode.PARAM_ERROR, "收藏分组不能为空");
        itemMapper.deleteGroupItems(userId, groupId);
        groupMapper.deleteOwned(userId, groupId);
        log.info("event=favorite_group_deleted userId={} groupId={}", userId, groupId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignItem(Long userId, Long groupId, String resourceType, Long resourceId) {
        requireOwnedGroup(userId, groupId);
        String type = normalizeType(resourceType);
        requireFavoriteExists(userId, type, resourceId);
        itemMapper.upsert(userId, groupId, type, resourceId);
        log.info("event=favorite_item_grouped userId={} groupId={} resourceType={} resourceId={}",
                userId, groupId, type, resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Long userId, String resourceType, Long resourceId) {
        requireUser(userId);
        String type = normalizeType(resourceType);
        if (resourceId == null || resourceId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "收藏资源不能为空");
        }
        itemMapper.deleteResource(userId, type, resourceId);
    }

    private FavoriteCollectionGroup requireOwnedGroup(Long userId, Long groupId) {
        requireUser(userId);
        if (groupId == null || groupId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "收藏分组不能为空");
        }
        FavoriteCollectionGroup group = groupMapper.selectOwned(userId, groupId);
        if (group == null) throw new BusinessException(ResultCode.NOT_FOUND, "收藏分组不存在");
        return group;
    }

    private void requireFavoriteExists(Long userId, String type, Long resourceId) {
        if (resourceId == null || resourceId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "收藏资源不能为空");
        }
        Long count;
        if ("song".equals(type)) {
            count = songLikeMapper.selectCount(new LambdaQueryWrapper<SongLike>()
                    .eq(SongLike::getUserId, userId).eq(SongLike::getSongId, resourceId)
                    .eq(SongLike::getIsFavorite, 1).eq(SongLike::getDeleted, CommonConstants.NOT_DELETED));
        } else if ("album".equals(type)) {
            count = albumFavoriteMapper.selectCount(new LambdaQueryWrapper<AlbumFavorite>()
                    .eq(AlbumFavorite::getUserId, userId).eq(AlbumFavorite::getAlbumId, resourceId)
                    .eq(AlbumFavorite::getDeleted, CommonConstants.NOT_DELETED));
        } else if ("mv".equals(type)) {
            count = mvFavoriteMapper.selectCount(new LambdaQueryWrapper<MvFavorite>()
                    .eq(MvFavorite::getUserId, userId).eq(MvFavorite::getMvId, resourceId)
                    .eq(MvFavorite::getDeleted, CommonConstants.NOT_DELETED));
        } else {
            count = playlistFavoriteMapper.selectCount(new LambdaQueryWrapper<PlaylistFavorite>()
                    .eq(PlaylistFavorite::getUserId, userId).eq(PlaylistFavorite::getPlaylistId, resourceId)
                    .eq(PlaylistFavorite::getDeleted, CommonConstants.NOT_DELETED));
        }
        if (count == null || count == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能整理自己仍在收藏中的内容");
        }
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 30 || normalized.matches(".*[\\p{Cntrl}].*")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分组名称需要是1至30个可见字符");
        }
        if (RESERVED_NAMES.contains(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "这个名称已经留给收藏架使用了");
        }
        return normalized;
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "收藏类型不正确");
        }
        return normalized;
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0) throw new BusinessException(ResultCode.UNAUTHORIZED);
    }
}

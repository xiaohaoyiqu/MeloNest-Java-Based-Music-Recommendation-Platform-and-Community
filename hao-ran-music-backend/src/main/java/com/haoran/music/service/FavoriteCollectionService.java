package com.haoran.music.service;

import com.haoran.music.vo.favorite.FavoriteGroupStateVO;
import com.haoran.music.vo.favorite.FavoriteGroupVO;

import java.util.List;

                            
public interface FavoriteCollectionService {
    FavoriteGroupStateVO getState(Long userId);
    FavoriteGroupVO createGroup(Long userId, String name);
    void renameGroup(Long userId, Long groupId, String name);
    void reorderGroups(Long userId, List<Long> groupIds);
    void deleteGroup(Long userId, Long groupId);
    void assignItem(Long userId, Long groupId, String resourceType, Long resourceId);
    void removeItem(Long userId, String resourceType, Long resourceId);
}

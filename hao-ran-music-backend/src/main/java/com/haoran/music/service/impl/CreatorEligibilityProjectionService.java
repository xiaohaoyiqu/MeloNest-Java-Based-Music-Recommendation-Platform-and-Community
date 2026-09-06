   
                      
   
package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.entity.Creator;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserExtension;
import com.haoran.music.mapper.CreatorMapper;
import com.haoran.music.mapper.UserExtensionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

   
                           
   
@Service
public class CreatorEligibilityProjectionService {

    private final UserExtensionMapper userExtensionMapper;
    private final CreatorMapper creatorMapper;

    public CreatorEligibilityProjectionService(UserExtensionMapper userExtensionMapper,
                                               CreatorMapper creatorMapper) {
        this.userExtensionMapper = userExtensionMapper;
        this.creatorMapper = creatorMapper;
    }

    public void synchronize(User user) {
        if (user == null || user.getId() == null) {
            throw new BusinessException("创作者资格主记录不能为空");
        }
        boolean active = Integer.valueOf(1).equals(user.getIsCreator())
                && "active".equalsIgnoreCase(user.getCreatorStatus());
        synchronizeLegacyExtension(user, active);
        synchronizeCreatorRecord(user);
    }

    private void synchronizeLegacyExtension(User user, boolean active) {
        UserExtension extension = userExtensionMapper.selectOne(new LambdaQueryWrapper<UserExtension>()
                .eq(UserExtension::getUserId, user.getId())
                .last("LIMIT 1"));
        if (extension == null) {
            if (!active) {
                return;
            }
            extension = new UserExtension();
            extension.setUserId(user.getId());
            extension.setVerified(0);
            extension.setFollowerCount(0);
            extension.setFollowingCount(0);
            extension.setWorksCount(0);
            extension.setTotalPlays(0L);
            extension.setCreateTime(LocalDateTime.now());
        }
        extension.setIsCreator(active ? 1 : 0);
        extension.setCreatorType(toLegacyCreatorType(user.getCreatorType()));
        extension.setUpdateTime(LocalDateTime.now());
        int updated = extension.getId() == null
                ? userExtensionMapper.insert(extension)
                : userExtensionMapper.updateById(extension);
        if (updated != 1) {
            throw new BusinessException("创作者扩展投影同步失败");
        }
    }

    private void synchronizeCreatorRecord(User user) {
        Creator creator = creatorMapper.selectOne(new LambdaQueryWrapper<Creator>()
                .eq(Creator::getUserId, user.getId())
                .last("LIMIT 1"));
        if (!Integer.valueOf(1).equals(user.getIsCreator()) || "removed".equals(user.getCreatorStatus())) {
            if (creator != null && creatorMapper.deleteById(creator.getId()) != 1) {
                throw new BusinessException("创作者记录移除失败");
            }
            return;
        }
        if (creator == null) {
            creator = new Creator();
            creator.setUserId(user.getId());
            creator.setCreateTime(LocalDateTime.now());
        }
        creator.setCreatorType(user.getCreatorType());
        creator.setStatus(user.getCreatorStatus());
        int updated = creator.getId() == null
                ? creatorMapper.insert(creator)
                : creatorMapper.updateById(creator);
        if (updated != 1) {
            throw new BusinessException("创作者记录投影同步失败");
        }
    }

    private Integer toLegacyCreatorType(String creatorType) {
        if ("singer".equalsIgnoreCase(creatorType)) {
            return 1;
        }
        if ("producer".equalsIgnoreCase(creatorType)) {
            return 2;
        }
        if ("lyricist".equalsIgnoreCase(creatorType)) {
            return 3;
        }
        return 0;
    }
}

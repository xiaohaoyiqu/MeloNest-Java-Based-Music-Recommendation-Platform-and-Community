   
                      
   
package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.entity.Artist;
import com.haoran.music.entity.ArtistClaim;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.ArtistClaimMapper;
import com.haoran.music.mapper.ArtistMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.ArtistProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class ArtistProfileServiceImpl implements ArtistProfileService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private ArtistMapper artistMapper;
    @Resource
    private ArtistClaimMapper artistClaimMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Artist resolveOwnedProfile(Long userId, String displayName, String sourceType) {
        if (userId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "投稿作者不能为空");
        }
        User owner = userMapper.selectByIdForUpdate(userId);
        if (owner == null || CommonConstants.DELETED.equals(owner.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿作者不存在");
        }

        ArtistClaim existingClaim = artistClaimMapper.selectOne(new LambdaQueryWrapper<ArtistClaim>()
                .eq(ArtistClaim::getUserId, userId)
                .eq(ArtistClaim::getStatus, "approved")
                .eq(ArtistClaim::getDeleted, CommonConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (existingClaim != null) {
            Artist artist = artistMapper.selectById(existingClaim.getArtistId());
            if (artist == null || CommonConstants.DELETED.equals(artist.getDeleted())) {
                throw new BusinessException(409, "歌手认领关系指向无效档案，请联系管理员修复");
            }
            return artist;
        }

        String normalizedName = firstNotBlank(displayName, owner.getNickname(), owner.getUsername());
        if (normalizedName == null) {
            normalizedName = "创作者" + userId;
        }
        normalizedName = normalizedName.trim();
        if (normalizedName.length() > 100) {
            normalizedName = normalizedName.substring(0, 100);
        }

        Artist artist = new Artist();
        artist.setName(normalizedName);
        artist.setCover("/default-artist.png");
        artist.setDescription("投稿作者独立档案");
        artist.setFansCount(0L);
        artist.setSongCount(0L);
        artist.setAlbumCount(0L);
        artist.setPlayCount(0L);
        artist.setCommentCount(0L);
        artist.setHotScore(0);
        artist.setStatus(CommonConstants.STATUS_NORMAL);
        artist.setDeleted(CommonConstants.NOT_DELETED);
        artist.setCreateTime(LocalDateTime.now());
        artist.setUpdateTime(LocalDateTime.now());
        if (artistMapper.insert(artist) != 1) {
            throw new BusinessException("创建歌手档案失败");
        }

        ArtistClaim claim = new ArtistClaim();
        claim.setArtistId(artist.getId());
        claim.setUserId(userId);
        claim.setStatus("approved");
        claim.setSourceType(StrUtil.blankToDefault(sourceType, "submission"));
        claim.setDeleted(CommonConstants.NOT_DELETED);
        claim.setCreateTime(LocalDateTime.now());
        claim.setUpdateTime(LocalDateTime.now());
        if (artistClaimMapper.insert(claim) != 1) {
            throw new BusinessException("创建歌手认领关系失败");
        }
        return artist;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}

   
                      
   
package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.Album;
import com.haoran.music.entity.MV;
import com.haoran.music.entity.PaidResource;
import com.haoran.music.entity.Playlist;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserPurchased;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.AlbumMapper;
import com.haoran.music.mapper.PaidResourceMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserPurchasedMapper;
import com.haoran.music.service.ContentAccessService;
import com.haoran.music.service.CreatorEligibilityService;
import com.haoran.music.service.UserVipService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ContentAccessServiceImpl implements ContentAccessService {

    @Resource
    private PaidResourceMapper paidResourceMapper;

    @Resource
    private UserPurchasedMapper userPurchasedMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private AlbumMapper albumMapper;

    @Resource
    private SongMapper songMapper;

    @Resource
    private CreatorEligibilityService creatorEligibilityService;

    @Resource
    private UserVipService userVipService;

    @Override
    public void requireSongMetadataAccess(Song song, Long userId) {
        requireSongMetadataState(song);
    }

    private Album requireSongMetadataState(Song song) {
        requirePublicState(song != null ? song.getStatus() : null,
                song != null ? song.getDeleted() : null, "歌曲不存在或已下架");
        if (song.getAlbumId() != null) {
            Album album = albumMapper.selectById(song.getAlbumId());
            requirePublicState(album != null ? album.getStatus() : null,
                    album != null ? album.getDeleted() : null, "歌曲所属专辑不存在或已下架");
            return album;
        }
        return null;
    }

    @Override
    public void requireMvMetadataAccess(MV mv, Long userId) {
        requirePublicState(mv != null ? mv.getStatus() : null,
                mv != null ? mv.getDeleted() : null, "MV不存在或已下架");
        if (mv.getSongId() != null) {
            Song song = songMapper.selectById(mv.getSongId());
            requirePublicState(song != null ? song.getStatus() : null,
                    song != null ? song.getDeleted() : null, "MV关联歌曲不存在或已下架");
        }
    }

    @Override
    public void requireAlbumMetadataAccess(Album album, Long userId) {
        requirePublicState(album != null ? album.getStatus() : null,
                album != null ? album.getDeleted() : null, "专辑不存在或已下架");
    }

    @Override
    public void requirePlaylistMetadataAccess(Playlist playlist, Long userId) {
        requirePublicState(playlist != null ? playlist.getStatus() : null,
                playlist != null ? playlist.getDeleted() : null, "歌单不存在或已下架");
        if (!Integer.valueOf(1).equals(playlist.getIsPublic())
                && !Objects.equals(playlist.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问私密歌单");
        }
    }

    @Override
    public void requireSongAccess(Song song, Long userId) {
        Album album = requireSongMetadataState(song);

        requirePaidResourceAccess(
                "song", song.getId(), Integer.valueOf(1).equals(song.getIsPaid()), song.getUploaderId(), userId);

        if (album == null) {
            return;
        }
        requirePaidResourceAccess(
                "album", album.getId(), Integer.valueOf(1).equals(album.getIsPaid()), null, userId);
    }

    @Override
    public void requireSongPreviewAccess(Song song, Long userId) {
        Album album = requireSongMetadataState(song);
        requirePreviewAvailability("song", song.getId(), Integer.valueOf(1).equals(song.getIsPaid()));
        if (album != null) {
            requirePreviewAvailability("album", album.getId(), Integer.valueOf(1).equals(album.getIsPaid()));
        }
    }

    @Override
    public void requireMvAccess(MV mv, Long userId) {
        requireMvMetadataAccess(mv, userId);
        requirePaidResourceAccess("mv", mv.getId(), false, null, userId);
    }

    @Override
    public void requireMvPreviewAccess(MV mv, Long userId) {
        requireMvMetadataAccess(mv, userId);
        requirePreviewAvailability("mv", mv.getId(), false);
    }

    @Override
    public void requireAlbumAccess(Album album, Long userId) {
        requireAlbumMetadataAccess(album, userId);
        requirePaidResourceAccess(
                "album", album.getId(), Integer.valueOf(1).equals(album.getIsPaid()), null, userId);
    }

    @Override
    public void requirePlaylistAccess(Playlist playlist, Long userId) {
        requirePlaylistMetadataAccess(playlist, userId);
        requirePaidResourceAccess(
                "playlist", playlist.getId(), Integer.valueOf(1).equals(playlist.getIsPaid()),
                playlist.getUserId(), userId);
    }

    private void requirePublicState(Integer status, Integer deleted, String message) {
        if (!CommonConstants.STATUS_NORMAL.equals(status)
                || !CommonConstants.NOT_DELETED.equals(deleted)) {
            throw new BusinessException(ResultCode.NOT_FOUND, message);
        }
    }

       
                            
      
                                   
       
    private boolean requirePaidResourceAccess(String resourceType, Long resourceId, boolean markedPaid,
                                              Long ownerHint, Long userId) {
        PaidResource paid = paidResourceMapper.selectByResourceIdentity(resourceType, resourceId);
        boolean activeSale = paid != null
                && Integer.valueOf(1).equals(paid.getIsEnabled())
                && "approved".equals(paid.getStatus());
        boolean stoppedSale = paid != null && "cancelled".equals(paid.getStatus());
        if (!markedPaid && !activeSale && !stoppedSale) {
            return true;
        }

        User user = userId == null ? null : userMapper.selectById(userId);
        if (UserAccountStatusUtil.canInteract(user) && UserRole.canModerate(user.getRole())) {
            return true;
        }
        Long ownerId = paid != null ? paid.getOwnerId() : ownerHint;
        if (paid != null && !creatorEligibilityService.isEligible(ownerId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "付费资源当前不可用");
        }
        if (userId != null && Objects.equals(ownerId, userId)) {
            UserAccountStatusUtil.requireCanInteract(user, "访问本人付费资源");
            return true;
        }
        if (paid == null || (!activeSale && !stoppedSale)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "付费资源配置尚未审核生效");
        }
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先购买后访问该资源");
        }
        UserAccountStatusUtil.requireCanInteract(user, "访问付费资源");

                                            
        if (Boolean.TRUE.equals(userVipService.isVip(userId))) {
            return true;
        }

        UserPurchased purchased = userPurchasedMapper.selectOne(new LambdaQueryWrapper<UserPurchased>()
                .eq(UserPurchased::getUserId, userId)
                .eq(UserPurchased::getResourceType, resourceType)
                .eq(UserPurchased::getResourceId, resourceId)
                .last("LIMIT 1"));
        if (purchased == null || (purchased.getExpireTime() != null
                && !purchased.getExpireTime().isAfter(LocalDateTime.now()))) {
            String message = stoppedSale
                    ? "付费资源已停售，且当前账号没有有效存量权益"
                    : "未获得该付费资源的有效访问权益";
            throw new BusinessException(ResultCode.FORBIDDEN, message);
        }
        return true;
    }

       
                                                                               
                                                                               
                                                                 
       
    private void requirePreviewAvailability(String resourceType, Long resourceId, boolean markedPaid) {
        PaidResource paid = paidResourceMapper.selectByResourceIdentity(resourceType, resourceId);
        boolean activeSale = paid != null
                && Integer.valueOf(1).equals(paid.getIsEnabled())
                && "approved".equals(paid.getStatus());
        if (!markedPaid && paid == null) {
            return;
        }
        if (!activeSale) {
            throw new BusinessException(ResultCode.FORBIDDEN, "付费资源当前不提供试听或试看");
        }
        if (!creatorEligibilityService.isEligible(paid.getOwnerId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "付费资源当前不可用");
        }
    }
}

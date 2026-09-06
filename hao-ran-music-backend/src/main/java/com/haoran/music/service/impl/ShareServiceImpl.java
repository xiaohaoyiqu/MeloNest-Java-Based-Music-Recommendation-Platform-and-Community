




package com.haoran.music.service.impl;

import com.haoran.music.entity.Album;
import com.haoran.music.entity.Artist;
import com.haoran.music.entity.Playlist;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.AlbumMapper;
import com.haoran.music.mapper.ArtistMapper;
import com.haoran.music.mapper.PlaylistMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.ShareService;
import com.haoran.music.service.UserStatisticsService;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.UserAccountStatusUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;




@Slf4j
@Service
public class ShareServiceImpl implements ShareService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SongMapper songMapper;
    private final AlbumMapper albumMapper;
    private final ArtistMapper artistMapper;
    private final PlaylistMapper playlistMapper;
    private final UserStatisticsService userStatisticsService;
    private final UserMapper userMapper;

    private static final String SHARE_KEY_PREFIX = "share:";
    private static final String SHARE_STATS_PREFIX = "share_stats:";
    private static final long SHARE_EXPIRE_HOURS = 72;              
    private static final int MAX_BATCH_SIZE = 20;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<String> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("song", "album", "artist", "playlist")));
    private static final Set<String> SUPPORTED_PLATFORMS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("wechat", "weibo", "qq", "link", "copy", "system", "other")));

    public ShareServiceImpl(RedisTemplate<String, Object> redisTemplate,
                            SongMapper songMapper,
                            AlbumMapper albumMapper,
                            ArtistMapper artistMapper,
                            PlaylistMapper playlistMapper,
                            UserStatisticsService userStatisticsService,
                            UserMapper userMapper) {
        this.redisTemplate = redisTemplate;
        this.songMapper = songMapper;
        this.albumMapper = albumMapper;
        this.artistMapper = artistMapper;
        this.playlistMapper = playlistMapper;
        this.userStatisticsService = userStatisticsService;
        this.userMapper = userMapper;
    }

    @Override
    public Map<String, Object> generateShareLink(String type, Long resourceId, Long userId) {
        requireAuthenticatedUser(userId, "生成分享链接");
        return generateShareLinkForValidatedUser(normalizeType(type), resourceId, userId);
    }

    private Map<String, Object> generateShareLinkForValidatedUser(String type, Long resourceId, Long userId) {
        if (resourceId == null || resourceId <= 0) {
            throw new IllegalArgumentException("资源ID不合法");
        }

        if (!validateResource(type, resourceId)) {
            throw new IllegalArgumentException("资源不存在、不可见或不允许分享");
        }


        String shareCode = generateShareCode(type, resourceId);


        Map<String, Object> shareInfo = new HashMap<>();
        shareInfo.put("type", type);
        shareInfo.put("resourceId", resourceId);
        shareInfo.put("creatorId", userId);
        shareInfo.put("createTime", System.currentTimeMillis());

        String key = SHARE_KEY_PREFIX + shareCode;
        redisTemplate.opsForValue().set(key, shareInfo, SHARE_EXPIRE_HOURS, TimeUnit.HOURS);


        initShareStats(type, resourceId);


        String shareUrl = buildShareUrl(shareCode);
        String qrCodeUrl = buildQrCodeUrl(shareUrl);

        Map<String, Object> result = new HashMap<>();
        result.put("shareCode", shareCode);
        result.put("shareUrl", shareUrl);
        result.put("qrCodeUrl", qrCodeUrl);
        result.put("expireTime", System.currentTimeMillis() + SHARE_EXPIRE_HOURS * 3600000);

        log.info("生成分享链接: type={}, resourceId={}, creatorId={}", type, resourceId, userId);

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getResourceByShareCode(String shareCode) {
        if (!isValidShareCodeFormat(shareCode)) {
            return null;
        }
        String key = SHARE_KEY_PREFIX + shareCode;
        Object cached = redisTemplate.opsForValue().get(key);

        if (!(cached instanceof Map)) {
            return null;
        }
        Map<String, Object> shareInfo = (Map<String, Object>) cached;

        Object typeValue = shareInfo.get("type");
        Object resourceIdValue = shareInfo.get("resourceId");
        if (!(typeValue instanceof String) || !(resourceIdValue instanceof Number)) {
            return null;
        }
        String type;
        try {
            type = normalizeType((String) typeValue);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Long resourceId = ((Number) resourceIdValue).longValue();


        Map<String, Object> resourceInfo = getResourceInfo(type, resourceId);
        if (resourceInfo != null) {
            resourceInfo.put("shareType", type);
            resourceInfo.put("shareCode", shareCode);
        }

        return resourceInfo;
    }

    @Override
    public void recordShare(String type, Long resourceId, String platform, Long userId) {
        String normalizedType = normalizeType(type);
        String normalizedPlatform = normalizePlatform(platform);
        User user = requireAuthenticatedUser(userId, "分享内容");
        if (!validateResource(normalizedType, resourceId)) {
            throw new IllegalArgumentException("资源不存在、不可见或不允许分享");
        }
        boolean canContributeStats = UserAccountStatusUtil.canContributePublicStats(user);
        if (!canContributeStats) {
            log.debug("跳过非公共统计账号分享热度: userId={}, type={}, resourceId={}", userId, type, resourceId);
            return;
        }

        String statsKey = SHARE_STATS_PREFIX + normalizedType + ":" + resourceId;
        String hashKey = "platform:" + normalizedPlatform;


        redisTemplate.opsForHash().increment(statsKey, hashKey, 1);
        redisTemplate.opsForHash().increment(statsKey, "total", 1);


        redisTemplate.expire(statsKey, 7, TimeUnit.DAYS);
        userStatisticsService.incrementInteraction(userId, "share", 1);

        log.info("记录分享: type={}, resourceId={}, platform={}", normalizedType, resourceId, normalizedPlatform);
    }

    @Override
    public Map<String, Object> getShareStats(String type, Long resourceId) {
        String statsKey = SHARE_STATS_PREFIX + type + ":" + resourceId;
        Map<Object, Object> stats = redisTemplate.opsForHash().entries(statsKey);

        Map<String, Object> result = new HashMap<>();
        if (stats != null && !stats.isEmpty()) {
            for (Map.Entry<Object, Object> entry : stats.entrySet()) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
        }


        if (result.isEmpty()) {
            result.put("total", 0);
            result.put("views", 0);
        }


        String viewsKey = "share_views:" + type + ":" + resourceId;
        Object views = redisTemplate.opsForValue().get(viewsKey);
        if (views != null) {
            result.put("views", views);
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchGenerateShareLinks(Map<String, Object> items, Long userId) {
        Map<String, Object> result = new HashMap<>();
        requireAuthenticatedUser(userId, "批量生成分享链接");

        Object rawItems = items == null ? null : items.get("items");
        if (!(rawItems instanceof List)) {
            return result;
        }
        List<?> shareItems = (List<?>) rawItems;
        if (shareItems.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("单次最多生成" + MAX_BATCH_SIZE + "个分享链接");
        }

        Map<String, Map<String, Object>> shareLinks = new HashMap<>();

        for (Object rawItem : shareItems) {
            if (!(rawItem instanceof Map)) {
                throw new IllegalArgumentException("分享项格式错误");
            }
            Map<?, ?> item = (Map<?, ?>) rawItem;
            Object typeValue = item.get("type");
            Object resourceIdValue = item.get("resourceId");
            if (!(typeValue instanceof String) || !(resourceIdValue instanceof Number)) {
                throw new IllegalArgumentException("分享项缺少合法的type或resourceId");
            }
            String type = normalizeType((String) typeValue);
            Long resourceId = ((Number) resourceIdValue).longValue();

            try {
                Map<String, Object> shareInfo = generateShareLinkForValidatedUser(type, resourceId, userId);
                shareLinks.put(type + ":" + resourceId, shareInfo);
            } catch (Exception e) {
                log.warn("生成分享链接失败: type={}, resourceId={}", type, resourceId);
            }
        }

        result.put("shareLinks", shareLinks);
        result.put("total", shareLinks.size());

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void incrementShareView(String shareCode, Long userId) {
        if (!isValidShareCodeFormat(shareCode)) {
            return;
        }
        if (userId != null && !UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            log.debug("跳过非公共统计账号分享访问热度: shareCode={}, userId={}", shareCode, userId);
            return;
        }

        String key = SHARE_KEY_PREFIX + shareCode;
        Map<String, Object> shareInfo = (Map<String, Object>) redisTemplate.opsForValue().get(key);

        if (shareInfo != null) {
            String type = (String) shareInfo.get("type");
            Long resourceId = ((Number) shareInfo.get("resourceId")).longValue();


            String viewsKey = "share_views:" + type + ":" + resourceId;
            redisTemplate.opsForValue().increment(viewsKey);
            redisTemplate.expire(viewsKey, 7, TimeUnit.DAYS);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean cancelShare(String shareCode, Long userId) {
        if (userId == null || !isValidShareCodeFormat(shareCode)) {
            return false;
        }
        String key = SHARE_KEY_PREFIX + shareCode;
        Map<String, Object> shareInfo = (Map<String, Object>) redisTemplate.opsForValue().get(key);

        if (shareInfo == null) {
            return false;
        }


        Object creatorValue = shareInfo.get("creatorId");
        if (!(creatorValue instanceof Number)) {
            return false;
        }
        Long creatorId = ((Number) creatorValue).longValue();
        if (!creatorId.equals(userId)) {
            return false;
        }

        redisTemplate.delete(key);
        log.info("取消分享: shareCode={}", shareCode);

        return true;
    }

    @Override
    public boolean validateShareCode(String shareCode) {
        if (!isValidShareCodeFormat(shareCode)) {
            return false;
        }
        String key = SHARE_KEY_PREFIX + shareCode;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key)) && getResourceByShareCode(shareCode) != null;
    }










    private boolean validateResource(String type, Long resourceId) {
        return getResourceInfo(type, resourceId) != null;
    }








    private Map<String, Object> getResourceInfo(String type, Long resourceId) {
        switch (type) {
            case "song":
                Song song = songMapper.selectById(resourceId);
                if (song != null
                        && CommonConstants.STATUS_NORMAL.equals(song.getStatus())
                        && (song.getPublishStatus() == null || "published".equals(song.getPublishStatus()))
                        && (song.getReviewStatus() == null || "approved".equals(song.getReviewStatus()))) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("id", song.getId());
                    info.put("name", song.getName());
                    info.put("cover", song.getCover());
                    info.put("artistIds", song.getArtistIds());
                    info.put("albumId", song.getAlbumId());
                    info.put("duration", song.getDuration());
                    return info;
                }
                break;
            case "album":
                Album album = albumMapper.selectById(resourceId);
                if (album != null
                        && CommonConstants.STATUS_NORMAL.equals(album.getStatus())
                        && !CommonConstants.NO.equals(album.getAllowShare())) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("id", album.getId());
                    info.put("name", album.getName());
                    info.put("cover", album.getCover());
                    info.put("artistId", album.getArtistId());
                    info.put("songCount", album.getSongCount());
                    return info;
                }
                break;
            case "artist":
                Artist artist = artistMapper.selectById(resourceId);
                if (artist != null && CommonConstants.STATUS_NORMAL.equals(artist.getStatus())) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("id", artist.getId());
                    info.put("name", artist.getName());
                    info.put("cover", artist.getCover());
                    info.put("description", artist.getDescription());
                    return info;
                }
                break;
            case "playlist":
                Playlist playlist = playlistMapper.selectById(resourceId);
                if (playlist != null
                        && CommonConstants.STATUS_NORMAL.equals(playlist.getStatus())
                        && CommonConstants.YES.equals(playlist.getIsPublic())
                        && !CommonConstants.NO.equals(playlist.getAllowShare())) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("id", playlist.getId());
                    info.put("name", playlist.getName());
                    info.put("cover", playlist.getCover());
                    info.put("userId", playlist.getUserId());
                    info.put("songCount", playlist.getSongCount());
                    return info;
                }
                break;
        }
        return null;
    }








    private String generateShareCode(String type, Long resourceId) {
        byte[] randomBytes = new byte[18];
        for (int attempt = 0; attempt < 5; attempt++) {
            SECURE_RANDOM.nextBytes(randomBytes);
            String shareCode = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(SHARE_KEY_PREFIX + shareCode))) {
                return shareCode;
            }
        }
        throw new IllegalStateException("分享码生成失败，请稍后重试");
    }

    private User requireAuthenticatedUser(Long userId, String action) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        User user = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(user, action);
        return user;
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的分享资源类型");
        }
        return normalized;
    }

    private String normalizePlatform(String platform) {
        String normalized = platform == null ? "" : platform.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_PLATFORMS.contains(normalized)) {
            throw new IllegalArgumentException("不支持的分享平台");
        }
        return normalized;
    }

    private boolean isValidShareCodeFormat(String shareCode) {
        return shareCode != null && shareCode.matches("[A-Za-z0-9_-]{8,64}");
    }







    private String buildShareUrl(String shareCode) {
        return "/share/" + shareCode;
    }







    private String buildQrCodeUrl(String shareUrl) {


        return shareUrl;
    }







    private void initShareStats(String type, Long resourceId) {
        String statsKey = SHARE_STATS_PREFIX + type + ":" + resourceId;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(statsKey))) {
            Map<String, Object> initStats = new HashMap<>();
            initStats.put("total", 0);
            initStats.put("views", 0);
            initStats.put("wechat", 0);
            initStats.put("weibo", 0);
            initStats.put("qq", 0);

            redisTemplate.opsForHash().putAll(statsKey, initStats);
            redisTemplate.expire(statsKey, 7, TimeUnit.DAYS);
        }
    }
}

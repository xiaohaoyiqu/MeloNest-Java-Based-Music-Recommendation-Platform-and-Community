   
                      
                        
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.common.constant.UserAccountPolicyConstants;
import com.haoran.music.common.enums.UserType;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.MarketplaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

   
           
   
@Slf4j
@Service
public class MarketplaceServiceImpl extends ServiceImpl<MarketplaceItemMapper, MarketplaceItem>
        implements MarketplaceService {

    private static final String OPERATION_RECORD_KEY_PREFIX = "op:record:";
    private static final String MARKETPLACE_VIEW_DEDUPE_PREFIX = "view:dedupe:marketplace:";
    private static final long VIEW_DEDUPE_MINUTES = 10L;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE_NUMBER = 10000;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final BigDecimal MAX_PRICE = new BigDecimal("999999.00");
    private static final Set<String> ITEM_CATEGORIES = setOf("cd", "vinyl", "cassette", "other");
    private static final Set<String> ITEM_CONDITIONS = setOf("new", "like_new", "good", "acceptable");
    private static final Set<String> DELIVERY_METHODS = setOf("pickup", "delivery", "both");
    private static final Set<String> RESOURCE_TYPES = setOf("song", "album");
    private static final Map<String, Set<String>> STATUS_TRANSITIONS = createStatusTransitions();

    private final MarketplaceItemMapper marketplaceItemMapper;
    private final MarketplaceFavoriteMapper marketplaceFavoriteMapper;
    private final UserMapper userMapper;
    private final UserCreditMapper userCreditMapper;
    private final AlbumMapper albumMapper;
    private final SongMapper songMapper;
    private final RedisUtils redisUtils;

    public MarketplaceServiceImpl(
            MarketplaceItemMapper marketplaceItemMapper,
            MarketplaceFavoriteMapper marketplaceFavoriteMapper,
            UserMapper userMapper,
            UserCreditMapper userCreditMapper,
            AlbumMapper albumMapper,
            SongMapper songMapper,
            RedisUtils redisUtils) {
        this.marketplaceItemMapper = marketplaceItemMapper;
        this.marketplaceFavoriteMapper = marketplaceFavoriteMapper;
        this.userMapper = userMapper;
        this.userCreditMapper = userCreditMapper;
        this.albumMapper = albumMapper;
        this.songMapper = songMapper;
        this.redisUtils = redisUtils;
    }

    @Override
    public IPage<Object> getItems(String category, String condition, String sortBy, String keyword,
                                  Long currentUserId, Integer page, Integer size) {
        page = normalizePage(page);
        size = normalizePageSize(size, 12);
        keyword = normalizeKeyword(keyword);

        LambdaQueryWrapper<MarketplaceItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MarketplaceItem::getIsDeleted, false);

                       
        wrapper.in(MarketplaceItem::getStatus, Arrays.asList("available", "reserved"));
        wrapper.apply("EXISTS (SELECT 1 FROM user seller WHERE seller.id = marketplace_item.seller_id "
                        + "AND seller.deleted = 0 AND seller.status = 1 "
                        + "AND (seller.is_banned IS NULL OR seller.is_banned = 0) "
                        + "AND (seller.user_type IS NULL OR seller.user_type NOT IN (" + UserType.RESTRICTED_CODE_SQL + ")) "
                        + "AND (seller.risk_score IS NULL OR seller.risk_score < {0}) "
                        + "AND (seller.credit_score IS NULL OR seller.credit_score >= {1}) "
                        + "AND (seller.creator_status IS NULL OR seller.creator_status <> 'suspended'))",
                UserAccountPolicyConstants.HIGH_RISK_SCORE_THRESHOLD,
                UserAccountPolicyConstants.PUBLIC_FLOW_CREDIT_MIN_SCORE);

                              
        if (!ObjectUtils.isEmpty(keyword) && !keyword.trim().isEmpty()) {
            String searchKeyword = keyword;
            wrapper.and(w -> w
                .like(MarketplaceItem::getTitle, searchKeyword)
                .or()
                .like(MarketplaceItem::getResourceName, searchKeyword)
                .or()
                .like(MarketplaceItem::getDescription, searchKeyword)
            );
        }

               
        if (!ObjectUtils.isEmpty(category) && !"all".equals(category)) {
            wrapper.eq(MarketplaceItem::getCategory, category);
        }

               
        if (!ObjectUtils.isEmpty(condition) && !"all".equals(condition)) {
            wrapper.eq(MarketplaceItem::getConditionInfo, condition);
        }

             
        if (ObjectUtils.isEmpty(sortBy)) {
            sortBy = "latest";
        }
        switch (sortBy) {
            case "price_asc":
                wrapper.orderByAsc(MarketplaceItem::getPrice);
                break;
            case "price_desc":
                wrapper.orderByDesc(MarketplaceItem::getPrice);
                break;
            case "hot":
                wrapper.orderByDesc(MarketplaceItem::getViewCount)
                      .orderByDesc(MarketplaceItem::getFavoriteCount);
                break;
            default:
                wrapper.orderByDesc(MarketplaceItem::getCreateTime);
                break;
        }

        IPage<MarketplaceItem> pageResult = marketplaceItemMapper.selectPage(
                new Page<>(page, size),
                wrapper
        );

                
        IPage<Object> result = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        MarketplaceVoContext voContext = buildMarketplaceVoContext(pageResult.getRecords(), currentUserId);
        List<Object> records = pageResult.getRecords().stream()
                .map(item -> convertToVO(item, currentUserId, voContext))
                .collect(Collectors.toList());
        result.setRecords(records);

        return result;
    }

    @Override
    public Map<String, Object> getItemDetail(Long itemId, Long currentUserId, String viewerKey) {
        if (ObjectUtils.isEmpty(itemId)) {
            return null;
        }

        MarketplaceItem item = marketplaceItemMapper.selectById(itemId);
        if (ObjectUtils.isEmpty(item) || Boolean.TRUE.equals(item.getIsDeleted())) {
            return null;
        }

        User seller = userMapper.selectById(item.getSellerId());
        boolean owner = currentUserId != null && currentUserId.equals(item.getSellerId());
        boolean favorited = isFavorited(itemId, currentUserId);
        if (!owner && (!UserAccountStatusUtil.canExposePublicContent(seller)
                || ("removed".equals(item.getStatus()) && !favorited))) {
            return null;
        }

        if (UserAccountStatusUtil.canExposePublicContent(seller)
                && ("available".equals(item.getStatus()) || "reserved".equals(item.getStatus()))) {
            incrementViewCountOnce(itemId, viewerKey);
        }

        return convertToDetailVO(item, currentUserId);
    }

    private void incrementViewCountOnce(Long itemId, String viewerKey) {
        if (viewerKey == null || viewerKey.trim().isEmpty()) {
            return;
        }
        try {
            String key = MARKETPLACE_VIEW_DEDUPE_PREFIX + itemId + ":" + viewerKey;
            if (redisUtils.setIfAbsent(key, 1, VIEW_DEDUPE_MINUTES, TimeUnit.MINUTES)) {
                incrementViewCount(itemId);
            }
        } catch (Exception ex) {
            log.warn("商品曝光去重不可用，本次不累计浏览量: itemId={}", itemId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createItem(Long sellerId, String title, String category, String condition,
                          BigDecimal price, BigDecimal originalPrice, String description,
                          String images, String resourceType, Long resourceId,
                          String resourceName, String resourceCover,
                          String location, String deliveryMethod) {
        if (ObjectUtils.isEmpty(sellerId)) {
            log.warn("发布商品失败: 卖家ID为空");
            return null;
        }

        User user = userMapper.selectById(sellerId);
        UserAccountStatusUtil.requireCanInteract(user, "发布商品");
        ValidatedItemInput input = validateItemInput(sellerId, title, category, condition, price,
                originalPrice, description, images, resourceType, resourceId, location, deliveryMethod);

        MarketplaceItem item = new MarketplaceItem();
        item.setSellerId(sellerId);
        applyValidatedInput(item, input);
        item.setStatus("available");
        item.setViewCount(0);
        item.setFavoriteCount(0);
        item.setIsDeleted(false);
        item.setCreateTime(LocalDateTime.now());

        int result = marketplaceItemMapper.insert(item);

        if (result > 0) {
            log.info("发布商品成功: itemId={}, sellerId={}", item.getId(), sellerId);
            return item.getId();
        }

        log.warn("发布商品失败: sellerId={}", sellerId);
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateItemStatus(Long itemId, Long sellerId, String status) {
        if (ObjectUtils.isEmpty(itemId) || ObjectUtils.isEmpty(sellerId) || ObjectUtils.isEmpty(status)) {
            log.warn("更新商品状态失败: 参数为空");
            return false;
        }

        MarketplaceItem item = marketplaceItemMapper.selectById(itemId);
        if (ObjectUtils.isEmpty(item) || Boolean.TRUE.equals(item.getIsDeleted())) {
            log.warn("商品不存在或已删除: itemId={}", itemId);
            return false;
        }

               
        if (!item.getSellerId().equals(sellerId)) {
            log.warn("用户无权更新该商品: itemId={}, sellerId={}, itemSellerId={}",
                    itemId, sellerId, item.getSellerId());
            return false;
        }
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(sellerId), "更新商品状态");
        String nextStatus = normalizeRequired(status, "商品状态", 16).toLowerCase(Locale.ROOT);
        Set<String> allowed = STATUS_TRANSITIONS.get(item.getStatus());
        if (allowed == null || !allowed.contains(nextStatus)) {
            throw new IllegalArgumentException("不允许从 " + item.getStatus() + " 变更为 " + nextStatus);
        }
        int result = marketplaceItemMapper.transitionStatus(itemId, sellerId, item.getStatus(), nextStatus);

        log.info("更新商品状态成功: itemId={}, status={}", itemId, status);

        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteItem(Long itemId, Long sellerId) {
        if (ObjectUtils.isEmpty(itemId) || ObjectUtils.isEmpty(sellerId)) {
            log.warn("删除商品失败: 参数为空");
            return false;
        }

        MarketplaceItem item = marketplaceItemMapper.selectById(itemId);
        if (ObjectUtils.isEmpty(item) || Boolean.TRUE.equals(item.getIsDeleted())) {
            log.warn("商品不存在或已删除: itemId={}", itemId);
            return false;
        }

               
        if (!item.getSellerId().equals(sellerId)) {
            log.warn("用户无权删除该商品: itemId={}, sellerId={}, itemSellerId={}",
                    itemId, sellerId, item.getSellerId());
            return false;
        }
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(sellerId), "删除商品");

        item.setIsDeleted(true);

        int result = marketplaceItemMapper.updateById(item);

        log.info("删除商品成功: itemId={}, sellerId={}", itemId, sellerId);

        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean favoriteItem(Long itemId, Long userId) {
        if (ObjectUtils.isEmpty(itemId) || ObjectUtils.isEmpty(userId)) {
            log.warn("收藏商品失败: 参数为空");
            return false;
        }
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "收藏商品");

        MarketplaceItem item = marketplaceItemMapper.selectByIdForUpdate(itemId);
        if (ObjectUtils.isEmpty(item) || Boolean.TRUE.equals(item.getIsDeleted())) {
            log.warn("商品不存在或已删除: itemId={}", itemId);
            return false;
        }
        LambdaQueryWrapper<MarketplaceFavorite> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.eq(MarketplaceFavorite::getItemId, itemId)
                .eq(MarketplaceFavorite::getUserId, userId)
                .eq(MarketplaceFavorite::getDeleted, 0);
        if (marketplaceFavoriteMapper.selectCount(activeWrapper) != 0) {
            return true;
        }
        if (!"available".equals(item.getStatus()) && !"reserved".equals(item.getStatus())) {
            throw new IllegalArgumentException("这件收藏已经离开在售货架");
        }
        User seller = userMapper.selectById(item.getSellerId());
        if (!UserAccountStatusUtil.canExposePublicContent(seller)) {
            throw new IllegalArgumentException("这位摊主暂时无法继续交易");
        }
        marketplaceFavoriteMapper.activateFavorite(itemId, userId);
        marketplaceItemMapper.incrementFavoriteCount(itemId);

        log.info("收藏商品成功: itemId={}, userId={}", itemId, userId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unfavoriteItem(Long itemId, Long userId) {
        if (ObjectUtils.isEmpty(itemId) || ObjectUtils.isEmpty(userId)) {
            log.warn("取消收藏失败: 参数为空");
            return false;
        }

        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "取消收藏商品");

        int changed = marketplaceFavoriteMapper.deactivateFavorite(itemId, userId);
        MarketplaceItem item = marketplaceItemMapper.selectByIdForUpdate(itemId);
        if (changed > 0 && item != null && !Boolean.TRUE.equals(item.getIsDeleted())) {
            marketplaceItemMapper.decrementFavoriteCount(itemId);
        }

        log.info("取消收藏成功: itemId={}, userId={}", itemId, userId);
        return true;
    }

    public IPage<Object> getMyItems(Long sellerId, String status, Integer page, Integer size) {
        page = normalizePage(page);
        size = normalizePageSize(size, 10);

        LambdaQueryWrapper<MarketplaceItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MarketplaceItem::getSellerId, sellerId)
                .eq(MarketplaceItem::getIsDeleted, false);

        if (!ObjectUtils.isEmpty(status) && !"all".equals(status)) {
            wrapper.eq(MarketplaceItem::getStatus, status);
        }

        wrapper.orderByDesc(MarketplaceItem::getCreateTime);

        IPage<MarketplaceItem> pageResult = marketplaceItemMapper.selectPage(
                new Page<>(page, size),
                wrapper
        );

                
        IPage<Object> result = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        List<Object> records = pageResult.getRecords().stream()
                .map(item -> convertToVO(item, sellerId))
                .collect(Collectors.toList());
        result.setRecords(records);

        return result;
    }

    @Override
    public IPage<Object> getMyFavorites(Long userId, Integer page, Integer size) {
        page = normalizePage(page);
        size = normalizePageSize(size, 10);

                                    
        LambdaQueryWrapper<MarketplaceFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MarketplaceFavorite::getUserId, userId)
                .eq(MarketplaceFavorite::getDeleted, 0)
                .orderByDesc(MarketplaceFavorite::getCreateTime);

        IPage<MarketplaceFavorite> favoritePage = marketplaceFavoriteMapper.selectPage(
                new Page<>(page, size),
                wrapper
        );

        List<Long> itemIds = favoritePage.getRecords().stream()
                .map(MarketplaceFavorite::getItemId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, MarketplaceItem> itemMap = new HashMap<>();
        if (!itemIds.isEmpty()) {
            itemMap = marketplaceItemMapper.selectBatchIds(itemIds).stream()
                    .filter(item -> item != null && !Boolean.TRUE.equals(item.getIsDeleted()))
                    .collect(Collectors.toMap(MarketplaceItem::getId, item -> item, (left, right) -> left));
        }

        MarketplaceVoContext voContext = buildMarketplaceVoContext(new ArrayList<>(itemMap.values()), userId);
        List<Object> records = new ArrayList<>();
        for (MarketplaceFavorite favorite : favoritePage.getRecords()) {
            MarketplaceItem item = itemMap.get(favorite.getItemId());
            if (item != null) records.add(convertToVO(item, userId, voContext));
            else records.add(deletedFavoriteVO(favorite));
        }

        IPage<Object> result = new Page<>(favoritePage.getCurrent(), favoritePage.getSize(), favoritePage.getTotal());
        result.setRecords(records);

        return result;
    }

    @Override
    public List<Object> getPublicSellerItems(Long sellerId, Long viewerId, Integer limit) {
        User seller = sellerId == null ? null : userMapper.selectById(sellerId);
        if (!UserAccountStatusUtil.canExposePublicContent(seller)) return Collections.emptyList();
        int safeLimit = Math.min(Math.max(limit == null ? 6 : limit, 1), 12);
        List<MarketplaceItem> items = marketplaceItemMapper.selectList(new LambdaQueryWrapper<MarketplaceItem>()
                .eq(MarketplaceItem::getSellerId, sellerId)
                .eq(MarketplaceItem::getIsDeleted, false)
                .in(MarketplaceItem::getStatus, Arrays.asList("available", "reserved"))
                .orderByDesc(MarketplaceItem::getCreateTime)
                .last("LIMIT " + safeLimit));
        MarketplaceVoContext context = buildMarketplaceVoContext(items, viewerId);
        return items.stream().map(item -> (Object) convertToVO(item, viewerId, context)).collect(Collectors.toList());
    }

    @Override
    public void incrementViewCount(Long itemId) {
        if (ObjectUtils.isEmpty(itemId)) {
            return;
        }

        marketplaceItemMapper.incrementViewCount(itemId);
    }

    @Override
    public Boolean isFavorited(Long itemId, Long userId) {
        if (ObjectUtils.isEmpty(itemId) || ObjectUtils.isEmpty(userId)) {
            return false;
        }

                                            
        LambdaQueryWrapper<MarketplaceFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MarketplaceFavorite::getItemId, itemId)
                .eq(MarketplaceFavorite::getUserId, userId)
                .eq(MarketplaceFavorite::getDeleted, 0);

        Long count = marketplaceFavoriteMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

       
             
       
    private Map<String, Object> getSellerInfo(Long sellerId) {
        if (ObjectUtils.isEmpty(sellerId)) {
            return getAnonymousSeller();
        }

        User user = userMapper.selectById(sellerId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            return getAnonymousSeller();
        }

        Map<String, Object> sellerInfo = new HashMap<>();
        sellerInfo.put("id", user.getId());
        sellerInfo.put("nickname", user.getNickname());
        sellerInfo.put("avatar", user.getAvatar());

                  
        UserCredit userCredit = userCreditMapper.selectById(sellerId);
        if (userCredit != null) {
            sellerInfo.put("credit", userCredit.getCreditScore());
        } else {
            sellerInfo.put("credit", 100);
        }

        return sellerInfo;
    }

       
               
       
    private Map<String, Object> getAnonymousSeller() {
        Map<String, Object> sellerInfo = new HashMap<>();
        String defaultAvatar = "https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png";

        sellerInfo.put("id", 0L);
        sellerInfo.put("nickname", "匿名用户");
        sellerInfo.put("avatar", defaultAvatar);
        sellerInfo.put("credit", 100);

        return sellerInfo;
    }

       
                     
       
    private List<String> parseImages(String imagesJson) {
        if (ObjectUtils.isEmpty(imagesJson)) {
            return new ArrayList<>();
        }

        try {
            if (imagesJson.startsWith("[")) {
                return Arrays.asList(imagesJson.replaceAll("[\\[\\]\"]", "").split(","));
            }
            return Arrays.asList(imagesJson.split(","));
        } catch (Exception e) {
            log.debug("解析图片JSON失败: {}", imagesJson);
            return new ArrayList<>();
        }
    }

       
                   
                  
                                  
                   
       
    private Map<String, Object> convertToVO(MarketplaceItem item, Long currentUserId) {
        return convertToVO(item, currentUserId, null);
    }

    private Map<String, Object> convertToVO(MarketplaceItem item, Long currentUserId, MarketplaceVoContext context) {
        Map<String, Object> vo = new HashMap<>();

               
        vo.put("id", item.getId());
        vo.put("title", item.getTitle());
        vo.put("category", item.getCategory());
        vo.put("condition", item.getConditionInfo());
        vo.put("price", item.getPrice());
        vo.put("originalPrice", item.getOriginalPrice());
        vo.put("location", item.getLocation());
        vo.put("deliveryMethod", item.getDeliveryMethod());
        vo.put("status", item.getStatus());
        vo.put("viewCount", item.getViewCount() == null ? 0 : item.getViewCount());
        vo.put("favoriteCount", item.getFavoriteCount() == null ? 0 : item.getFavoriteCount());

             
        vo.put("images", parseImages(item.getImages()));

                                                   
        if (!ObjectUtils.isEmpty(item.getResourceType()) && !ObjectUtils.isEmpty(item.getResourceId())) {
            Map<String, Object> resource = new HashMap<>();
            resource.put("type", item.getResourceType());
            resource.put("id", item.getResourceId());
            resource.put("name", item.getResourceName());
            resource.put("cover", item.getResourceCover());

                             
            if ("song".equals(item.getResourceType())) {
                Song song = context != null ? context.songs.get(item.getResourceId()) : songMapper.selectById(item.getResourceId());
                if (song != null) {
                    resource.put("artistNames", song.getArtistNames());
                    resource.put("albumName", song.getAlbumName());
                    resource.put("duration", song.getDuration());
                }
            } else if ("album".equals(item.getResourceType())) {
                Album album = context != null ? context.albums.get(item.getResourceId()) : albumMapper.selectById(item.getResourceId());
                if (album != null) {
                    resource.put("artistNames", album.getArtistNames());
                    resource.put("songCount", album.getSongCount());
                    resource.put("releaseDate", album.getReleaseDate());
                }
            }
            vo.put("resource", resource);
        }

        boolean favorited = context != null
                ? context.favoriteItemIds.contains(item.getId())
                : isFavorited(item.getId(), currentUserId);
        vo.put("isFavorited", favorited);

             
        vo.put("createTime", item.getCreateTime());

                    
        Map<String, Object> sellerInfo = new HashMap<>();
        User seller = context != null ? context.sellers.get(item.getSellerId()) : userMapper.selectById(item.getSellerId());
        if (seller != null) {
            sellerInfo.put("id", seller.getId());
            sellerInfo.put("nickname", seller.getNickname());
            sellerInfo.put("avatar", seller.getAvatar());
        }
        vo.put("seller", sellerInfo);
        boolean sellerAvailable = UserAccountStatusUtil.canExposePublicContent(seller);
        boolean owner = currentUserId != null && currentUserId.equals(item.getSellerId());
        vo.put("sellerAvailable", sellerAvailable);
        vo.put("canOpen", owner || (sellerAvailable && (!"removed".equals(item.getStatus()) || favorited)));
        vo.put("canFavorite", !owner && sellerAvailable
                && ("available".equals(item.getStatus()) || "reserved".equals(item.getStatus())));
        vo.put("availabilityMessage", availabilityMessage(item, sellerAvailable));

        return vo;
    }

    private Map<String, Object> deletedFavoriteVO(MarketplaceFavorite favorite) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", favorite.getItemId());
        vo.put("title", "这件收藏已被摊主删除");
        vo.put("category", "other");
        vo.put("condition", "good");
        vo.put("price", BigDecimal.ZERO);
        vo.put("images", Collections.emptyList());
        vo.put("status", "deleted");
        vo.put("viewCount", 0);
        vo.put("favoriteCount", 0);
        vo.put("isFavorited", true);
        vo.put("seller", Collections.emptyMap());
        vo.put("sellerAvailable", false);
        vo.put("canOpen", false);
        vo.put("canFavorite", false);
        vo.put("availabilityMessage", "商品记录已删除，你仍可从收藏夹移除这张留档");
        vo.put("createTime", favorite.getCreateTime());
        return vo;
    }

    private String availabilityMessage(MarketplaceItem item, boolean sellerAvailable) {
        if (!sellerAvailable) return "摊主账号暂不可交易，商品已停止公开曝光";
        if ("reserved".equals(item.getStatus())) return "已有人先一步预留，仍可收藏并等待变化";
        if ("sold".equals(item.getStatus())) return "已经成交，收藏记录会保留供你回看";
        if ("removed".equals(item.getStatus())) return "摊主已下架，收藏记录会保留供你整理";
        return "正在集市货架上";
    }

    private MarketplaceVoContext buildMarketplaceVoContext(List<MarketplaceItem> items, Long currentUserId) {
        MarketplaceVoContext context = new MarketplaceVoContext();
        if (items == null || items.isEmpty()) {
            return context;
        }

        Set<Long> itemIds = items.stream()
                .map(MarketplaceItem::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!ObjectUtils.isEmpty(currentUserId) && !itemIds.isEmpty()) {
            LambdaQueryWrapper<MarketplaceFavorite> favoriteWrapper = new LambdaQueryWrapper<>();
            favoriteWrapper.eq(MarketplaceFavorite::getUserId, currentUserId)
                    .eq(MarketplaceFavorite::getDeleted, 0)
                    .in(MarketplaceFavorite::getItemId, itemIds);
            context.favoriteItemIds = marketplaceFavoriteMapper.selectList(favoriteWrapper).stream()
                    .map(MarketplaceFavorite::getItemId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        Set<Long> sellerIds = items.stream()
                .map(MarketplaceItem::getSellerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!sellerIds.isEmpty()) {
            context.sellers = userMapper.selectBatchIds(sellerIds).stream()
                    .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        }

        Set<Long> songIds = items.stream()
                .filter(item -> "song".equals(item.getResourceType()))
                .map(MarketplaceItem::getResourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!songIds.isEmpty()) {
            context.songs = songMapper.selectBatchIds(songIds).stream()
                    .collect(Collectors.toMap(Song::getId, song -> song, (left, right) -> left));
        }

        Set<Long> albumIds = items.stream()
                .filter(item -> "album".equals(item.getResourceType()))
                .map(MarketplaceItem::getResourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!albumIds.isEmpty()) {
            context.albums = albumMapper.selectBatchIds(albumIds).stream()
                    .collect(Collectors.toMap(Album::getId, album -> album, (left, right) -> left));
        }

        return context;
    }

    private static class MarketplaceVoContext {
        private Set<Long> favoriteItemIds = Collections.emptySet();
        private Map<Long, User> sellers = Collections.emptyMap();
        private Map<Long, Song> songs = Collections.emptyMap();
        private Map<Long, Album> albums = Collections.emptyMap();
    }

    private int normalizePage(Integer page) {
        if (ObjectUtils.isEmpty(page) || page <= 0) {
            return 1;
        }
        if (page > MAX_PAGE_NUMBER) {
            throw new IllegalArgumentException("页码不能超过 " + MAX_PAGE_NUMBER);
        }
        return page;
    }

    private static Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    private static Map<String, Set<String>> createStatusTransitions() {
        Map<String, Set<String>> transitions = new HashMap<>();
        transitions.put("available", setOf("reserved", "sold", "removed"));
        transitions.put("reserved", setOf("available", "sold", "removed"));
        transitions.put("sold", Collections.emptySet());
        transitions.put("removed", setOf("available"));
        return Collections.unmodifiableMap(transitions);
    }

    private int normalizePageSize(Integer size, int defaultSize) {
        if (ObjectUtils.isEmpty(size) || size <= 0) {
            return defaultSize;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        String value = keyword.trim().replaceAll("\\s+", " ");
        if (value.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("搜索关键词不能超过 " + MAX_KEYWORD_LENGTH + " 个字符");
        }
        if (value.indexOf('%') >= 0 || value.indexOf('_') >= 0) {
            throw new IllegalArgumentException("搜索关键词不能包含 SQL 通配符");
        }
        return value;
    }

    private ValidatedItemInput validateItemInput(Long sellerId, String title, String category,
                                                 String condition, BigDecimal price,
                                                 BigDecimal originalPrice, String description,
                                                 String images, String resourceType, Long resourceId,
                                                 String location, String deliveryMethod) {
        ValidatedItemInput input = new ValidatedItemInput();
        input.title = normalizeRequired(title, "商品标题", 100);
        if (input.title.length() < 5) {
            throw new IllegalArgumentException("商品标题不能少于 5 个字符");
        }
        input.category = normalizeEnum(category, "商品分类", ITEM_CATEGORIES, "other");
        input.condition = normalizeEnum(condition, "商品成色", ITEM_CONDITIONS, "good");
        input.deliveryMethod = normalizeEnum(deliveryMethod, "交易方式", DELIVERY_METHODS, "both");
        input.price = validatePrice(price, "价格", false);
        input.originalPrice = validatePrice(originalPrice, "原价", true);
        if (input.originalPrice != null && input.originalPrice.compareTo(input.price) < 0) {
            throw new IllegalArgumentException("原价不能低于当前价格");
        }
        input.description = normalizeOptional(description, "商品描述", 500);
        input.location = normalizeRequired(location, "所在地", 100);
        input.images = validateImages(sellerId, images);
        populateResourceFacts(input, resourceType, resourceId);
        return input;
    }

    private void applyValidatedInput(MarketplaceItem item, ValidatedItemInput input) {
        item.setTitle(input.title);
        item.setCategory(input.category);
        item.setConditionInfo(input.condition);
        item.setPrice(input.price);
        item.setOriginalPrice(input.originalPrice);
        item.setDescription(input.description);
        item.setImages(input.images);
        item.setResourceType(input.resourceType);
        item.setResourceId(input.resourceId);
        item.setResourceName(input.resourceName);
        item.setResourceCover(input.resourceCover);
        item.setLocation(input.location);
        item.setDeliveryMethod(input.deliveryMethod);
    }

    private void populateResourceFacts(ValidatedItemInput input, String resourceType, Long resourceId) {
        String type = resourceType == null ? null : resourceType.trim().toLowerCase(Locale.ROOT);
        if ((type == null || type.isEmpty()) && resourceId == null) {
            return;
        }
        if (type == null || !RESOURCE_TYPES.contains(type) || resourceId == null || resourceId <= 0) {
            throw new IllegalArgumentException("关联资源类型和 ID 必须同时有效");
        }
        input.resourceType = type;
        input.resourceId = resourceId;
        if ("song".equals(type)) {
            Song song = songMapper.selectById(resourceId);
            if (song == null || Integer.valueOf(1).equals(song.getDeleted())
                    || !Integer.valueOf(1).equals(song.getStatus())
                    || !"published".equalsIgnoreCase(song.getPublishStatus())
                    || !"approved".equalsIgnoreCase(song.getReviewStatus())) {
                throw new IllegalArgumentException("关联歌曲不存在或当前不可展示");
            }
            input.resourceName = song.getName();
            input.resourceCover = song.getCover();
            return;
        }
        Album album = albumMapper.selectById(resourceId);
        if (album == null || Integer.valueOf(1).equals(album.getDeleted())
                || !Integer.valueOf(1).equals(album.getStatus())) {
            throw new IllegalArgumentException("关联专辑不存在或当前不可展示");
        }
        input.resourceName = album.getName();
        input.resourceCover = album.getCover();
    }

    private String validateImages(Long sellerId, String images) {
        if (images == null || images.trim().isEmpty()) {
            return null;
        }
        List<String> values = Arrays.stream(images.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (values.size() > 5) {
            throw new IllegalArgumentException("商品图片不能超过 5 张");
        }
        String ownerPrefix = sellerId + "_";
        for (String value : values) {
            if (value.length() > 512) {
                throw new IllegalArgumentException("商品图片地址过长");
            }
            try {
                String path = URI.create(value).getPath();
                String fileName = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
                if (path == null || !path.contains("/posts/images/") || !fileName.startsWith(ownerPrefix)) {
                    throw new IllegalArgumentException("商品图片必须来自当前账号的受控上传");
                }
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("商品图片必须来自当前账号的受控上传");
            }
        }
        return String.join(",", values);
    }

    private BigDecimal validatePrice(BigDecimal value, String field, boolean optional) {
        if (value == null) {
            if (optional) {
                return null;
            }
            throw new IllegalArgumentException(field + "不能为空");
        }
        if (value.scale() > 2 || value.compareTo(BigDecimal.ZERO) <= (optional ? -1 : 0)
                || value.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException(field + "必须在有效范围内且最多保留两位小数");
        }
        return value;
    }

    private String normalizeEnum(String value, String field, Set<String> allowed, String defaultValue) {
        String normalized = value == null || value.trim().isEmpty()
                ? defaultValue
                : value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(field + "不合法");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String field, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String normalizeOptional(String value, String field, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private static class ValidatedItemInput {
        private String title;
        private String category;
        private String condition;
        private BigDecimal price;
        private BigDecimal originalPrice;
        private String description;
        private String images;
        private String resourceType;
        private Long resourceId;
        private String resourceName;
        private String resourceCover;
        private String location;
        private String deliveryMethod;
    }

       
                
       
    private Map<String, Object> convertToDetailVO(MarketplaceItem item, Long currentUserId) {
        Map<String, Object> vo = convertToVO(item, currentUserId);

                 
        vo.put("description", item.getDescription());

                   
        vo.put("seller", getSellerInfo(item.getSellerId()));

                 
        vo.put("soldTime", item.getSoldTime());

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateItem(Long itemId, Long sellerId, String title, String category, String condition,
                              BigDecimal price, BigDecimal originalPrice, String description,
                              String images, String resourceType, Long resourceId,
                              String resourceName, String resourceCover,
                              String location, String deliveryMethod) {
        if (ObjectUtils.isEmpty(itemId) || ObjectUtils.isEmpty(sellerId)) {
            log.warn("编辑商品失败: 参数为空");
            return false;
        }

        MarketplaceItem item = marketplaceItemMapper.selectById(itemId);
        if (ObjectUtils.isEmpty(item) || Boolean.TRUE.equals(item.getIsDeleted())) {
            log.warn("商品不存在或已删除: itemId={}", itemId);
            return false;
        }

               
        if (!item.getSellerId().equals(sellerId)) {
            log.warn("用户无权编辑该商品: itemId={}, sellerId={}, itemSellerId={}",
                    itemId, sellerId, item.getSellerId());
            return false;
        }
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(sellerId), "编辑商品");
        if (!"available".equals(item.getStatus()) && !"reserved".equals(item.getStatus())) {
            throw new IllegalArgumentException("只有可售或已预留商品允许编辑");
        }
        ValidatedItemInput input = validateItemInput(sellerId, title, category, condition, price,
                originalPrice, description, images, resourceType, resourceId, location, deliveryMethod);

        applyValidatedInput(item, input);

        int result = marketplaceItemMapper.updateById(item);

        log.info("编辑商品成功: itemId={}, sellerId={}", itemId, sellerId);

        return result > 0;
    }

    @Override
    public Map<String, Object> getOperationStats(Long userId, Integer hours) {
        if (ObjectUtils.isEmpty(userId)) {
            return new HashMap<>();
        }

        if (ObjectUtils.isEmpty(hours) || hours <= 0) {
            hours = 24;
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("userId", userId);
        stats.put("timeRangeHours", hours);

                     
        List<Map<String, Object>> operations = new ArrayList<>();

               
        operations.add(getOperationCount(userId, "createPost", 5));
                 
        operations.add(getOperationCount(userId, "createDiary", 5));
               
        operations.add(getOperationCount(userId, "editPost", 5));
               
        operations.add(getOperationCount(userId, "createMarketplaceItem", 3));
               
        operations.add(getOperationCount(userId, "editMarketplaceItem", 5));
               
        operations.add(getOperationCount(userId, "favoriteItem", 30));
               
        operations.add(getOperationCount(userId, "uploadPostImage", 20));
               
        operations.add(getOperationCount(userId, "uploadVideoPost", 3));

        stats.put("operations", operations);

        return stats;
    }

       
                
      
                            
                            
                            
                   
       
    private Map<String, Object> getOperationCount(Long userId, String operation, int limit) {
        Map<String, Object> opStat = new HashMap<>();
        opStat.put("operation", operation);
        opStat.put("limit", limit);

        try {
            String key = OPERATION_RECORD_KEY_PREFIX + userId + ":" + operation;
            String countStr = String.valueOf(redisUtils.get(key));
            int count = 0;
            if (countStr != null) {
                try {
                    count = Integer.parseInt(countStr);
                } catch (NumberFormatException e) {
                    log.debug("解析操作计数失败: key={}, value={}", key, countStr);
                }
            }
            opStat.put("count", count);
            opStat.put("remaining", Math.max(0, limit - count));
        } catch (Exception e) {
            log.error("获取操作计数异常: operation={}", operation);
            opStat.put("count", 0);
            opStat.put("remaining", limit);
        }

        return opStat;
    }
}

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.constant.EmojiPackageCapacity;
import com.haoran.music.common.dto.PageResult;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.Emoji;
import com.haoran.music.entity.EmojiPackage;
import com.haoran.music.entity.EmojiItem;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserEmoji;
import com.haoran.music.mapper.EmojiMapper;
import com.haoran.music.mapper.EmojiPackageMapper;
import com.haoran.music.mapper.EmojiItemMapper;
import com.haoran.music.mapper.UserEmojiMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.EmojiShopService;
import com.haoran.music.service.StoreProductPolicyService;
import com.haoran.music.service.UserActivityPointsService;
import com.haoran.music.vo.emoji.EmojiItemVO;
import com.haoran.music.vo.emoji.EmojiPreviewItemVO;
import com.haoran.music.vo.emoji.EmojiPackageDetailVO;
import com.haoran.music.vo.emoji.EmojiPackageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

   
                      
                             
   
@Slf4j
@Service
public class EmojiShopServiceImpl implements EmojiShopService {

    @Resource
    private EmojiPackageMapper emojiPackageMapper;

    @Resource
    private UserEmojiMapper userEmojiMapper;

    @Resource
    private EmojiItemMapper emojiItemMapper;

    @Resource
    private EmojiMapper emojiMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserActivityPointsService activityPointsService;

    @Resource
    private StoreProductPolicyService storeProductPolicyService;

    @Override
    public EmojiShopService.EmojiShopHomeVO getHomeData() {
        EmojiShopService.EmojiShopHomeVO result = new EmojiShopService.EmojiShopHomeVO();

                  
        List<EmojiPackageVO> recommended = getRecommendedPackages();
        result.setRecommended(recommended);

                  
        List<EmojiPackageVO> hot = getHotPackages(10);
        result.setHot(hot);

                  
        List<EmojiPackageVO> latest = getLatestPackages(10);
        result.setLatest(latest);

                  
        List<EmojiPackageVO> free = getFreePackages(10);
        result.setFree(free);

                 
        List<EmojiPackageVO> myEmojis = getMyEmojis();
        result.setMyEmojis(myEmojis);

        return result;
    }

    @Override
    public PageResult<EmojiPackageVO> getPackages(String type, String category, Integer page, Integer size) {
        LambdaQueryWrapper<EmojiPackage> wrapper = new LambdaQueryWrapper<>();

        if (!ObjectUtils.isEmpty(type)) {
            wrapper.eq(EmojiPackage::getType, type);
        }

        if (!ObjectUtils.isEmpty(category)) {
            wrapper.eq(EmojiPackage::getCategory, category);
        }

        wrapper.eq(EmojiPackage::getStatus, 1)
                .orderByDesc(EmojiPackage::getDownloadCount)
                .orderByAsc(EmojiPackage::getSortOrder)
                .orderByAsc(EmojiPackage::getId);

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        List<EmojiPackage> visiblePackages = emojiPackageMapper.selectList(wrapper).stream()
                .filter(pkg -> storeProductPolicyService.isCatalogVisible("emoji_package", pkg.getId()))
                .collect(java.util.stream.Collectors.toList());
        int from = Math.min((safePage - 1) * safeSize, visiblePackages.size());
        int to = Math.min(from + safeSize, visiblePackages.size());

        PageResult<EmojiPackageVO> result = new PageResult<>();
        result.setRecords(convertToVOList(visiblePackages.subList(from, to), true));
        result.setTotal((long) visiblePackages.size());
        result.setCurrent((long) safePage);
        result.setSize((long) safeSize);
        result.setPages((visiblePackages.size() + safeSize - 1L) / safeSize);

        return result;
    }

       
       
    @Override
    public EmojiPackageDetailVO getPackageDetail(Long id) {
        EmojiPackage emojiPackage = emojiPackageMapper.selectById(id);
        if (ObjectUtils.isEmpty(emojiPackage)) {
            return new EmojiPackageDetailVO();
        }

        Long userId = getCurrentUserId();
        UserEmoji userEmoji = null;
        if (userId != null) {
            userEmoji = userEmojiMapper.selectOne(new LambdaQueryWrapper<UserEmoji>()
                    .eq(UserEmoji::getUserId, userId)
                    .eq(UserEmoji::getEmojiPackageId, id));
        }
        boolean purchased = userEmoji != null && Integer.valueOf(1).equals(userEmoji.getIsPurchased());
        boolean owner = userId != null && userId.equals(emojiPackage.getCreatorId());
        boolean retained = purchased && storeProductPolicyService.canUseOwnedEntitlement("emoji_package", id);
        if (!owner && !retained && !storeProductPolicyService.isDirectlyVisible("emoji_package", id)) {
            return new EmojiPackageDetailVO();
        }

        EmojiPackageDetailVO result = new EmojiPackageDetailVO();
        BeanUtils.copyProperties(emojiPackage, result);
        EmojiPackagePreviewData previewData = findPackagePreviewData(Collections.singletonList(emojiPackage)).get(id);
        applyQuantity(result, emojiPackage, previewData);
        if (previewData != null) {
            result.setPreviewItems(previewData.getPreviewItems());
            if (!ObjectUtils.isEmpty(previewData.getCoverUrl())) {
                result.setCoverEmojiId(previewData.getCoverEmojiId());
                result.setCoverUrl(previewData.getCoverUrl());
            }
        }

                      
        if (userId != null) {
            result.setIsPurchased(purchased);
            result.setIsFavorited(userEmoji != null && userEmoji.getIsFavorited() != null && userEmoji.getIsFavorited() == 1);
        } else {
            result.setIsPurchased(false);
            result.setIsFavorited(false);
        }

                  
        LambdaQueryWrapper<EmojiItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(EmojiItem::getEmojiPackageId, id)
                .orderByAsc(EmojiItem::getSortOrder);
        boolean canLoadAllItems = owner || retained || Integer.valueOf(1).equals(emojiPackage.getIsFree());
        List<EmojiItem> items = canLoadAllItems
                ? emojiItemMapper.selectList(itemWrapper) : Collections.emptyList();

                
        List<EmojiItemVO> itemVOs = new ArrayList<>();
        for (EmojiItem item : items) {
            EmojiItemVO itemVO = new EmojiItemVO();
            BeanUtils.copyProperties(item, itemVO);
            itemVOs.add(itemVO);
        }
        result.setItems(itemVOs);

        return result;
    }

       
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void purchasePackage(Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        UserAccountStatusUtil.requireCanInteract(userMapper.selectByIdForUpdate(userId), "购买表情包");

                                           
        EmojiPackage emojiPackage = emojiPackageMapper.selectById(id);
        if (ObjectUtils.isEmpty(emojiPackage)) {
            throw new RuntimeException("表情包不存在");
        }
        if (!Integer.valueOf(1).equals(emojiPackage.getStatus())) {
            throw new RuntimeException("表情包暂未上架");
        }
        storeProductPolicyService.requirePurchasable("emoji_package", id);
        boolean freePackage = Integer.valueOf(1).equals(emojiPackage.getIsFree());
        if ("cash".equals(emojiPackage.getPurchaseMode())) {
            throw new RuntimeException("请使用支付订单购买该表情包");
        }
        Integer configuredPrice = emojiPackage.getPrice();
        if (!freePackage && (configuredPrice == null || configuredPrice < 0)) {
            throw new IllegalStateException("表情包价格配置无效");
        }
        int purchaseCost = freePackage ? 0 : configuredPrice;

                  
        LambdaQueryWrapper<UserEmoji> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(UserEmoji::getUserId, userId)
                .eq(UserEmoji::getEmojiPackageId, id);
        UserEmoji existUserEmoji = userEmojiMapper.selectOne(existWrapper);
        if (existUserEmoji != null && existUserEmoji.getIsPurchased() != null && existUserEmoji.getIsPurchased() == 1) {
            return;
        }

                        
        UserEmoji userEmoji;
        if (existUserEmoji != null) {
            userEmoji = existUserEmoji;
        } else {
            userEmoji = new UserEmoji();
            userEmoji.setUserId(userId);
            userEmoji.setEmojiPackageId(id);
        }

        userEmoji.setIsPurchased(1);
        userEmoji.setPurchaseTime(LocalDateTime.now());

                                        
        if (purchaseCost > 0) {
            activityPointsService.consumePoints(
                    userId, purchaseCost, "emoji", "购买表情包：" + emojiPackage.getName());
        }

        if (existUserEmoji == null) {
            userEmoji.setIsFavorited(0);
            userEmojiMapper.insert(userEmoji);
        } else {
            userEmojiMapper.updateById(userEmoji);
        }

                                     
        if (emojiPackageMapper.incrementDownloadCount(id) != 1) {
            throw new RuntimeException("表情包当前不可购买");
        }

        log.info("用户购买表情包成功: userId={}, packageId={}, cost={}", userId, id, purchaseCost);
    }

       
       
    @Override
    public List<EmojiPackageVO> getMyEmojis() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return new ArrayList<>();
        }

                         
        LambdaQueryWrapper<UserEmoji> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEmoji::getUserId, userId)
                .eq(UserEmoji::getIsPurchased, 1);
        List<UserEmoji> userEmojis = userEmojiMapper.selectList(wrapper);

        if (userEmojis.isEmpty()) {
            return new ArrayList<>();
        }

                                       
        List<Long> packageIds = new ArrayList<>();
        for (UserEmoji userEmoji : userEmojis) {
            if (userEmoji.getEmojiPackageId() != null) {
                packageIds.add(userEmoji.getEmojiPackageId());
            }
        }
        if (packageIds.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, EmojiPackage> packagesById = new HashMap<>();
        for (EmojiPackage pkg : emojiPackageMapper.selectBatchIds(packageIds)) {
            packagesById.put(pkg.getId(), pkg);
        }

                                     
        List<EmojiPackage> orderedPackages = new ArrayList<>();
        for (UserEmoji userEmoji : userEmojis) {
            EmojiPackage pkg = packagesById.get(userEmoji.getEmojiPackageId());
            if (pkg != null) {
                orderedPackages.add(pkg);
            }
        }
        return convertToVOList(orderedPackages, false);
    }

       
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void favoritePackage(Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        UserAccountStatusUtil.requireCanInteract(userMapper.selectByIdForUpdate(userId), "收藏表情包");

                    
        EmojiPackage pkg = emojiPackageMapper.selectById(id);
        if (pkg == null) {
            throw new RuntimeException("表情包不存在");
        }
        if (!storeProductPolicyService.isDirectlyVisible("emoji_package", id)) {
            throw new RuntimeException("表情包当前不可收藏");
        }

                 
        LambdaQueryWrapper<UserEmoji> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEmoji::getUserId, userId)
                .eq(UserEmoji::getEmojiPackageId, id);
        UserEmoji userEmoji = userEmojiMapper.selectOne(wrapper);

        if (userEmoji != null) {
                     
            if (userEmoji.getIsFavorited() == null || userEmoji.getIsFavorited() != 1) {
                userEmoji.setIsFavorited(1);
                userEmoji.setFavoriteTime(LocalDateTime.now());
                userEmojiMapper.updateById(userEmoji);
            }
        } else {
                    
            userEmoji = new UserEmoji();
            userEmoji.setUserId(userId);
            userEmoji.setEmojiPackageId(id);
            userEmoji.setIsPurchased(0);
            userEmoji.setIsFavorited(1);
            userEmoji.setFavoriteTime(LocalDateTime.now());
            userEmojiMapper.insert(userEmoji);
        }

        log.info("用户{}收藏表情包{}成功", userId, id);
    }

       
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfavoritePackage(Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return;
        }

        LambdaQueryWrapper<UserEmoji> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEmoji::getUserId, userId)
                .eq(UserEmoji::getEmojiPackageId, id);
        UserEmoji userEmoji = userEmojiMapper.selectOne(wrapper);

        if (userEmoji != null && userEmoji.getIsFavorited() != null && userEmoji.getIsFavorited() == 1) {
            userEmoji.setIsFavorited(0);
            userEmoji.setFavoriteTime(null);
            userEmojiMapper.updateById(userEmoji);
            log.info("用户{}取消收藏表情包{}", userId, id);
        }
    }

       
              
       
    private List<EmojiPackageVO> getRecommendedPackages() {
                                           
        LambdaQueryWrapper<EmojiPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmojiPackage::getStatus, 1)
                .eq(EmojiPackage::getType, "system")
                .orderByDesc(EmojiPackage::getDownloadCount)
                .last("LIMIT 10");

        List<EmojiPackage> packages = emojiPackageMapper.selectList(wrapper);
        return convertToVOList(packages);
    }

       
              
       
    private List<EmojiPackageVO> getHotPackages(Integer limit) {
        LambdaQueryWrapper<EmojiPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmojiPackage::getStatus, 1)
                .orderByDesc(EmojiPackage::getDownloadCount)
                .last("LIMIT " + limit);

        List<EmojiPackage> packages = emojiPackageMapper.selectList(wrapper);
        return convertToVOList(packages);
    }

       
              
       
    private List<EmojiPackageVO> getLatestPackages(Integer limit) {
        LambdaQueryWrapper<EmojiPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmojiPackage::getStatus, 1)
                .orderByDesc(EmojiPackage::getCreateTime)
                .last("LIMIT " + limit);

        List<EmojiPackage> packages = emojiPackageMapper.selectList(wrapper);
        return convertToVOList(packages);
    }

       
              
       
    private List<EmojiPackageVO> getFreePackages(Integer limit) {
        LambdaQueryWrapper<EmojiPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmojiPackage::getStatus, 1)
                .eq(EmojiPackage::getIsFree, 1)
                .orderByDesc(EmojiPackage::getDownloadCount)
                .last("LIMIT " + limit);

        List<EmojiPackage> packages = emojiPackageMapper.selectList(wrapper);
        return convertToVOList(packages);
    }

       
              
       
    private List<EmojiPackageVO> convertToVOList(List<EmojiPackage> packages) {
        return convertToVOList(packages, true);
    }

    private List<EmojiPackageVO> convertToVOList(List<EmojiPackage> packages, boolean catalogOnly) {
        List<EmojiPackageVO> result = new ArrayList<>();
        if (packages == null || packages.isEmpty()) {
            return result;
        }

        Map<Long, EmojiPackagePreviewData> previewDataByPackage = findPackagePreviewData(packages);
        Set<Long> purchasedPackageIds = findPurchasedPackageIds();

        for (EmojiPackage pkg : packages) {
            if (catalogOnly && !storeProductPolicyService.isCatalogVisible("emoji_package", pkg.getId())) {
                continue;
            }
            if (!catalogOnly && purchasedPackageIds.contains(pkg.getId())
                    && !storeProductPolicyService.canUseOwnedEntitlement("emoji_package", pkg.getId())) {
                continue;
            }
            EmojiPackageVO vo = convertToVO(pkg);
            EmojiPackagePreviewData previewData = previewDataByPackage.get(pkg.getId());
            List<EmojiPreviewItemVO> previewItems = previewData == null
                    ? Collections.emptyList()
                    : previewData.getPreviewItems();
            applyQuantity(vo, pkg, previewData);
            if (previewData != null && !ObjectUtils.isEmpty(previewData.getCoverUrl())) {
                vo.setCoverEmojiId(previewData.getCoverEmojiId());
                vo.setCoverUrl(previewData.getCoverUrl());
            }
            vo.setPreviewItems(previewItems);
            vo.setIsPurchased(purchasedPackageIds.contains(pkg.getId()));
            result.add(vo);
        }
        return result;
    }

       
                                                 
       
    private Map<Long, EmojiPackagePreviewData> findPackagePreviewData(List<EmojiPackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> packageIds = packages.stream()
                .filter(Objects::nonNull)
                .map(EmojiPackage::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
        if (packageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, EmojiPackage> packagesById = packages.stream()
                .filter(Objects::nonNull)
                .filter(pkg -> pkg.getId() != null)
                .collect(java.util.stream.Collectors.toMap(EmojiPackage::getId, pkg -> pkg, (left, right) -> left));

        List<Emoji> emojis = emojiMapper.selectList(new LambdaQueryWrapper<Emoji>()
                .in(Emoji::getPackageId, packageIds)
                .eq(Emoji::getEnabled, 1)
                .orderByAsc(Emoji::getPackageId)
                .orderByAsc(Emoji::getSortOrder)
                .orderByAsc(Emoji::getId));
        Map<Long, List<Emoji>> emojisByPackage = new LinkedHashMap<>();
        for (Emoji emoji : emojis) {
            if (emoji.getPackageId() != null) {
                emojisByPackage.computeIfAbsent(emoji.getPackageId(), ignored -> new ArrayList<>()).add(emoji);
            }
        }

        Map<Long, EmojiPackagePreviewData> result = new HashMap<>();
        for (Map.Entry<Long, List<Emoji>> entry : emojisByPackage.entrySet()) {
            List<Emoji> packageEmojis = entry.getValue();
            List<Emoji> previewCandidates = packageEmojis.stream()
                    .filter(emoji -> !ObjectUtils.isEmpty(emoji.getImageUrl()))
                    .collect(java.util.stream.Collectors.toList());
            EmojiPackage emojiPackage = packagesById.get(entry.getKey());
            Emoji coverEmoji = previewCandidates.stream()
                    .filter(emoji -> emojiPackage != null
                            && Objects.equals(emoji.getId(), emojiPackage.getCoverEmojiId()))
                    .findFirst()
                    .orElse(previewCandidates.isEmpty() ? null : previewCandidates.get(0));
            int previewCount = previewCandidates.isEmpty()
                    ? 0 : Math.max(1, (packageEmojis.size() + 1) / 2);
            previewCount = Math.min(previewCount, previewCandidates.size());
            List<EmojiPreviewItemVO> previewItems = new ArrayList<>();
            for (Emoji emoji : previewCandidates.subList(0, previewCount)) {
                EmojiPreviewItemVO item = new EmojiPreviewItemVO();
                item.setId(emoji.getId());
                item.setEmojiId(emoji.getCode());
                item.setName(emoji.getName());
                item.setImageUrl(emoji.getImageUrl());
                previewItems.add(item);
            }
            result.put(entry.getKey(), new EmojiPackagePreviewData(
                    packageEmojis.size(),
                    coverEmoji == null ? null : coverEmoji.getId(),
                    coverEmoji == null ? null : coverEmoji.getImageUrl(),
                    previewItems));
        }
        return result;
    }

    private static final class EmojiPackagePreviewData {
        private final int itemCount;
        private final Long coverEmojiId;
        private final String coverUrl;
        private final List<EmojiPreviewItemVO> previewItems;

        private EmojiPackagePreviewData(int itemCount,
                                        Long coverEmojiId,
                                        String coverUrl,
                                        List<EmojiPreviewItemVO> previewItems) {
            this.itemCount = itemCount;
            this.coverEmojiId = coverEmojiId;
            this.coverUrl = coverUrl;
            this.previewItems = previewItems;
        }

        private int getItemCount() {
            return itemCount;
        }

        private Long getCoverEmojiId() {
            return coverEmojiId;
        }

        private String getCoverUrl() {
            return coverUrl;
        }

        private List<EmojiPreviewItemVO> getPreviewItems() {
            return previewItems;
        }
    }

    private Set<Long> findPurchasedPackageIds() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Collections.emptySet();
        }

        List<UserEmoji> userEmojis = userEmojiMapper.selectList(new LambdaQueryWrapper<UserEmoji>()
                .eq(UserEmoji::getUserId, userId)
                .eq(UserEmoji::getIsPurchased, 1));
        Set<Long> packageIds = new HashSet<>();
        for (UserEmoji userEmoji : userEmojis) {
            if (userEmoji.getEmojiPackageId() != null) {
                packageIds.add(userEmoji.getEmojiPackageId());
            }
        }
        return packageIds;
    }

       
            
       
    private EmojiPackageVO convertToVO(EmojiPackage pkg) {
        EmojiPackageVO vo = new EmojiPackageVO();
        BeanUtils.copyProperties(pkg, vo);
        vo.setIsFree(pkg.getIsFree() != null && pkg.getIsFree() == 1);
        if (ObjectUtils.isEmpty(vo.getPurchaseMode())) {
            vo.setPurchaseMode(Boolean.TRUE.equals(vo.getIsFree()) ? "free" : "points");
        }
        vo.setTypeName(getTypeName(pkg.getType()));
        vo.setCategoryName(getCategoryName(pkg.getCategory()));
        vo.setItemLimit(effectiveItemLimit(pkg));
        return vo;
    }

    private int effectiveItemLimit(EmojiPackage pkg) {
        return EmojiPackageCapacity.effective(pkg.getItemLimit());
    }

    private void applyQuantity(EmojiPackageVO vo,
                               EmojiPackage pkg,
                               EmojiPackagePreviewData previewData) {
        int itemCount = previewData == null ? 0 : previewData.getItemCount();
        int itemLimit = effectiveItemLimit(pkg);
        vo.setItemLimit(itemLimit);
        vo.setItemCount(itemCount);
        vo.setRemainingCount(Math.max(0, itemLimit - itemCount));
    }

       
       
       
             
       
    private String getTypeName(String type) {
        if (ObjectUtils.isEmpty(type)) return "";
        Map<String, String> typeMap = new HashMap<>();
        typeMap.put("official", "官方");
        typeMap.put("custom", "用户投稿");
        return typeMap.getOrDefault(type, type);
    }

       
             
       
    private String getCategoryName(String category) {
        if (ObjectUtils.isEmpty(category)) return "";
        Map<String, String> categoryMap = new HashMap<>();
        categoryMap.put("emotion", "表情");
        categoryMap.put("animal", "动物");
        categoryMap.put("food", "美食");
        categoryMap.put("character", "人物");
        return categoryMap.getOrDefault(category, category);
    }

       
       
    private Long getCurrentUserId() {
        return UserContext.getCurrentUserId();
    }
}

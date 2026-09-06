   
                      
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.dto.store.StoreProductRow;
import com.haoran.music.entity.DecorationConfig;
import com.haoran.music.entity.Emoji;
import com.haoran.music.entity.EmojiPackage;
import com.haoran.music.entity.StoreProductPolicy;
import com.haoran.music.entity.StoreProductPolicyLog;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.DecorationConfigMapper;
import com.haoran.music.mapper.EmojiMapper;
import com.haoran.music.mapper.EmojiPackageMapper;
import com.haoran.music.mapper.PaymentOrderMapper;
import com.haoran.music.mapper.StoreProductPolicyLogMapper;
import com.haoran.music.mapper.StoreProductPolicyMapper;
import com.haoran.music.mapper.StoreProductQueryMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.StoreProductPolicyService;
import com.haoran.music.vo.StoreProductVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StoreProductPolicyServiceImpl implements StoreProductPolicyService {

    public static final String EMOJI_PACKAGE = "emoji_package";
    public static final String DECORATION = "decoration";
    public static final String ON_SALE = "on_sale";
    public static final String PUBLIC = "public";
    public static final String UNLISTED = "unlisted";
    public static final String HIDDEN = "hidden";
    public static final String RETAIN = "retain";
    public static final String NORMAL = "normal";

    private final StoreProductPolicyMapper policyMapper;
    private final StoreProductPolicyLogMapper logMapper;
    private final EmojiPackageMapper emojiPackageMapper;
    private final EmojiMapper emojiMapper;
    private final DecorationConfigMapper decorationConfigMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final UserMapper userMapper;
    private final StoreProductQueryMapper productQueryMapper;

    public StoreProductPolicyServiceImpl(StoreProductPolicyMapper policyMapper,
                                         StoreProductPolicyLogMapper logMapper,
                                         EmojiPackageMapper emojiPackageMapper,
                                         EmojiMapper emojiMapper,
                                         DecorationConfigMapper decorationConfigMapper,
                                         PaymentOrderMapper paymentOrderMapper,
                                         UserMapper userMapper,
                                         StoreProductQueryMapper productQueryMapper) {
        this.policyMapper = policyMapper;
        this.logMapper = logMapper;
        this.emojiPackageMapper = emojiPackageMapper;
        this.emojiMapper = emojiMapper;
        this.decorationConfigMapper = decorationConfigMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.userMapper = userMapper;
        this.productQueryMapper = productQueryMapper;
    }

    @Override
    public StoreProductPolicy getPolicy(String productType, Long productId) {
        String type = normalizeType(productType);
        requireProductExists(type, productId);
        StoreProductPolicy stored = policyMapper.selectOne(new LambdaQueryWrapper<StoreProductPolicy>()
                .eq(StoreProductPolicy::getProductType, type)
                .eq(StoreProductPolicy::getProductId, productId));
        return stored == null ? defaultPolicy(type, productId) : stored;
    }

    @Override
    public boolean isCatalogVisible(String productType, Long productId) {
        StoreProductPolicy policy = getPolicy(productType, productId);
        if (!ON_SALE.equals(policy.getSaleStatus()) || !PUBLIC.equals(policy.getVisibility())) {
            return false;
        }
        if (EMOJI_PACKAGE.equals(policy.getProductType())) {
            EmojiPackage emojiPackage = emojiPackageMapper.selectById(productId);
            return approvedForCommerce(emojiPackage)
                    && Integer.valueOf(1).equals(emojiPackage.getStatus())
                    && sellerCanTrade(emojiPackage);
        }
        DecorationConfig config = decorationConfigMapper.selectById(productId);
        return approvedForCommerce(config)
                && Integer.valueOf(1).equals(config.getIsEnabled())
                && decorationSellerCanTrade(config);
    }

    @Override
    public boolean isDirectlyVisible(String productType, Long productId) {
        StoreProductPolicy policy = getPolicy(productType, productId);
        if (!ON_SALE.equals(policy.getSaleStatus()) || HIDDEN.equals(policy.getVisibility())) {
            return false;
        }
        if (EMOJI_PACKAGE.equals(policy.getProductType())) {
            EmojiPackage emojiPackage = emojiPackageMapper.selectById(productId);
            return approvedForCommerce(emojiPackage)
                    && Integer.valueOf(1).equals(emojiPackage.getStatus())
                    && sellerCanTrade(emojiPackage);
        }
        DecorationConfig config = decorationConfigMapper.selectById(productId);
        return approvedForCommerce(config)
                && Integer.valueOf(1).equals(config.getIsEnabled())
                && decorationSellerCanTrade(config);
    }

    @Override
    public boolean canUseOwnedEntitlement(String productType, Long productId) {
        StoreProductPolicy policy = getPolicy(productType, productId);
        return RETAIN.equals(policy.getEntitlementPolicy());
    }

    @Override
    public void requirePurchasable(String productType, Long productId) {
        StoreProductPolicy policy = getPolicy(productType, productId);
        if (!ON_SALE.equals(policy.getSaleStatus()) || HIDDEN.equals(policy.getVisibility())) {
            throw new BusinessException("这件好物暂时不在货架上，先逛逛别的吧");
        }
        if (!NORMAL.equals(policy.getSettlementStatus())) {
            throw new BusinessException("这件好物正在暂停收款，稍后再来看看吧");
        }
        if (EMOJI_PACKAGE.equals(policy.getProductType())) {
            EmojiPackage emojiPackage = emojiPackageMapper.selectById(productId);
            if (!approvedForCommerce(emojiPackage)) {
                throw new BusinessException("这套表情还没有通过内容审核，暂时不能购买");
            }
            if (!sellerCanTrade(emojiPackage)) {
                throw new BusinessException("这位创作者的铺子暂时歇业，先逛逛别的吧");
            }
        } else {
            DecorationConfig decoration = decorationConfigMapper.selectById(productId);
            if (!approvedForCommerce(decoration)) {
                throw new BusinessException("这件装饰还没有通过内容审核，暂时不能购买");
            }
            if (!decorationSellerCanTrade(decoration)) {
                throw new BusinessException("这位创作者的铺子暂时歇业，先逛逛别的吧");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreProductPolicy changeByOwner(String productType, Long productId, Long ownerId,
                                            String action, String reason) {
        String type = normalizeType(productType);
        if (EMOJI_PACKAGE.equals(type)) {
            EmojiPackage emojiPackage = emojiPackageMapper.selectActiveByIdForUpdate(productId);
            if (emojiPackage == null || ownerId == null || !ownerId.equals(emojiPackage.getCreatorId())
                    || !"custom".equals(emojiPackage.getType())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "这不是你打理的表情包");
            }
            if (!"approved".equals(emojiPackage.getReviewStatus())) {
                throw new BusinessException("这套表情还没通过审核，暂时不能调整货架状态");
            }
        } else {
            DecorationConfig decoration = decorationConfigMapper.selectActiveByIdForUpdate(productId);
            if (decoration == null || ownerId == null || !ownerId.equals(decoration.getCreatorId())
                    || !"custom".equals(decoration.getSourceType())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "这不是你打理的装饰");
            }
            if (!"approved".equals(decoration.getReviewStatus())) {
                throw new BusinessException("这件装饰还没通过审核，暂时不能调整货架状态");
            }
        }
        User owner = userMapper.selectById(ownerId);
        if (("on_sale".equals(normalizeAction(action)) || "public".equals(normalizeAction(action)))
                && !UserAccountStatusUtil.canExposePublicContent(owner)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "你的创作者铺子暂时歇业，还不能重新摆上货架");
        }
        if (DECORATION.equals(type)
                && ("on_sale".equals(normalizeAction(action)) || "public".equals(normalizeAction(action)))
                && !decorationSellerCanTrade(decorationConfigMapper.selectById(productId))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前创作者资格还不能重新摆上现金装饰");
        }
        return change(type, productId, ownerId, "owner", action, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreProductPolicy changeByAdmin(String productType, Long productId, Long operatorId,
                                            String action, String reason) {
        String type = normalizeType(productType);
        String normalizedAction = normalizeAction(action);
        if (EMOJI_PACKAGE.equals(type)) {
            EmojiPackage emojiPackage = emojiPackageMapper.selectActiveByIdForUpdate(productId);
            if (emojiPackage == null) {
                throw new BusinessException("这件好物已经找不到了");
            }
            if ("custom".equals(emojiPackage.getType())
                    && !"approved".equals(emojiPackage.getReviewStatus())) {
                throw new BusinessException("这套表情还没有通过内容审核，不能进行店务处理");
            }
        } else {
            DecorationConfig decoration = decorationConfigMapper.selectActiveByIdForUpdate(productId);
            if (decoration == null) {
                throw new BusinessException("这件好物已经找不到了");
            }
            if ("custom".equals(decoration.getSourceType())
                    && !"approved".equals(decoration.getReviewStatus())) {
                throw new BusinessException("这件装饰还没有通过内容审核，不能进行店务处理");
            }
        }
        return change(type, productId, operatorId, "admin", normalizedAction, reason);
    }

    @Override
    public Map<String, Object> getAdminProducts(String productType, String keyword,
                                                Integer page, Integer size) {
        String typeFilter = productType == null || productType.trim().isEmpty()
                ? null : normalizeType(productType);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        long offset = (long) (safePage - 1) * safeSize;
        List<StoreProductVO> products = new ArrayList<>();
        for (StoreProductRow row : productQueryMapper.selectAdminPage(
                typeFilter, normalizedKeyword, offset, safeSize)) {
            StoreProductVO product = getProduct(row.getProductType(), row.getProductId());
            if (product.getSellerId() == null || "approved".equals(product.getReviewStatus())) {
                products.add(product);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("list", products);
        result.put("total", productQueryMapper.countAdminProducts(typeFilter, normalizedKeyword));
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    @Override
    public StoreProductVO getProduct(String productType, Long productId) {
        String type = normalizeType(productType);
        if (EMOJI_PACKAGE.equals(type)) {
            EmojiPackage value = emojiPackageMapper.selectById(productId);
            if (value == null) {
                throw new BusinessException("商品不存在");
            }
            return toEmojiVO(value);
        }
        DecorationConfig value = decorationConfigMapper.selectById(productId);
        if (value == null) {
            throw new BusinessException("商品不存在");
        }
        return toDecorationVO(value);
    }

    @Override
    public List<StoreProductVO> getPublicSellerProducts(Long sellerId, Integer limit) {
        User seller = sellerId == null ? null : userMapper.selectById(sellerId);
        if (!UserAccountStatusUtil.canExposePublicContent(seller)
                || !Integer.valueOf(1).equals(seller.getIsCreator())
                || !"active".equalsIgnoreCase(seller.getCreatorStatus())) {
            return Collections.emptyList();
        }
        int safeLimit = Math.min(Math.max(limit == null ? 6 : limit, 1), 12);
        List<StoreProductVO> products = new ArrayList<>();
        for (EmojiPackage emojiPackage : emojiPackageMapper.selectList(new LambdaQueryWrapper<EmojiPackage>()
                .eq(EmojiPackage::getCreatorId, sellerId)
                .orderByDesc(EmojiPackage::getUpdateTime)
                .last("LIMIT " + safeLimit))) {
            if (isCatalogVisible(EMOJI_PACKAGE, emojiPackage.getId())) products.add(toEmojiVO(emojiPackage));
        }
        if (products.size() < safeLimit) {
            for (DecorationConfig decoration : decorationConfigMapper.selectList(new LambdaQueryWrapper<DecorationConfig>()
                    .eq(DecorationConfig::getCreatorId, sellerId)
                    .orderByDesc(DecorationConfig::getUpdateTime)
                    .last("LIMIT " + safeLimit))) {
                if (isCatalogVisible(DECORATION, decoration.getId())) products.add(toDecorationVO(decoration));
            }
        }
        products.sort(Comparator.comparing(StoreProductVO::getUpdateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        if (products.size() > safeLimit) {
            return new ArrayList<>(products.subList(0, safeLimit));
        }
        return products;
    }

    private StoreProductPolicy change(String type, Long productId, Long operatorId,
                                      String actorType, String rawAction, String rawReason) {
        String action = normalizeAction(rawAction);
        String reason = sanitizeReason(rawReason);
        if ("admin".equals(actorType) && requiresReason(action) && reason.isEmpty()) {
            throw new BusinessException("请写一句店务处理原因");
        }
        StoreProductPolicy stored = policyMapper.selectForUpdate(type, productId);
        if (stored == null) {
            stored = defaultPolicy(type, productId);
            stored.setLastOperatorId(operatorId);
            stored.setReason(reason);
            try {
                policyMapper.insert(stored);
            } catch (DuplicateKeyException e) {
                stored = policyMapper.selectForUpdate(type, productId);
            }
        }
        String before = snapshot(stored);
        applyAction(stored, action, actorType);
        int oldVersion = stored.getVersion() == null ? 0 : stored.getVersion();
        if (policyMapper.updatePolicy(stored.getId(), oldVersion, stored.getSaleStatus(),
                stored.getVisibility(), stored.getEntitlementPolicy(), stored.getSettlementStatus(),
                operatorId, reason) != 1) {
            throw new BusinessException("货架状态刚刚有变化，请刷新后再试");
        }
        stored.setVersion(oldVersion + 1);
        stored.setLastOperatorId(operatorId);
        stored.setReason(reason);
        syncSourceAvailability(type, productId, stored);
        if (!ON_SALE.equals(stored.getSaleStatus()) || HIDDEN.equals(stored.getVisibility())) {
            paymentOrderMapper.cancelPendingByProduct(type, productId);
        }
        StoreProductPolicyLog log = new StoreProductPolicyLog();
        log.setPolicyId(stored.getId());
        log.setProductType(type);
        log.setProductId(productId);
        log.setActorType(actorType);
        log.setAction(action);
        log.setBeforeState(before);
        log.setAfterState(snapshot(stored));
        log.setOperatorId(operatorId);
        log.setReason(reason);
        log.setCreateTime(LocalDateTime.now());
        logMapper.insert(log);
        return stored;
    }

    private void applyAction(StoreProductPolicy policy, String action, String actorType) {
        switch (action) {
            case "off_sale":
                policy.setSaleStatus("admin".equals(actorType) ? "off_sale_admin" : "off_sale_owner");
                break;
            case "on_sale":
                if (HIDDEN.equals(policy.getVisibility())) {
                    throw new BusinessException("这件好物已被收进库房，请先联系小镇管理员");
                }
                policy.setSaleStatus(ON_SALE);
                break;
            case "unlisted":
                policy.setVisibility(UNLISTED);
                break;
            case "public":
                if ("admin".equals(actorType) || !"off_sale_admin".equals(policy.getSaleStatus())) {
                    policy.setVisibility(PUBLIC);
                    break;
                }
                throw new BusinessException("管理员收下货架的商品，暂时不能由作者公开");
            case "hide":
                requireAdmin(actorType);
                policy.setVisibility(HIDDEN);
                policy.setSaleStatus("off_sale_admin");
                break;
            case "restore":
                requireAdmin(actorType);
                policy.setVisibility(PUBLIC);
                policy.setSaleStatus(ON_SALE);
                break;
            case "retain_entitlement":
                requireAdmin(actorType);
                policy.setEntitlementPolicy(RETAIN);
                break;
            case "block_entitlement":
                requireAdmin(actorType);
                policy.setEntitlementPolicy("temporarily_blocked");
                break;
            case "revoke_entitlement":
                requireAdmin(actorType);
                policy.setEntitlementPolicy("revoked");
                break;
            case "hold_settlement":
                requireAdmin(actorType);
                policy.setSettlementStatus("hold");
                break;
            case "resume_settlement":
                requireAdmin(actorType);
                policy.setSettlementStatus(NORMAL);
                break;
            default:
                throw new BusinessException("暂不支持这项店务处理");
        }
    }

    private StoreProductPolicy defaultPolicy(String type, Long productId) {
        StoreProductPolicy policy = new StoreProductPolicy();
        policy.setProductType(type);
        policy.setProductId(productId);
        policy.setSaleStatus(sourceEnabled(type, productId) ? ON_SALE : "off_sale_admin");
        policy.setVisibility(PUBLIC);
        policy.setEntitlementPolicy(RETAIN);
        policy.setSettlementStatus(NORMAL);
        policy.setVersion(0);
        policy.setSellerId(resolveSeller(type, productId));
        return policy;
    }

    private StoreProductVO toEmojiVO(EmojiPackage emojiPackage) {
        StoreProductPolicy policy = getPolicy(EMOJI_PACKAGE, emojiPackage.getId());
        StoreProductVO vo = baseVO(policy);
        vo.setName(emojiPackage.getName());
        vo.setCoverUrl(emojiPackage.getCoverUrl());
        vo.setSellerId(emojiPackage.getCreatorId());
        vo.setPurchaseMode(emojiPackage.getPurchaseMode());
        vo.setPointsPrice(emojiPackage.getPrice());
        vo.setCashPrice(emojiPackage.getCashPrice());
        vo.setItemLimit(emojiPackage.getItemLimit());
        vo.setItemCount(Math.toIntExact(emojiMapper.selectCount(new LambdaQueryWrapper<Emoji>()
                .eq(Emoji::getPackageId, emojiPackage.getId())
                .eq(Emoji::getEnabled, 1))));
        vo.setReviewStatus(emojiPackage.getReviewStatus());
        vo.setUpdateTime(emojiPackage.getUpdateTime());
        if (!sellerCanTrade(emojiPackage)) {
            vo.setEffectiveStatus("account_restricted");
        }
        return vo;
    }

    private StoreProductVO toDecorationVO(DecorationConfig config) {
        StoreProductPolicy policy = getPolicy(DECORATION, config.getId());
        StoreProductVO vo = baseVO(policy);
        vo.setName(config.getDecorationName());
        vo.setCoverUrl(config.getPreviewUrl() == null ? config.getIconUrl() : config.getPreviewUrl());
        vo.setSellerId(config.getCreatorId());
        vo.setPurchaseMode(config.getObtainType());
        vo.setPointsPrice(config.getPointsCost());
        vo.setCashPrice(config.getCashPrice() == null ? null : BigDecimal.valueOf(config.getCashPrice(), 2));
        vo.setReviewStatus(config.getReviewStatus());
        vo.setUpdateTime(config.getUpdateTime());
        if (!decorationSellerCanTrade(config)) {
            vo.setEffectiveStatus("account_restricted");
        }
        return vo;
    }

    private StoreProductVO baseVO(StoreProductPolicy policy) {
        StoreProductVO vo = new StoreProductVO();
        vo.setProductType(policy.getProductType());
        vo.setProductId(policy.getProductId());
        vo.setSellerId(policy.getSellerId());
        vo.setSaleStatus(policy.getSaleStatus());
        vo.setVisibility(policy.getVisibility());
        vo.setEntitlementPolicy(policy.getEntitlementPolicy());
        vo.setSettlementStatus(policy.getSettlementStatus());
        vo.setReason(policy.getReason());
        vo.setEffectiveStatus(ON_SALE.equals(policy.getSaleStatus()) && PUBLIC.equals(policy.getVisibility())
                ? "available" : policy.getSaleStatus());
        return vo;
    }

    private void syncSourceAvailability(String type, Long productId, StoreProductPolicy policy) {
        boolean enabled = ON_SALE.equals(policy.getSaleStatus()) && !HIDDEN.equals(policy.getVisibility());
        if (EMOJI_PACKAGE.equals(type)) {
            EmojiPackage update = new EmojiPackage();
            update.setId(productId);
            update.setStatus(enabled ? 1 : 0);
            if (emojiPackageMapper.updateById(update) != 1) {
                throw new BusinessException("表情包销售状态同步失败");
            }
        } else {
            DecorationConfig update = new DecorationConfig();
            update.setId(productId);
            update.setIsEnabled(enabled ? 1 : 0);
            if (decorationConfigMapper.updateById(update) != 1) {
                throw new BusinessException("装饰销售状态同步失败");
            }
        }
    }

    private boolean sellerCanTrade(EmojiPackage emojiPackage) {
        if (emojiPackage == null) {
            return false;
        }
        if ("system".equals(emojiPackage.getType())) {
            return true;
        }
        User seller = emojiPackage.getCreatorId() == null ? null : userMapper.selectById(emojiPackage.getCreatorId());
        return UserAccountStatusUtil.canExposePublicContent(seller)
                && Integer.valueOf(1).equals(seller.getIsCreator())
                && "active".equalsIgnoreCase(seller.getCreatorStatus());
    }

    private boolean decorationSellerCanTrade(DecorationConfig decoration) {
        if (decoration == null) return false;
        if (!"custom".equals(decoration.getSourceType())) return true;
        User seller = decoration.getCreatorId() == null ? null : userMapper.selectById(decoration.getCreatorId());
        if (!UserAccountStatusUtil.canExposePublicContent(seller)) return false;
        return !"cash".equals(decoration.getObtainType())
                || (Integer.valueOf(1).equals(seller.getIsCreator())
                && "active".equalsIgnoreCase(seller.getCreatorStatus()));
    }

    private void requireProductExists(String type, Long productId) {
        if (productId == null || (EMOJI_PACKAGE.equals(type)
                ? emojiPackageMapper.selectById(productId) == null
                : decorationConfigMapper.selectById(productId) == null)) {
            throw new BusinessException("商品不存在");
        }
    }

    private boolean sourceEnabled(String type, Long productId) {
        if (EMOJI_PACKAGE.equals(type)) {
            EmojiPackage value = emojiPackageMapper.selectById(productId);
            return value != null && Integer.valueOf(1).equals(value.getStatus());
        }
        DecorationConfig value = decorationConfigMapper.selectById(productId);
        return value != null && Integer.valueOf(1).equals(value.getIsEnabled());
    }

    private boolean approvedForCommerce(EmojiPackage value) {
        return value != null && (!"custom".equals(value.getType())
                || "approved".equals(value.getReviewStatus()));
    }

    private boolean approvedForCommerce(DecorationConfig value) {
        return value != null && (!"custom".equals(value.getSourceType())
                || "approved".equals(value.getReviewStatus()));
    }

    private Long resolveSeller(String type, Long productId) {
        if (EMOJI_PACKAGE.equals(type)) {
            EmojiPackage emojiPackage = emojiPackageMapper.selectById(productId);
            return emojiPackage == null ? null : emojiPackage.getCreatorId();
        }
        DecorationConfig decoration = decorationConfigMapper.selectById(productId);
        return decoration == null ? null : decoration.getCreatorId();
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!EMOJI_PACKAGE.equals(normalized) && !DECORATION.equals(normalized)) {
            throw new BusinessException("不支持的商店商品类型");
        }
        return normalized;
    }

    private String normalizeAction(String action) {
        return action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
    }

    private String sanitizeReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return "";
        }
        SecurityCheckUtil.CheckResult check = SecurityCheckUtil.checkDescription(reason.trim());
        if (!check.isSafe()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, check.getMessage());
        }
        String value = SecurityCheckUtil.escapeHtml(check.getCleanedValue());
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private boolean requiresReason(String action) {
        return "off_sale".equals(action) || "hide".equals(action)
                || "block_entitlement".equals(action) || "revoke_entitlement".equals(action)
                || "hold_settlement".equals(action);
    }

    private void requireAdmin(String actorType) {
        if (!"admin".equals(actorType)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该操作仅限管理员");
        }
    }

    private String snapshot(StoreProductPolicy policy) {
        return "sale=" + policy.getSaleStatus() + ",visibility=" + policy.getVisibility()
                + ",entitlement=" + policy.getEntitlementPolicy()
                + ",settlement=" + policy.getSettlementStatus()
                + ",version=" + policy.getVersion();
    }
}

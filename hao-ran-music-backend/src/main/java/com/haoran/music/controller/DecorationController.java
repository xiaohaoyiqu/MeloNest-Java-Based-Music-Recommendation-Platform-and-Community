   
                      
                       
   

package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.DecorationConfig;
import com.haoran.music.entity.UserDecoration;
import com.haoran.music.service.DecorationService;
import com.haoran.music.service.UserActivityPointsService;
import com.haoran.music.service.StoreProductPolicyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

   
          
   
@Slf4j
@RestController
@RequestMapping("/decoration")
public class DecorationController {

    private final DecorationService decorationService;
    private final UserActivityPointsService activityPointsService;
    private final StoreProductPolicyService storeProductPolicyService;

    public DecorationController(
            DecorationService decorationService,
            UserActivityPointsService activityPointsService,
            StoreProductPolicyService storeProductPolicyService) {
        this.decorationService = decorationService;
        this.activityPointsService = activityPointsService;
        this.storeProductPolicyService = storeProductPolicyService;
    }

       
               
       
    @GetMapping("/user/list")
    public Result<Map<String, List<UserDecoration>>> getUserDecorations(
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        Map<String, List<UserDecoration>> decorations = new HashMap<>();
        decorations.put("avatar_frame", decorationService.getUserDecorationsByType(userId, "avatar_frame"));
        decorations.put("comment_bar", decorationService.getUserDecorationsByType(userId, "comment_bar"));
        decorations.put("player", decorationService.getUserDecorationsByType(userId, "player"));
        decorations.put("dialog_box", decorationService.getUserDecorationsByType(userId, "dialog_box"));
        decorations.put("theme", decorationService.getUserDecorationsByType(userId, "theme"));
        decorations.put("badge", decorationService.getUserDecorationsByType(userId, "badge"));

        return Result.success(decorations);
    }

       
                
       
    @GetMapping("/user/equipped")
    @ApiLog("获取用户装备的装饰")
    public Result<Map<String, UserDecoration>> getEquippedDecorations(
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        Map<String, UserDecoration> equipped = new HashMap<>();
        equipped.put("avatar_frame", decorationService.getEquippedDecoration(userId, "avatar_frame"));
        equipped.put("comment_bar", decorationService.getEquippedDecoration(userId, "comment_bar"));
        equipped.put("player", decorationService.getEquippedDecoration(userId, "player"));
        equipped.put("dialog_box", decorationService.getEquippedDecoration(userId, "dialog_box"));
        equipped.put("theme", decorationService.getEquippedDecoration(userId, "theme"));
        equipped.put("badge", decorationService.getEquippedDecoration(userId, "badge"));

        return Result.success(equipped);
    }

       
             
       
    @GetMapping("/shop")
    public Result<List<DecorationService.DecorationDTO>> getDecorationShop(
            @RequestParam(required = false) String type) {

        List<DecorationConfig> configs = decorationService.getDecorationConfigs(type);

        List<DecorationService.DecorationDTO> dtoList = configs.stream()
                .filter(config -> storeProductPolicyService.isCatalogVisible("decoration", config.getId()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return Result.success(dtoList);
    }

       
                
       
    @GetMapping("/shop/{type}")
    public Result<List<DecorationService.DecorationDTO>> getDecorationByType(
            @PathVariable("type") String type) {

        List<DecorationConfig> configs = decorationService.getDecorationConfigs(type);

        List<DecorationService.DecorationDTO> dtoList = configs.stream()
                .filter(config -> storeProductPolicyService.isCatalogVisible("decoration", config.getId()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return Result.success(dtoList);
    }

       
             
       
    @GetMapping("/detail/{decorationId}")
    public Result<DecorationService.DecorationDTO> getDecorationDetail(
            @PathVariable("decorationId") String decorationId) {

        List<DecorationConfig> configs = decorationService.getDecorationConfigs(null);
        DecorationConfig config = configs.stream()
                .filter(c -> c.getDecorationId().equals(decorationId))
                .findFirst()
                .orElse(null);

        if (config == null) {
            return Result.error(404, "装饰不存在");
        }

        return Result.success(convertToDTO(config));
    }

       
           
       
    @PostMapping("/equip")
    @ApiLog("装备装饰")
    public Result<Boolean> equipDecoration(
            HttpServletRequest request,
            @RequestParam String decorationId) {

        Long userId = (Long) request.getAttribute("userId");
        boolean success = decorationService.equipDecoration(userId, decorationId);

        return Result.success(success);
    }

       
           
       
    @PostMapping("/unequip")
    @ApiLog("卸载装饰")
    public Result<Boolean> unequipDecoration(
            HttpServletRequest request,
            @RequestParam String decorationType) {

        Long userId = (Long) request.getAttribute("userId");
        boolean success = decorationService.unequipDecoration(userId, decorationType);

        return Result.success(success);
    }

       
                
       
    @PostMapping("/redeem")
    @ApiLog("兑换装饰")
    public Result<Boolean> redeemDecoration(
            HttpServletRequest request,
            @RequestParam String decorationId) {

        Long userId = (Long) request.getAttribute("userId");
        boolean success = decorationService.redeemDecoration(userId, decorationId);

        return Result.success(success);
    }

       
                
       
    @GetMapping("/user/points")
    public Result<Integer> getUserPoints(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Integer points = activityPointsService.getUserTotalPoints(userId);
        return Result.success(points);
    }

       
             
       
    @GetMapping("/types")
    public Result<List<Map<String, Object>>> getDecorationTypes() {
        List<Map<String, Object>> types = new java.util.ArrayList<>();

        Map<String, Object> type1 = new HashMap<>();
        type1.put("type", "avatar_frame");
        type1.put("name", "头像框");
        type1.put("description", "用户头像边框装饰，尺寸自适应头像大小");
        types.add(type1);

        Map<String, Object> type2 = new HashMap<>();
        type2.put("type", "comment_bar");
        type2.put("name", "评论区条");
        type2.put("description", "评论左侧装饰条，透明度和尺寸自适应");
        types.add(type2);

        Map<String, Object> type3 = new HashMap<>();
        type3.put("type", "player");
        type3.put("name", "播放器");
        type3.put("description", "播放器皮肤，全尺寸自适应");
        types.add(type3);

        Map<String, Object> type4 = new HashMap<>();
        type4.put("type", "dialog_box");
        type4.put("name", "对话框");
        type4.put("description", "对话框装饰，透明度自适应");
        types.add(type4);

        Map<String, Object> type5 = new HashMap<>();
        type5.put("type", "theme");
        type5.put("name", "主题");
        type5.put("description", "界面主题样式");
        types.add(type5);

        Map<String, Object> type6 = new HashMap<>();
        type6.put("type", "badge");
        type6.put("name", "徽章");
        type6.put("description", "成就徽章标识");
        types.add(type6);

        return Result.success(types);
    }

       
             
       
    private DecorationService.DecorationDTO convertToDTO(DecorationConfig config) {
        DecorationService.DecorationDTO dto = new DecorationService.DecorationDTO();
        dto.setConfigId(config.getId());
        dto.setDecorationId(config.getDecorationId());
        dto.setDecorationName(config.getDecorationName());
        dto.setDecorationType(config.getDecorationType());
        dto.setDescription(config.getDescription());
        dto.setIconUrl(config.getIconUrl());
        dto.setPreviewUrl(config.getPreviewUrl());
        dto.setStyleConfig(config.getStyleConfig());
        dto.setRarity(config.getRarity());
        dto.setPointsCost(config.getPointsCost());
        dto.setCashPrice(config.getCashPrice());
        dto.setObtainType(config.getObtainType());
        dto.setPermanent(Integer.valueOf(1).equals(config.getIsPermanent()));
        dto.setDurationDays(config.getDurationDays());
        dto.setCanRedeem("points".equals(config.getObtainType()));

                 
        String obtainDesc = getObtainDescription(config);
        dto.setObtainDescription(obtainDesc);

        return dto;
    }

       
             
       
    private String getObtainDescription(DecorationConfig config) {
        switch (config.getObtainType()) {
            case "sign":
                return "连续签到获得";
            case "activity":
                return "参与活动获得";
            case "vip":
                return "VIP专属";
            case "achievement":
                return "成就达成获得";
            case "points":
                return config.getPointsCost() + "活跃值兑换";
            case "cash":
                return "现金购买：¥" + (config.getCashPrice() != null ? config.getCashPrice() / 100.0 : 0);
            default:
                return "";
        }
    }
}

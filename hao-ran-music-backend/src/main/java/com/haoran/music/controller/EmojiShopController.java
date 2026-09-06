package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.dto.PageResult;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.EmojiShopService;
import com.haoran.music.service.EmojiShopService.EmojiShopHomeVO;
import com.haoran.music.vo.emoji.EmojiPackageDetailVO;
import com.haoran.music.vo.emoji.EmojiPackageVO;

import javax.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

   
                      
                              
   
@RestController
@RequestMapping("/emoji-shop")
public class EmojiShopController {

    @Resource
    private EmojiShopService emojiShopService;

       
               
       
    @ApiLog("获取表情商城首页")
    @GetMapping("/home")
    public Result<EmojiShopHomeVO> getHomeData() {
        EmojiShopHomeVO result = emojiShopService.getHomeData();
        return Result.success(result);
    }

       
              
       
    @ApiLog("获取表情包列表")
    @GetMapping("/packages")
    public Result<PageResult<EmojiPackageVO>> getPackages(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        PageResult<EmojiPackageVO> result = emojiShopService.getPackages(type, category, page, size);
        return Result.success(result);
    }

       
              
       
    @ApiLog("获取表情包详情")
    @GetMapping("/package/{id}")
    public Result<EmojiPackageDetailVO> getPackageDetail(@PathVariable Long id) {
        EmojiPackageDetailVO result = emojiShopService.getPackageDetail(id);
        return Result.success(result);
    }

       
               
       
    @ApiLog("购买表情包")
    @PostMapping("/package/{id}/purchase")
    public Result<Void> purchasePackage(@PathVariable Long id) {
        emojiShopService.purchasePackage(id);
        return Result.success();
    }

       
             
       
    @ApiLog("获取我的表情")
    @GetMapping("/my")
    public Result<List<EmojiPackageVO>> getMyEmojis() {
        List<EmojiPackageVO> result = emojiShopService.getMyEmojis();
        return Result.success(result);
    }

       
            
       
    @ApiLog("收藏表情包")
    @PostMapping("/package/{id}/favorite")
    public Result<Void> favoritePackage(@PathVariable Long id) {
        emojiShopService.favoritePackage(id);
        return Result.success();
    }

       
           
       
    @DeleteMapping("/package/{id}/favorite")
    @ApiLog("取消收藏表情包")
    public Result<Void> unfavoritePackage(@PathVariable Long id) {
        emojiShopService.unfavoritePackage(id);
        return Result.success();
    }
}

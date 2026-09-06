   
                      
   
package com.haoran.music.controller;

import com.haoran.music.common.result.Result;
import com.haoran.music.service.CuratedCarouselService;
import com.haoran.music.vo.curated.CuratedCarouselItemVO;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

   
            
   
@RestController
@RequestMapping("/curated-content/carousel")
public class CuratedCarouselController {

    @Resource
    private CuratedCarouselService curatedCarouselService;

    @GetMapping
    public Result<List<CuratedCarouselItemVO>> getItems(
            @RequestParam(defaultValue = "discover_top") String scene,
            @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(curatedCarouselService.getPublicItems(scene, limit));
    }

    @PostMapping("/{itemId}/view")
    public Result<Boolean> incrementViewCount(@PathVariable Long itemId) {
        return Result.success(curatedCarouselService.incrementViewCount(itemId));
    }
}

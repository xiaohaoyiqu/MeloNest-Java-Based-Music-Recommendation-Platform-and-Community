




package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.SubscribeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;





@Slf4j
@RestController
@RequestMapping("/subscribe")
public class SubscribeController {

    private final SubscribeService subscribeService;

    public SubscribeController(SubscribeService subscribeService) {
        this.subscribeService = subscribeService;
    }




    @ApiLog("设置歌单订阅")
    @PostMapping("/set")
    public Result setPlaylistSubscribe(HttpServletRequest request,
                                    @RequestParam Long playlistId,
                                    @RequestParam java.math.BigDecimal price,
                                    @RequestParam Integer period) {
        Long creatorId = (Long) request.getAttribute("userId");
        return Result.success(subscribeService.setPlaylistSubscribe(creatorId,
                playlistId, price, period));
    }




    @ApiLog("订阅歌单")
    @PostMapping("/{playlistId}")
    public Result subscribePlaylist(HttpServletRequest request,
                                @PathVariable Long playlistId,
                                @RequestParam(defaultValue = "month") String subscribeType,
                                @RequestParam(defaultValue = "false") Boolean autoRenew,
                                @RequestHeader("Idempotency-Key") String idempotencyKey) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(subscribeService.subscribePlaylist(userId, playlistId,
                subscribeType, autoRenew, idempotencyKey));
    }




    @ApiLog("检查订阅状态")
    @GetMapping("/check/{id}")
    public Result checkSubscribed(HttpServletRequest request,
                               @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(subscribeService.checkSubscribed(userId, id));
    }




    @ApiLog("获取订阅列表")
    @GetMapping("/my")
    public Result getMySubscribes(HttpServletRequest request,
                                @RequestParam(required = false) String status,
                                @RequestParam(defaultValue = "1") Integer page,
                                @RequestParam(defaultValue = "20") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(subscribeService.getMySubscribes(userId, status, page, size));
    }




    @GetMapping("/subscribers")
    public Result getPlaylistSubscribers(HttpServletRequest request,
                                        @RequestParam Long playlistId,
                                        @RequestParam(defaultValue = "1") Integer page,
                                        @RequestParam(defaultValue = "20") Integer size) {
        Long creatorId = (Long) request.getAttribute("userId");
        return Result.success(subscribeService.getPlaylistSubscribers(creatorId, playlistId, page, size));
    }




    @ApiLog("获取订阅统计")
    @GetMapping("/stats")
    public Result getPlaylistSubscribeStats(@RequestParam Long playlistId) {
        return Result.success(subscribeService.getPlaylistSubscribeStats(playlistId));
    }




    @ApiLog("取消订阅")
    @PostMapping("/{playlistId}/cancel")
    public Result cancelSubscribe(HttpServletRequest request,
                               @PathVariable Long playlistId) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(subscribeService.cancelSubscribe(userId, playlistId));
    }




    @ApiLog("续费订阅")
    @PostMapping("/{playlistId}/renew")
    public Result renewSubscribe(HttpServletRequest request,
                              @PathVariable Long playlistId,
                              @RequestParam(defaultValue = "month") String subscribeType,
                              @RequestHeader("Idempotency-Key") String idempotencyKey) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(subscribeService.subscribePlaylist(
                userId, playlistId, subscribeType, false, idempotencyKey));
    }




    @ApiLog("取消自动续费")
    @PostMapping("/{playlistId}/auto-renew/disable")
    public Result cancelAutoRenew(HttpServletRequest request,
                                  @PathVariable Long playlistId) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(subscribeService.cancelAutoRenew(userId, playlistId));
    }




    @ApiLog("启用自动续费")
    @PostMapping("/{playlistId}/auto-renew/enable")
    public Result enableAutoRenew(HttpServletRequest request,
                                  @PathVariable Long playlistId) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(subscribeService.enableAutoRenew(userId, playlistId));
    }




    @ApiLog("取消歌单付费")
    @DeleteMapping("/{playlistId}")
    public Result cancelPlaylistSubscribe(HttpServletRequest request,
                                      @PathVariable Long playlistId) {
        Long creatorId = (Long) request.getAttribute("userId");
        return Result.success(subscribeService.cancelPlaylistSubscribe(creatorId, playlistId));
    }




    @ApiLog("获取即将到期订阅")
    @GetMapping("/expiring")
    public Result getExpiringSubscribes(@RequestParam(defaultValue = "3") Integer days) {
        return Result.success(subscribeService.getExpiringSubscribes(days));
    }




    @ApiLog("获取订阅的歌单列表")
    @GetMapping("/subscribed-playlists")
    public Result getMySubscribedPlaylists(HttpServletRequest request,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "20") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(subscribeService.getMySubscribedPlaylists(userId, page, size));
    }
}

   
                      
   
package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.PlayEventDeadLetter;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.PlayEventDeadLetterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

   
               
   
@RestController
@RequestMapping("/admin/play-events")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class AdminPlayEventController {

    @Resource
    private PlayEventDeadLetterService deadLetterService;

    @GetMapping("/dead-letters")
    public Result<Map<String, Object>> listDeadLetters(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String eventId,
                                                       @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(deadLetterService.list(status, eventId,
                page == null ? 1 : page, size == null ? 20 : size));
    }

    @GetMapping("/dead-letters/status")
    public Result<Map<String, Object>> getDeadLetterStatus() {
        return Result.success(deadLetterService.getStatus());
    }

    @GetMapping("/dead-letters/{id}")
    public Result<PlayEventDeadLetter> getDeadLetter(@PathVariable Long id) {
        PlayEventDeadLetter deadLetter = deadLetterService.getDetail(id);
        return deadLetter == null ? Result.error(404, "播放事件死信不存在") : Result.success(deadLetter);
    }

    @PostMapping("/dead-letters/{id}/replay")
    public Result<PlayEventDeadLetter> replayDeadLetter(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            return Result.success(deadLetterService.replay(id, operatorId, reason));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(409, e.getMessage());
        }
    }

    @PostMapping("/dead-letters/{id}/ignore")
    public Result<PlayEventDeadLetter> ignoreDeadLetter(
            @PathVariable Long id,
            @RequestParam String reason,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            return Result.success(deadLetterService.ignore(id, operatorId, reason));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(409, e.getMessage());
        }
    }

    @PostMapping("/dead-letters/{id}/resolve")
    public Result<PlayEventDeadLetter> resolveDeadLetter(
            @PathVariable Long id,
            @RequestParam String reason,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            return Result.success(deadLetterService.resolve(id, operatorId, reason));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(409, e.getMessage());
        }
    }
}

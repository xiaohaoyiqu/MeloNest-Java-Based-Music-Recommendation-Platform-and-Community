   
                      
   
package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.UserQuickPhrase;
import com.haoran.music.service.UserQuickPhraseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

   
                                
   
@Slf4j
@RestController
@RequestMapping("/user/quick-phrase")
public class UserQuickPhraseController {

    @Autowired
    private UserQuickPhraseService userQuickPhraseService;

    @GetMapping("/list")
    @ApiLog("Get user quick phrases")
    public Result<List<UserQuickPhrase>> getUserPhrases(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<UserQuickPhrase> phrases = userQuickPhraseService.getUserPhrases(userId);
        return Result.success(phrases);
    }

    @PostMapping("/add")
    @ApiLog("Add user quick phrase")
    public Result<UserQuickPhrase> addPhrase(@RequestBody Map<String, Object> params,
                                             HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        UserQuickPhrase phrase = userQuickPhraseService.addPhrase(userId, readPhrase(params));
        return Result.success(phrase);
    }

    @PutMapping("/{id}")
    @ApiLog("Update user quick phrase")
    public Result<Boolean> updatePhrase(@PathVariable Long id,
                                        @RequestBody Map<String, Object> params,
                                        HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        boolean success = userQuickPhraseService.updatePhrase(id, userId, readPhrase(params));
        return Result.success(success);
    }

    @PostMapping("/update")
    @ApiLog("Update user quick phrase legacy")
    public Result<Boolean> updatePhraseLegacy(@RequestBody Map<String, Object> params,
                                              HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        Long phraseId = parseLong(params.get("id"));
        boolean success = userQuickPhraseService.updatePhrase(phraseId, userId, readPhrase(params));
        return Result.success(success);
    }

    @DeleteMapping("/{id}")
    @ApiLog("Delete user quick phrase")
    public Result<Boolean> deletePhrase(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        boolean success = userQuickPhraseService.deletePhrase(id, userId);
        return Result.success(success);
    }

    @PostMapping("/delete/{id}")
    @ApiLog("Delete user quick phrase legacy")
    public Result<Boolean> deletePhraseLegacy(@PathVariable Long id, HttpServletRequest request) {
        return deletePhrase(id, request);
    }

    @PostMapping("/sort")
    @ApiLog("Sort user quick phrases")
    public Result<Boolean> updateSortOrder(@RequestBody Map<String, Object> params,
                                           HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        boolean success = userQuickPhraseService.updateSortOrder(userId, readLongList(params.get("phraseIds")));
        return Result.success(success);
    }

    @PostMapping("/batch-save")
    @ApiLog("Batch save user quick phrases")
    public Result<Boolean> batchSave(@RequestBody Map<String, Object> params,
                                     HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<String> phrases = readStringList(params.get("phrases"));
        userQuickPhraseService.replaceUserPhrases(userId, phrases);
        return Result.success(true);
    }

    @DeleteMapping("/clear")
    @ApiLog("Clear user quick phrases")
    public Result<Integer> clear(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        int count = userQuickPhraseService.clearUserPhrases(userId);
        return Result.success(count);
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        Long userId = parseLong(request.getAttribute("userId"));
        if (userId == null) {
            throw new IllegalArgumentException("Please login first");
        }
        return userId;
    }

    private String readPhrase(Map<String, Object> params) {
        Object phrase = params == null ? null : params.get("phrase");
        return phrase == null ? null : String.valueOf(phrase);
    }

    private List<Long> readLongList(Object value) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("常用语ID列表格式错误");
        }
        List<?> rawValues = (List<?>) value;
        List<Long> result = new ArrayList<>(rawValues.size());
        for (Object rawValue : rawValues) {
            Long parsed = parseLong(rawValue);
            if (parsed == null || parsed <= 0) {
                throw new IllegalArgumentException("常用语ID列表包含无效项");
            }
            result.add(parsed);
        }
        return result;
    }

    private List<String> readStringList(Object value) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("常用语列表格式错误");
        }
        List<?> rawValues = (List<?>) value;
        List<String> result = new ArrayList<>(rawValues.size());
        for (Object rawValue : rawValues) {
            if (!(rawValue instanceof String)) {
                throw new IllegalArgumentException("常用语列表包含无效项");
            }
            String phrase = ((String) rawValue).trim();
            if (!phrase.isEmpty()) {
                result.add(phrase);
            }
        }
        return result;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

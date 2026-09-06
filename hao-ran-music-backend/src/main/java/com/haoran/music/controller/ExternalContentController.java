


package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.aspect.RateLimitScope;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.ExternalContent;
import com.haoran.music.dto.external.ExternalContentCreateDTO;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.ExternalContentMapper;
import com.haoran.music.service.PermissionService;
import com.haoran.music.utils.HttpUtil;
import com.haoran.music.common.util.ExternalUrlGuard;
import com.haoran.music.common.util.ExternalCatalogSearchQuery;
import com.haoran.music.vo.external.ExternalContentPublicVO;
import com.haoran.music.vo.external.ExternalContentCreateResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;






@Slf4j
@RestController
@RequestMapping("/external-content")
public class ExternalContentController {
    private static final int MAX_UPSTREAM_BYTES = 2 * 1024 * 1024;
    private static final Set<String> CONTENT_TYPES = Collections.unmodifiableSet(
            new java.util.HashSet<>(Arrays.asList("game", "anime", "music")));
    private static final Set<Integer> CONTENT_STATUSES = Collections.unmodifiableSet(
            new java.util.HashSet<>(Arrays.asList(0, 1, 2)));

    @Autowired
    private ExternalContentMapper externalContentMapper;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private ObjectMapper objectMapper;

    private void checkAdmin(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        if (!permissionService.isAdmin(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Only administrators can perform this operation");
        }
    }

    private boolean containsKeyword(Map<String, Object> game, String keyword) {
        return containsKeyword(game.get("title"), keyword)
                || containsKeyword(game.get("genre"), keyword)
                || containsKeyword(game.get("platform"), keyword)
                || containsKeyword(game.get("short_description"), keyword);
    }

    private boolean containsKeyword(Object value, String keyword) {
        return ExternalCatalogSearchQuery.contains(value, keyword);
    }




    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/search/games")
    @RateLimit(maxRequests = 5, timeWindowSeconds = 1, operation = "externalFreeToGame",
            scope = RateLimitScope.GLOBAL, message = "外部内容搜索过于频繁，请稍后再试")
    public Result<String> searchGames(@RequestParam String keyword,
                                      @RequestAttribute(value = "userId", required = false) Long userId) {
        try {
            checkAdmin(userId);
            String normalizedKeyword = ExternalCatalogSearchQuery.canonicalize(
                    requireText(keyword, "keyword", 100));
            String response = HttpUtil.getJson("https://www.freetogame.com/api/games", 10000,
                    MAX_UPSTREAM_BYTES, "www.freetogame.com");

            List<Map<String, Object>> games = objectMapper.readValue(
                    response,
                    new TypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> filteredGames = games.stream()
                    .filter(game -> containsKeyword(game, normalizedKeyword))
                    .limit(30)
                    .collect(Collectors.toList());
            return Result.successData(objectMapper.writeValueAsString(filteredGames));
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("event=external_content_game_search_failed errorType={}", e.getClass().getSimpleName());
            return Result.error("Search failed");
        }
    }




    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/add")
    @RateLimit(maxRequests = 30, timeWindowSeconds = 60, operation = "externalContentAdd",
            scope = RateLimitScope.USER, message = "添加操作过于频繁，请稍后再试")
    public Result<ExternalContentCreateResultVO> addContent(
            @RequestBody ExternalContentCreateDTO request,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        try {
            checkAdmin(userId);
            ExternalContent content = toEntity(request);
            ExternalContent existing = findExistingSource(content);
            if (existing != null) {
                log.info("event=external_content_create_replayed contentType={} contentId={} operatorId={}",
                        content.getContentType(), existing.getId(), userId);
                return Result.success(new ExternalContentCreateResultVO(existing.getId(), false));
            }
            content.setStatus(1);
            content.setPriority(0);
            content.setCreatedBy(userId);
            content.setCreateTime(LocalDateTime.now());
            content.setUpdateTime(LocalDateTime.now());
            try {
                externalContentMapper.insert(content);
            } catch (DuplicateKeyException duplicate) {
                ExternalContent concurrentWinner = findExistingSource(content);
                if (concurrentWinner == null) {
                    throw duplicate;
                }
                log.info("event=external_content_create_race_replayed contentType={} contentId={} operatorId={}",
                        content.getContentType(), concurrentWinner.getId(), userId);
                return Result.success(new ExternalContentCreateResultVO(concurrentWinner.getId(), false));
            }
            log.info("event=external_content_created contentType={} contentId={} operatorId={}",
                    content.getContentType(), content.getId(), userId);
            return Result.success(new ExternalContentCreateResultVO(content.getId(), true));
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("event=external_content_create_failed errorType={}", e.getClass().getSimpleName());
            return Result.error("Add failed");
        }
    }




    @GetMapping("/list/{type}")
    public Result<List<ExternalContentPublicVO>> getPublishedContent(
            @PathVariable String type,
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            String safeType = requireContentType(type);
            List<ExternalContent> list = externalContentMapper.selectPublishedByType(
                    safeType, normalizeLimit(limit, 10, 50));
            return Result.success(toPublicViews(list));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("event=external_content_published_list_failed errorType={}", e.getClass().getSimpleName());
            return Result.error("Query failed");
        }
    }




    @GetMapping("/recommend")
    public Result<List<ExternalContentPublicVO>> getRecommend(@RequestParam(defaultValue = "20") Integer limit) {
        try {
            List<ExternalContent> list = externalContentMapper.selectAllPublished(normalizeLimit(limit, 20, 50));
            return Result.success(toPublicViews(list));
        } catch (Exception e) {
            log.error("event=external_content_recommendation_failed errorType={}", e.getClass().getSimpleName());
            return Result.error("Query failed");
        }
    }




    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/page")
    public Result<Page<ExternalContent>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) Integer status,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        try {
            checkAdmin(userId);
            long safePageNum = pageNum == null ? 1L : Math.max(1L, Math.min(pageNum.longValue(), 100000L));
            int safePageSize = normalizeLimit(pageSize, 20, 100);
            Page<ExternalContent> page = new Page<>(safePageNum, safePageSize);
            QueryWrapper<ExternalContent> wrapper = new QueryWrapper<>();
            if (contentType != null) {
                wrapper.eq("content_type", requireContentType(contentType));
            }
            if (status != null) {
                if (!CONTENT_STATUSES.contains(status)) {
                    return Result.error(400, "内容状态不合法");
                }
                wrapper.eq("status", status);
            }
            wrapper.orderByDesc("create_time");
            Page<ExternalContent> result = externalContentMapper.selectPage(page, wrapper);
            return Result.success(result);
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("event=external_content_admin_page_failed errorType={}", e.getClass().getSimpleName());
            return Result.error("Query failed");
        }
    }




    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PutMapping("/status")
    public Result<Void> updateStatus(@RequestParam Long id,
                                     @RequestParam Integer status,
                                     @RequestAttribute(value = "userId", required = false) Long userId) {
        try {
            checkAdmin(userId);
            if (id == null || !CONTENT_STATUSES.contains(status)) {
                return Result.error(400, "内容ID或状态不合法");
            }
            ExternalContent content = new ExternalContent();
            content.setId(id);
            content.setStatus(status);
            content.setUpdateTime(LocalDateTime.now());
            if (externalContentMapper.updateById(content) != 1) {
                return Result.error(404, "外部内容不存在");
            }
            return Result.success();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("event=external_content_status_update_failed errorType={}", e.getClass().getSimpleName());
            return Result.error("Update failed");
        }
    }




    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestAttribute(value = "userId", required = false) Long userId) {
        try {
            checkAdmin(userId);
            if (id == null || externalContentMapper.deleteById(id) != 1) {
                return Result.error(404, "外部内容不存在");
            }
            return Result.success();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("event=external_content_delete_failed errorType={}", e.getClass().getSimpleName());
            return Result.error("Delete failed");
        }
    }

    private ExternalContent toEntity(ExternalContentCreateDTO request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        ExternalContent content = new ExternalContent();
        content.setContentType(requireContentType(request.getContentType()));
        content.setExternalId(optionalText(request.getExternalId(), "externalId", 100));
        content.setTitle(requireText(request.getTitle(), "title", 200));
        content.setDescription(optionalText(request.getDescription(), "description", 4000));
        content.setThumbnail(optionalPublicUrl(request.getThumbnail(), "thumbnail", 500));
        List<String> images = request.getImages() == null ? Collections.emptyList() : request.getImages();
        if (images.size() > 10) {
            throw new IllegalArgumentException("images最多10项");
        }
        List<String> safeImages = new java.util.ArrayList<>();
        for (String image : images) {
            String safeImage = optionalPublicUrl(image, "images", 2048);
            if (safeImage == null) {
                throw new IllegalArgumentException("images包含空项");
            }
            safeImages.add(safeImage);
        }
        content.setImages(safeImages.isEmpty() ? null : objectMapper.writeValueAsString(safeImages));
        content.setExternalUrl(optionalPublicUrl(request.getExternalUrl(), "externalUrl", 500));
        content.setCategory(optionalText(request.getCategory(), "category", 100));
        content.setPlatform(optionalText(request.getPlatform(), "platform", 50));
        content.setReleaseDate(optionalText(request.getReleaseDate(), "releaseDate", 40));
        if (request.getRating() != null && (request.getRating() < 0 || request.getRating() > 10)) {
            throw new IllegalArgumentException("rating必须在0到10之间");
        }
        content.setRating(request.getRating());
        return content;
    }

    private ExternalContent findExistingSource(ExternalContent content) {
        if (content.getExternalId() == null) {
            return null;
        }
        return externalContentMapper.selectBySource(content.getContentType(), content.getExternalId());
    }

    private List<ExternalContentPublicVO> toPublicViews(List<ExternalContent> contents) {
        if (contents == null || contents.isEmpty()) {
            return Collections.emptyList();
        }
        return contents.stream().map(this::toPublicView).collect(Collectors.toList());
    }

    private ExternalContentPublicVO toPublicView(ExternalContent content) {
        ExternalContentPublicVO view = new ExternalContentPublicVO();
        view.setId(content.getId());
        view.setContentType(content.getContentType());
        view.setExternalId(content.getExternalId());
        view.setTitle(content.getTitle());
        view.setDescription(content.getDescription());
        view.setThumbnail(content.getThumbnail());
        view.setImages(parseImages(content.getImages()));
        view.setExternalUrl(content.getExternalUrl());
        view.setCategory(content.getCategory());
        view.setPlatform(content.getPlatform());
        view.setReleaseDate(content.getReleaseDate());
        view.setRating(content.getRating());
        view.setCreateTime(content.getCreateTime());
        return view;
    }

    private List<String> parseImages(String images) {
        if (images == null || images.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(images, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            log.warn("event=external_content_images_parse_rejected errorType={}",
                    e.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    private int normalizeLimit(Integer value, int defaultValue, int maxValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }

    private String requireContentType(String value) {
        String normalized = requireText(value, "contentType", 20).toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("内容类型不合法");
        }
        return normalized;
    }

    private String requireText(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "不合法");
        }
        return normalized;
    }

    private String optionalText(String value, String field, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "过长");
        }
        return normalized;
    }

    private String optionalPublicUrl(String value, String field, int maxLength) {
        String normalized = optionalText(value, field, maxLength);
        if (normalized != null && !ExternalUrlGuard.validate(normalized).isAllowed()) {
            throw new IllegalArgumentException(field + "不安全");
        }
        return normalized;
    }
}

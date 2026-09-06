   
                      
   
package com.haoran.music.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.aspect.RateLimitScope;
import com.haoran.music.common.result.Result;
import com.haoran.music.utils.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

   
              
  
                                       
   
@Slf4j
@RestController
@RequestMapping("/external")
public class ExternalProxyController {
    private static final int MAX_UPSTREAM_BYTES = 2 * 1024 * 1024;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final String KITSU_ANIME_ENDPOINT = "https://kitsu.io/api/edge/anime";

    @Autowired
    private ObjectMapper objectMapper;

       
                             
      
                         
                              
       
    private String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to encode URL parameter", e);
        }
    }

       
                               
       
    private int normalizeLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }

       
                                 
       
    private String requireQuery(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("查询参数长度不合法");
        }
        return normalized;
    }

       
                            
       
    private String optionalToken(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("筛选参数不合法");
        }
        return normalized;
    }

       
                                   
       
    @GetMapping("/itunes/search")
    @RateLimit(maxRequests = 15, timeWindowSeconds = 60, operation = "externalItunes",
            scope = RateLimitScope.GLOBAL, message = "外部搜索过于频繁，请稍后再试")
    public Result<String> searchItunes(
            @RequestParam String term,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "music") String media,
            @RequestParam(defaultValue = "song") String entity
    ) {
        try {
            if (!"music".equals(media) || !"song".equals(entity)) {
                return Result.error(400, "不支持的媒体查询类型");
            }
            String url = "https://itunes.apple.com/search?term=" + encode(requireQuery(term))
                    + "&media=music"
                    + "&entity=song"
                    + "&limit=" + normalizeLimit(limit, 20, 30)
                    + "&country=US";
            String response = HttpUtil.getJson(url, 5000, MAX_UPSTREAM_BYTES, "itunes.apple.com");
            return Result.successData(response);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("event=external_itunes_search_failed errorType={}", e.getClass().getSimpleName());
            return Result.error("音乐目录暂时不可用，请稍后再试");
        }
    }

       
                  
       
    @GetMapping("/anime/seasonal/now")
    @RateLimit(maxRequests = 20, timeWindowSeconds = 60, operation = "externalKitsu",
            scope = RateLimitScope.GLOBAL, message = "外部查询过于频繁，请稍后再试")
    public Result<String> getSeasonalAnime(
            @RequestParam(defaultValue = "24") Integer limit
    ) {
        try {
            int safeLimit = normalizeLimit(limit, 20, 20);
            LocalDate today = LocalDate.now();
            String url = KITSU_ANIME_ENDPOINT
                    + "?filter%5Bseason%5D=" + resolveAnimeSeason(today.getMonthValue()).toLowerCase(java.util.Locale.ROOT)
                    + "&filter%5BseasonYear%5D=" + today.getYear()
                    + "&sort=-userCount&include=categories&page%5Blimit%5D=" + safeLimit;
            String response = HttpUtil.getJson(url, 8000, MAX_UPSTREAM_BYTES, "kitsu.io");
            log.info("event=external_seasonal_anime_query_succeeded source=kitsu resultLimit={}", safeLimit);
            return Result.successData(toKitsuAnimeCatalogResponse(response));
        } catch (Exception e) {
            log.error("event=external_seasonal_anime_query_failed errorType={}", e.getClass().getSimpleName());
            return Result.error("本季动漫目录暂时不可用，请稍后再试");
        }
    }

    private String resolveAnimeSeason(int month) {
        if (month <= 3) return "WINTER";
        if (month <= 6) return "SPRING";
        if (month <= 9) return "SUMMER";
        return "FALL";
    }

       
                     
       
    @GetMapping("/anime/{id}")
    @RateLimit(maxRequests = 20, timeWindowSeconds = 60, operation = "externalKitsu",
            scope = RateLimitScope.GLOBAL, message = "外部查询过于频繁，请稍后再试")
    public Result<String> getAnime(@PathVariable Long id) {
        try {
            if (id == null || id <= 0) {
                return Result.error(400, "动漫ID不合法");
            }
            String response = HttpUtil.getJson(KITSU_ANIME_ENDPOINT + "/" + id + "?include=categories",
                    8000, MAX_UPSTREAM_BYTES, "kitsu.io");
            JsonNode catalog = objectMapper.readTree(toKitsuAnimeCatalogResponse(response));
            JsonNode anime = catalog.path("data").path(0);
            if (!anime.isObject()) {
                throw new IllegalStateException("invalid Kitsu detail response");
            }
            ObjectNode result = objectMapper.createObjectNode();
            result.set("data", anime);
            return Result.successData(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            log.error("event=external_anime_detail_query_failed errorType={}", e.getClass().getSimpleName());
            return Result.error("动漫详情暂时不可用，请稍后再试");
        }
    }

       
                  
       
    @GetMapping("/anime")
    @RateLimit(maxRequests = 20, timeWindowSeconds = 60, operation = "externalKitsu",
            scope = RateLimitScope.GLOBAL, message = "外部搜索过于频繁，请稍后再试")
    public Result<String> searchAnime(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        try {
            String safeQuery = requireQuery(q);
            int safeLimit = normalizeLimit(limit, 10, 20);
            String url = KITSU_ANIME_ENDPOINT
                    + "?filter%5Btext%5D=" + encode(safeQuery)
                    + "&include=categories&page%5Blimit%5D=" + safeLimit;
            String response = HttpUtil.getJson(url, 8000, MAX_UPSTREAM_BYTES, "kitsu.io");
            String catalogResponse = toKitsuAnimeCatalogResponse(response);
            log.info("event=external_anime_search_succeeded source=kitsu resultLimit={}", safeLimit);
            return Result.successData(catalogResponse);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("event=external_anime_search_failed errorType={}", e.getClass().getSimpleName());
            return Result.error("动漫目录暂时不可用，请稍后再试");
        }
    }

       
                                         
       
    String toKitsuAnimeCatalogResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode resourceData = root.path("data");
        if (!resourceData.isArray() && !resourceData.isObject()) {
            throw new IllegalStateException("invalid Kitsu response");
        }
        List<JsonNode> resources = new ArrayList<>();
        if (resourceData.isArray()) {
            resourceData.forEach(resources::add);
        } else {
            resources.add(resourceData);
        }

        java.util.Map<String, String> categories = new java.util.HashMap<>();
        for (JsonNode included : root.path("included")) {
            if ("categories".equals(included.path("type").asText())) {
                String name = firstNullableText(included.path("attributes").path("title"),
                        included.path("attributes").path("slug"));
                if (name != null) {
                    categories.put(included.path("id").asText(), name);
                }
            }
        }

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode data = result.putArray("data");
        for (JsonNode resource : resources) {
            JsonNode attributes = resource.path("attributes");
            ObjectNode item = data.addObject();
            item.put("mal_id", resource.path("id").asLong());
            JsonNode titles = attributes.path("titles");
            item.put("title", firstText(attributes.path("canonicalTitle"), titles.path("en_jp"),
                    titles.path("en"), titles.path("ja_jp")));
            item.put("title_english", textOrNull(titles.path("en")));
            item.put("title_japanese", textOrNull(titles.path("ja_jp")));
            item.put("synopsis", firstNullableText(attributes.path("synopsis"), attributes.path("description")));
            String imageUrl = firstNullableText(attributes.path("posterImage").path("large"),
                    attributes.path("posterImage").path("medium"),
                    attributes.path("posterImage").path("original"));
            ObjectNode images = item.putObject("images");
            images.putObject("jpg").put("image_url", imageUrl);
            images.putObject("webp").put("image_url", imageUrl);
            String slug = textOrNull(attributes.path("slug"));
            item.put("url", slug == null ? resource.path("links").path("self").asText()
                    : "https://kitsu.app/anime/" + slug);

            ArrayNode genres = item.putArray("genres");
            for (JsonNode category : resource.path("relationships").path("categories").path("data")) {
                String categoryName = categories.get(category.path("id").asText());
                if (categoryName != null) {
                    genres.addObject().put("name", categoryName);
                }
            }

            String rating = textOrNull(attributes.path("averageRating"));
            if (rating != null) {
                try {
                    item.put("score", Double.parseDouble(rating) / 10.0D);
                } catch (NumberFormatException ignored) {
                    item.putNull("score");
                }
            }
            if (attributes.path("episodeCount").isNumber()) {
                item.put("episodes", attributes.path("episodeCount").asInt());
            }
            item.put("status", normalizeKitsuAnimeStatus(textOrNull(attributes.path("status"))));
            String releaseDate = textOrNull(attributes.path("startDate"));
            if (releaseDate != null) {
                item.put("release_date", releaseDate);
            }
            item.put("source", "Kitsu");
        }
        return objectMapper.writeValueAsString(result);
    }

    private String normalizeKitsuAnimeStatus(String status) {
        if (status == null) return null;
        switch (status.toLowerCase(java.util.Locale.ROOT)) {
            case "current": return "RELEASING";
            case "finished": return "FINISHED";
            case "tba":
            case "unreleased":
            case "upcoming": return "NOT_YET_RELEASED";
            default: return status.toUpperCase(java.util.Locale.ROOT);
        }
    }

    private String firstText(JsonNode... values) {
        String text = firstNullableText(values);
        return text == null ? "未命名动漫" : text;
    }

    private String firstNullableText(JsonNode... values) {
        for (JsonNode value : values) {
            String text = textOrNull(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private String textOrNull(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

       
                            
       
    @GetMapping("/games")
    @RateLimit(maxRequests = 5, timeWindowSeconds = 1, operation = "externalFreeToGame",
            scope = RateLimitScope.GLOBAL, message = "外部查询过于频繁，请稍后再试")
    public Result<String> getGames(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String platform
    ) {
        try {
            String url = "https://www.freetogame.com/api/games";
            List<String> query = new ArrayList<>();
            String safeCategory = optionalToken(category);
            String safePlatform = optionalToken(platform);
            if (safeCategory != null) {
                query.add("category=" + encode(safeCategory));
            }
            if (safePlatform != null) {
                query.add("platform=" + encode(safePlatform));
            }
            if (!query.isEmpty()) {
                url += "?" + String.join("&", query);
            }
            String response = HttpUtil.getJson(url, 5000, MAX_UPSTREAM_BYTES, "www.freetogame.com");
            return Result.successData(response);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("event=external_game_list_query_failed errorType={}", e.getClass().getSimpleName());
            return Result.error("游戏目录暂时不可用，请稍后再试");
        }
    }

       
                       
       
    @GetMapping("/games/{id}")
    @RateLimit(maxRequests = 5, timeWindowSeconds = 1, operation = "externalFreeToGame",
            scope = RateLimitScope.GLOBAL, message = "外部查询过于频繁，请稍后再试")
    public Result<String> getGameDetail(@PathVariable Long id) {
        try {
            if (id == null || id <= 0) {
                return Result.error(400, "游戏ID不合法");
            }
            String url = "https://www.freetogame.com/api/game?id=" + id;
            String response = HttpUtil.getJson(url, 5000, MAX_UPSTREAM_BYTES, "www.freetogame.com");
            return Result.successData(response);
        } catch (Exception e) {
            log.error("event=external_game_detail_query_failed errorType={}", e.getClass().getSimpleName());
            return Result.error("游戏详情暂时不可用，请稍后再试");
        }
    }
}

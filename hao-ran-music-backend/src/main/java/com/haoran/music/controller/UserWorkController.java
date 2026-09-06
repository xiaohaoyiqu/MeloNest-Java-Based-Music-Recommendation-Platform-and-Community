package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.config.UserWorkRewardConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserWork;
import com.haoran.music.enums.ProductionType;
import com.haoran.music.enums.VersionType;
import com.haoran.music.common.util.AudioQualityDetector;
import java.util.ArrayList;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.IUserWorkService;
import com.haoran.music.service.SubmissionFileSecurityService;
import com.haoran.music.common.util.ZipUtil;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;





@Slf4j
@RestController
@RequestMapping("/user-work")
@RequiredArgsConstructor
public class UserWorkController {

    private final IUserWorkService userWorkService;
    private final AudioQualityDetector audioQualityDetector;
    private final ZipUtil zipUtil;
    private final SubmissionFileSecurityService submissionFileSecurityService;




    @ApiLog("提交用户投稿")
    @PostMapping("/submit")
    public Result submitWork(HttpServletRequest request,
                           @RequestBody UserWork userWork) {
        try {
            if (userWork == null) {
                return Result.error(400, "request body is required");
            }
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return Result.error(401, "login is required");
            }
            userWork.setUserId(userId);


            if (userWork.getWorkName() == null || userWork.getWorkName().trim().isEmpty()) {
                return Result.error(400, "workName is required");
            }
            if (userWork.getWorkName().length() > 200) {
                return Result.error(400, "作品名称不能超过50个字符");
            }
            if (userWork.getWorkType() == null || userWork.getWorkType() < 1 || userWork.getWorkType() > 4) {
                return Result.error(400, "请选择作品类型");
            }
            if (userWork.getCoverUrl() == null || userWork.getCoverUrl().trim().isEmpty()) {
                return Result.error(400, "请上传封面图片");
            }



            boolean hasFileUrl = userWork.getFileUrl() != null && !userWork.getFileUrl().trim().isEmpty();
            boolean hasFileUrls = userWork.getFileUrls() != null && !userWork.getFileUrls().trim().isEmpty();
            boolean hasZipFileUrl = userWork.getZipFileUrl() != null && !userWork.getZipFileUrl().trim().isEmpty();

            boolean albumLike = userWork.getWorkType() == 2 || userWork.getWorkType() == 3;
            if (albumLike) {
                if (!hasFileUrls) {
                    return Result.error(400, "album/EP submissions must include extracted fileUrls");
                }
            } else {
                if (!hasFileUrl) {
                    return Result.error(400, "single/remix submissions must include fileUrl");
                }
            }

            if (albumLike) {
                userWork.setUploadType(hasZipFileUrl ? 3 : 2);
            } else {
                userWork.setUploadType(1);
            }
            if (albumLike) {
                submissionFileSecurityService.validateAlbumFileUrls(userId, userWork.getFileUrls(), false);
            } else {
                submissionFileSecurityService.validateSingleAudioFile(userId, userWork.getFileUrl(), false);
            }

            if (userWork.getLanguage() == null || userWork.getLanguage() < 1 || userWork.getLanguage() > 5) {
                return Result.error(400, "请选择语言类型");
            }
            if (userWork.getDescription() == null || userWork.getDescription().trim().isEmpty()) {
                return Result.error(400, "请输入作品描述");
            }
            if (userWork.getDescription().length() > 1000) {
                return Result.error(400, "作品描述不能超过1000个字符");
            }


            String fileUrlToDetect = hasFileUrl ? userWork.getFileUrl() : null;
            if (fileUrlToDetect != null) {
                Integer detectedQuality = audioQualityDetector.detectQuality(fileUrlToDetect);
                userWork.setQualityType(detectedQuality);
            }


            if (userWork.getVersionType() == null || userWork.getVersionType().trim().isEmpty()) {
                userWork.setVersionType(VersionType.ORIGINAL.getCode());
            }


            if (userWork.getProductionType() == null || userWork.getProductionType().trim().isEmpty()) {
                userWork.setProductionType(ProductionType.OFFICIAL.getCode());
            }

            UserWork saved = userWorkService.submitWork(userWork);
            return Result.success("投稿提交成功，请等待审核", saved);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException | SecurityException e) {
            log.warn("event=user_work_submission_rejected errorType={}", e.getClass().getSimpleName());
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("event=user_work_submission_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "提交失败，请稍后重试");
        }
    }




    @ApiLog("获取我的投稿列表")
    @GetMapping("/my")
    public Result getMyWorks(HttpServletRequest request,
                          @RequestParam(required = false) Integer status) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return Result.error(401, "login is required");
            }
            List<UserWork> works = userWorkService.getMyWorks(userId);


            if (status != null) {
                works = works.stream()
                        .filter(w -> status.equals(w.getStatus()))
                        .collect(Collectors.toList());
            }

            return Result.success(works);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("event=user_work_list_query_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "获取投稿列表失败，请稍后重试");
        }
    }




    @ApiLog("获取投稿详情")
    @GetMapping("/{workId}")
    public Result getWorkDetail(@PathVariable Long workId,
                               HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return Result.error(401, "login is required");
            }
            UserWork work = userWorkService.getWorkDetail(workId);

            if (work == null) {
                return Result.error(404, "投稿不存在");
            }


            if (!work.getUserId().equals(userId)) {
                return Result.error(403, "无权查看此投稿");
            }

            return Result.success(work);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("event=user_work_detail_query_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "获取投稿详情失败，请稍后重试");
        }
    }




    @ApiLog("编辑投稿")
    @PutMapping("/{workId}")
    public Result updateWork(@PathVariable Long workId,
                           @RequestBody UserWork userWork,
                           HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return Result.error(401, "login is required");
            }
            if (userWork == null) {
                return Result.error(400, "request body is required");
            }
            userWork.setId(workId);
            userWork.setUserId(userId);

            boolean success = userWorkService.updateWork(userWork);
            if (success) {
                return Result.success(null, "修改成功");
            } else {
                return Result.error(500, "修改失败");
            }
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("event=user_work_update_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "修改失败，请稍后重试");
        }
    }




    @ApiLog("删除投稿")
    @DeleteMapping("/{workId}")
    public Result deleteWork(@PathVariable Long workId,
                           HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return Result.error(401, "login is required");
            }
            boolean success = userWorkService.deleteWork(workId, userId);
            if (success) {
                return Result.success(null, "删除成功");
            } else {
                return Result.error(500, "删除失败");
            }
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("event=user_work_delete_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "删除失败，请稍后重试");
        }
    }




    @ApiLog("获取待审核投稿列表")
    @GetMapping("/pending")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result getPendingWorks() {
        try {
            List<UserWork> works = userWorkService.getPendingWorks();
            return Result.success(works);
        } catch (Exception e) {
            log.error("event=user_work_pending_list_query_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "获取待审核投稿失败，请稍后重试");
        }
    }




    @ApiLog("审核用户投稿")
    @PostMapping("/{workId}/review")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result reviewWork(@PathVariable Long workId,
                            @RequestAttribute(value = "userId", required = false) Long reviewerId,
                            @RequestParam Integer status,
                            @RequestParam(required = false) String reviewReason) {
        try {
            if (reviewerId == null) {
                return Result.error(401, "Unauthorized");
            }
            if (status < 0 || status > 2) {
                return Result.error(400, "状态值无效");
            }

            boolean success = userWorkService.reviewWork(workId, reviewerId, status, reviewReason);
            if (success) {
                String statusText = status == 1 ? "通过" : (status == 2 ? "拒绝" : "待审核");
                return Result.success(null, "审核" + statusText);
            } else {
                return Result.error(500, "审核失败");
            }
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("event=user_work_review_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "审核失败，请稍后重试");
        }
    }




    @ApiLog("获取投稿统计")
    @GetMapping("/stats")
    public Result getStats(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return Result.error(401, "login is required");
            }
            List<UserWork> allWorks = userWorkService.getMyWorks(userId);

            int totalCount = allWorks.size();
            int pendingCount = (int) allWorks.stream().filter(w -> w.getStatus() == 0).count();
            int publishedCount = (int) allWorks.stream().filter(w -> w.getStatus() == 1).count();
            int rejectedCount = (int) allWorks.stream().filter(w -> w.getStatus() == 2).count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCount", totalCount);
            stats.put("pendingCount", pendingCount);
            stats.put("publishedCount", publishedCount);
            stats.put("rejectedCount", rejectedCount);

            return Result.success(stats);
        } catch (Exception e) {
            log.error("event=user_work_statistics_query_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "获取投稿统计失败，请稍后重试");
        }
    }




    @ApiLog("获取投稿奖励进度")
    @GetMapping("/{workId}/reward-progress")
    public Result getRewardProgress(@PathVariable Long workId,
                                    HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return Result.error(401, "login is required");
            }
            UserWork work = userWorkService.getWorkDetail(workId);

            if (work == null) {
                return Result.error(404, "投稿不存在");
            }


            if (!work.getUserId().equals(userId)) {
                return Result.error(403, "无权查看此投稿");
            }

            UserWorkRewardConfig.RewardProgress progress = userWorkService.getRewardProgress(workId);

            Map<String, Object> result = new HashMap<>();
            result.put("workId", workId);
            result.put("workName", work.getWorkName());
            result.put("status", work.getStatus());
            result.put("rewardPoints", work.getRewardPoints());
            result.put("progress", progress);

            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("event=user_work_reward_progress_query_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "获取奖励进度失败，请稍后重试");
        }
    }




    @ApiLog("获取奖励规则说明")
    @GetMapping("/reward-rules")
    public Result getRewardRules() {
        try {
            Map<String, Object> rules = new HashMap<>();


            Map<String, Object> levels = new HashMap<>();

            Map<String, Object> bronze = new HashMap<>();
            bronze.put("name", "青铜奖励");
            bronze.put("points", 10);
            bronze.put("likeRequired", 50);
            bronze.put("collectRequired", 10);
            bronze.put("playRequired", 200);
            levels.put("bronze", bronze);

            Map<String, Object> silver = new HashMap<>();
            silver.put("name", "白银奖励");
            silver.put("points", 20);
            silver.put("likeRequired", 100);
            silver.put("collectRequired", 20);
            silver.put("playRequired", 500);
            levels.put("silver", silver);

            Map<String, Object> gold = new HashMap<>();
            gold.put("name", "黄金奖励");
            gold.put("points", 50);
            gold.put("likeRequired", 200);
            gold.put("collectRequired", 50);
            gold.put("playRequired", 1000);
            levels.put("gold", gold);

            Map<String, Object> platinum = new HashMap<>();
            platinum.put("name", "铂金奖励");
            platinum.put("points", 100);
            platinum.put("likeRequired", 500);
            platinum.put("collectRequired", 100);
            platinum.put("playRequired", 5000);
            levels.put("platinum", platinum);

            rules.put("levels", levels);
            rules.put("description", "发布作品后，根据作品的点赞、收藏、播放数据自动获得奖励积分");
            rules.put("note", "奖励积分在审核通过时根据当前数据发放，后续数据增长不会补发积分");

            return Result.success(rules);
        } catch (Exception e) {
            log.error("event=user_work_reward_rule_query_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "获取奖励规则失败，请稍后重试");
        }
    }



    @ApiLog("获取版本类型选项")
    @GetMapping("/version-types")
    public Result getVersionTypes() {
        try {
            Map<String, Object> result = new HashMap<>();


            List<Map<String, String>> versionTypes = new ArrayList<>();
            for (VersionType type : VersionType.values()) {
                Map<String, String> item = new HashMap<>();
                item.put("code", type.getCode());
                item.put("name", type.getName());
                versionTypes.add(item);
            }
            result.put("versionTypes", versionTypes);


            List<Map<String, String>> productionTypes = new ArrayList<>();
            for (ProductionType type : ProductionType.values()) {
                Map<String, String> item = new HashMap<>();
                item.put("code", type.getCode());
                item.put("name", type.getName());
                productionTypes.add(item);
            }
            result.put("productionTypes", productionTypes);


            List<Map<String, Object>> qualityTypes = new ArrayList<>();
Map<String, Object> q1 = new HashMap<>(); q1.put("code", 1); q1.put("name", "标准音质"); qualityTypes.add(q1);            Map<String, Object> q2 = new HashMap<>(); q2.put("code", 2); q2.put("name", "高品质"); qualityTypes.add(q2);            Map<String, Object> q3 = new HashMap<>(); q3.put("code", 3); q3.put("name", "无损音质"); qualityTypes.add(q3);
            result.put("qualityTypes", qualityTypes);

            return Result.success(result);
        } catch (Exception e) {
            log.error("event=user_work_version_option_query_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "获取版本类型失败，请稍后重试");
        }
    }

}

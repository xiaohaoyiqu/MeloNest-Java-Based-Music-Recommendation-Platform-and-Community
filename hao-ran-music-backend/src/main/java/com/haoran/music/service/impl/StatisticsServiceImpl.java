package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.haoran.music.common.constant.PublicStatsSql;
import com.haoran.music.common.util.PaymentOrderStatusUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.*;
import com.haoran.music.service.StatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

   
                      
                             
   
@Slf4j
@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private SongMapper songMapper;

    @Resource
    private AlbumMapper albumMapper;

    @Resource
    private MVMapper mvMapper;

    @Resource
    private PlaylistMapper playlistMapper;

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private UserFavoriteMapper userFavoriteMapper;

    @Resource
    private SongPlayRecordMapper songPlayRecordMapper;

    @Resource
    private CreatorMapper creatorMapper;

    @Resource
    private PaymentOrderMapper paymentOrderMapper;

    @Resource
    private SongLikeMapper songLikeMapper;

    @Resource
    private ShareRecordMapper shareRecordMapper;

    @Resource
    private MusicPostMapper musicPostMapper;

    @Resource
    private ModerationMapper moderationMapper;

    @Resource
    private ModerationRecordMapper moderationRecordMapper;

    @Resource
    private ArtistApplicationMapper artistApplicationMapper;

    @Resource
    private LyricRequestMapper lyricRequestMapper;

    @Resource
    private CreatorWorkMapper creatorWorkMapper;

    @Resource
    private UserStatisticsMapper userStatisticsMapper;

    @Resource
    private ListenHistoryMapper listenHistoryMapper;

    @Override
    public StatisticsService.PlatformOverviewVO getOverview() {
        StatisticsService.PlatformOverviewVO result = new StatisticsService.PlatformOverviewVO();

                                           
        LambdaQueryWrapper<User> totalUserWrapper = new LambdaQueryWrapper<>();
        totalUserWrapper.eq(User::getDeleted, 0);
        Long totalUsers = userMapper.selectCount(totalUserWrapper);
        result.setTotalUsers(totalUsers);
        Long publicUsers = userMapper.selectCount(UserAccountStatusUtil.publicStatsUserQuery());
        result.setPublicUsers(publicUsers);
        result.setRestrictedUsers(Math.max(0L, totalUsers - publicUsers));

                 
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getDeleted, 0)
                .ge(User::getCreateTime, todayStart);
        Long todayNewUsers = userMapper.selectCount(userWrapper);
        result.setTodayNewUsers(todayNewUsers);

               
        Long totalSongs = songMapper.selectCount(null);
        result.setTotalSongs(totalSongs);

               
        result.setTotalMvs(mvMapper.selectCount(null));

                
        QueryWrapper<SongPlayRecord> totalPlayWrapper = new QueryWrapper<>();
        totalPlayWrapper.apply(publicActorExists("user_id"));
        Long totalPlays = songPlayRecordMapper.selectCount(totalPlayWrapper);
        result.setTotalPlays(totalPlays);

                 
        LambdaQueryWrapper<SongPlayRecord> playWrapper = new LambdaQueryWrapper<>();
        playWrapper.ge(SongPlayRecord::getCreateTime, todayStart)
                .apply(publicActorExists("user_id"));
        Long todayPlays = songPlayRecordMapper.selectCount(playWrapper);
        result.setTodayPlays(todayPlays);

               
        QueryWrapper<Comment> totalCommentWrapper = new QueryWrapper<>();
        totalCommentWrapper.apply(publicActorExists("user_id"));
        Long totalComments = commentMapper.selectCount(totalCommentWrapper);
        result.setTotalComments(totalComments);

                                                  
                                     
        Long songFavorites = songLikeMapper.countPublicActiveSongFavorites();
        Long nonSongFavorites = userFavoriteMapper.countPublicActiveNonSongFavorites();
        result.setTotalFavorites(defaultLong(songFavorites) + defaultLong(nonSongFavorites));

                                              
        LambdaQueryWrapper<User> vipWrapper = UserAccountStatusUtil.interactableUserQuery()
                .inSql(User::getId,
                        "SELECT user_id FROM user_vip WHERE vip_status = 1 " +
                                "AND vip_expire_time > NOW() AND deleted = 0");
        Long vipUsers = userMapper.selectCount(vipWrapper);
        result.setVipUsers(vipUsers);

                                                 
        QueryWrapper<PaymentOrder> paymentWrapper = new QueryWrapper<>();
        paymentWrapper.select("COALESCE(SUM(amount), 0) AS today_amount")
                .ge("review_time", todayStart)
                .lt("review_time", LocalDateTime.now());
        PaymentOrderStatusUtil.applyPaidOrderFilter(paymentWrapper);
        List<Map<String, Object>> paymentRows = paymentOrderMapper.selectMaps(paymentWrapper);
        BigDecimal todayRevenue = paymentRows.isEmpty()
                ? BigDecimal.ZERO
                : toBigDecimal(paymentRows.get(0).get("today_amount"));
        result.setTodayRevenue(todayRevenue.longValue());

        return result;
    }

    @Override
    public List<StatisticsService.TrendDataVO> getUserTrend(Date startDate, Date endDate, String interval) {
        List<StatisticsService.TrendDataVO> result = new ArrayList<>();

        if (startDate == null || endDate == null || startDate.after(endDate)) {
            return result;
        }

                            
        LocalDate start = startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate end = endDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

                           
        int daysToAdd = "week".equals(interval) ? 7 : "month".equals(interval) ? 30 : 1;

        LambdaQueryWrapper<User> wrapper = UserAccountStatusUtil.publicStatsUserQuery()
                .select(User::getCreateTime)
                .ge(User::getCreateTime, start.atStartOfDay())
                .lt(User::getCreateTime, end.plusDays(1).atStartOfDay());
        List<User> users = userMapper.selectList(wrapper);
        Map<Integer, Long> countsByBucket = new HashMap<>();
        for (User user : users) {
            if (user.getCreateTime() == null) {
                continue;
            }
            long daysSinceStart = ChronoUnit.DAYS.between(start, user.getCreateTime().toLocalDate());
            int bucket = (int) (daysSinceStart / daysToAdd);
            countsByBucket.merge(bucket, 1L, Long::sum);
        }

        LocalDate currentDate = start;
        int bucket = 0;
        Long previousCount = 0L;

        while (!currentDate.isAfter(end)) {
            Long count = countsByBucket.getOrDefault(bucket, 0L);

            StatisticsService.TrendDataVO vo = new StatisticsService.TrendDataVO();
            vo.setDate(currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            vo.setCount(count);

                    
            if (previousCount > 0) {
                double growthRate = ((double) (count - previousCount) / previousCount) * 100;
                vo.setGrowthRate(Math.round(growthRate * 100.0) / 100.0);
            } else {
                vo.setGrowthRate(0.0);
            }

            result.add(vo);
            previousCount = count;
            currentDate = currentDate.plusDays(daysToAdd);
            bucket++;
        }

        return result;
    }

    @Override
    public StatisticsService.ContentStatisticsVO getContentStatistics() {
        StatisticsService.ContentStatisticsVO result = new StatisticsService.ContentStatisticsVO();

               
        result.setSongCount(songMapper.selectCount(null));

               
        result.setAlbumCount(albumMapper.selectCount(null));

               
        result.setMvCount(mvMapper.selectCount(null));

               
        result.setPlaylistCount(playlistMapper.selectCount(null));

                
        LambdaQueryWrapper<Creator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Creator::getStatus, "active")
                .eq(Creator::getDeleted, 0);                
        result.setCreatorCount(creatorMapper.selectCount(wrapper));

        return result;
    }

       
       
    @Override
    public StatisticsService.InteractionStatisticsVO getInteractionStatistics() {
        StatisticsService.InteractionStatisticsVO result = new StatisticsService.InteractionStatisticsVO();

        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

                
        LambdaQueryWrapper<Comment> commentWrapper = new LambdaQueryWrapper<>();
        commentWrapper.ge(Comment::getCreateTime, todayStart)
                .apply(publicActorExists("user_id"));
        result.setTodayComments(commentMapper.selectCount(commentWrapper));

                                           
        LambdaQueryWrapper<SongLike> likeWrapper = new LambdaQueryWrapper<>();
        likeWrapper.eq(SongLike::getIsLike, 1)
                .ge(SongLike::getCreateTime, todayStart)
                .apply(publicActorExists("user_id"));
        Long todayLikes = songLikeMapper.selectCount(likeWrapper);
        result.setTodayLikes(todayLikes != null ? todayLikes : 0L);

                
        LambdaQueryWrapper<UserFavorite> favoriteWrapper = new LambdaQueryWrapper<>();
        favoriteWrapper.ge(UserFavorite::getCreateTime, todayStart)
                .apply(publicActorExists("user_id"));
        result.setTodayFavorites(userFavoriteMapper.selectCount(favoriteWrapper));

                                  
        LambdaQueryWrapper<ShareRecord> shareWrapper = new LambdaQueryWrapper<>();
        shareWrapper.ge(ShareRecord::getCreateTime, todayStart)
                .apply(publicActorExists("user_id"));
        Long todayShares = shareRecordMapper.selectCount(shareWrapper);
        result.setTodayShares(todayShares != null ? todayShares : 0L);

        return result;
    }

    @Override
    public List<StatisticsService.RevenueDataVO> getRevenue(Date startDate, Date endDate, String interval) {
        List<StatisticsService.RevenueDataVO> result = new ArrayList<>();

        LocalDate start = startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate end = endDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        int daysToAdd = "week".equals(interval) ? 7 : "month".equals(interval) ? 30 : 1;
        QueryWrapper<PaymentOrder> paymentWrapper = new QueryWrapper<>();
        paymentWrapper.select("business_type", "amount", "review_time")
                .eq("status", "paid")
                .eq("deleted", 0)
                .ge("review_time", start.atStartOfDay())
                .lt("review_time", end.plusDays(1).atStartOfDay());
        List<Map<String, Object>> paymentRows = paymentOrderMapper.selectMaps(paymentWrapper);

        Map<Integer, BigDecimal> vipRevenueByBucket = new HashMap<>();
        Map<Integer, BigDecimal> contentRevenueByBucket = new HashMap<>();
        for (Map<String, Object> row : paymentRows) {
            LocalDateTime reviewTime = toLocalDateTime(row.get("review_time"));
            if (reviewTime == null) {
                continue;
            }
            long daysSinceStart = ChronoUnit.DAYS.between(start, reviewTime.toLocalDate());
            if (daysSinceStart < 0) {
                continue;
            }
            int bucket = (int) (daysSinceStart / daysToAdd);
            BigDecimal amount = toBigDecimal(row.get("amount"));
            String businessType = String.valueOf(row.get("business_type"));
            if ("vip".equalsIgnoreCase(businessType)) {
                vipRevenueByBucket.merge(bucket, amount, BigDecimal::add);
            } else {
                contentRevenueByBucket.merge(bucket, amount, BigDecimal::add);
            }
        }

        LocalDate currentDate = start;
        int bucket = 0;
        while (!currentDate.isAfter(end)) {
            StatisticsService.RevenueDataVO vo = new StatisticsService.RevenueDataVO();
            vo.setDate(currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            long vipRevenue = vipRevenueByBucket.getOrDefault(bucket, BigDecimal.ZERO).longValue();
            long contentRevenue = contentRevenueByBucket.getOrDefault(bucket, BigDecimal.ZERO).longValue();
            vo.setAmount(vipRevenue + contentRevenue);
            vo.setVipRevenue(vipRevenue);
            vo.setContentRevenue(contentRevenue);

            result.add(vo);
            currentDate = currentDate.plusDays(daysToAdd);
            bucket++;
        }

        return result;
    }

    @Override
    public List<StatisticsService.HotSongVO> getHotSongs(Integer limit) {
        int actualLimit = limit != null && limit > 0 ? Math.min(limit, 100) : 10;
        int candidateLimit = Math.min(actualLimit * 3, 300);

                         
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0)
                .orderByDesc(Song::getPlayCount)
                .last("LIMIT " + candidateLimit);

        List<Song> songs = filterPublicSongs(songMapper.selectList(wrapper));

        List<StatisticsService.HotSongVO> result = new ArrayList<>();
        for (int i = 0; i < songs.size() && result.size() < actualLimit; i++) {
            Song song = songs.get(i);
            StatisticsService.HotSongVO vo = new StatisticsService.HotSongVO();
            vo.setId(song.getId());
            vo.setName(song.getName());
            vo.setArtistName(song.getArtistNames() != null ? song.getArtistNames() : "未知歌手");
            vo.setCover(song.getCover());
            vo.setPlayCount(song.getPlayCount() != null ? song.getPlayCount() : 0L);
            vo.setRank(i + 1);
            result.add(vo);
        }

        return result;
    }

       
       
    @Override
    public List<StatisticsService.HotCreatorVO> getHotCreators(Integer limit) {
        int actualLimit = limit != null && limit > 0 ? Math.min(limit, 100) : 10;
        int candidateLimit = Math.min(actualLimit * 3, 300);

                              
        LambdaQueryWrapper<Creator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Creator::getStatus, "active")
                .eq(Creator::getDeleted, 0)                
                .orderByDesc(Creator::getFansCount)
                .last("LIMIT " + candidateLimit);

        List<Creator> creators = creatorMapper.selectList(wrapper);
        Set<Long> creatorUserIds = creators.stream()
                .map(Creator::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = creatorUserIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(creatorUserIds).stream()
                .filter(UserAccountStatusUtil::canExposePublicContent)
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        creators = creators.stream()
                .filter(creator -> userMap.containsKey(creator.getUserId()))
                .limit(actualLimit)
                .collect(Collectors.toList());

        Map<Long, Long> workCountMap = countPublicWorksByUserIds(
                creators.stream().map(Creator::getUserId).collect(Collectors.toSet()));

        List<StatisticsService.HotCreatorVO> result = new ArrayList<>();
        for (int i = 0; i < creators.size(); i++) {
            Creator creator = creators.get(i);
            StatisticsService.HotCreatorVO vo = new StatisticsService.HotCreatorVO();
            vo.setId(creator.getUserId());

                               
            User user = userMap.get(creator.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname() != null ? user.getNickname() : "用户" + creator.getUserId());
                vo.setAvatar(user.getAvatar() != null ? user.getAvatar() : "");
            } else {
                vo.setNickname("创作者" + creator.getUserId());
            }

            vo.setFansCount(creator.getFansCount() != null ? creator.getFansCount() : 0L);

            vo.setWorkCount(workCountMap.getOrDefault(creator.getUserId(), 0L));

                                             
            vo.setTotalPlays(0L);
            vo.setRank(i + 1);
            result.add(vo);
        }

        return result;
    }

    private List<Song> filterPublicSongs(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> uploaderIds = songs.stream()
                .map(Song::getUploaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allowedUploaderIds = uploaderIds.isEmpty()
                ? Collections.emptySet()
                : userMapper.selectBatchIds(uploaderIds).stream()
                .filter(UserAccountStatusUtil::canExposePublicContent)
                .map(User::getId)
                .collect(Collectors.toSet());

        return songs.stream()
                .filter(song -> song.getUploaderId() == null
                        || allowedUploaderIds.contains(song.getUploaderId()))
                .collect(Collectors.toList());
    }

    private Map<Long, Long> countPublicWorksByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        QueryWrapper<MusicPost> wrapper = new QueryWrapper<>();
        wrapper.select("user_id", "COUNT(*) AS work_count")
                .in("user_id", userIds)
                .eq("is_deleted", false)
                .groupBy("user_id");

        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : musicPostMapper.selectMaps(wrapper)) {
            Object userIdValue = row.get("user_id");
            Object countValue = row.get("work_count");
            if (userIdValue != null && countValue instanceof Number) {
                result.put(((Number) userIdValue).longValue(), ((Number) countValue).longValue());
            }
        }
        return result;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return BigDecimal.ZERO;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        return null;
    }

    private String publicActorExists(String userColumn) {
        return "EXISTS (" + PublicStatsSql.USER_EXISTS_PREFIX + userColumn
                + PublicStatsSql.USER_FILTER + ")";
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

                                                       

    @Override
    public Map<String, Object> getAuditOverview(String startTime, String endTime, Long moderatorId) {
        Map<String, Object> overview = new HashMap<String, Object>();
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);

        List<ModerationRecord> records = queryModerationRecordsByCreateTime(start, end, moderatorId);
        overview.put("totalAudits", records.size());
        overview.put("pendingAudits", countStatus(records, "pending"));
        overview.put("approvedAudits", countStatus(records, "approved"));
        overview.put("rejectedAudits", countStatus(records, "rejected"));
        overview.put("avgProcessTime", averageProcessTime(records));

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayAudits = records.stream()
                .filter(this::isCompletedRecord)
                .filter(record -> record.getReviewTime() != null && !record.getReviewTime().isBefore(todayStart))
                .count();
        overview.put("todayAudits", todayAudits);
        return overview;
    }

    @Override
    public Map<String, Object> getRealtimeAuditStatus(Long moderatorId) {
        Map<String, Object> status = new HashMap<String, Object>();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        status.put("pendingCount", countRecordsByStatus("pending", moderatorId));
        status.put("processingCount", countRecordsByStatus("in_progress", moderatorId));
        status.put("completedToday", countCompletedAfter(todayStart, moderatorId));
        status.put("avgProcessTime", averageProcessTime(queryCompletedRecordsAfter(todayStart, moderatorId)));
        return status;
    }

    @Override
    public List<Map<String, Object>> getModeratorRanking(String startTime, String endTime, Long moderatorId) {
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);

        LambdaQueryWrapper<ModerationRecord> wrapper = new LambdaQueryWrapper<ModerationRecord>();
        wrapper.in(ModerationRecord::getStatus, Arrays.asList("approved", "rejected"))
                .isNotNull(ModerationRecord::getReviewerId);
        if (moderatorId != null) {
            wrapper.eq(ModerationRecord::getReviewerId, moderatorId);
        }
        if (start != null) {
            wrapper.ge(ModerationRecord::getReviewTime, start);
        }
        if (end != null) {
            wrapper.le(ModerationRecord::getReviewTime, end);
        }

        List<ModerationRecord> audits = moderationRecordMapper.selectList(wrapper);
        Map<Long, List<ModerationRecord>> byReviewer = audits.stream()
                .collect(java.util.stream.Collectors.groupingBy(ModerationRecord::getReviewerId));

        List<Map<String, Object>> ranking = new ArrayList<Map<String, Object>>();
        for (Map.Entry<Long, List<ModerationRecord>> entry : byReviewer.entrySet()) {
            Long reviewerId = entry.getKey();
            List<ModerationRecord> reviewerAudits = entry.getValue();
            User reviewer = userMapper.selectById(reviewerId);

            Map<String, Object> stat = new HashMap<String, Object>();
            stat.put("moderatorId", String.valueOf(reviewerId));
            stat.put("moderatorName", getUserDisplayName(reviewer, reviewerId));
            stat.put("role", reviewer == null ? null : UserRole.fromCode(reviewer.getRole()).getCode());
            stat.put("isAdmin", reviewer != null && UserRole.isAdmin(reviewer.getRole()));
            stat.put("isModerator", reviewer != null && isModeratorUser(reviewer));
            stat.put("totalAudits", reviewerAudits.size());
            stat.put("approvedCount", countStatus(reviewerAudits, "approved"));
            stat.put("rejectedCount", countStatus(reviewerAudits, "rejected"));
            stat.put("avgProcessTime", averageProcessTime(reviewerAudits));
            ranking.add(stat);
        }

        ranking.sort((a, b) -> {
            Number countA = (Number) a.get("totalAudits");
            Number countB = (Number) b.get("totalAudits");
            return Long.compare(countB.longValue(), countA.longValue());
        });
        return ranking;
    }

    @Override
    public Map<String, Object> getAuditTypeStats(String startTime, String endTime, Long moderatorId) {
        Map<String, Object> stats = new HashMap<String, Object>();
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        List<ModerationRecord> records = queryModerationRecordsByCreateTime(start, end, moderatorId);

        Map<String, Long> byType = records.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        record -> normalizeKey(record.getTargetType()),
                        java.util.stream.Collectors.counting()
                ));
        Map<String, Long> byStatus = records.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        record -> normalizeKey(record.getStatus()),
                        java.util.stream.Collectors.counting()
                ));

        stats.put("byType", byType);
        stats.put("byStatus", byStatus);
        stats.put("totalTypes", byType.size());
        return stats;
    }

    @Override
    public List<Map<String, Object>> getAuditTrend(Integer days, Long moderatorId) {
        int safeDays = days == null ? 30 : Math.max(1, Math.min(days, 365));
        List<Map<String, Object>> trend = new ArrayList<Map<String, Object>>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = safeDays - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
            List<ModerationRecord> dayRecords = queryModerationRecordsByCreateTime(dayStart, dayEnd, moderatorId);

            Map<String, Object> point = new HashMap<String, Object>();
            point.put("date", date.format(formatter));
            point.put("totalCount", dayRecords.size());
            point.put("approvedCount", countStatus(dayRecords, "approved"));
            point.put("rejectedCount", countStatus(dayRecords, "rejected"));
            point.put("avgProcessTime", averageProcessTime(dayRecords));
            trend.add(point);
        }
        return trend;
    }

    private List<ModerationRecord> queryModerationRecordsByCreateTime(LocalDateTime start,
                                                                       LocalDateTime end,
                                                                       Long moderatorId) {
        LambdaQueryWrapper<ModerationRecord> wrapper = new LambdaQueryWrapper<ModerationRecord>();
        if (start != null) {
            wrapper.ge(ModerationRecord::getCreateTime, start);
        }
        if (end != null) {
            wrapper.le(ModerationRecord::getCreateTime, end);
        }
        applyModeratorScope(wrapper, moderatorId);
        return moderationRecordMapper.selectList(wrapper);
    }

    private List<ModerationRecord> queryCompletedRecordsAfter(LocalDateTime start, Long moderatorId) {
        LambdaQueryWrapper<ModerationRecord> wrapper = new LambdaQueryWrapper<ModerationRecord>();
        wrapper.in(ModerationRecord::getStatus, Arrays.asList("approved", "rejected"))
                .isNotNull(ModerationRecord::getReviewTime)
                .ge(ModerationRecord::getReviewTime, start);
        if (moderatorId != null) {
            wrapper.eq(ModerationRecord::getReviewerId, moderatorId);
        }
        return moderationRecordMapper.selectList(wrapper);
    }

    private long countRecordsByStatus(String status, Long moderatorId) {
        LambdaQueryWrapper<ModerationRecord> wrapper = new LambdaQueryWrapper<ModerationRecord>();
        wrapper.eq(ModerationRecord::getStatus, status);
        if (moderatorId != null) {
            if ("approved".equals(status) || "rejected".equals(status)) {
                wrapper.eq(ModerationRecord::getReviewerId, moderatorId);
            } else {
                wrapper.eq(ModerationRecord::getAssignedModeratorId, moderatorId);
            }
        }
        Long count = moderationRecordMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private long countCompletedAfter(LocalDateTime start, Long moderatorId) {
        LambdaQueryWrapper<ModerationRecord> wrapper = new LambdaQueryWrapper<ModerationRecord>();
        wrapper.in(ModerationRecord::getStatus, Arrays.asList("approved", "rejected"))
                .ge(ModerationRecord::getReviewTime, start);
        if (moderatorId != null) {
            wrapper.eq(ModerationRecord::getReviewerId, moderatorId);
        }
        Long count = moderationRecordMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private void applyModeratorScope(LambdaQueryWrapper<ModerationRecord> wrapper, Long moderatorId) {
        if (moderatorId != null) {
            wrapper.and(item -> item.eq(ModerationRecord::getAssignedModeratorId, moderatorId)
                    .or()
                    .eq(ModerationRecord::getReviewerId, moderatorId));
        }
    }

    private long countStatus(List<ModerationRecord> records, String status) {
        return records.stream()
                .filter(record -> status.equals(record.getStatus()))
                .count();
    }

    private boolean isCompletedRecord(ModerationRecord record) {
        return "approved".equals(record.getStatus()) || "rejected".equals(record.getStatus());
    }

    private double averageProcessTime(List<ModerationRecord> records) {
        return records.stream()
                .filter(this::isCompletedRecord)
                .filter(record -> record.getAssignedTime() != null && record.getReviewTime() != null)
                .mapToLong(record -> java.time.Duration.between(record.getAssignedTime(), record.getReviewTime()).toMillis())
                .average()
                .orElse(0.0);
    }

    private boolean isModeratorUser(User user) {
        return UserRole.canModerate(user.getRole()) || Integer.valueOf(1).equals(user.getIsModerator());
    }

    private String getUserDisplayName(User user, Long userId) {
        if (user != null && user.getNickname() != null && !user.getNickname().isEmpty()) {
            return user.getNickname();
        }
        if (user != null && user.getUsername() != null && !user.getUsername().isEmpty()) {
            return user.getUsername();
        }
        return "审核员" + userId;
    }

    private String normalizeKey(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        return value;
    }
                                                     

       
                
       
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr + " 00:00:00",
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateTimeStr,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e2) {
                log.warn("解析日期时间失败: {}", dateTimeStr);
                return null;
            }
        }
    }
}

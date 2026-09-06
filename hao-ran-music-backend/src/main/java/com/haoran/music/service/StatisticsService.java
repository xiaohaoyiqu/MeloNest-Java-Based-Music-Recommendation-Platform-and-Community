package com.haoran.music.service;


import java.util.Date;
import java.util.List;
import java.util.Map;

   
                      
                             
   
public interface StatisticsService {

       
               
       
    PlatformOverviewVO getOverview();

       
               
       
    List<TrendDataVO> getUserTrend(Date startDate, Date endDate, String interval);

       
               
       
    ContentStatisticsVO getContentStatistics();

       
               
       
    InteractionStatisticsVO getInteractionStatistics();

       
               
       
    List<RevenueDataVO> getRevenue(Date startDate, Date endDate, String interval);

       
               
       
    List<HotSongVO> getHotSongs(Integer limit);

       
                
       
    List<HotCreatorVO> getHotCreators(Integer limit);

                                                     

       
               
                            
                          
                     
       
    Map<String, Object> getAuditOverview(String startTime, String endTime, Long moderatorId);

       
               
                     
       
    Map<String, Object> getRealtimeAuditStatus(Long moderatorId);

       
                 
                            
                          
                      
       
    List<Map<String, Object>> getModeratorRanking(String startTime, String endTime, Long moderatorId);

       
               
                            
                          
                     
       
    Map<String, Object> getAuditTypeStats(String startTime, String endTime, Long moderatorId);

       
               
                       
                     
       
    List<Map<String, Object>> getAuditTrend(Integer days, Long moderatorId);

       
             
       
    @lombok.Data
    public static class PlatformOverviewVO {
        private Long totalUsers;
        private Long publicUsers;
        private Long restrictedUsers;
        private Long todayNewUsers;
        private Long totalSongs;
        private Long totalMvs;
        private Long totalPlays;
        private Long todayPlays;
        private Long totalComments;
        private Long totalFavorites;
        private Long vipUsers;
        private Long todayRevenue;
    }

       
             
       
    @lombok.Data
    public static class TrendDataVO {
        private String date;
        private Long count;
        private Double growthRate;
    }

       
             
       
    @lombok.Data
    public static class ContentStatisticsVO {
        private Long songCount;
        private Long albumCount;
        private Long mvCount;
        private Long playlistCount;
        private Long creatorCount;
    }

       
             
       
    @lombok.Data
    public static class InteractionStatisticsVO {
        private Long todayComments;
        private Long todayLikes;
        private Long todayFavorites;
        private Long todayShares;
    }

       
             
       
    @lombok.Data
    public static class RevenueDataVO {
        private String date;
        private Long amount;
        private Long vipRevenue;
        private Long contentRevenue;
    }

       
             
       
    @lombok.Data
    public static class HotSongVO {
        private Long id;
        private String name;
        private String artistName;
        private String cover;
        private Long playCount;
        private Integer rank;
    }

       
              
       
    @lombok.Data
    public static class HotCreatorVO {
        private Long id;
        private String nickname;
        private String avatar;
        private Long fansCount;
        private Long workCount;
        private Long totalPlays;
        private Integer rank;
    }
}

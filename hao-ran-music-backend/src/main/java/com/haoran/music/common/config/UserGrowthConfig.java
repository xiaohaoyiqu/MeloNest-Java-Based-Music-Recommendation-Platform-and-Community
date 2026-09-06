



package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;




@Data
@Component
@ConfigurationProperties(prefix = "user.growth")
public class UserGrowthConfig {




    private Checkin checkin = new Checkin();




    private Points points = new Points();




    private Activity activity = new Activity();




    private Statistics statistics = new Statistics();

    @Data
    public static class Checkin {
        private Integer dailyPoints = 5;
        private Integer weeklyBonus = 15;
        private Integer weeklyVipDays = 5;
        private Integer monthlyBonus = 100;
        private Integer makeupWindowDays = 7;
        private Integer makeupCostPoints = 10;
    }

    @Data
    public static class Points {
        private Integer dailyLimit = 100;
        private Integer checkinPoints = 10;
        private Integer listenPoints = 1;
        private Integer sharePoints = 5;
        private Integer commentPoints = 2;
        private Integer expDivisor = 10;
        private Integer extendedLevelStepExp = 1000;
        private List<Integer> levelExpThresholds = new ArrayList<>(Arrays.asList(
                100, 300, 600, 1000, 1500, 2100, 2800, 3600, 4500, 5500
        ));
        private Map<Integer, String> levelTitles = defaultLevelTitles();





        public int resolveNextLevelExp(int level) {
            int step = extendedLevelStepExp == null || extendedLevelStepExp <= 0 ? 1000 : extendedLevelStepExp;
            if (levelExpThresholds == null || levelExpThresholds.isEmpty()) {
                return Math.max(level, 1) * step;
            }
            if (level <= levelExpThresholds.size()) {
                return levelExpThresholds.get(Math.max(level - 1, 0));
            }
            int lastThreshold = levelExpThresholds.get(levelExpThresholds.size() - 1);
            return lastThreshold + (level - levelExpThresholds.size()) * step;
        }





        public String resolveLevelTitle(int level) {
            if (levelTitles == null || levelTitles.isEmpty()) {
                return "expert";
            }
            for (Map.Entry<Integer, String> entry : levelTitles.entrySet()) {
                if (level <= entry.getKey()) {
                    return entry.getValue();
                }
            }
            return "expert";
        }

        private static Map<Integer, String> defaultLevelTitles() {
            Map<Integer, String> titles = new LinkedHashMap<>();
            titles.put(2, "rookie");
            titles.put(4, "junior");
            titles.put(6, "intermediate");
            titles.put(8, "senior");
            titles.put(10, "expert");
            return titles;
        }
    }

    @Data
    public static class Activity {
        private String cacheKeyPrefix = "activity:score:";
        private Long cacheExpireMinutes = 30L;
        private Integer maxScore = 100;
        private Integer monthCheckinMaxScore = 50;
        private Integer monthCheckinPerDayScore = 2;
        private Integer continuousMaxScore = 20;
        private Integer continuousPerDayScore = 2;
        private Integer pointsMaxScore = 20;
        private Integer pointsPerScore = 10;
        private Integer totalCheckinMaxScore = 10;
        private Integer totalCheckinPerScore = 10;
        private Integer baseScoreWeight = 80;
        private Integer socialScoreWeight = 20;
        private Integer activeMinScore = 60;
        private Integer superActiveMinScore = 80;
        private Integer normalMinScore = 30;
        private Integer socialScoreMax = 100;
        private Integer socialFollowMaxScore = 20;
        private Integer socialCommentMaxScore = 40;
        private Integer socialLikeMaxScore = 30;
        private Integer socialShareMaxScore = 10;
        private Integer socialCommentPerScore = 2;
        private Integer socialLikePerScore = 1;
        private Integer socialSharePerScore = 2;
    }

    @Data
    public static class Statistics {
        private Integer cacheDays = 2;
        private Integer botMaxDailyPlayCount = 800;
        private Integer botMaxDailyDurationSeconds = 14 * 3600;
        private Integer riskPlayCountWarnThreshold = 600;
        private Integer riskDurationWarnSeconds = 10 * 3600;
        private Integer riskLoginCountWarnThreshold = 20;
        private Integer riskSearchCountWarnThreshold = 200;
        private Integer riskInteractionCountWarnThreshold = 300;
        private Integer riskLowDiversityPlayCountThreshold = 100;
        private Integer riskLowDiversityUniqueSongThreshold = 3;
        private Integer riskAbnormalScoreThreshold = 70;
        private Integer classificationActivePlayDurationSeconds = 20 * 3600;
        private Integer classificationActiveUniqueSongCount = 20;
    }
}

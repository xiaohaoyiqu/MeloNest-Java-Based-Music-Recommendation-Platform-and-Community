package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;






@Data
@Component
@ConfigurationProperties(prefix = "moderation")
public class ModerationWorkflowConfig {

    private Appeal appeal = new Appeal();
    private Assignment assignment = new Assignment();

    public Appeal getAppeal() {
        if (appeal == null) {
            appeal = new Appeal();
        }
        return appeal;
    }

    public Assignment getAssignment() {
        if (assignment == null) {
            assignment = new Assignment();
        }
        return assignment;
    }

    @Data
    public static class Appeal {
        private int maxCount = 3;
        private int windowDays = 7;
        private int countCacheDays = 90;
        private String countPrefix = "appeal:count:";
    }

    @Data
    public static class Assignment {
        private String loadPrefix = "moderation:load:";
        private String todayPrefix = "moderation:today:";
        private int defaultDailyQuota = 100;
        private int autoAssignBatchLimit = 50;
        private int todayQuotaCacheDays = 2;
        private int loadCacheDays = 1;
    }
}

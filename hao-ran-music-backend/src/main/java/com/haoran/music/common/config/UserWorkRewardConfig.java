




package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;





@Component
@ConfigurationProperties(prefix = "user-work-reward")
@Data
public class UserWorkRewardConfig {




    private Boolean enabled = true;




    private Integer baseRewardPoints = 5;




    private Integer likeTarget = 100;




    private Integer collectTarget = 20;




    private Integer playTarget = 500;




    private Integer likeRewardPoints = 10;




    private Integer collectRewardPoints = 10;




    private Integer playRewardPoints = 5;




    private Integer bonusRewardPoints = 20;




    public boolean checkLikeTarget(Integer currentLikes) {
        return currentLikes != null && currentLikes >= likeTarget;
    }




    public boolean checkCollectTarget(Integer currentCollects) {
        return currentCollects != null && currentCollects >= collectTarget;
    }




    public boolean checkPlayTarget(Integer currentPlays) {
        return currentPlays != null && currentPlays >= playTarget;
    }









    public Integer calculateRewardPoints(Integer likeCount, Integer collectCount, Integer playCount) {
        if (!enabled) {
            return 0;
        }

        int totalPoints = baseRewardPoints;


        boolean likeReached = checkLikeTarget(likeCount);
        boolean collectReached = checkCollectTarget(collectCount);
        boolean playReached = checkPlayTarget(playCount);

        if (likeReached) {
            totalPoints += likeRewardPoints;
        }
        if (collectReached) {
            totalPoints += collectRewardPoints;
        }
        if (playReached) {
            totalPoints += playRewardPoints;
        }


        if (likeReached && collectReached && playReached) {
            totalPoints += bonusRewardPoints;
        }

        return totalPoints;
    }









    public RewardProgress getRewardProgress(Integer likeCount, Integer collectCount, Integer playCount) {
        RewardProgress progress = new RewardProgress();
        progress.setLikeTarget(likeTarget);
        progress.setLikeCurrent(likeCount != null ? likeCount : 0);
        progress.setLikeReached(checkLikeTarget(likeCount));

        progress.setCollectTarget(collectTarget);
        progress.setCollectCurrent(collectCount != null ? collectCount : 0);
        progress.setCollectReached(checkCollectTarget(collectCount));

        progress.setPlayTarget(playTarget);
        progress.setPlayCurrent(playCount != null ? playCount : 0);
        progress.setPlayReached(checkPlayTarget(playCount));

        progress.setTotalReward(calculateRewardPoints(likeCount, collectCount, playCount));
        progress.setBaseReward(baseRewardPoints);


        int totalTargets = 3;
        int reachedTargets = (progress.getLikeReached() ? 1 : 0) +
                             (progress.getCollectReached() ? 1 : 0) +
                             (progress.getPlayReached() ? 1 : 0);
        progress.setProgressPercent((reachedTargets * 100) / totalTargets);

        return progress;
    }




    @Data
    public static class RewardProgress {

        private Integer likeTarget;
        private Integer likeCurrent;
        private Boolean likeReached;


        private Integer collectTarget;
        private Integer collectCurrent;
        private Boolean collectReached;


        private Integer playTarget;
        private Integer playCurrent;
        private Boolean playReached;


        private Integer totalReward;
        private Integer baseReward;
        private Integer progressPercent;




        public Integer getLikeProgress() {
            if (likeTarget == 0) return 100;
            return Math.min(100, (likeCurrent * 100) / likeTarget);
        }




        public Integer getCollectProgress() {
            if (collectTarget == 0) return 100;
            return Math.min(100, (collectCurrent * 100) / collectTarget);
        }




        public Integer getPlayProgress() {
            if (playTarget == 0) return 100;
            return Math.min(100, (playCurrent * 100) / playTarget);
        }




        public boolean isAllTargetsReached() {
            return Boolean.TRUE.equals(likeReached) &&
                   Boolean.TRUE.equals(collectReached) &&
                   Boolean.TRUE.equals(playReached);
        }
    }
}

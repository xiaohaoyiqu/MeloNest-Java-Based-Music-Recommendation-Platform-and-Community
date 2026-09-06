




package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.SigninAchievement;

import java.util.List;
import java.util.Map;




public interface SigninAchievementService extends IService<SigninAchievement> {




    List<SigninAchievement> getAllEnabledAchievements();







    List<Map<String, Object>> getUserAchievementProgress(Long userId, Integer continuousDays);







    boolean checkAndUnlockAchievement(Long userId, Integer continuousDays);








    Map<String, Object> claimAchievementReward(Long userId, Long achievementId, Integer continuousDays);
}

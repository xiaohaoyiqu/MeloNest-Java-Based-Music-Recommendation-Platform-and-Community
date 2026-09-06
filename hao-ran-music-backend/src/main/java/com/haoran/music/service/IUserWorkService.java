package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.config.UserWorkRewardConfig;
import com.haoran.music.entity.UserWork;

import java.util.List;





public interface IUserWorkService extends IService<UserWork> {




    UserWork submitWork(UserWork userWork);




    List<UserWork> getMyWorks(Long userId);




    UserWork getWorkDetail(Long workId);




    boolean updateWork(UserWork userWork);




    boolean deleteWork(Long workId, Long userId);




    List<UserWork> getPendingWorks();




    boolean reviewWork(Long workId, Long reviewerId, Integer status, String reviewReason);




    Integer getWorkCount(Long userId);









    boolean updateWorkStats(Long workId, Long playCount, Integer likeCount, Integer collectCount);






    UserWorkRewardConfig.RewardProgress getRewardProgress(Long workId);





    int checkAndUpdateRewards();




    List<UserWork> getPublishedWorks();




    UserWork submitMultipleFiles(UserWork userWork, org.springframework.web.multipart.MultipartFile[] files);




    UserWork submitZipFile(UserWork userWork, org.springframework.web.multipart.MultipartFile zipFile) throws Exception;




    boolean updateLyricFile(Long workId, String lyricFileName, String lyricContent);
}

package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.vo.ModeratorVO;

import java.util.List;





public interface ModeratorManagementService {









    boolean setAsModerator(Long userId, String moderatorNote, Integer dailyQuota);







    boolean removeModerator(Long userId);








    boolean updateModeratorStatus(Long userId, String moderatorStatus);









    boolean updateModeratorInfo(Long userId, String moderatorNote, Integer dailyQuota);







    List<ModeratorVO> getAllModerators(String status);








    IPage<ModeratorVO> getModeratorPage(String status, PageQuery pageQuery);







    ModeratorVO getModeratorDetail(Long userId);






    void resetTodayQuota(Long userId);







    int batchSetAsModerator(List<Long> userIds);
}

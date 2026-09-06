package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.dto.creator.CreatorWorkDTO;
import com.haoran.music.entity.CreatorWork;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;





public interface CreatorWorkService extends IService<CreatorWork> {








    Long submitWork(Long userId, CreatorWorkDTO dto) throws Exception;









    void reviewWork(Long workId, Long reviewerId, Integer status, String reviewReason);









    Boolean updateWork(Long workId, Long userId, CreatorWorkDTO dto);




    Boolean deleteWork(Long workId, Long userId);






    Long createSongFromWork(CreatorWork work);










    IPage<CreatorWork> pageWorks(Integer current, Integer size, Integer status, Long userId);







    List<CreatorWork> getCreatorWorks(Long userId);






    Long getPendingCount();








    void handlePolicyUpdate(Long workId, String reason);







    Boolean canSubmit(Long userId);












    Map<String, Object> setWorkPaid(Long creatorId, Long workId, BigDecimal price, Integer subscribePeriod);








    Boolean cancelWorkPaid(Long creatorId, Long workId);









    Map<String, Object> getMyPaidWorks(Long creatorId, Integer page, Integer size);








    Map<String, Object> getWorkEarnings(Long creatorId, Long workId);







    Map<String, Object> getCreatorTotalEarnings(Long creatorId);
}

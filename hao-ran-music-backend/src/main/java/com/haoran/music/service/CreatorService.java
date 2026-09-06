




package com.haoran.music.service;

import com.haoran.music.entity.CreatorApply;
import com.haoran.music.entity.ExternalCreatorApply;
import com.haoran.music.entity.User;
import com.haoran.music.vo.CreatorApplyPrivateVO;
import com.haoran.music.vo.CreatorApplyPublicVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;





public interface CreatorService {














    Map<String, Object> applyCreator(Long userId, String realName, String idCardNo,
                                     String idCardUrl, String phone, String email,
                                     String applyReason, String worksSample);












    Map<String, Object> reviewCreatorApply(Long applicationId, Long reviewerId,
                                           Boolean approved, String reviewReason,
                                           String creatorType, BigDecimal feeRate);


















    Map<String, Object> externalCreatorApply(String realName, String idCardNo, String idCardUrl,
                                             String phone, String email,
                                             String creatorName, String externalPlatform,
                                             String externalHomepage, String worksDescription,
                                             String cooperationType, BigDecimal expectedFeeRate,
                                             String attachmentUrls);







    Map<String, Object> getCreatorInfo(Long creatorId);







    Map<String, Object> getMyCreatorInfo(Long userId);







    Map<String, Object> getCreatorEarnings(Long creatorId);










    Map<String, Object> getCreatorList(String status, String creatorType, Integer page, Integer size);








    Map<String, Object> getPendingApplications(Integer page, Integer size);








    Map<String, Object> getPendingApplicationsPublic(Integer page, Integer size);







    CreatorApplyPrivateVO getApplicationDetail(Long applicationId);







    CreatorApplyPublicVO getApplicationDetailPublic(Long applicationId);







    CreatorApplyPublicVO getMyApplication(Long userId);









    Boolean updateCreatorStatus(Long creatorId, String status, String reason);








    Boolean removeCreator(Long creatorId, String reason);







    Map<String, Object> getCreatorStats(Long userId);
}

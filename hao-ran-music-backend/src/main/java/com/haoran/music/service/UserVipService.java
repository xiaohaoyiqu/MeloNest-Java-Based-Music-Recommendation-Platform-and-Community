package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.enums.VipLevel;
import com.haoran.music.dto.user.VipPurchaseDTO;
import com.haoran.music.entity.UserVip;
import com.haoran.music.vo.user.UserVipVO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;






public interface UserVipService extends IService<UserVip> {







    UserVipVO getUserVipInfo(Long userId);








    UserVipVO purchaseVip(Long userId, VipPurchaseDTO dto);







    UserVipVO renewVip(Long userId);








    void setAutoRenew(Long userId, Boolean autoRenew, Integer cycle);







    Boolean isVip(Long userId);




    Map<Long, LocalDateTime> getActiveVipExpirations(Collection<Long> userIds);




    Map<Long, VipLevel> getActiveVipLevels(Collection<Long> userIds);








    Boolean isVipOrAbove(Long userId, VipLevel vipLevel);







    VipLevel getVipLevel(Long userId);







    void updateVipExpireTime(Long userId, LocalDateTime expireTime);





    void processExpiredVips();





    void sendExpiringVipReminders();




    void processAutoRenew();







    void recordPrivilegeUsage(Long userId, String privilege);







    List<String> getVipPrivileges(VipLevel vipLevel);









    void grantVip(Long userId, int vipLevel, int days, String source);
}






package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.UserDecoration;

import java.util.List;




public interface UserDecorationService extends IService<UserDecoration> {







    boolean hasDecoration(Long userId, String decorationId);






    List<UserDecoration> getUserDecorations(Long userId);







    List<UserDecoration> getUserDecorationsByType(Long userId, String decorationType);








    boolean addDecoration(Long userId, String decorationId, String source);


                                                                                                                                                                                                           boolean addUserBadge(Long userId, String badgeType, String badgeName, String badgeIcon,                        String badgeColor, String displayPosition, int days);






    void equipDecoration(Long userId, String decorationId);






    void unequipDecoration(Long userId, String decorationType);
}

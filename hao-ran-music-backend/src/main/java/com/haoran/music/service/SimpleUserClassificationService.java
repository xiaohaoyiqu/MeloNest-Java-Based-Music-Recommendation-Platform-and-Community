package com.haoran.music.service;

import com.haoran.music.common.enums.UserType;





public interface SimpleUserClassificationService {




    UserType getUserType(Long userId);




    void updateUserType(Long userId, UserType userType, String reason);




    void dailyUserClassification();






    Integer checkBotUsers();




    Boolean isBotUser(Long userId);




    Boolean isRestricted(Long userId);




    Boolean isFrozen(Long userId);




    void forceLogout(Long userId);




    void recordUserPlay(Long userId, Integer duration);




    void recordUserLogin(Long userId);




    void recordUserSearch(Long userId);




    void recordUserInteraction(Long userId, String type);
}

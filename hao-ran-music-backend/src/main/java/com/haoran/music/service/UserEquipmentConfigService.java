




package com.haoran.music.service;

import java.util.Map;




public interface UserEquipmentConfigService {






    Map<String, Object> getUserEquipmentConfig(Long userId);






    void updateEquipmentConfig(Long userId, Map<String, Object> config);







    void equipDecoration(Long userId, String decorationType, String decorationId);






    void unequipDecoration(Long userId, String decorationType);






    java.util.List<String> getEquippedBadges(Long userId);
}

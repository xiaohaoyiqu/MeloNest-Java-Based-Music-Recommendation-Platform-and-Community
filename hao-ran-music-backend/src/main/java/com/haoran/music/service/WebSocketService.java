package com.haoran.music.service;

import com.haoran.music.entity.Notification;





public interface WebSocketService {







    void sendToUser(Long userId, Object message);








    boolean trySendToUser(Long userId, Object message);







    void sendToUser(Long userId, String message);






    void broadcast(Object message);






    int getOnlineUserCount();







    boolean isUserOnline(Long userId);











    boolean disconnectUser(Long userId, String reason);
}

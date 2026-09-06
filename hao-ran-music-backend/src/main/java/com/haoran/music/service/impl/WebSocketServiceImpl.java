package com.haoran.music.service.impl;

import com.haoran.music.service.WebSocketService;
import com.haoran.music.websocket.ModerationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;






@Service
@Primary
public class WebSocketServiceImpl implements WebSocketService {

    @Autowired
    private ModerationWebSocketHandler webSocketHandler;

    @Override
    public void sendToUser(Long userId, Object message) {
        webSocketHandler.sendToUser(userId, message);
    }

    @Override
    public boolean trySendToUser(Long userId, Object message) {
        return webSocketHandler.trySendToUser(userId, message);
    }

    @Override
    public void sendToUser(Long userId, String message) {
        webSocketHandler.sendToUser(userId, message);
    }

    @Override
    public void broadcast(Object message) {
        webSocketHandler.broadcast(message);
    }

    @Override
    public int getOnlineUserCount() {
        return webSocketHandler.getOnlineUserCount();
    }

    @Override
    public boolean isUserOnline(Long userId) {
        return webSocketHandler.isUserOnline(userId);
    }

    @Override
    public boolean disconnectUser(Long userId, String reason) {
        return webSocketHandler.disconnectUser(userId, reason);
    }
}

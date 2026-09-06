



package com.haoran.music.service;




public interface EmailSender {




    EmailSendResult send(String email, String scene, String code);
}
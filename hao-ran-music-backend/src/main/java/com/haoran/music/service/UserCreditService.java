package com.haoran.music.service;





public interface UserCreditService {








    Boolean addUserCredit(Long userId, Integer creditScore, String reason);








    Boolean deductUserCredit(Long userId, Integer creditScore, String reason);






    Integer getUserCredit(Long userId);
}

package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.dto.user.UserPasswordQueryDTO;
import com.haoran.music.entity.UserPassword;
import com.haoran.music.vo.user.UserPasswordVO;





public interface UserPasswordService extends IService<UserPassword> {





    @Deprecated
    Boolean savePassword(Long userId, String username, String passwordPlain);




    @Deprecated
    Boolean updatePassword(Long userId, String passwordPlain);







    UserPasswordVO getByUserId(Long userId);







    IPage<UserPasswordVO> pagePasswords(UserPasswordQueryDTO dto);







    Boolean deleteByUserId(Long userId);
}

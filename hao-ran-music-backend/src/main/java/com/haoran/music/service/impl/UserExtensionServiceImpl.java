package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.entity.UserExtension;
import com.haoran.music.mapper.UserExtensionMapper;
import com.haoran.music.service.UserExtensionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

   
                      
                                                     
   
@Slf4j
@Service
public class UserExtensionServiceImpl extends ServiceImpl<UserExtensionMapper, UserExtension> implements UserExtensionService {

    @Override
    public UserExtension getByUserId(Long userId) {
        if (userId == null) {
            return null;
        }

        LambdaQueryWrapper<UserExtension> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserExtension::getUserId, userId);

        return getOne(wrapper);
    }
}

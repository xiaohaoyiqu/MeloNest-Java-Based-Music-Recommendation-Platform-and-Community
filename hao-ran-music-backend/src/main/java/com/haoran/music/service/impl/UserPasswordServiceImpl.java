package com.haoran.music.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.AdminAccountOperationGuard;
import com.haoran.music.dto.user.UserPasswordQueryDTO;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserPassword;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserPasswordMapper;
import com.haoran.music.service.UserPasswordService;
import com.haoran.music.vo.user.UserPasswordVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

   
                      
                                                                     
   
@Slf4j
@Service
public class UserPasswordServiceImpl extends ServiceImpl<UserPasswordMapper, UserPassword> implements UserPasswordService {

    private static final String PRIVILEGED_USER_ID_SQL =
            "SELECT id FROM `user` WHERE UPPER(role) IN ('MODERATOR','ADMIN','SUPER_ADMIN') OR is_moderator = 1";
    private static final String SUPER_ADMIN_USER_ID_SQL =
            "SELECT id FROM `user` WHERE UPPER(role) = 'SUPER_ADMIN'";

    @Resource
    private UserPasswordMapper userPasswordMapper;

    @Resource
    private UserMapper userMapper;

    @Override
    public Boolean savePassword(Long userId, String username, String passwordPlain) {
        throw new BusinessException("明文密码保存已禁用，请只使用用户表中的密码哈希");
    }

    @Override
    public Boolean updatePassword(Long userId, String passwordPlain) {
        throw new BusinessException("明文密码更新已禁用，请只使用用户表中的密码哈希");
    }

    @Override
    public UserPasswordVO getByUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }

        User target = userMapper.selectById(userId);
        AdminAccountOperationGuard.requireCanOperateAccount(
                getCurrentOperator(), target, "查看用户密码");

        LambdaQueryWrapper<UserPassword> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPassword::getUserId, userId);

        UserPassword userPassword = userPasswordMapper.selectOne(wrapper);
        if (userPassword == null) {
            throw new BusinessException("未找到用户密码记录");
        }

        return convertToVO(userPassword);
    }

    @Override
    public IPage<UserPasswordVO> pagePasswords(UserPasswordQueryDTO dto) {
        User operator = getCurrentOperator();
        Page<UserPassword> page = new Page<>(dto.getPage(), dto.getSize());

        LambdaQueryWrapper<UserPassword> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPassword::getDeleted, CommonConstants.NOT_DELETED);
        applyPrivilegedPasswordFilter(wrapper, operator);

        if (dto.getUserId() != null) {
            User target = userMapper.selectById(dto.getUserId());
            AdminAccountOperationGuard.requireCanOperateAccount(
                    operator, target, "查看用户密码");
            wrapper.eq(UserPassword::getUserId, dto.getUserId());
        }
        if (dto.getUsername() != null) {
            wrapper.like(UserPassword::getUsername, dto.getUsername());
        }

                  
        wrapper.orderByDesc(UserPassword::getCreateTime);

        IPage<UserPassword> userPasswordPage = userPasswordMapper.selectPage(page, wrapper);

                
        return userPasswordPage.convert(this::convertToVO);
    }

    @Override
    public Boolean deleteByUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }

        LambdaQueryWrapper<UserPassword> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPassword::getUserId, userId);

        return userPasswordMapper.delete(wrapper) > 0;
    }

       
                          
      
                                 
                     
       
    private UserPasswordVO convertToVO(UserPassword userPassword) {
        return BeanUtil.copyProperties(userPassword, UserPasswordVO.class);
    }

    private void applyPrivilegedPasswordFilter(LambdaQueryWrapper<UserPassword> wrapper, User operator) {
        if (AdminAccountOperationGuard.isSuperAdmin(operator)) {
            wrapper.notInSql(UserPassword::getUserId, SUPER_ADMIN_USER_ID_SQL);
            return;
        }
        wrapper.notInSql(UserPassword::getUserId, PRIVILEGED_USER_ID_SQL);
    }

    private User getCurrentOperator() {
        Long operatorId = UserContext.getCurrentUserId();
        if (operatorId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "当前操作账号不存在");
        }
        return operator;
    }
}

package com.haoran.music.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.PageUtils;
import com.haoran.music.common.util.RedisUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;





public abstract class BaseService<T> {

    @Autowired
    protected RedisUtils redisUtils;







    protected <E> Page<E> buildPage(PageQuery query) {
        return PageUtils.buildPage(query);
    }







    protected void validateUserId(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
    }








    protected void validateUserId(Long userId, String message) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, message);
        }
    }








    protected void validateId(Long id, String name) {
        if (ObjectUtils.isEmpty(id)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, name + "不能为空");
        }
    }








    protected <T> LambdaQueryWrapper<T> baseWrapper(Class<T> entityClass) {
        LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<>();

        return wrapper;
    }







    protected <T> LambdaQueryWrapper<T> notDeleted(LambdaQueryWrapper<T> wrapper) {

        return wrapper;
    }








    protected void updateCache(String key, Object value, long expire) {
        redisUtils.set(key, value, expire, TimeUnit.SECONDS);
    }







    protected void updateCache(String key, Object value) {
        redisUtils.set(key, value, 3600, TimeUnit.SECONDS);
    }






    protected void clearCache(String key) {
        redisUtils.delete(key);
    }






    protected void clearCache(String... keys) {
        for (String key : keys) {
            redisUtils.delete(key);
        }
    }
}

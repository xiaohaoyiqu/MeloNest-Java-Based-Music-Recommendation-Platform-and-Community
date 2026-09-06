package com.haoran.music.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.haoran.music.common.constant.CommonConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;





@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {






    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "createBy", String.class, getCurrentUserId());
        this.strictInsertFill(metaObject, "updateBy", String.class, getCurrentUserId());
    }






    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateBy", String.class, getCurrentUserId());
    }






    private String getCurrentUserId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                Object userId = request.getAttribute(CommonConstants.USER_ID_KEY);
                if (userId != null) {
                    return String.valueOf(userId);
                }
            }
        } catch (Exception e) {

            log.warn("event=meta_object_user_resolution_failed errorType={}",
                    e.getClass().getSimpleName());
        }
        return "system";
    }
}

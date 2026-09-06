package com.haoran.music.common.result;

import lombok.Getter;





@Getter
public enum ResultCode {




    SUCCESS(200, "操作成功"),




    ERROR(500, "操作失败"),




    PARAM_ERROR(400, "参数错误"),




    BAD_REQUEST(400, "请求错误"),




    UNAUTHORIZED(401, "未授权，请先登录"),




    FORBIDDEN(403, "禁止访问"),




    NOT_FOUND(404, "资源不存在"),




    METHOD_NOT_ALLOWED(405, "请求方法不支持"),




    TOO_MANY_REQUESTS(429, "操作过于频繁，请稍后再试"),




    LOGIN_ERROR(1001, "用户名或密码错误"),




    USER_NOT_EXIST(1002, "用户不存在"),




    USER_ALREADY_EXIST(1003, "用户已存在"),




    TOKEN_EXPIRED(1004, "Token已过期"),




    TOKEN_INVALID(1005, "Token无效"),




    SONG_NOT_EXIST(2001, "歌曲不存在"),




    ALBUM_NOT_EXIST(2002, "专辑不存在"),




    ARTIST_NOT_EXIST(2003, "歌手不存在"),




    PLAYLIST_NOT_EXIST(3001, "歌单不存在"),




    PLAYLIST_NAME_EXIST(3002, "歌单名称已存在"),




    PLAYLIST_SONG_LIMIT(3003, "歌单歌曲数量超过限制"),




    COMMENT_NOT_EXIST(4001, "评论不存在"),




    FILE_UPLOAD_ERROR(5001, "文件上传失败"),




    FILE_TYPE_NOT_SUPPORT(5002, "文件类型不支持"),




    FILE_SIZE_EXCEED(5003, "文件大小超限"),




    SYSTEM_BUSY(9999, "系统繁忙，请稍后再试"),




    DATA_NOT_EXIST(5004, "数据不存在"),




    BUSINESS_ERROR(5005, "业务错误");




    private final Integer code;




    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}

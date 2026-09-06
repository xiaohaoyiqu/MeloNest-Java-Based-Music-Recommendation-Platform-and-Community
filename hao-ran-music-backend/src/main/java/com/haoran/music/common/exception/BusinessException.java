package com.haoran.music.common.exception;

import com.haoran.music.common.result.ResultCode;
import lombok.Getter;





@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;




    private Integer code;




    private String message;






    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
        this.message = message;
    }







    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }






    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }







    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
        this.message = message;
    }
}

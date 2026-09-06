package com.haoran.music.dto.gift;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

   
                      
                         
   
@Data
public class VipGiftCreateDTO {

       
             
       
    @NotNull(message = "收礼用户不能为空")
    private Long receiverId;

       
                               
       
    @NotBlank(message = "VIP类型不能为空")
    private String vipType;

       
           
       
    private String giftMessage;
}

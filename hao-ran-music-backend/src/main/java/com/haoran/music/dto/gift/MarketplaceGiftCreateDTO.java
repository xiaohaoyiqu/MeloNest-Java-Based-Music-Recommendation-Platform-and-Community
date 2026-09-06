package com.haoran.music.dto.gift;

import lombok.Data;

import javax.validation.constraints.NotNull;

   
                      
                            
   
@Data
public class MarketplaceGiftCreateDTO {

       
             
       
    @NotNull(message = "收礼用户不能为空")
    private Long receiverId;

       
               
       
    @NotNull(message = "商品不能为空")
    private Long itemId;

       
           
       
    private String giftMessage;
}

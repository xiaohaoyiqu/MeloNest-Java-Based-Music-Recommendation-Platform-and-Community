package com.haoran.music.vo.playlist;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

   
                      
                     
   
@Data
public class PlaylistSubscriberVO implements Serializable {

    private static final long serialVersionUID = 1L;

       
             
       
    private Long id;

       
           
       
    private Long userId;

       
           
       
    private String nickname;

       
           
       
    private String avatar;

       
           
       
    private Date subscribeTime;

       
           
       
    private Date expireTime;

       
                               
       
    private String status;

       
                                             
       
    private String subscribeType;

       
              
       
    private Integer price;
}

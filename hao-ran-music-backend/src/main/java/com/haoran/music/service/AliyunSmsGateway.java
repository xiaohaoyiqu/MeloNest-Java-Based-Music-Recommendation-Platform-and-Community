   
                      
   
package com.haoran.music.service;

   
                                                                        
   
public interface AliyunSmsGateway {

       
                                                                              
       
    String send(String phone, String signName, String templateCode, String templateParam) throws Exception;
}

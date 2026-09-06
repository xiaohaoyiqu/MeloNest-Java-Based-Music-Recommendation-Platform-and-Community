package com.haoran.music.service;

import com.haoran.music.common.dto.PageResult;
import com.haoran.music.vo.emoji.EmojiPackageDetailVO;
import com.haoran.music.vo.emoji.EmojiPackageVO;

import java.util.List;

   
                      
                             
   
public interface EmojiShopService {

       
                 
       
    EmojiShopHomeVO getHomeData();

       
              
       
    PageResult<EmojiPackageVO> getPackages(String type, String category, Integer page, Integer size);

       
              
       
    EmojiPackageDetailVO getPackageDetail(Long id);

       
               
       
    void purchasePackage(Long id);

       
             
       
    List<EmojiPackageVO> getMyEmojis();

       
            
       
    void favoritePackage(Long id);

       
           
       
    void unfavoritePackage(Long id);

       
               
       
    @lombok.Data
    class EmojiShopHomeVO {
        private List<EmojiPackageVO> recommended;
        private List<EmojiPackageVO> hot;
        private List<EmojiPackageVO> latest;
        private List<EmojiPackageVO> free;
        private List<EmojiPackageVO> myEmojis;
    }
}

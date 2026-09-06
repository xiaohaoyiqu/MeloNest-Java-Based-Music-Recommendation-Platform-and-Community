package com.haoran.music.common.constant;

   
                      
                       
  
                            
   
public class RecommendWeightConstants {

                                                        

                 
    public static final double VIP_WEIGHT_FREE = 1.0;

                  
    public static final double VIP_WEIGHT_MONTHLY = 1.1;

                  
    public static final double VIP_WEIGHT_QUARTERLY = 1.2;

                  
    public static final double VIP_WEIGHT_YEARLY = 1.4;

                  
    public static final double VIP_WEIGHT_LIFETIME = 1.5;

                                                        

                     
    public static final int VIP_LEVEL_FREE = 0;

                     
    public static final int VIP_LEVEL_MONTHLY = 1;

                     
    public static final int VIP_LEVEL_QUARTERLY = 2;

                     
    public static final int VIP_LEVEL_YEARLY = 3;

                     
    public static final int VIP_LEVEL_LIFETIME = 4;

                                                       

                   
    public static final double ROLE_WEIGHT_USER = 1.0;

                 
    public static final double ROLE_WEIGHT_MODERATOR = 1.1;

                  
    public static final double ROLE_WEIGHT_ADMIN = 1.2;

                  
    public static final double ROLE_WEIGHT_CREATOR = 1.4;

                    
    public static final double ROLE_WEIGHT_SUPER_ADMIN = 1.4;

                                                       

                     
    public static final String ROLE_USER = "USER";

                   
    public static final String ROLE_MODERATOR = "MODERATOR";

                    
    public static final String ROLE_ADMIN = "ADMIN";

                    
    public static final String ROLE_CREATOR = "CREATOR";

                      
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

                                                        

                         
    public static final double CREDIT_WEIGHT_EXCELLENT = 1.1;

                          
    public static final double CREDIT_WEIGHT_GOOD = 1.0;

                          
    public static final double CREDIT_WEIGHT_AVERAGE = 1.0;

                          
    public static final double CREDIT_WEIGHT_POOR = 0.8;

                        
    public static final double CREDIT_WEIGHT_VERY_POOR = 0.5;

                                                        

                   
    public static final int CREDIT_SCORE_EXCELLENT_MIN = UserAccountPolicyConstants.CREDIT_SCORE_EXCELLENT_MIN;

                   
    public static final int CREDIT_SCORE_GOOD_MIN = UserAccountPolicyConstants.CREDIT_SCORE_GOOD_MIN;

                   
    public static final int CREDIT_SCORE_AVERAGE_MIN = UserAccountPolicyConstants.CREDIT_SCORE_NORMAL_MIN;

                   
    public static final int CREDIT_SCORE_POOR_MIN = UserAccountPolicyConstants.PUBLIC_FLOW_CREDIT_MIN_SCORE;

                                                       

                
    public static final double DECORATION_WEIGHT_NONE = 1.0;

                 
    public static final double DECORATION_WEIGHT_RARE = 1.0;

                 
    public static final double DECORATION_WEIGHT_EPIC = 1.1;

                 
    public static final double DECORATION_WEIGHT_LEGENDARY = 1.3;

                                                        

                   
    public static final String RARITY_COMMON = "common";

                   
    public static final String RARITY_RARE = "rare";

                   
    public static final String RARITY_EPIC = "epic";

                   
    public static final String RARITY_LEGENDARY = "legendary";

                                                         

                  
    public static final double SOCIAL_WEIGHT_NONE = 1.0;

                       
    public static final double SOCIAL_WEIGHT_FOLLOWED = 1.1;

                      
    public static final double SOCIAL_WEIGHT_FOLLOWING = 1.3;

               
    public static final double SOCIAL_WEIGHT_MUTUAL = 1.9;

                                                          

                  
    public static final double CREATOR_WEIGHT_INDEPENDENT = 1.0;

                    
    public static final double CREATOR_WEIGHT_EXTERNAL_INDEPENDENT = 0.8;

                    
    public static final double CREATOR_WEIGHT_EXTERNAL_SIGNED = 1.1;

                  
    public static final double CREATOR_WEIGHT_SIGNED = 1.4;

                                                        

                     
    public static final String CREATOR_TYPE_INDEPENDENT = "independent";

                       
    public static final String CREATOR_TYPE_EXTERNAL_INDEPENDENT = "external_independent";

                       
    public static final String CREATOR_TYPE_EXTERNAL_SIGNED = "external_signed";

                     
    public static final String CREATOR_TYPE_SIGNED = "signed";

                                                         

                        
    public static final double INTEREST_SCORE_FAVORITE = 40.0;

                                  
    public static final double INTEREST_SCORE_PLAY_PER_5 = 1.0;

                         
    public static final double INTEREST_SCORE_PLAY_MAX = 30.0;

                          
    public static final double INTEREST_SCORE_RECENT = 30.0;

                            
    public static final int INTEREST_RECENT_DAYS = 7;

                                                         

                 
    public static final double SONG_BASE_WEIGHT_RATING = 0.4;

                   
    public static final double SONG_BASE_WEIGHT_PLAY_COUNT = 0.2;

                  
    public static final double SONG_BASE_WEIGHT_HOT_SCORE = 0.2;

                   
    public static final double SONG_BASE_WEIGHT_NEW_BONUS = 0.05;

                  
    public static final double SONG_BASE_WEIGHT_FAVORITE = 0.15;

                                                         

                               
    public static final double SONG_PLAY_COUNT_FACTOR = 100.0;

                    
    public static final double SONG_PLAY_COUNT_MAX_SCORE = 20.0;

                                
    public static final double SONG_HOT_SCORE_FACTOR = 1000.0;

                   
    public static final double SONG_HOT_SCORE_MAX_SCORE = 20.0;

                       
    public static final int SONG_NEW_DAYS = 30;

                 
    public static final double SONG_NEW_BONUS_SCORE = 5.0;

                             
    public static final double SONG_FAVORITE_COUNT_FACTOR = 10.0;

                   
    public static final double SONG_FAVORITE_COUNT_MAX_SCORE = 15.0;

                                                         

                              
    public static final double PLAYLIST_PLAY_COUNT_FACTOR = 100.0;

                   
    public static final double PLAYLIST_PLAY_COUNT_MAX_SCORE = 30.0;

                             
    public static final double PLAYLIST_FAVORITE_COUNT_FACTOR = 10.0;

                   
    public static final double PLAYLIST_FAVORITE_COUNT_MAX_SCORE = 40.0;

                            
    public static final double PLAYLIST_SONG_COUNT_FACTOR = 2.0;

                    
    public static final double PLAYLIST_SONG_COUNT_MAX_SCORE = 20.0;

                   
    public static final double PLAYLIST_FEATURED_BONUS_SCORE = 30.0;

                                                       

                   
    public static final int DEFAULT_HALF_LIFE_DAYS = 30;

                
    public static final long SECONDS_PER_DAY = 86400L;

                 
    public static final long SECONDS_PER_HOUR = 3600L;

                                                          

                     
    public static final String CACHE_VIP_WEIGHT_PREFIX = "recommend_weight:vip:";

                    
    public static final String CACHE_ROLE_WEIGHT_PREFIX = "recommend_weight:role:";

                     
    public static final String CACHE_CREDIT_WEIGHT_PREFIX = "recommend_weight:credit:";

                    
    public static final String CACHE_DECORATION_WEIGHT_PREFIX = "recommend_weight:decoration:";

                     
    public static final String CACHE_CREATOR_TYPE_PREFIX = "recommend_weight:creator:";

                      
    public static final String CACHE_USER_COEFFICIENT_PREFIX = "recommend_coefficient:";

                          
    public static final long CACHE_EXPIRE_SECONDS = 3600L;

                                                         

                    
    public static final double USER_INTEREST_MAX_SCORE = 100.0;

                         
    public static final double DEFAULT_RECOMMEND_WEIGHT = 0.0;

                       
    public static final double DEFAULT_USER_COEFFICIENT = 1.0;

       
                  
                            
                  
       
    public static double getVipWeight(int vipLevel) {
        switch (vipLevel) {
            case VIP_LEVEL_LIFETIME:
                return VIP_WEIGHT_LIFETIME;
            case VIP_LEVEL_YEARLY:
                return VIP_WEIGHT_YEARLY;
            case VIP_LEVEL_QUARTERLY:
                return VIP_WEIGHT_QUARTERLY;
            case VIP_LEVEL_MONTHLY:
                return VIP_WEIGHT_MONTHLY;
            case VIP_LEVEL_FREE:
            default:
                return VIP_WEIGHT_FREE;
        }
    }

       
               
                      
                  
       
    public static double getRoleWeight(String role) {
        if (role == null) {
            return ROLE_WEIGHT_USER;
        }
        switch (role) {
            case ROLE_CREATOR:
            case ROLE_SUPER_ADMIN:
                return ROLE_WEIGHT_CREATOR;
            case ROLE_ADMIN:
                return ROLE_WEIGHT_ADMIN;
            case ROLE_MODERATOR:
                return ROLE_WEIGHT_MODERATOR;
            default:
                return ROLE_WEIGHT_USER;
        }
    }

       
                
                             
                  
       
    public static double getCreditWeight(int creditScore) {
        if (creditScore >= CREDIT_SCORE_EXCELLENT_MIN) {
            return CREDIT_WEIGHT_EXCELLENT;
        } else if (creditScore >= CREDIT_SCORE_GOOD_MIN) {
            return CREDIT_WEIGHT_GOOD;
        } else if (creditScore >= CREDIT_SCORE_AVERAGE_MIN) {
            return CREDIT_WEIGHT_AVERAGE;
        } else if (creditScore >= CREDIT_SCORE_POOR_MIN) {
            return CREDIT_WEIGHT_POOR;
        } else {
            return CREDIT_WEIGHT_VERY_POOR;
        }
    }

       
                  
                        
                  
       
    public static double getDecorationWeight(String rarity) {
        if (rarity == null) {
            return DECORATION_WEIGHT_NONE;
        }
        switch (rarity) {
            case RARITY_LEGENDARY:
                return DECORATION_WEIGHT_LEGENDARY;
            case RARITY_EPIC:
                return DECORATION_WEIGHT_EPIC;
            case RARITY_RARE:
                return DECORATION_WEIGHT_RARE;
            default:
                return DECORATION_WEIGHT_NONE;
        }
    }

       
                  
                               
                  
       
    public static double getCreatorWeight(String creatorType) {
        if (creatorType == null) {
            return CREATOR_WEIGHT_INDEPENDENT;
        }
        switch (creatorType) {
            case CREATOR_TYPE_SIGNED:
                return CREATOR_WEIGHT_SIGNED;
            case CREATOR_TYPE_EXTERNAL_SIGNED:
                return CREATOR_WEIGHT_EXTERNAL_SIGNED;
            case CREATOR_TYPE_EXTERNAL_INDEPENDENT:
                return CREATOR_WEIGHT_EXTERNAL_INDEPENDENT;
            case CREATOR_TYPE_INDEPENDENT:
            default:
                return CREATOR_WEIGHT_INDEPENDENT;
        }
    }

                   
    private RecommendWeightConstants() {
    }
}

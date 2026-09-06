package com.haoran.music.common.constant;

   
                      
                     
   
public class CommonConstants {

       
              
       
    public static final String UTF8 = "UTF-8";

       
           
       
    public static final int DEFAULT_PAGE = 1;

       
             
       
    public static final int DEFAULT_SIZE = 20;

       
             
       
    public static final int MAX_SIZE = 100;

       
                       
       
    public static final String USER_ID_KEY = "userId";

       
                       
       
    public static final String USER_INFO_KEY = "userInfo";

       
               
       
    public static final String TOKEN_HEADER = "Authorization";

       
              
       
    public static final String TOKEN_PREFIX = "Bearer ";

       
                 
       
    public static final Integer DELETED = 1;

       
                 
       
    public static final Integer NOT_DELETED = 0;

       
              
       
    public static final Integer STATUS_NORMAL = 1;

       
                 
       
    public static final Integer STATUS_DISABLED = 0;

       
               
       
    public static final Integer STATUS_AUDITING = 2;

       
              
       
    public static final Integer GENDER_UNKNOWN = 0;

       
             
       
    public static final Integer GENDER_MALE = 1;

       
             
       
    public static final Integer GENDER_FEMALE = 2;

       
             
       
    public static final Integer NO = 0;

       
             
       
    public static final Integer YES = 1;

       
                
       
    public static final Integer PUBLIC_PRIVATE = 0;

       
                
       
    public static final Integer PUBLIC_PUBLIC = 1;

       
              
       
    public static final String QUALITY_STANDARD = "standard";

       
               
       
    public static final String QUALITY_HIGH = "high";

       
              
       
    public static final String QUALITY_LOSSLESS = "lossless";

       
                 
       
    public static final int NEW_SONG_DAYS = 14;

       
                  
       
    public static final int HOT_SONG_DAYS = 14;

       
               
       
    public static final int PLAYLIST_MAX_SONGS = 200;

       
                
       
    public static final int HISTORY_MAX_COUNT = 560;

       
              
       
    public static final int SUB_TYPE_MAX_COUNT = 5;

       
                        
       
    public static final int USER_MAX_PLAYLISTS_NORMAL = 30;

       
                         
       
    public static final int USER_MAX_PLAYLISTS_VIP = 50;

                                                       

       
                
       
    public static final int LYRIC_TYPE_ORIGINAL = 1;

       
                
       
    public static final int LYRIC_TYPE_TRANSLATE = 2;

       
                 
       
    public static final int TRANSLATION_STATUS_PENDING = 0;

       
                 
       
    public static final int TRANSLATION_STATUS_PROCESSING = 1;

       
                
       
    public static final int TRANSLATION_STATUS_COMPLETED = 2;

       
                
       
    public static final int TRANSLATION_STATUS_FAILED = 3;

       
                
       
    public static final String LYRIC_SOURCE_USER = "user";

       
                 
       
    public static final String LYRIC_SOURCE_ADMIN = "admin";

       
                         
       
    public static final String LYRIC_SOURCE_DEEPSEEK = "deepseek";

       
                         
       
    public static final String LYRIC_SOURCE_NODE3 = "node3";

                                                                 
    public static final String LYRIC_SOURCE_CREATOR = "creator";

       
                    
       
    public static final String DEFAULT_LYRIC_LANGUAGE = "zh-CN";

                                                       

       
                   
       
    public static final String ERROR_LYRIC_NOT_FOUND = "原文歌词不存在";

       
                    
       
    public static final String ERROR_TRANSLATION_FAILED = "调用翻译服务失败";

       
                       
       
    public static final String ERROR_KAFKA_SEND_FAILED = "[Kafka] 发送事件失败";

       
                   
       
    public static final String ERROR_CLOSE_STREAM_FAILED = "关闭流失败";

                                                        

       
                    
       
    public static final String LYRIC_TRANSLATOR_EXECUTOR = "lyricTranslatorExecutor";

       
                    
       
    public static final String TASK_EXECUTOR = "taskExecutor";

       
                    
       
    public static final String RECOMMEND_EXECUTOR = "recommendExecutor";

       
                    
       
    public static final String MEDIA_PROCESSING_EXECUTOR = "mediaProcessingExecutor";

                                                       
       
       

       
            
       
    public static final int RATING_MIN = 1;

       
            
       
    public static final int RATING_MAX = 5;

       
                
       
    public static final int RATING_COOLDOWN_SECONDS = 5;

       
                   
       
    public static final int RATING_DAILY_MAX_NORMAL = 10;

       
                    
       
    public static final int RATING_DAILY_MAX_VIP = 30;

       
                            
       
    public static final int RATING_DAILY_MAX_EXCELLENT = 20;

       
                       
       
    public static final int RATING_MODIFY_COOLDOWN_HOURS = 24;

       
               
       
    public static final int RATING_BATCH_MAX = 10;

       
                        
       
    public static final String RATING_COOLDOWN_KEY = "rating:cooldown:";

       
                        
       
    public static final String RATING_DAILY_KEY = "rating:daily:";

       
                          
       
    public static final String RATING_SONG_MODIFY_KEY = "rating:song:modify:";

       
                        
       
    public static final int RATING_ANOMALY_THRESHOLD = 5;

       
                  
       
    public static final int RATING_ANOMALY_CHECK_DAYS = 7;

       
                    
       
    public static final String ERROR_RATING_OUT_OF_RANGE = "评分必须在1-5星之间";

       
                      
       
    public static final String ERROR_RATING_TOO_FREQUENT = "评分操作过于频繁，请稍后再试";

       
                        
       
    public static final String ERROR_RATING_DAILY_LIMIT = "今日评分次数已达上限，请明天再试";

       
                     
       
    public static final String ERROR_RATING_MODIFY_COOLDOWN = "同一首歌评分修改需要等待24小时";
}


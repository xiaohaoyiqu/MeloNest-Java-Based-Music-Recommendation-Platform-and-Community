package com.haoran.music.common.constant;

import java.util.concurrent.TimeUnit;

   
                      
                        
   
public class RedisConstants {

       
              
       
    public static final String TOKEN_PREFIX = "token:";

       
                    
       
    public static final long TOKEN_EXPIRE = 7;
    public static final TimeUnit TOKEN_TIME_UNIT = TimeUnit.DAYS;

       
               
       
    public static final String USER_INFO_PREFIX = "user:info:";

       
                     
       
    public static final long USER_INFO_EXPIRE = 30;
    public static final TimeUnit USER_INFO_TIME_UNIT = TimeUnit.MINUTES;

       
               
       
    public static final String SONG_INFO_PREFIX = "song:info:";

       
                    
       
    public static final long SONG_INFO_EXPIRE = 1;
    public static final TimeUnit SONG_INFO_TIME_UNIT = TimeUnit.HOURS;

       
               
       
    public static final String RECOMMEND_PREFIX = "recommend:";

       
                   
       
    public static final long RECOMMEND_EXPIRE = 1;
    public static final TimeUnit RECOMMEND_TIME_UNIT = TimeUnit.DAYS;

       
              
       
    public static final String HOT_SONG_PREFIX = "hot:song:";

       
                  
       
    public static final long HOT_SONG_EXPIRE = 1;
    public static final TimeUnit HOT_SONG_TIME_UNIT = TimeUnit.DAYS;

       
               
       
    public static final String SEARCH_PREFIX = "search:";

       
                    
       
    public static final long SEARCH_EXPIRE = 1;
    public static final TimeUnit SEARCH_TIME_UNIT = TimeUnit.HOURS;

       
             
       
    public static final String PLAYLIST_PREFIX = "playlist:";

       
                   
       
    public static final long PLAYLIST_EXPIRE = 30;
    public static final TimeUnit PLAYLIST_TIME_UNIT = TimeUnit.MINUTES;

       
                 
       
    public static final String PLAY_COUNT_PREFIX = "play:count:";

       
                                    
       
    public static final String PLAYLIST_VISIT_PREFIX = "visit:playlist:";

       
                   
       
    public static final long VISIT_EXPIRE = 30;
    public static final TimeUnit VISIT_TIME_UNIT = TimeUnit.MINUTES;

       
                
       
    public static final String FAVORITE_COUNT_PREFIX = "favorite:count:";

       
             
       
    public static final String NEW_SONG_KEY = "new:song:list";

       
             
       
    public static final String LOCK_PREFIX = "lock:";

       
           
       
    public static final String RATE_LIMIT_PREFIX = "rate:limit:";

       
            
       
    public static final String VERIFY_CODE_PREFIX = "verify:code:";
}

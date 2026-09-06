package com.haoran.music.common.constant;







public class ActivityAntiSpamConstants {




    public static final double CHECKIN_WEIGHT = 0.25;


    public static final double SOCIAL_WEIGHT = 0.30;


    public static final double CONSUMPTION_WEIGHT = 0.25;


    public static final double CREATION_WEIGHT = 0.20;


    public static final double CONTENT_BONUS_WEIGHT = 0.10;




    public static final int LIKE_RATE_LIMIT_PER_MINUTE = 10;


    public static final int COMMENT_RATE_LIMIT_PER_MINUTE = 5;


    public static final int SHARE_RATE_LIMIT_PER_MINUTE = 8;


    public static final int POST_RATE_LIMIT_PER_MINUTE = 2;


    public static final int FOLLOW_RATE_LIMIT_PER_MINUTE = 5;




    public static final int DAILY_LIKE_LIMIT = 100;


    public static final int DAILY_COMMENT_LIMIT = 30;


    public static final int DAILY_SHARE_LIMIT = 50;


    public static final int DAILY_POST_LIMIT = 20;


    public static final int DAILY_FOLLOW_LIMIT = 30;




    public static final int LIKE_COOLDOWN_SECONDS = 3;


    public static final int COMMENT_COOLDOWN_SECONDS = 10;


    public static final int SHARE_COOLDOWN_SECONDS = 5;




    public static final int LISTEN_MIN_DURATION_SECONDS = 30;


    public static final int MV_WATCH_MIN_DURATION_SECONDS = 60;


    public static final double COMPLETE_PLAY_RATIO = 0.8;




    public static final int DECAY_START_COUNT = 10;


    public static final double DECAY_MIN_RATE = 0.5;


    public static final double DECAY_STEP = 0.05;




    public static final int SUSPICIOUS_BEHAVIOR_COUNT = 50;


    public static final double SUSPICIOUS_SINGLE_BEHAVIOR_RATIO = 0.8;


    public static final int CONTINUOUS_ACTIVE_HOURS_LIMIT = 20;


    public static final int ABNORMAL_HOUR_START = 2;


    public static final int ABNORMAL_HOUR_END = 6;




    public static final int NEW_USER_PROTECT_DAYS = 7;


    public static final double NEW_USER_WEIGHT_RATE = 0.5;




    public static final int EXCELLENT_CREDIT_MIN_SCORE = 80;


    public static final double EXCELLENT_CREDIT_WEIGHT = 1.2;


    public static final int NORMAL_CREDIT_MIN_SCORE = 60;


    public static final double NORMAL_CREDIT_WEIGHT = 1.0;


    public static final int OBSERVE_CREDIT_MIN_SCORE = 40;


    public static final double OBSERVE_CREDIT_WEIGHT = 0.7;


    public static final double LOW_CREDIT_WEIGHT = 0.4;




    public static final double ABNORMAL_HOUR_WEIGHT = 0.5;




    public static final int INACTIVE_MAX_SCORE = 29;


    public static final int NORMAL_MIN_SCORE = 30;


    public static final int NORMAL_MAX_SCORE = 59;


    public static final int ACTIVE_MIN_SCORE = 60;


    public static final int ACTIVE_MAX_SCORE = 79;


    public static final int SUPER_ACTIVE_MIN_SCORE = 80;




    public static final int CHECKIN_DAILY_SCORE = 2;                  
    public static final int CHECKIN_CONTINUOUS_SCORE = 2;                
    public static final int CHECKIN_ACHIEVEMENT_SCORE = 5;                 


    public static final int SOCIAL_FOLLOW_SCORE = 1;                   
    public static final int SOCIAL_COMMENT_SCORE = 3;                  
    public static final int SOCIAL_LIKE_SCORE = 1;                     
    public static final int SOCIAL_SHARE_SCORE = 2;                    
    public static final int SOCIAL_POST_SCORE = 5;                       


    public static final int CONSUMPTION_VIP_SCORE = 50;                
    public static final int CONSUMPTION_PAID_SCORE = 20;                
    public static final int CONSUMPTION_REWARD_SCORE = 10;          
    public static final int CONSUMPTION_AMOUNT_SCORE = 1;              


    public static final int CREATION_WORK_SCORE = 30;                 
    public static final int CREATION_LYRIC_SCORE = 10;                
    public static final int CREATION_REQUEST_SCORE = 5;               
    public static final int CREATION_APPROVED_SCORE = 20;                 


    public static final int CONTENT_LISTEN_MINUTE_SCORE = 1;            
    public static final int CONTENT_COMPLETE_SCORE = 5;               
    public static final int CONTENT_PLAYLIST_SCORE = 10;              
    public static final int CONTENT_MV_SCORE = 3;                     




    public static final String ACTIVITY_CACHE_KEY_PREFIX = "activity:enhanced:";


    public static final String RATE_LIMIT_CACHE_KEY_PREFIX = "rate_limit:";


    public static final String BEHAVIOR_RECORD_CACHE_KEY_PREFIX = "behavior:";


    public static final String SUSPICIOUS_USER_CACHE_KEY_PREFIX = "suspicious:";


    public static final long ACTIVITY_CACHE_EXPIRE_MINUTES = 30;


    public static final long BEHAVIOR_RECORD_CACHE_EXPIRE_DAYS = 7;




    public static final String BEHAVIOR_TYPE_LIKE = "like";


    public static final String BEHAVIOR_TYPE_COMMENT = "comment";


    public static final String BEHAVIOR_TYPE_SHARE = "share";


    public static final String BEHAVIOR_TYPE_POST = "post";


    public static final String BEHAVIOR_TYPE_FOLLOW = "follow";


    public static final String BEHAVIOR_TYPE_LISTEN = "listen";


    public static final String BEHAVIOR_TYPE_WATCH_MV = "watch_mv";




    public static final String DIMENSION_ALL = "all";


    public static final String DIMENSION_CHECKIN = "checkin";


    public static final String DIMENSION_SOCIAL = "social";


    public static final String DIMENSION_CONSUMPTION = "consumption";


    public static final String DIMENSION_CREATION = "creation";


    public static final String DIMENSION_CONTENT = "content";




    public static final String ACTIVITY_QUICK_CACHE_PREFIX = "activity:score:";


    public static final long ACTIVITY_QUICK_CACHE_EXPIRE_MINUTES = 30;


    public static final int QUICK_MONTH_CHECKIN_MAX_SCORE = 50;


    public static final int QUICK_MONTH_CHECKIN_PER_DAY_SCORE = 2;


    public static final int QUICK_CONTINUOUS_MAX_SCORE = 20;


    public static final int QUICK_CONTINUOUS_PER_DAY_SCORE = 2;


    public static final int QUICK_POINTS_MAX_SCORE = 20;


    public static final int QUICK_POINTS_PER_SCORE = 10;


    public static final int QUICK_TOTAL_CHECKIN_MAX_SCORE = 10;


    public static final int QUICK_TOTAL_CHECKIN_PER_SCORE = 10;


    public static final int QUICK_ACTIVE_MIN_SCORE = 60;


    public static final int QUICK_SUPER_ACTIVE_MIN_SCORE = 80;


    public static final int QUICK_NORMAL_MIN_SCORE = 30;




    public static final int SOCIAL_SCORE_MAX = 100;


    public static final int SOCIAL_FOLLOW_MAX_SCORE = 20;


    public static final int SOCIAL_COMMENT_MAX_SCORE = 40;


    public static final int SOCIAL_LIKE_MAX_SCORE = 30;


    public static final int SOCIAL_SHARE_MAX_SCORE = 10;


    public static final int SOCIAL_COMMENT_PER_SCORE = 2;


    public static final int SOCIAL_LIKE_PER_SCORE = 1;


    public static final int SOCIAL_SHARE_PER_SCORE = 2;


    public static final String ACTIVITY_LEVEL_INACTIVE = "inactive";


    public static final String ACTIVITY_LEVEL_NORMAL = "normal";


    public static final String ACTIVITY_LEVEL_ACTIVE = "active";


    public static final String ACTIVITY_LEVEL_SUPER_ACTIVE = "super_active";


    public static final String ACTIVITY_LEVEL_UNKNOWN = "unknown";


    private ActivityAntiSpamConstants() {
    }
}

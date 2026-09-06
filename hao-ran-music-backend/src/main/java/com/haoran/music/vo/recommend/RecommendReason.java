package com.haoran.music.vo.recommend;

import java.util.Random;





public enum RecommendReason {


    BECAUSE_YOU_LIKE("因为你喜欢《%s》", "因为你收藏了《%s》", "因为你经常听《%s》"),


    SIMILAR_TO("与《%s》风格相似", "和《%s》来自同一歌手"),


    USERS_ALSO_LIKE("喜欢《%s》的用户也喜欢", "和你品味相似的用户都在听"),


    POPULAR_NOW("当下热门", "本周最受欢迎", "大家都在听"),


    NEW_RELEASE("新歌速递", "%s的新作品", "为你发现的新歌"),


    DISCOVERY("每日发现", "探索新风格", "跳出舒适区"),


    MIX_FOR_YOU("为你精选", "根据你的品味推荐");

    private final String[] templates;
    private static final Random random = new Random();

    RecommendReason(String... templates) {
        this.templates = templates;
    }




    public String getRandomTemplate() {
        return templates[random.nextInt(templates.length)];
    }




    public String format(Object... args) {
        return String.format(getRandomTemplate(), args);
    }




    public String getSourceName() {
        switch (this) {
            case BECAUSE_YOU_LIKE:
            case USERS_ALSO_LIKE:
                return "个性化推荐";
            case SIMILAR_TO:
                return "相似推荐";
            case POPULAR_NOW:
                return "热门推荐";
            case NEW_RELEASE:
                return "新歌推荐";
            case DISCOVERY:
                return "发现推荐";
            case MIX_FOR_YOU:
                return "为你推荐";
            default:
                return "推荐";
        }
    }
}

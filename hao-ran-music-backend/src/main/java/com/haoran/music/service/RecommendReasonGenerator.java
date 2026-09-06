package com.haoran.music.service;

import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.Song;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.service.SongService;
import com.haoran.music.vo.recommend.RecommendReason;
import com.haoran.music.vo.recommend.RecommendVO;
import com.haoran.music.vo.recommend.RecommendedSongVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Random;





@Slf4j
@Component
public class RecommendReasonGenerator {

    @Resource
    private SongMapper songMapper;

    @Resource
    private SongService songService;

    private static final Random random = new Random();









    public String generateReason(Long userId, Long songId, String source) {
        if (ObjectUtils.isEmpty(source)) {
            source = "discovery";
        }

        switch (source) {
            case "collaborative":
                return RecommendReason.USERS_ALSO_LIKE.format("你喜欢的歌曲");
            case "similar":
                return generateSimilarReason(songId);
            case "hot":
                return generateHotReason();
            case "new":
                return generateNewReason(songId);
            case "discovery":
                return generateDiscoveryReason(userId);
            default:
                return "为你推荐";
        }
    }









    public Integer calculateConfidence(Long userId, Long songId, String source) {

        int baseConfidence = 70;
        switch (source) {
            case "collaborative":
                baseConfidence = 85;
                break;
            case "similar":
                baseConfidence = 80;
                break;
            case "hot":
                baseConfidence = 75;
                break;
            case "new":
                baseConfidence = 65;
                break;
            case "discovery":
                baseConfidence = 60;
                break;
        }

        return Math.min(100, Math.max(50, baseConfidence + random.nextInt(15) - 7));
    }




    private String generateSimilarReason(Long songId) {
        try {
            Song song = songMapper.selectById(songId);
            if (song != null) {
                return RecommendReason.SIMILAR_TO.format(song.getName());
            }
        } catch (Exception e) {
            log.error("生成相似推荐理由失败");
        }
        return "相似推荐";
    }




    private String generateHotReason() {
        return RecommendReason.POPULAR_NOW.format();
    }




    private String generateNewReason(Long songId) {
        return "新歌速递";
    }




    private String generateDiscoveryReason(Long userId) {
        return RecommendReason.DISCOVERY.format();
    }




    public String getSourceName(String source) {
        if (ObjectUtils.isEmpty(source)) {
            return "推荐";
        }
        switch (source) {
            case "collaborative":
                return "个性化推荐";
            case "similar":
                return "相似推荐";
            case "hot":
                return "热门推荐";
            case "new":
                return "新歌推荐";
            case "discovery":
                return "发现推荐";
            default:
                return "推荐";
        }
    }
}

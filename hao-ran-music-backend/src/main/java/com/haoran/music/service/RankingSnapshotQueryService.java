package com.haoran.music.service;

import com.haoran.music.entity.RankingSnapshot;
import com.haoran.music.mapper.RankingSnapshotItemMapper;
import com.haoran.music.mapper.RankingSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

   
                 
  
                      
   
@Service
@RequiredArgsConstructor
public class RankingSnapshotQueryService {
    private static final String HOT_SONG_7D = "hot_song_7d";
    private static final String ALL = "all";
    private static final int MAX_ITEMS = 100;

    private final RankingSnapshotMapper rankingSnapshotMapper;
    private final RankingSnapshotItemMapper rankingSnapshotItemMapper;

       
                           
      
                         
                                
  
    public List<Long> getActiveHotSongIds(Integer limit) {
        int safeLimit = limit == null ? MAX_ITEMS : Math.max(1, Math.min(limit, MAX_ITEMS));
        RankingSnapshot snapshot = rankingSnapshotMapper.selectActive(HOT_SONG_7D, ALL);
        if (snapshot == null || snapshot.getSnapshotId() == null
                || snapshot.getItemCount() == null || snapshot.getItemCount() <= 0) {
            return Collections.emptyList();
        }
        List<Long> ids = rankingSnapshotItemMapper.selectRankedSongIds(snapshot.getSnapshotId(), safeLimit);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }
}

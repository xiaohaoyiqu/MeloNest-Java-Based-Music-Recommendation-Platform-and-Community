package com.haoran.music.service;

import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.SongArtist;
import com.haoran.music.entity.SongCredit;
import com.haoran.music.mapper.SongCreditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

   
                      
  
                                  
  
                      
   
@Service
@RequiredArgsConstructor
public class SongCreditProjectionService {
    private static final String DISPLAY_ONLY = "display_only";
    private static final String ACCEPTED = "accepted";

    private final SongCreditMapper songCreditMapper;

       
                                  
      
                          
       
    @Transactional(rollbackFor = Exception.class)
    public void lockForDisplayCreditSync(Long songId) {
        if (ObjectUtils.isEmpty(songId) || songCreditMapper.lockSong(songId) == null) {
            throw new IllegalStateException("song unavailable for credit sync");
        }
    }

       
                                 
      
                          
                                
                             
                         
       
    @Transactional(rollbackFor = Exception.class)
    public void syncDisplayCredits(Long songId, List<SongArtist> relations,
                                   String sourceType, String reason) {
        if (ObjectUtils.isEmpty(songId)) {
            return;
        }
        if (songCreditMapper.lockSong(songId) == null) {
            throw new IllegalStateException("song unavailable for credit sync");
        }
        int version = songCreditMapper.selectNextVersion(songId);
        songCreditMapper.retireManagedDisplayCredits(songId);

        List<SongArtist> safeRelations = relations == null ? Collections.emptyList() : relations;
        LocalDateTime now = LocalDateTime.now();
        for (SongArtist relation : safeRelations) {
            if (relation == null || relation.getArtistId() == null) {
                continue;
            }
            SongCredit credit = new SongCredit();
            credit.setSongId(songId);
            credit.setArtistId(relation.getArtistId());
            credit.setRoleCode(toRoleCode(relation.getType()));
            credit.setCreditVersion(version);
            credit.setDisplayNameSnapshot(relation.getArtistName());
            credit.setSortOrder(relation.getSortOrder() == null ? 0 : relation.getSortOrder());
            credit.setSourceType(ObjectUtils.isEmpty(sourceType) ? "song_service" : sourceType);
            credit.setSourceId(songId);
            credit.setAcceptanceStatus(ACCEPTED);
            credit.setRightsScope(DISPLAY_ONLY);
            credit.setChangeReason(ObjectUtils.isEmpty(reason) ? "application display credit sync" : reason);
            credit.setValidFrom(now);
            credit.setCreateTime(now);
            credit.setUpdateTime(now);
            if (songCreditMapper.insert(credit) != 1) {
                throw new IllegalStateException("song credit projection insert conflict");
            }
        }
    }

       
                            
      
                          
       
    @Transactional(rollbackFor = Exception.class)
    public void retireDisplayCredits(Long songId) {
        if (ObjectUtils.isEmpty(songId) || songCreditMapper.lockSong(songId) == null) {
            return;
        }
        songCreditMapper.retireManagedDisplayCredits(songId);
    }

       
                       
      
                        
                   
       
    private String toRoleCode(Integer type) {
        if (type == null) {
            return "legacy_other";
        }
        switch (type) {
            case 1:
                return "primary";
            case 2:
                return "co_artist";
            case 3:
                return "lyricist";
            case 4:
                return "composer";
            case 5:
                return "producer";
            default:
                return "legacy_other";
        }
    }
}

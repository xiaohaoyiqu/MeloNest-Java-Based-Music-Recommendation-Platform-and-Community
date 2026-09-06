package com.haoran.music.mapper;

import com.haoran.music.entity.QualifiedPlayFact;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

   
                    
  
                      
   
@Mapper
public interface QualifiedPlayFactMapper {
       
                       
      
                       
                        
       
    @Insert("INSERT IGNORE INTO qualified_play_fact "
            + "(event_id, user_id, song_id, progress_seconds, duration_seconds, policy_version, "
            + "fact_status, occurred_at, create_time) VALUES "
            + "(#{fact.eventId}, #{fact.userId}, #{fact.songId}, #{fact.progressSeconds}, "
            + "#{fact.durationSeconds}, #{fact.policyVersion}, 'valid', NOW(), NOW())")
    int insertIgnore(@Param("fact") QualifiedPlayFact fact);
}

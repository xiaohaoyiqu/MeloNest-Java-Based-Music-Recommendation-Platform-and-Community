package com.haoran.music.mapper;

import com.haoran.music.entity.BadgeGrantEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

   
                       
  
                      
   
@Mapper
public interface BadgeGrantEventMapper {

       
                  
      
                      
                         
       
    @Insert("INSERT IGNORE INTO badge_grant_event "
            + "(event_id, business_key, user_id, badge_type, rule_version, action, evidence_type, "
            + "evidence_id, evidence_summary, operator_id, reason, create_time) VALUES "
            + "(#{event.eventId}, #{event.businessKey}, #{event.userId}, #{event.badgeType}, "
            + "#{event.ruleVersion}, #{event.action}, #{event.evidenceType}, #{event.evidenceId}, "
            + "#{event.evidenceSummary}, #{event.operatorId}, #{event.reason}, NOW())")
    int insertIgnore(@Param("event") BadgeGrantEvent event);

       
                    
      
                             
                   
       
    @Select("SELECT * FROM badge_grant_event WHERE business_key = #{businessKey} LIMIT 1")
    BadgeGrantEvent selectByBusinessKey(@Param("businessKey") String businessKey);
}

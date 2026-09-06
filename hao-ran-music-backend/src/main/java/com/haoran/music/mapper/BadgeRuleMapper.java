package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.BadgeRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

   
             
  
                      
   
@Mapper
public interface BadgeRuleMapper extends BaseMapper<BadgeRule> {

       
                           
      
                            
                      
                   
       
    @Select("SELECT * FROM badge_rule WHERE badge_type = #{badgeType} AND status = 'published' "
            + "AND (valid_from IS NULL OR valid_from <= #{now}) "
            + "AND (valid_to IS NULL OR valid_to > #{now}) "
            + "ORDER BY rule_version DESC LIMIT 1")
    BadgeRule selectActiveByType(@Param("badgeType") String badgeType,
                                 @Param("now") LocalDateTime now);

       
                             
      
                      
                     
       
    @Select("SELECT r.* FROM badge_rule r WHERE r.status = 'published' "
            + "AND (r.valid_from IS NULL OR r.valid_from <= #{now}) "
            + "AND (r.valid_to IS NULL OR r.valid_to > #{now}) "
            + "AND r.rule_version = (SELECT MAX(r2.rule_version) FROM badge_rule r2 "
            + "WHERE r2.badge_type = r.badge_type AND r2.status = 'published' "
            + "AND (r2.valid_from IS NULL OR r2.valid_from <= #{now}) "
            + "AND (r2.valid_to IS NULL OR r2.valid_to > #{now})) "
            + "ORDER BY r.category, r.badge_type LIMIT 200")
    List<BadgeRule> selectActiveRules(@Param("now") LocalDateTime now);

       
                        
      
                            
                   
       
    @Select("SELECT * FROM badge_rule WHERE replacement_of = #{ruleId} AND status = 'published' "
            + "ORDER BY rule_version DESC, id DESC LIMIT 1")
    BadgeRule selectPublishedReplacement(@Param("ruleId") Long ruleId);
}

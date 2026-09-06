   
                      
   
package com.haoran.music.mapper;

import com.haoran.music.dto.user.RfmAggregateDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

   
             
   
@Mapper
public interface RfmAnalysisMapper {

    @Select({
            "<script>",
            "SELECT u.id AS user_id, u.username AS username,",
            "       u.status AS user_status, u.is_banned AS user_banned,",
            "       u.user_type AS user_type, u.risk_score AS risk_score,",
            "       u.credit_score AS credit_score, u.creator_status AS creator_status,",
            "       vip.last_vip_time AS last_vip_time,",
            "       COALESCE(vip.vip_count, 0) AS vip_count,",
            "       COALESCE(vip.vip_amount, 0) AS vip_amount,",
            "       checkin.last_checkin_time AS last_checkin_time,",
            "       COALESCE(checkin.checkin_count, 0) AS checkin_count",
            "FROM `user` u",
            "LEFT JOIN (",
            "    SELECT ov.user_id, MAX(ov.start_time) AS last_vip_time,",
            "           COUNT(*) AS vip_count, COALESCE(SUM(ov.amount), 0) AS vip_amount",
            "    FROM order_vip ov",
            "    WHERE ov.status = 'active'",
            "    <if test='userIds != null and userIds.size() > 0'>",
            "      AND ov.user_id IN",
            "      <foreach collection='userIds' item='userId' open='(' separator=',' close=')'>",
            "        #{userId}",
            "      </foreach>",
            "    </if>",
            "    GROUP BY ov.user_id",
            ") vip ON vip.user_id = u.id",
            "LEFT JOIN (",
            "    SELECT uc.user_id, MAX(uc.checkin_time) AS last_checkin_time,",
            "           COUNT(*) AS checkin_count",
            "    FROM user_checkin uc",
            "    <if test='userIds != null and userIds.size() > 0'>",
            "      WHERE uc.user_id IN",
            "      <foreach collection='userIds' item='userId' open='(' separator=',' close=')'>",
            "        #{userId}",
            "      </foreach>",
            "    </if>",
            "    GROUP BY uc.user_id",
            ") checkin ON checkin.user_id = u.id",
            "WHERE u.deleted = 0",
            "<if test='userIds != null and userIds.size() > 0'>",
            "  AND u.id IN",
            "  <foreach collection='userIds' item='userId' open='(' separator=',' close=')'>",
            "    #{userId}",
            "  </foreach>",
            "</if>",
            "</script>"
    })
    List<RfmAggregateDTO> selectAggregates(@Param("userIds") Collection<Long> userIds);
}

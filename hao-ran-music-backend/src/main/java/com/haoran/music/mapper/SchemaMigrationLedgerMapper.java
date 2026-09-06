


package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.SchemaMigrationLedger;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SchemaMigrationLedgerMapper extends BaseMapper<SchemaMigrationLedger> {

    @Select("SELECT COUNT(*) AS scriptCount, MAX(applied_at) AS latestAppliedAt "
            + "FROM schema_migration_ledger")
    Map<String, Object> selectLedgerSummary();

    @Select("SELECT id, script_name AS scriptName, checksum, applied_at AS appliedAt, "
            + "applied_by AS appliedBy, note, create_time AS createTime, update_time AS updateTime "
            + "FROM schema_migration_ledger ORDER BY applied_at DESC, id DESC LIMIT #{limit}")
    List<Map<String, Object>> selectRecent(@Param("limit") Integer limit);

    @Insert("INSERT INTO schema_migration_ledger "
            + "(script_name, checksum, applied_at, applied_by, note, create_time, update_time) "
            + "VALUES (#{scriptName}, #{checksum}, NOW(), #{appliedBy}, #{note}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE checksum = VALUES(checksum), applied_at = VALUES(applied_at), "
            + "applied_by = VALUES(applied_by), note = VALUES(note), update_time = NOW()")
    int upsert(@Param("scriptName") String scriptName,
               @Param("checksum") String checksum,
               @Param("appliedBy") String appliedBy,
               @Param("note") String note);
}

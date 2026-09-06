





SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT ;
SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS ;
SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION ;
SET NAMES utf8mb4 ;
SET @OLD_TIME_ZONE=@@TIME_ZONE ;
SET TIME_ZONE='+00:00' ;
SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 ;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 ;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' ;
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 ;


CREATE DATABASE IF NOT EXISTS `haoranmusic_bus` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci ;

USE `haoranmusic_bus`;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `album` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name_en` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '英文名',
  `original_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原名/外文名',
  `artist_id` bigint(20) DEFAULT NULL,
  `artist_ids` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `artist_names` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cover` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `release_date` date DEFAULT NULL,
  `country` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `province` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `region` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `genres` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `language` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `song_count` bigint(20) DEFAULT '0' COMMENT '姝屾洸鏁',
  `play_count` bigint(20) DEFAULT '0',
  `favorite_count` bigint(20) DEFAULT '0' COMMENT '鏀惰棌娆℃暟',
  `comment_count` bigint(20) DEFAULT '0' COMMENT '璇勮?娆℃暟',
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `priority` int(11) DEFAULT '0' COMMENT '浼樺厛绾',
  `is_paid` tinyint(4) DEFAULT '0' COMMENT '鏄?惁浠樿垂',
  `paid_resource_id` bigint(20) DEFAULT NULL COMMENT '浠樿垂璧勬簮ID',
  `price` decimal(10,2) DEFAULT NULL COMMENT '浠锋牸',
  `allow_download` tinyint(1) DEFAULT '1' COMMENT '鏄?惁鍏佽?涓嬭浇锛?-鍚︼紝1-鏄?級',
  `allow_comment` tinyint(1) DEFAULT '1' COMMENT '鏄?惁鍏佽?璇勮?锛?-鍚︼紝1-鏄?級',
  `allow_share` tinyint(1) DEFAULT '1' COMMENT '鏄?惁鍏佽?鍒嗕韩锛?-鍚︼紝1-鏄?級',
  PRIMARY KEY (`id`),
  KEY `idx_release` (`status`,`deleted`,`release_date`),
  KEY `idx_artist_status` (`artist_id`,`status`,`deleted`),
  KEY `idx_hot_album` (`play_count`,`status`,`deleted`),
  FULLTEXT KEY `ft_album` (`name`,`description`) WITH PARSER `ngram`  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `album_favorite` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `album_id` bigint(20) NOT NULL COMMENT '专辑ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_album_id` (`album_id`),
  KEY `idx_user_create_time` (`user_id`,`create_time`),
  KEY `idx_user_album_deleted` (`user_id`,`album_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专辑收藏表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client      = @@character_set_client  ;
SET @saved_cs_results     = @@character_set_results  ;
SET @saved_col_connection = @@collation_connection  ;
SET character_set_client  = utf8mb4  ;
SET character_set_results = utf8mb4  ;
SET collation_connection  = utf8mb4_general_ci  ;
SET @saved_sql_mode       = @@sql_mode  ;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;
DELIMITER ;;
CREATE DEFINER=CURRENT_USER TRIGGER trg_album_favorite_insert
AFTER INSERT ON album_favorite
FOR EACH ROW
BEGIN
    IF NEW.deleted = 0 THEN
        UPDATE album SET favorite_count = favorite_count + 1 WHERE id = NEW.album_id;
    END IF;
END ;;
DELIMITER ;
SET sql_mode              = @saved_sql_mode  ;
SET character_set_client  = @saved_cs_client  ;
SET character_set_results = @saved_cs_results  ;
SET collation_connection  = @saved_col_connection  ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `appeal` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '鐢宠瘔ID',
  `user_id` bigint(20) NOT NULL COMMENT '鐢宠瘔鐢ㄦ埛ID',
  `appeal_type` varchar(50) NOT NULL COMMENT '鐢宠瘔绫诲瀷锛欱AN-璐﹀彿灏佺?鐢宠瘔锛孋REATOR-鍒涗綔鑰呰祫璐ㄧ敵璇夛紝CONTENT-鍐呭?杩濊?鐢宠瘔',
  `appeal_reason` text COMMENT '鐢宠瘔鐞嗙敱',
  `appeal_status` varchar(20) DEFAULT 'PENDING' COMMENT '鐘舵?锛歅ENDING-寰呭?鏍革紝APPROVED-宸查?杩囷紝REJECTED-宸叉嫆缁',
  `related_id` bigint(20) DEFAULT NULL COMMENT '鍏宠仈ID锛堝皝绂佽?褰旾D/鍒涗綔鑰呯敵璇稩D/鍐呭?ID锛',
  `evidence_urls` text COMMENT '璇佹嵁鏉愭枡URLs锛圝SON鏁扮粍锛',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '瀹℃牳浜篒D',
  `review_result` text COMMENT '瀹℃牳缁撴灉',
  `review_time` datetime DEFAULT NULL COMMENT '瀹℃牳鏃堕棿',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`appeal_status`),
  KEY `idx_type` (`appeal_type`),
  KEY `idx_reviewer` (`reviewer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢宠瘔琛';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `appeal_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '璁板綍ID',
  `appeal_id` bigint(20) NOT NULL COMMENT '鐢宠瘔ID',
  `operator_id` bigint(20) DEFAULT NULL COMMENT '鎿嶄綔浜篒D',
  `operator_type` varchar(20) DEFAULT NULL COMMENT '鎿嶄綔浜虹被鍨嬶細USER-鐢ㄦ埛锛孉DMIN-绠＄悊鍛',
  `action` varchar(50) DEFAULT NULL COMMENT '鎿嶄綔绫诲瀷锛欳REATE-鍒涘缓锛孉PPROVE-閫氳繃锛孯EJECT-鎷掔粷锛孋ANCEL-鍙栨秷',
  `action_remark` text COMMENT '鎿嶄綔澶囨敞',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鎿嶄綔鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_appeal_id` (`appeal_id`),
  KEY `idx_operator` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢宠瘔璁板綍琛';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `artist` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `original_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原名/外文名',
  `name_en` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '英文名',
  `avatar` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cover` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `gender` tinyint(4) DEFAULT NULL,
  `birth_date` date DEFAULT NULL,
  `location` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` int(11) DEFAULT '0',
  `genres` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `area` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `first_letter` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fans_count` bigint(20) DEFAULT '0' COMMENT '绮変笣鏁',
  `song_count` bigint(20) DEFAULT '0' COMMENT '姝屾洸鏁',
  `album_count` bigint(20) DEFAULT '0' COMMENT '涓撹緫鏁',
  `play_count` bigint(20) DEFAULT '0',
  `comment_count` bigint(20) DEFAULT '0' COMMENT '璇勮?娆℃暟',
  `hot_score` int(11) DEFAULT '0' COMMENT '热度分数',
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_status_deleted` (`status`,`deleted`),
  KEY `idx_area_status` (`area`,`status`,`deleted`),
  KEY `idx_type_status` (`type`,`status`,`deleted`),
  KEY `idx_status_deleted_fans` (`status`,`deleted`,`fans_count`),
  FULLTEXT KEY `ft_artist` (`name`) WITH PARSER `ngram`  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `artist_application` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `user_name` varchar(100) DEFAULT NULL COMMENT '用户名',
  `real_name` varchar(100) DEFAULT NULL COMMENT '真实姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `introduction` text COMMENT '个人简介',
  `demo_works` text COMMENT '作品链接（JSON数组）',
  `application_type` tinyint(4) DEFAULT '1' COMMENT '申请类型：1-歌手申请，2-制作人申请',
  `status` tinyint(4) DEFAULT '0' COMMENT '审核状态：0-待审核，1-已通过，2-已拒绝',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核人ID',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_reason` varchar(500) DEFAULT NULL COMMENT '审核意见',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌手/创作者申请表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `artist_claim` (
  `id` bigint(20) NOT NULL COMMENT 'Snowflake primary key',
  `artist_id` bigint(20) NOT NULL COMMENT 'artist.id',
  `user_id` bigint(20) NOT NULL COMMENT 'user.id',
  `status` varchar(20) NOT NULL COMMENT 'pending/approved/rejected/revoked',
  `source_type` varchar(40) NOT NULL COMMENT 'claim creation source',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT 'server-authenticated reviewer',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'logical delete',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_artist_claim_user` (`user_id`),
  UNIQUE KEY `uk_artist_claim_artist` (`artist_id`),
  KEY `idx_artist_claim_status` (`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户与不可变歌手档案认领关系';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `artist_song_relation_backup_20260830` (
  `song_id` bigint(20) NOT NULL,
  `old_song_artist_id` bigint(20) DEFAULT NULL,
  `old_song_artist_ids` varchar(500) DEFAULT NULL,
  `old_song_artist_names` varchar(500) DEFAULT NULL,
  `relation_id` bigint(20) DEFAULT NULL,
  `old_relation_artist_id` bigint(20) DEFAULT NULL,
  `old_relation_artist_name` varchar(200) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`song_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='2026-08-30 artist/song relation recovery snapshot';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `audit_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `operator_id` bigint(20) NOT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人姓名',
  `operation_type` varchar(50) NOT NULL COMMENT '操作类型',
  `target_type` varchar(50) NOT NULL COMMENT '目标类型',
  `target_id` bigint(20) NOT NULL COMMENT '目标ID',
  `old_value` text COMMENT '修改前的值（JSON）',
  `new_value` text COMMENT '修改后的值（JSON）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `ip_address` varchar(50) DEFAULT NULL COMMENT '操作IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `duration_seconds` int(11) DEFAULT NULL COMMENT '??????',
  `success` tinyint(1) NOT NULL DEFAULT '1' COMMENT '????',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_target` (`target_type`,`target_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_audit_log_time_action` (`create_time`,`operation_type`),
  KEY `idx_audit_log_time_target` (`create_time`,`target_type`),
  KEY `idx_audit_log_operator_time` (`operator_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核日志表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `badge_grant_event` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '内部主键',
  `event_id` char(36) NOT NULL COMMENT '公开稳定事件ID',
  `business_key` varchar(255) NOT NULL COMMENT '幂等业务键',
  `user_id` bigint(20) NOT NULL,
  `badge_type` varchar(64) NOT NULL,
  `rule_version` int(11) NOT NULL DEFAULT '0',
  `action` varchar(16) NOT NULL COMMENT 'grant/revoke',
  `evidence_type` varchar(64) NOT NULL,
  `evidence_id` varchar(128) NOT NULL,
  `evidence_summary` varchar(500) DEFAULT NULL COMMENT '最小非敏感摘要',
  `operator_id` bigint(20) DEFAULT NULL COMMENT '管理员操作时来自认证上下文',
  `reason` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_badge_grant_event_id` (`event_id`),
  UNIQUE KEY `uk_badge_grant_business` (`business_key`),
  KEY `idx_badge_grant_user_time` (`user_id`,`create_time`),
  KEY `idx_badge_grant_type_time` (`badge_type`,`create_time`),
  KEY `idx_badge_grant_operator_time` (`operator_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='徽章授予撤销只追加事实';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `badge_rule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '规则主键',
  `badge_type` varchar(64) NOT NULL COMMENT '稳定徽章类型',
  `rule_version` int(11) NOT NULL COMMENT '同类型单调递增版本',
  `badge_name` varchar(100) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `obtain_method` varchar(500) DEFAULT NULL,
  `category` varchar(32) NOT NULL,
  `rarity` varchar(32) NOT NULL DEFAULT 'common',
  `icon_fallback` varchar(255) DEFAULT NULL COMMENT '素材不可用时的安全占位',
  `badge_color` varchar(32) DEFAULT NULL,
  `default_position` varchar(16) NOT NULL DEFAULT 'name',
  `trigger_type` varchar(64) NOT NULL COMMENT '服务端白名单触发器',
  `threshold_value` bigint(20) DEFAULT NULL,
  `evidence_type` varchar(64) NOT NULL COMMENT '可信证据类型',
  `valid_from` datetime DEFAULT NULL,
  `valid_to` datetime DEFAULT NULL,
  `visibility` varchar(16) NOT NULL DEFAULT 'public' COMMENT 'public/private',
  `asset_id` bigint(20) DEFAULT NULL COMMENT 'media_asset公开图片',
  `replacement_of` bigint(20) DEFAULT NULL COMMENT '被本规则替代的规则ID',
  `grant_days` int(11) DEFAULT NULL COMMENT 'NULL为永久',
  `status` varchar(16) NOT NULL DEFAULT 'draft' COMMENT 'draft/published/retired',
  `created_by` bigint(20) DEFAULT NULL,
  `change_reason` varchar(500) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_badge_rule_type_version` (`badge_type`,`rule_version`),
  KEY `idx_badge_rule_active` (`status`,`valid_from`,`valid_to`),
  KEY `idx_badge_rule_replacement` (`replacement_of`,`status`),
  KEY `idx_badge_rule_asset` (`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本化徽章规则';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `comment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `target_type` tinyint(4) NOT NULL COMMENT '1-歌曲,2-专辑,3-歌单,4-MV',
  `target_id` bigint(20) NOT NULL,
  `parent_id` bigint(20) DEFAULT NULL,
  `reply_user_id` bigint(20) DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `like_count` bigint(20) DEFAULT '0' COMMENT '鐐硅禐鏁',
  `reply_count` bigint(20) DEFAULT '0' COMMENT '鍥炲?鏁',
  `is_pinned` tinyint(4) DEFAULT '0',
  `ip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `edit_count` bigint(20) DEFAULT '0' COMMENT '已编辑次数，最多3次',
  PRIMARY KEY (`id`),
  KEY `idx_target_status` (`target_type`,`target_id`,`parent_id`,`status`,`deleted`),
  KEY `idx_parent_create_time` (`parent_id`,`create_time`),
  KEY `idx_target_create_time` (`target_type`,`target_id`,`create_time`),
  KEY `idx_edit_count` (`edit_count`),
  KEY `idx_target_user_time` (`target_type`,`target_id`,`create_time`,`deleted`),
  KEY `idx_comment_target_state_parent` (`target_type`,`target_id`,`status`,`deleted`,`parent_id`),
  KEY `idx_comment_parent_state` (`parent_id`,`status`,`deleted`),
  KEY `idx_comment_target_deleted_time` (`target_type`,`deleted`,`create_time`,`target_id`,`parent_id`,`like_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `comment_edit_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '鍘嗗彶璁板綍ID',
  `comment_id` bigint(20) NOT NULL COMMENT '璇勮?ID',
  `original_content` text COMMENT '鍘熷?鍐呭?',
  `edited_content` text COMMENT '缂栬緫鍚庡唴瀹',
  `editor_id` bigint(20) NOT NULL COMMENT '缂栬緫鐢ㄦ埛ID',
  `editor_nickname` varchar(50) DEFAULT NULL COMMENT '缂栬緫鐢ㄦ埛鏄电О',
  `editor_ip` varchar(50) DEFAULT NULL COMMENT '缂栬緫IP鍦板潃锛堣劚鏁忥級',
  `edit_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '缂栬緫鏃堕棿',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎锛?-鏈?垹闄わ紝1-宸插垹闄わ級',
  PRIMARY KEY (`id`),
  KEY `idx_comment_id` (`comment_id`),
  KEY `idx_editor_id` (`editor_id`),
  KEY `idx_edit_time` (`edit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='璇勮?缂栬緫鍘嗗彶琛';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `comment_like` (
  `id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `comment_id` bigint(20) NOT NULL COMMENT '评论ID',
  `is_like` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否点赞(0-取消,1-点赞)',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删除,1-已删除)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_comment` (`user_id`,`comment_id`,`deleted`),
  KEY `idx_comment_id` (`comment_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_comment_like_comment_state` (`comment_id`,`is_like`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论点赞表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `comment_quality_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `criteria_name` varchar(100) NOT NULL COMMENT '标准名称',
  `min_length` int(11) DEFAULT '0' COMMENT '最小长度（字符）',
  `min_likes` int(11) DEFAULT '0' COMMENT '最低点赞数',
  `max_days` int(11) DEFAULT '7' COMMENT '有效天数',
  `reward_score` int(11) DEFAULT '1' COMMENT '奖励分数',
  `description` varchar(500) DEFAULT NULL COMMENT '标准描述',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_criteria_name` (`criteria_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优质评论判定标准配置表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `conversation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '浼氳瘽ID',
  `user_a_id` bigint(20) NOT NULL COMMENT '鐢ㄦ埛A鐨処D锛堣緝灏忕殑ID锛',
  `user_b_id` bigint(20) NOT NULL COMMENT '鐢ㄦ埛B鐨処D锛堣緝澶х殑ID锛',
  `last_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鏈?悗涓?潯娑堟伅鍐呭?',
  `last_message_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'text' COMMENT '鏈?悗涓?潯娑堟伅绫诲瀷',
  `last_message_time` datetime DEFAULT NULL COMMENT '鏈?悗涓?潯娑堟伅鏃堕棿',
  `user_a_unread_count` int(11) NOT NULL DEFAULT '0' COMMENT '鐢ㄦ埛A鐨勬湭璇绘秷鎭?暟',
  `user_b_unread_count` int(11) NOT NULL DEFAULT '0' COMMENT '鐢ㄦ埛B鐨勬湭璇绘秷鎭?暟',
  `user_a_pinned` tinyint(4) NOT NULL DEFAULT '0' COMMENT '鐢ㄦ埛A鏄?惁缃?《: 0-涓嶇疆椤? 1-缃?《',
  `user_b_pinned` tinyint(4) NOT NULL DEFAULT '0' COMMENT '鐢ㄦ埛B鏄?惁缃?《: 0-涓嶇疆椤? 1-缃?《',
  `user_a_blocked` tinyint(4) NOT NULL DEFAULT '0' COMMENT '鐢ㄦ埛A鏄?惁灞忚斀: 0-姝ｅ父, 1-灞忚斀',
  `user_b_blocked` tinyint(4) NOT NULL DEFAULT '0' COMMENT '鐢ㄦ埛B鏄?惁灞忚斀: 0-姝ｅ父, 1-灞忚斀',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '浼氳瘽鐘舵?: active-娲昏穬, archived-宸插綊妗',
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users` (`user_a_id`,`user_b_id`),
  KEY `idx_user_a` (`user_a_id`,`is_deleted`),
  KEY `idx_user_b` (`user_b_id`,`is_deleted`),
  KEY `idx_last_message_time` (`last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绉佷俊浼氳瘽琛';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `creator` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `user_id` bigint(20) NOT NULL COMMENT '??ID',
  `creator_type` varchar(30) DEFAULT NULL COMMENT '?????',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT '??',
  `fans_count` bigint(20) NOT NULL DEFAULT '0' COMMENT '???',
  `creator_note` varchar(500) DEFAULT NULL COMMENT '????',
  `creator_apply_time` datetime DEFAULT NULL COMMENT '????',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '????',
  PRIMARY KEY (`id`),
  KEY `idx_creator_user` (`user_id`,`deleted`),
  KEY `idx_creator_status` (`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者身份表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `creator_album` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '专辑ID',
  `user_id` bigint(20) NOT NULL COMMENT '创作者用户ID',
  `album_name` varchar(200) NOT NULL COMMENT '专辑名称',
  `album_type` tinyint(4) DEFAULT '1' COMMENT '专辑类型：1-专辑 2-EP 3-单曲合集 4-Remix',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面图片URL',
  `description` text COMMENT '专辑描述',
  `tags` varchar(500) DEFAULT NULL COMMENT '标签（逗号分隔）',
  `language` tinyint(4) DEFAULT '1' COMMENT '语言类型：1-中文 2-英语 3-日语 4-韩语 5-其他',
  `release_date` date DEFAULT NULL COMMENT '发行日期',
  `status` tinyint(4) DEFAULT '0' COMMENT '状态：0-草稿 1-已发布 2-已下架',
  `is_publish_date_set` tinyint(4) DEFAULT '0' COMMENT '是否设置发行日期',
  `auto_create_song` tinyint(4) DEFAULT '1' COMMENT '是否自动创建歌曲记录',
  `song_count` int(11) DEFAULT '0' COMMENT '包含歌曲数量',
  `total_duration` int(11) DEFAULT '0' COMMENT '总时长（秒）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者专辑表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `creator_album_song` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `album_id` bigint(20) NOT NULL COMMENT '专辑ID',
  `song_id` bigint(20) DEFAULT NULL COMMENT '关联的歌曲ID（自动创建或手动关联）',
  `song_name` varchar(200) DEFAULT NULL COMMENT '歌曲名称（冗余字段，便于查询）',
  `position` int(11) DEFAULT '0' COMMENT '在专辑中的位置',
  `is_single` tinyint(4) DEFAULT '1' COMMENT '是否为单曲：0-专辑歌曲 1-单独发布的单曲',
  `audio_url` varchar(500) DEFAULT NULL COMMENT '音频文件URL（单独上传的歌曲）',
  `audio_quality` tinyint(4) DEFAULT NULL COMMENT '音质等级：1-标准 2-高品质 3-无损 4-Hi-Res 5-母带',
  `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小（字节）',
  `duration` int(11) DEFAULT NULL COMMENT '时长（秒）',
  `bitrate` int(11) DEFAULT NULL COMMENT '比特率（kbps）',
  `sample_rate` int(11) DEFAULT NULL COMMENT '采样率（Hz）',
  `format` varchar(10) DEFAULT NULL COMMENT '文件格式：mp3/flac/wav等',
  `upload_type` tinyint(4) DEFAULT '1' COMMENT '上传方式：1-单文件 2-多文件 3-压缩包',
  `file_urls` text COMMENT '多文件URL（JSON数组）',
  `zip_file_url` varchar(500) DEFAULT NULL COMMENT '压缩包文件URL',
  `source` varchar(50) DEFAULT 'creator_upload' COMMENT '来源：creator_upload/user_upload/admin_add',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '????',
  PRIMARY KEY (`id`),
  KEY `idx_album_id` (`album_id`),
  KEY `idx_song_id` (`song_id`),
  KEY `idx_position` (`position`),
  KEY `idx_creator_album_song_album` (`album_id`,`position`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者专辑歌曲关联表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `creator_apply` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `real_name` varchar(50) NOT NULL COMMENT '真实姓名',
  `id_card` varchar(18) NOT NULL COMMENT '身份证号（加密）',
  `id_card_masked` varchar(18) DEFAULT NULL COMMENT '身份证号（脱敏，对外展示）',
  `id_card_url` varchar(500) DEFAULT NULL COMMENT '身份证图片URL',
  `phone` varchar(20) NOT NULL COMMENT '联系电话',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `artist_name` varchar(100) NOT NULL COMMENT '艺名',
  `artist_type` varchar(20) DEFAULT 'singer' COMMENT '艺术家类型',
  `description` text COMMENT '个人简介',
  `works_demo` varchar(500) DEFAULT NULL COMMENT '作品链接',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核人ID',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_result` varchar(500) DEFAULT NULL COMMENT '审核结果',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_creator_apply_user_time` (`user_id`,`create_time`),
  KEY `idx_creator_apply_status_time` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者申请表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `creator_debt` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '创作者用户ID',
  `refund_id` bigint(20) DEFAULT NULL,
  `withdraw_id` bigint(20) DEFAULT NULL,
  `debt_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `paid_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `remaining_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `status` varchar(20) NOT NULL DEFAULT 'pending',
  `reason` varchar(500) DEFAULT NULL,
  `cleared_time` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `total_earnings` bigint(20) DEFAULT '0' COMMENT '总收益（分）',
  `withdrawn_amount` bigint(20) DEFAULT '0' COMMENT '已提现金额（分）',
  `frozen_amount` bigint(20) DEFAULT '0' COMMENT '冻结金额（分）',
  `available_amount` bigint(20) DEFAULT '0' COMMENT '可提现金额（分）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_creator_debt_withdraw_refund_fact` (`refund_id`,`withdraw_id`),
  KEY `idx_creator_debt_user_status_time` (`user_id`,`status`,`create_time`),
  KEY `idx_creator_debt_refund` (`refund_id`),
  KEY `idx_creator_debt_withdraw` (`withdraw_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者债务表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `creator_earnings` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '创作者用户ID',
  `work_id` bigint(20) NOT NULL COMMENT '作品ID',
  `work_type` varchar(20) NOT NULL COMMENT '作品类型',
  `earnings_type` varchar(20) NOT NULL COMMENT '收益类型',
  `earnings_amount` bigint(20) NOT NULL COMMENT '收益金额（分）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_work_id` (`work_id`),
  KEY `idx_creator_earnings_user_deleted_time` (`user_id`,`deleted`,`create_time`),
  KEY `idx_creator_earnings_user_type_deleted` (`user_id`,`earnings_type`,`deleted`),
  KEY `idx_creator_earnings_store_order` (`work_type`,`work_id`,`earnings_type`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者收益表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `creator_earnings_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `creator_id` bigint(20) NOT NULL,
  `order_id` bigint(20) NOT NULL,
  `order_type` varchar(20) DEFAULT NULL,
  `amount` decimal(10,2) NOT NULL,
  `platform_fee` decimal(10,2) DEFAULT '0.00',
  `fee_rate` decimal(5,4) DEFAULT '0.0200',
  `creator_earnings` decimal(10,2) NOT NULL,
  `is_withdrawn` tinyint(1) DEFAULT '0',
  `withdraw_id` bigint(20) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_creator` (`creator_id`,`is_withdrawn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者收益表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `creator_eligibility_outbox` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `creator_id` bigint(20) NOT NULL COMMENT 'user.id',
  `event_version` bigint(20) NOT NULL COMMENT '资格单调版本',
  `schema_version` int(11) NOT NULL DEFAULT '1' COMMENT '事件结构版本',
  `event_type` varchar(32) NOT NULL COMMENT 'activated/status_changed/removed',
  `old_status` varchar(32) DEFAULT NULL COMMENT '变化前状态，仅供审计',
  `new_status` varchar(32) DEFAULT NULL COMMENT '变化后状态，仅供审计',
  `is_creator` tinyint(1) NOT NULL COMMENT '事件时资格快照，仅供审计',
  `creator_type` varchar(32) DEFAULT NULL COMMENT '事件时创作者类型',
  `operator_id` bigint(20) DEFAULT NULL COMMENT '服务端认证操作者ID',
  `reason` varchar(500) DEFAULT NULL COMMENT '变更原因',
  `status` varchar(16) NOT NULL COMMENT 'pending/processing/success/failed',
  `attempt_count` int(11) NOT NULL DEFAULT '0' COMMENT '已领取次数',
  `max_attempts` int(11) NOT NULL DEFAULT '5' COMMENT '最大尝试次数',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '最近失败原因',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下一次重试时间',
  `started_at` datetime DEFAULT NULL COMMENT '最近开始时间',
  `completed_at` datetime DEFAULT NULL COMMENT '最近完成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_creator_eligibility_event_version` (`creator_id`,`event_version`),
  KEY `idx_creator_eligibility_due` (`status`,`next_retry_time`,`attempt_count`,`create_time`),
  KEY `idx_creator_eligibility_processing` (`status`,`started_at`,`attempt_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者资格事务outbox';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `creator_external_apply` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `apply_no` varchar(64) DEFAULT NULL COMMENT '????',
  `real_name` varchar(100) NOT NULL COMMENT '真实姓名',
  `id_card` varchar(18) DEFAULT NULL COMMENT '身份证号（完整）',
  `id_card_masked` varchar(18) DEFAULT NULL COMMENT '身份证号（脱敏）',
  `id_card_url` varchar(500) DEFAULT NULL COMMENT '身份证图片URL',
  `phone` varchar(20) NOT NULL COMMENT '联系电话',
  `email` varchar(100) NOT NULL COMMENT '邮箱',
  `creator_name` varchar(100) DEFAULT NULL COMMENT '?????',
  `external_platform` varchar(100) DEFAULT NULL COMMENT '????',
  `external_homepage` varchar(500) DEFAULT NULL COMMENT '??????',
  `works_description` text COMMENT '????',
  `cooperation_type` varchar(30) DEFAULT NULL COMMENT '????: independent/contract',
  `expected_fee_rate` decimal(5,4) DEFAULT NULL COMMENT '??????',
  `attachment_urls` text COMMENT '??URL?JSON??',
  `platform_name` varchar(100) DEFAULT NULL COMMENT '所属平台/公司',
  `introduction` text COMMENT '个人简介',
  `demo_works` text COMMENT '作品链接（JSON）',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核人ID',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_reason` varchar(500) DEFAULT NULL COMMENT '????',
  `linked_user_id` bigint(20) DEFAULT NULL COMMENT '????????ID',
  `contract_file` varchar(500) DEFAULT NULL COMMENT '????URL',
  `review_result` varchar(500) DEFAULT NULL COMMENT '审核结果',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_creator_external_apply_status_time` (`status`,`create_time`),
  KEY `idx_creator_external_apply_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部创作者申请表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `creator_vip_apply` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '创作者用户ID',
  `apply_type` varchar(20) NOT NULL COMMENT '申请类型',
  `reason` text COMMENT '申请理由',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核人ID',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_result` varchar(500) DEFAULT NULL COMMENT '审核结果',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者VIP申请表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `creator_work` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '作品ID',
  `user_id` bigint(20) NOT NULL COMMENT '创作者用户ID',
  `work_type` tinyint(4) DEFAULT '1' COMMENT '作品类型：1-单曲，2-专辑，3-EP，4-Remix',
  `work_name` varchar(200) NOT NULL COMMENT '作品名称',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面图片',
  `file_url` varchar(500) DEFAULT NULL COMMENT '音频文件URL',
  `detected_quality` tinyint(4) DEFAULT NULL COMMENT '检测到的音质等级：1-标准 2-高品质 3-无损 4-Hi-Res 5-母带',
  `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小（字节）',
  `duration` int(11) DEFAULT NULL COMMENT '音频时长（秒）',
  `bitrate` int(11) DEFAULT NULL COMMENT '比特率（kbps）',
  `sample_rate` int(11) DEFAULT NULL COMMENT '采样率（Hz）',
  `audio_format` varchar(10) DEFAULT NULL COMMENT '音频格式：mp3/flac/wav等',
  `upload_type` tinyint(4) DEFAULT '1' COMMENT '上传方式：1-单文件 2-多文件 3-压缩包',
  `file_urls` text COMMENT '多文件URL（JSON数组格式）',
  `zip_file_url` varchar(500) DEFAULT NULL COMMENT '压缩包文件URL',
  `description` text COMMENT '作品描述',
  `submitted_lyric` mediumtext COMMENT '投稿歌词候选，审核发布后写入正式歌词',
  `tags` varchar(500) DEFAULT NULL COMMENT '标签（JSON数组或逗号分隔）',
  `language` tinyint(4) DEFAULT '1' COMMENT '语言类型',
  `status` tinyint(4) DEFAULT '0' COMMENT '审核状态：0-待审核，1-已发布，2-已拒绝',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核人ID',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_reason` varchar(500) DEFAULT NULL COMMENT '审核意见',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `play_count` bigint(20) DEFAULT '0' COMMENT '播放次数',
  `like_count` int(11) DEFAULT '0' COMMENT '点赞数',
  `collect_count` int(11) DEFAULT '0' COMMENT '收藏数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除',
  `auto_song_id` bigint(20) DEFAULT NULL COMMENT '自动创建的歌曲ID',
  `file_hash` varchar(64) DEFAULT NULL COMMENT 'File SHA256 hash',
  `is_paid` tinyint(4) DEFAULT '0' COMMENT '鏄?惁浠樿垂',
  `paid_resource_id` bigint(20) DEFAULT NULL COMMENT '浠樿垂璧勬簮ID',
  `price` decimal(10,2) DEFAULT NULL COMMENT '浠锋牸',
  `subscribe_period` int(11) DEFAULT NULL COMMENT '璁㈤槄鍛ㄦ湡',
  `allow_download` tinyint(1) DEFAULT '1' COMMENT '鏄?惁鍏佽?涓嬭浇锛?-鍚︼紝1-鏄?級',
  `allow_comment` tinyint(1) DEFAULT '1' COMMENT '鏄?惁鍏佽?璇勮?锛?-鍚︼紝1-鏄?級',
  `allow_share` tinyint(1) DEFAULT '1' COMMENT '鏄?惁鍏佽?鍒嗕韩锛?-鍚︼紝1-鏄?級',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_work_type` (`work_type`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_creator_paid` (`is_paid`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作者作品表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `creator_work_purchase` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `user_id` bigint(20) NOT NULL COMMENT '璐?拱鐢ㄦ埛ID',
  `creator_id` bigint(20) NOT NULL COMMENT '鍒涗綔鑰匢D',
  `work_id` bigint(20) NOT NULL COMMENT '浣滃搧ID',
  `purchase_amount` decimal(10,2) NOT NULL COMMENT '璐?拱閲戦?',
  `payment_method` varchar(20) DEFAULT NULL COMMENT '鏀?粯鏂瑰紡: balance/vip/points',
  `order_no` varchar(64) DEFAULT NULL COMMENT '璁㈠崟鍙',
  `status` varchar(20) DEFAULT 'success' COMMENT '鐘舵?: pending/success/failed/refunded',
  `purchase_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '璐?拱鏃堕棿',
  `expire_time` datetime DEFAULT NULL COMMENT '杩囨湡鏃堕棿锛堣?闃呯被鍨嬶級',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '閫昏緫鍒犻櫎锛?-鏈?垹闄わ紝1-宸插垹闄わ級',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_work` (`user_id`,`work_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_work_id` (`work_id`),
  KEY `idx_purchase_time` (`purchase_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鍒涗綔鑰呬綔鍝佽喘涔拌?褰曡〃';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `credit_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `credit_type` varchar(50) NOT NULL COMMENT '信用分类型：report/refund/comment/login/listen/share/checkin/vip/other',
  `score` int(11) NOT NULL COMMENT '变动分数（正数为增加，负数为减少）',
  `reason` varchar(500) DEFAULT NULL COMMENT '变动原因',
  `operator_id` bigint(20) DEFAULT NULL COMMENT '操作员ID（系统调整时记录）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_credit_type` (`credit_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用分变动记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `curated_carousel_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `scene` varchar(32) NOT NULL DEFAULT 'all' COMMENT 'discover_top/square_top/all',
  `content_type` varchar(32) NOT NULL COMMENT 'official/news/hot_post/hot_topic/new_song/playlist/collab_playlist/vote',
  `title` varchar(200) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `badge` varchar(64) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `fallback_image_url` varchar(512) DEFAULT NULL,
  `source_type` varchar(16) NOT NULL DEFAULT 'external' COMMENT 'internal/external',
  `source_name` varchar(128) DEFAULT NULL,
  `link` varchar(512) DEFAULT NULL,
  `fallback_link` varchar(512) DEFAULT NULL,
  `target_type` varchar(64) DEFAULT NULL,
  `target_id` varchar(64) DEFAULT NULL,
  `priority` int(11) NOT NULL DEFAULT '0',
  `sort_order` int(11) NOT NULL DEFAULT '0',
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0??/???,1??',
  `review_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0???,1??,2??',
  `reviewer_id` bigint(20) DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `review_remark` varchar(500) DEFAULT NULL,
  `operator_id` bigint(20) DEFAULT NULL,
  `view_count` bigint(20) NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `legacy_source_key` varchar(160) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_curated_carousel_legacy_source` (`legacy_source_key`),
  KEY `idx_curated_carousel_public` (`scene`,`status`,`review_status`,`deleted`,`start_time`,`end_time`,`sort_order`,`priority`,`create_time`),
  KEY `idx_curated_carousel_content` (`content_type`,`source_type`,`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='????????/??????????';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `decoration_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `decoration_id` varchar(50) NOT NULL COMMENT '装饰ID',
  `creator_id` bigint(20) DEFAULT NULL COMMENT '创作型装饰作者，平台装饰为空',
  `source_type` varchar(16) NOT NULL DEFAULT 'system' COMMENT 'system/custom',
  `decoration_type` varchar(20) NOT NULL COMMENT '装饰类型: avatar_frame/comment_bar/player/dialog_box/theme/badge',
  `decoration_name` varchar(50) NOT NULL COMMENT '装饰名称',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `icon_url` varchar(500) DEFAULT NULL COMMENT '图标URL',
  `preview_url` varchar(500) DEFAULT NULL COMMENT '预览图URL',
  `style_config` text COMMENT '样式配置JSON（透明度、尺寸等自适应参数）',
  `rarity` varchar(20) DEFAULT 'common' COMMENT '稀有度: common/rare/epic/legendary',
  `obtain_type` varchar(50) DEFAULT NULL COMMENT '获取方式: sign/activity/vip/achievement/points',
  `obtain_condition` varchar(500) DEFAULT NULL COMMENT '获取条件JSON',
  `points_cost` int(11) DEFAULT NULL COMMENT '活跃值兑换价格',
  `cash_price` int(11) DEFAULT NULL COMMENT '现金价格（分）',
  `is_permanent` tinyint(4) DEFAULT '1' COMMENT '是否永久（0-限时，1-永久）',
  `duration_days` int(11) DEFAULT NULL COMMENT '有效天数（限时装饰）',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序顺序',
  `is_enabled` tinyint(4) DEFAULT '1' COMMENT '是否启用（0-禁用，1-启用）',
  `review_status` varchar(16) NOT NULL DEFAULT 'approved' COMMENT 'draft/pending/approved/rejected',
  `submit_time` datetime DEFAULT NULL,
  `reviewer_id` bigint(20) DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `review_reason` varchar(500) DEFAULT NULL,
  `content_version` int(11) NOT NULL DEFAULT '1' COMMENT '不可变内容版本号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `decoration_id` (`decoration_id`),
  KEY `idx_type_enabled` (`decoration_type`,`is_enabled`),
  KEY `idx_rarity` (`rarity`),
  KEY `idx_obtain_type` (`obtain_type`),
  KEY `idx_decoration_creator_time` (`creator_id`,`update_time`),
  KEY `idx_decoration_review_time` (`review_status`,`submit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='装饰配置表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `decoration_purchase_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `decoration_id` bigint(20) NOT NULL COMMENT '装饰ID',
  `shop_id` bigint(20) DEFAULT NULL COMMENT '商店商品ID',
  `payment_type` varchar(20) NOT NULL COMMENT '支付类型：points/gems/alipay/wxpay',
  `payment_amount` int(11) NOT NULL COMMENT '支付金额',
  `order_no` varchar(50) DEFAULT NULL COMMENT '订单号',
  `status` varchar(20) DEFAULT 'success' COMMENT '状态：pending/success/failed/refunded',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='装饰购买记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `decoration_purchase_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `decoration_config_id` bigint(20) NOT NULL,
  `decoration_id` varchar(100) NOT NULL,
  `payment_order_id` bigint(20) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `platform_fee_rate` decimal(8,6) DEFAULT NULL,
  `platform_fee` decimal(10,2) DEFAULT NULL,
  `creator_earnings` decimal(10,2) DEFAULT NULL,
  `content_version` int(11) DEFAULT NULL,
  `currency` varchar(3) NOT NULL DEFAULT 'CNY',
  `status` varchar(20) NOT NULL DEFAULT 'active',
  `refund_id` bigint(20) DEFAULT NULL,
  `refund_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_decoration_purchase_order` (`payment_order_id`),
  KEY `idx_decoration_purchase_user_item` (`user_id`,`decoration_id`,`status`),
  KEY `idx_decoration_purchase_config_time` (`decoration_config_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现金装饰支付履约记录';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `decoration_shop` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `decoration_id` bigint(20) NOT NULL COMMENT '装饰ID',
  `shop_type` varchar(20) DEFAULT 'points' COMMENT '商店类型：points/gems/vip',
  `price` int(11) NOT NULL COMMENT '价格',
  `discount_price` int(11) DEFAULT NULL COMMENT '折扣价（NULL=无折扣）',
  `discount_start` datetime DEFAULT NULL COMMENT '折扣开始时间',
  `discount_end` datetime DEFAULT NULL COMMENT '折扣结束时间',
  `stock_type` varchar(20) DEFAULT 'unlimited' COMMENT '库存类型：unlimited/limited',
  `stock_count` int(11) DEFAULT '-1' COMMENT '库存数量（-1=无限）',
  `sold_count` int(11) DEFAULT '0' COMMENT '已售数量',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序',
  `is_recommend` tinyint(4) DEFAULT '0' COMMENT '是否推荐',
  `status` tinyint(4) DEFAULT '1' COMMENT '状态：0-下架，1-上架',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_decoration_id` (`decoration_id`),
  KEY `idx_shop_type` (`shop_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='装饰商店表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `emoji_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `emoji_package_id` bigint(20) NOT NULL COMMENT '琛ㄦ儏鍖匢D',
  `item_name` varchar(50) NOT NULL COMMENT '琛ㄦ儏鍚嶇О',
  `item_code` varchar(50) NOT NULL COMMENT '琛ㄦ儏浠ｇ爜',
  `image_url` varchar(500) NOT NULL COMMENT '琛ㄦ儏鍥剧墖URL',
  `gif_url` varchar(500) DEFAULT NULL COMMENT '鍔ㄥ浘URL锛堝?鏋滄湁锛',
  `category` varchar(50) DEFAULT NULL COMMENT '鍒嗙被',
  `sort_order` int(11) DEFAULT '0' COMMENT '鎺掑簭',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '閫昏緫鍒犻櫎锛?-鏈?垹闄わ紝1-宸插垹闄わ級',
  PRIMARY KEY (`id`),
  KEY `idx_emoji_package_id` (`emoji_package_id`),
  KEY `idx_item_code` (`item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='琛ㄦ儏椤硅〃';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `emoji_package_purchase_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `emoji_package_id` bigint(20) NOT NULL,
  `payment_order_id` bigint(20) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `platform_fee_rate` decimal(8,6) DEFAULT NULL,
  `platform_fee` decimal(10,2) DEFAULT NULL,
  `creator_earnings` decimal(10,2) DEFAULT NULL,
  `currency` varchar(3) NOT NULL DEFAULT 'CNY',
  `purchase_channel` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'active',
  `refund_id` bigint(20) DEFAULT NULL,
  `refund_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emoji_purchase_order` (`payment_order_id`),
  KEY `idx_emoji_purchase_package_time` (`emoji_package_id`,`create_time`),
  KEY `idx_emoji_purchase_user_time` (`user_id`,`create_time`),
  KEY `idx_emoji_purchase_user_package_status` (`user_id`,`emoji_package_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表情包支付履约记录';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `emoji_upload_batch` (
  `id` bigint(20) NOT NULL,
  `creator_id` bigint(20) NOT NULL,
  `package_id` bigint(20) NOT NULL,
  `idempotency_key` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_fingerprint` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_count` int(11) NOT NULL,
  `total_bytes` bigint(20) NOT NULL,
  `uploaded_paths_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `emoji_ids_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime NOT NULL,
  `error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emoji_upload_creator_key` (`creator_id`,`idempotency_key`),
  KEY `idx_emoji_upload_creator_created` (`creator_id`,`created_at`),
  KEY `idx_emoji_upload_status_expiry` (`status`,`expires_at`,`updated_at`),
  KEY `idx_emoji_upload_package` (`package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表情包安全上传批次、幂等与失败回收记录';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `external_content` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `content_type` varchar(20) NOT NULL COMMENT '内容类型: game-游戏, anime-动漫, music-音乐',
  `external_id` varchar(100) DEFAULT NULL COMMENT '外部平台ID',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `description` text COMMENT '描述',
  `thumbnail` varchar(500) DEFAULT NULL COMMENT '缩略图',
  `images` json DEFAULT NULL COMMENT '详细图片JSON数组',
  `external_url` varchar(500) DEFAULT NULL COMMENT '外部链接',
  `category` varchar(100) DEFAULT NULL COMMENT '分类/流派',
  `platform` varchar(50) DEFAULT NULL COMMENT '平台',
  `release_date` varchar(50) DEFAULT NULL COMMENT '发行日期',
  `rating` decimal(3,1) DEFAULT NULL COMMENT '评分0-10',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态: 0-待审核, 1-已发布, 2-已下架',
  `priority` int(11) NOT NULL DEFAULT '0' COMMENT '推荐权重',
  `created_by` bigint(20) DEFAULT NULL COMMENT '添加者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_content_source` (`content_type`,`external_id`),
  KEY `idx_type_status` (`content_type`,`status`),
  KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部推荐内容表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `favorite_collection_group` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '分组ID',
  `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
  `name` varchar(30) NOT NULL COMMENT '分组名称',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '用户内排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_group_user_name` (`user_id`,`name`),
  UNIQUE KEY `uk_favorite_group_id_user` (`id`,`user_id`),
  KEY `idx_favorite_group_user_order` (`user_id`,`sort_order`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏分组';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `favorite_collection_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '归组记录ID',
  `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
  `group_id` bigint(20) NOT NULL COMMENT '收藏分组ID',
  `resource_type` varchar(16) NOT NULL COMMENT 'song/album/mv/playlist',
  `resource_id` bigint(20) NOT NULL COMMENT '资源ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_item_user_resource` (`user_id`,`resource_type`,`resource_id`),
  KEY `idx_favorite_item_group` (`user_id`,`group_id`,`update_time`),
  CONSTRAINT `fk_favorite_item_group_user` FOREIGN KEY (`group_id`,`user_id`) REFERENCES `favorite_collection_group` (`id`,`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异构收藏归组记录';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `favorite_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `song_id` bigint(20) NOT NULL COMMENT '歌曲ID',
  `action_type` tinyint(4) NOT NULL COMMENT '操作类型：1-收藏，2-取消收藏',
  `action_time` datetime NOT NULL COMMENT '操作时间',
  `song_name` varchar(255) DEFAULT NULL COMMENT '歌曲名称快照',
  `artist_names` varchar(500) DEFAULT NULL COMMENT '艺术家名称快照',
  `cover` varchar(500) DEFAULT NULL COMMENT '封面URL快照',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_song_id` (`song_id`),
  KEY `idx_action_time` (`action_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏历史表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `feedback_reward` (
  `id` bigint(20) NOT NULL COMMENT '??ID',
  `user_id` bigint(20) NOT NULL COMMENT '??ID',
  `feedback_id` bigint(20) NOT NULL COMMENT '??ID',
  `reward_level` varchar(30) NOT NULL COMMENT '????',
  `points` int(11) DEFAULT '0' COMMENT '????',
  `vip_days` int(11) DEFAULT '0' COMMENT '??VIP??',
  `credit_score` int(11) DEFAULT '0' COMMENT '?????',
  `reward_description` varchar(500) DEFAULT NULL COMMENT '????',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT '??',
  `is_granted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '?????',
  `granted_time` datetime DEFAULT NULL COMMENT '????',
  `grantor_id` bigint(20) DEFAULT NULL COMMENT '???ID',
  `cancel_reason` varchar(500) DEFAULT NULL COMMENT '????',
  `cancelled_time` datetime DEFAULT NULL COMMENT '????',
  `remark` varchar(500) DEFAULT NULL COMMENT '??',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feedback_reward_feedback` (`feedback_id`),
  KEY `idx_feedback_reward_user` (`user_id`,`status`,`create_time`),
  KEY `idx_feedback_reward_feedback` (`feedback_id`),
  KEY `idx_feedback_reward_user_status` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='反馈奖励表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `gift_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '???ID',
  `gift_no` varchar(64) NOT NULL COMMENT '?????????',
  `gift_type` varchar(32) NOT NULL DEFAULT 'vip' COMMENT '??????: vip/marketplace_item',
  `giver_id` bigint(20) NOT NULL COMMENT '?????ID',
  `receiver_id` bigint(20) NOT NULL COMMENT '?????D',
  `target_type` varchar(32) DEFAULT NULL COMMENT '?????????: vip/marketplace_item',
  `target_id` bigint(20) DEFAULT NULL COMMENT '??????ID',
  `target_name` varchar(255) DEFAULT NULL COMMENT '?????????',
  `seller_id` bigint(20) DEFAULT NULL COMMENT '?????????????D',
  `vip_type` varchar(32) DEFAULT NULL COMMENT 'VIP???: month/quarter/year',
  `vip_level` int(11) DEFAULT NULL COMMENT 'VIP???',
  `vip_days` int(11) DEFAULT NULL COMMENT 'VIP???',
  `amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '??????',
  `currency` varchar(8) NOT NULL DEFAULT 'CNY' COMMENT '???',
  `gift_message` varchar(200) DEFAULT NULL COMMENT '??????',
  `payment_order_id` bigint(20) DEFAULT NULL COMMENT '?????????ID',
  `status` varchar(20) NOT NULL DEFAULT 'pending_payment' COMMENT 'pending_payment/paid/completed/blocked/failed/cancelled',
  `completion_error` varchar(500) DEFAULT NULL COMMENT '????????????',
  `completed_at` datetime DEFAULT NULL COMMENT '?????????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '??????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '??????',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '??????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gift_order_no` (`gift_no`),
  UNIQUE KEY `uk_gift_order_payment` (`payment_order_id`),
  KEY `idx_gift_order_giver_time` (`giver_id`,`deleted`,`create_time`),
  KEY `idx_gift_order_receiver_time` (`receiver_id`,`deleted`,`create_time`),
  KEY `idx_gift_order_target` (`gift_type`,`target_id`,`deleted`),
  KEY `idx_gift_order_status_time` (`status`,`deleted`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='??????';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `hot_event` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '事件ID',
  `title` varchar(200) NOT NULL COMMENT '事件标题',
  `description` text COMMENT '事件描述',
  `cover` varchar(500) DEFAULT NULL COMMENT '封面图',
  `event_type` varchar(50) DEFAULT NULL COMMENT '类型: news/release/concert/award',
  `event_date` date DEFAULT NULL COMMENT '事件日期',
  `source` varchar(100) DEFAULT NULL COMMENT '来源',
  `source_url` varchar(500) DEFAULT NULL COMMENT '来源链接',
  `source_type` varchar(32) DEFAULT 'external' COMMENT '鏉ユ簮绫诲瀷:internal/external/news',
  `fallback_source_url` varchar(500) DEFAULT NULL COMMENT '澶囩敤鏉ユ簮閾炬帴',
  `related_artists` varchar(500) DEFAULT NULL COMMENT '关联艺人ID数组(JSON)',
  `related_songs` varchar(500) DEFAULT NULL COMMENT '关联歌曲ID数组(JSON)',
  `view_count` int(11) DEFAULT '0' COMMENT '浏览量',
  `is_featured` tinyint(1) DEFAULT '0' COMMENT '是否轮播',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除',
  `review_status` tinyint(4) DEFAULT '1' COMMENT '瀹℃牳鐘舵€?0寰呭鏍?1閫氳繃,2鎷掔粷',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '瀹℃牳浜篒D',
  `review_time` datetime DEFAULT NULL COMMENT '瀹℃牳鏃堕棿',
  `review_remark` varchar(500) DEFAULT NULL COMMENT '瀹℃牳澶囨敞',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_event_date` (`event_date`),
  KEY `idx_is_featured` (`is_featured`),
  KEY `idx_hot_event_public_review` (`is_featured`,`is_deleted`,`review_status`,`source_type`,`sort_order`,`event_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热点事件表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `language` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `native_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `direction` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT 'ltr',
  `sort_order` int(11) DEFAULT '0',
  `deleted` int(11) DEFAULT '0',
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `listen_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `song_id` bigint(20) NOT NULL,
  `duration` int(11) DEFAULT NULL,
  `progress` int(11) DEFAULT '0',
  `is_completed` tinyint(4) DEFAULT '0',
  `is_local` tinyint(4) DEFAULT '0',
  `quality` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'standard',
  `listen_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`listen_time`),
  KEY `idx_user_song_time` (`user_id`,`song_id`,`listen_time`),
  KEY `idx_listen_history_user_time` (`user_id`,`create_time`),
  KEY `idx_listen_history_user_song_local_time` (`user_id`,`song_id`,`is_local`,`create_time`),
  KEY `idx_listen_history_time_song` (`create_time`,`song_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client      = @@character_set_client  ;
SET @saved_cs_results     = @@character_set_results  ;
SET @saved_col_connection = @@collation_connection  ;
SET character_set_client  = utf8mb4  ;
SET character_set_results = utf8mb4  ;
SET collation_connection  = utf8mb4_general_ci  ;
SET @saved_sql_mode       = @@sql_mode  ;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;
DELIMITER ;;
CREATE DEFINER=CURRENT_USER TRIGGER trg_listen_history_insert
AFTER INSERT ON listen_history
FOR EACH ROW
BEGIN
    UPDATE song SET play_count = play_count + 1 WHERE id = NEW.song_id;
END ;;
DELIMITER ;
SET sql_mode              = @saved_sql_mode  ;
SET character_set_client  = @saved_cs_client  ;
SET character_set_results = @saved_cs_results  ;
SET collation_connection  = @saved_col_connection  ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `lyric` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `song_id` bigint(20) NOT NULL,
  `content` mediumtext COLLATE utf8mb4_unicode_ci,
  `language` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '????URL',
  `creator_id` bigint(20) DEFAULT NULL COMMENT '???ID',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '??: 0-??/??, 1-??',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '????: 0-???, 1-???',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `lyric_type` int(11) DEFAULT '0' COMMENT '歌词类型：0-原文，1-译文，2-音译',
  PRIMARY KEY (`id`),
  KEY `idx_lyric_song_type_lang_status` (`song_id`,`lyric_type`,`language`,`status`,`deleted`,`create_time`),
  KEY `idx_lyric_song_deleted` (`song_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `lyric_request` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '提交ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `user_name` varchar(100) DEFAULT NULL COMMENT '用户名',
  `song_id` bigint(20) DEFAULT NULL COMMENT '歌曲ID',
  `song_name` varchar(200) DEFAULT NULL COMMENT '歌曲名称',
  `original_lyric` text COMMENT '原始歌词',
  `corrected_lyric` text COMMENT '修正后歌词',
  `change_description` varchar(500) DEFAULT NULL COMMENT '修改说明',
  `change_type` tinyint(4) DEFAULT '1' COMMENT '修改类型：1-错别字修正，2-添加歌词，3-格式调整，4-时间轴修正',
  `status` tinyint(4) DEFAULT '0' COMMENT '审核状态：0-待审核，1-已通过，2-已拒绝',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核人ID',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_reason` varchar(500) DEFAULT NULL COMMENT '审核意见',
  `is_applied` tinyint(4) DEFAULT '0' COMMENT '是否已应用',
  `applied_time` datetime DEFAULT NULL COMMENT '应用时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_song_id` (`song_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌词修正提交表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `lyric_translation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `song_id` bigint(20) NOT NULL,
  `source_lyric_id` bigint(20) DEFAULT NULL,
  `target_language` varchar(20) NOT NULL,
  `lyric_type` int(11) DEFAULT '2' COMMENT '2-翻译，3-音译',
  `status` int(11) DEFAULT '0' COMMENT '0-待处理，1-处理中，2-完成，3-失败',
  `result_content` mediumtext,
  `error_message` varchar(500) DEFAULT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `model_name` varchar(50) DEFAULT NULL,
  `attempt_count` int(11) NOT NULL DEFAULT '0',
  `next_retry_time` datetime DEFAULT NULL,
  `processing_token` varchar(64) DEFAULT NULL,
  `request_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `complete_time` datetime DEFAULT NULL,
  `creator_id` bigint(20) DEFAULT NULL,
  `deleted` int(11) DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_song` (`song_id`),
  KEY `idx_status` (`status`),
  KEY `idx_lyric_translation_recovery` (`status`,`deleted`,`next_retry_time`,`update_time`),
  KEY `idx_lyric_translation_owner_song` (`creator_id`,`song_id`,`deleted`,`request_time`),
  KEY `idx_lyric_translation_active` (`song_id`,`target_language`,`lyric_type`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `marketplace_favorite` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `item_id` bigint(20) NOT NULL COMMENT '商品ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '鍒犻櫎鏍囪?锛?-鏈?垹闄わ紝1-宸插垹闄わ級',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_user_deleted` (`item_id`,`user_id`,`deleted`),
  UNIQUE KEY `uk_marketplace_favorite_user_item` (`user_id`,`item_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_marketplace_favorite_user_deleted_time` (`user_id`,`deleted`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品收藏表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `marketplace_gift_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `gift_order_id` bigint(20) NOT NULL COMMENT '????ID',
  `payment_order_id` bigint(20) DEFAULT NULL COMMENT '????ID',
  `marketplace_item_id` bigint(20) NOT NULL COMMENT '??????ID',
  `giver_id` bigint(20) NOT NULL COMMENT '???ID',
  `receiver_id` bigint(20) NOT NULL COMMENT '???ID',
  `seller_id` bigint(20) DEFAULT NULL COMMENT '??ID',
  `status` varchar(20) NOT NULL COMMENT 'applied/blocked/failed',
  `apply_time` datetime DEFAULT NULL COMMENT '????',
  `operator_id` bigint(20) DEFAULT NULL COMMENT '?????ID',
  `error_message` varchar(500) DEFAULT NULL COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_marketplace_gift_order` (`gift_order_id`),
  KEY `idx_marketplace_gift_payment` (`payment_order_id`,`deleted`),
  KEY `idx_marketplace_gift_receiver_status` (`receiver_id`,`status`,`deleted`,`create_time`),
  KEY `idx_marketplace_gift_item` (`marketplace_item_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='????????????';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `marketplace_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `seller_id` bigint(20) NOT NULL COMMENT '卖家ID',
  `title` varchar(100) NOT NULL COMMENT '商品标题',
  `category` varchar(20) DEFAULT NULL COMMENT '类型',
  `condition` varchar(20) DEFAULT NULL COMMENT '成色',
  `price` decimal(10,2) NOT NULL COMMENT '价格',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价',
  `description` text COMMENT '商品描述',
  `images` varchar(1000) DEFAULT NULL COMMENT '商品图片(JSON)',
  `resource_type` varchar(20) DEFAULT NULL COMMENT '关联资源类型',
  `resource_id` bigint(20) DEFAULT NULL COMMENT '关联资源ID',
  `resource_name` varchar(100) DEFAULT NULL COMMENT '关联资源名称',
  `resource_cover` varchar(255) DEFAULT NULL COMMENT '关联资源封面',
  `status` varchar(20) DEFAULT 'available' COMMENT '状态',
  `location` varchar(100) DEFAULT NULL COMMENT '所在地',
  `delivery_method` varchar(20) DEFAULT NULL COMMENT '交易方式',
  `view_count` int(11) DEFAULT '0' COMMENT '浏览量',
  `favorite_count` int(11) DEFAULT '0' COMMENT '收藏数',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `sold_time` datetime DEFAULT NULL COMMENT '售出时间',
  PRIMARY KEY (`id`),
  KEY `idx_seller_id` (`seller_id`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`),
  KEY `idx_price` (`price`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易商品表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `media_asset` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '资产ID',
  `owner_id` bigint(20) DEFAULT NULL COMMENT '私有资产所有者',
  `upload_session_id` bigint(20) DEFAULT NULL COMMENT '私有上传会话ID',
  `purpose` varchar(64) DEFAULT NULL COMMENT '私有附件用途',
  `visibility` varchar(16) DEFAULT NULL COMMENT 'PRIVATE/PUBLIC',
  `original_name` varchar(255) DEFAULT NULL COMMENT '安全截断后的原始文件名',
  `content_type` varchar(128) DEFAULT NULL COMMENT '服务端确认的内容类型',
  `media_type` varchar(32) NOT NULL COMMENT 'audio/video/image等',
  `source_type` varchar(64) DEFAULT NULL COMMENT '来源业务类型',
  `source_id` bigint(20) DEFAULT NULL COMMENT '来源业务ID',
  `asset_role` varchar(64) DEFAULT NULL COMMENT 'source/original/standard等',
  `public_url` varchar(1000) DEFAULT NULL COMMENT '公开访问URL',
  `storage_node` varchar(32) NOT NULL COMMENT 'node1/node3',
  `storage_path` varchar(1000) DEFAULT NULL COMMENT '节点绝对路径',
  `file_hash` varchar(128) DEFAULT NULL COMMENT '文件哈希',
  `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小',
  `scan_status` varchar(32) DEFAULT NULL COMMENT '扫描状态',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/RECLAIMING/RECLAIMED/RECLAIM_FAILED',
  `grace_until` datetime DEFAULT NULL COMMENT '无引用后的最早回收时间',
  `last_error` varchar(2000) DEFAULT NULL COMMENT '最近回收失败原因',
  `reclaimed_at` datetime DEFAULT NULL COMMENT '回收完成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `storage_identity_hash` char(64) GENERATED ALWAYS AS ((case when ((`storage_path` is not null) and (`storage_path` <> '')) then sha2(concat('path|',`storage_node`,'|',`storage_path`),256) when ((`public_url` is not null) and (`public_url` <> '')) then sha2(concat('url|',`public_url`),256) else NULL end)) STORED COMMENT '受管存储位置稳定哈希',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_media_asset_private_session_hash` (`upload_session_id`,`file_hash`,`visibility`),
  UNIQUE KEY `uk_media_asset_storage_identity` (`storage_identity_hash`),
  KEY `idx_media_asset_storage` (`storage_node`,`storage_path`(255)),
  KEY `idx_media_asset_public_url` (`public_url`(255)),
  KEY `idx_media_asset_reclaim` (`status`,`grace_until`),
  KEY `idx_media_asset_private_owner` (`owner_id`,`purpose`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体资产';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `media_asset_reference` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '引用ID',
  `asset_id` bigint(20) NOT NULL COMMENT '媒体资产ID',
  `target_type` varchar(64) NOT NULL COMMENT '引用业务类型',
  `target_id` bigint(20) NOT NULL COMMENT '引用业务ID',
  `reference_role` varchar(64) NOT NULL DEFAULT 'active' COMMENT '引用角色',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `released_at` datetime DEFAULT NULL COMMENT '引用释放时间',
  `active_slot` tinyint(4) GENERATED ALWAYS AS (if(isnull(`released_at`),1,NULL)) STORED COMMENT '有效引用唯一槽位',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_media_asset_reference_active` (`asset_id`,`target_type`,`target_id`,`reference_role`,`active_slot`),
  KEY `idx_media_asset_ref_asset` (`asset_id`,`released_at`),
  KEY `idx_media_asset_ref_target` (`target_type`,`target_id`,`released_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体资产业务引用';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `media_derivative_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `media_type` varchar(32) NOT NULL COMMENT '媒体类型：song/mv',
  `media_id` bigint(20) NOT NULL COMMENT '业务媒体ID',
  `source_url` varchar(1000) NOT NULL COMMENT '审核后的源文件URL',
  `source_size` bigint(20) DEFAULT NULL COMMENT '源文件大小（字节）',
  `source_quality` int(11) DEFAULT NULL COMMENT '源文件质量等级',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/FAILED',
  `retry_count` int(11) NOT NULL DEFAULT '0' COMMENT '已重试次数',
  `max_retry_count` int(11) NOT NULL DEFAULT '3' COMMENT '最大重试次数',
  `last_error` varchar(2000) DEFAULT NULL COMMENT '最近失败原因',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `started_at` datetime DEFAULT NULL COMMENT '开始处理时间',
  `finished_at` datetime DEFAULT NULL COMMENT '完成时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_media_derivative_status_retry` (`status`,`next_retry_time`,`retry_count`),
  KEY `idx_media_derivative_media` (`media_type`,`media_id`),
  KEY `idx_media_derivative_source` (`source_url`(255)),
  KEY `idx_media_derivative_status_updated` (`status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='正式歌曲/MV媒体派生任务';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `media_upload_session` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `session_token` char(36) NOT NULL COMMENT '不可预测会话令牌',
  `owner_id` bigint(20) NOT NULL COMMENT '所有者用户ID',
  `purpose` varchar(64) NOT NULL COMMENT '附件用途',
  `status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/COMPLETED/CANCELLED/EXPIRED',
  `max_files` int(11) NOT NULL COMMENT '最大文件数',
  `uploaded_count` int(11) NOT NULL DEFAULT '0' COMMENT '已保留上传槽位数',
  `target_type` varchar(64) DEFAULT NULL COMMENT '完成后绑定的唯一业务类型',
  `target_id` bigint(20) DEFAULT NULL COMMENT '完成后绑定的唯一业务ID',
  `expires_at` datetime NOT NULL COMMENT '会话过期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_media_upload_session_token` (`session_token`),
  KEY `idx_media_upload_session_owner_status` (`owner_id`,`status`,`expires_at`),
  KEY `idx_media_upload_session_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私有附件上传会话';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `message` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '娑堟伅ID',
  `sender_id` bigint(20) NOT NULL COMMENT '鍙戦?鑰呯敤鎴稩D',
  `receiver_id` bigint(20) NOT NULL COMMENT '鎺ユ敹鑰呯敤鎴稩D',
  `message_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'text' COMMENT '娑堟伅绫诲瀷: text-鏂囨湰, image-鍥剧墖, emoji-琛ㄦ儏, song-姝屾洸, album-涓撹緫, playlist-姝屽崟',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT '娑堟伅鍐呭?',
  `resource_id` bigint(20) DEFAULT NULL COMMENT '鍏宠仈璧勬簮ID',
  `resource_data` text COLLATE utf8mb4_unicode_ci COMMENT '鍏宠仈璧勬簮JSON',
  `is_read` tinyint(4) NOT NULL DEFAULT '0' COMMENT '鏄?惁宸茶?: 0-鏈??, 1-宸茶?',
  `is_recalled` tinyint(4) NOT NULL DEFAULT '0' COMMENT '鏄?惁鎾ゅ洖: 0-姝ｅ父, 1-宸叉挙鍥',
  `read_time` datetime DEFAULT NULL COMMENT '璇诲彇鏃堕棿',
  `is_deleted_by_sender` tinyint(4) NOT NULL DEFAULT '0' COMMENT '鍙戦?鏂规槸鍚﹀垹闄? 0-鏈?垹闄? 1-宸插垹闄',
  `is_deleted_by_receiver` tinyint(4) NOT NULL DEFAULT '0' COMMENT '鎺ユ敹鏂规槸鍚﹀垹闄? 0-鏈?垹闄? 1-宸插垹闄',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'normal' COMMENT '娑堟伅鐘舵?: normal-姝ｅ父, blocked-琚?睆钄',
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_sender_receiver` (`sender_id`,`receiver_id`,`is_deleted_by_sender`),
  KEY `idx_receiver_read` (`receiver_id`,`is_read`,`is_deleted_by_receiver`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绉佷俊娑堟伅琛';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `message_keyword_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `message_id` bigint(20) DEFAULT NULL COMMENT '关联的私信ID',
  `keyword_hash` varchar(64) NOT NULL COMMENT '关键词哈希值',
  `keyword_type` varchar(20) DEFAULT NULL COMMENT '关键词类型: artist/song/genre',
  `confidence` decimal(3,2) DEFAULT NULL COMMENT '置信度（0.00-1.00）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_keyword_hash` (`keyword_hash`),
  KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私信关键词记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `moderation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `content_type` varchar(50) NOT NULL COMMENT '内容类型：song-歌曲, lyric-歌词, artist-歌手, album-专辑, playlist-歌单',
  `content_id` bigint(20) NOT NULL COMMENT '内容ID',
  `title` varchar(500) DEFAULT NULL COMMENT '内容标题',
  `description` text COMMENT '内容描述/预览',
  `submitter_id` bigint(20) NOT NULL COMMENT '提交者ID',
  `submitter_name` varchar(100) DEFAULT NULL COMMENT '提交者名称',
  `status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '审核状态：0-待审核, 1-已通过, 2-已拒绝',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核者ID',
  `reviewer_name` varchar(100) DEFAULT NULL COMMENT '审核者名称',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_reason` varchar(500) DEFAULT NULL COMMENT '审核原因/拒绝原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `policy_id` bigint(20) DEFAULT NULL COMMENT '关联的审核规则ID',
  `reaudit_from_id` bigint(20) DEFAULT NULL COMMENT '复审的原审核ID',
  `reapply_available_time` datetime DEFAULT NULL COMMENT '可重新申请时间',
  `can_modify` tinyint(4) DEFAULT '1' COMMENT '是否可修改后重新提交',
  PRIMARY KEY (`id`),
  KEY `idx_content_type_id` (`content_type`,`content_id`),
  KEY `idx_status` (`status`),
  KEY `idx_submitter` (`submitter_id`),
  KEY `idx_reviewer` (`reviewer_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容审核表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `moderation_appeal` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `moderation_id` bigint(20) NOT NULL COMMENT '审核ID',
  `user_id` bigint(20) NOT NULL COMMENT '申诉用户ID',
  `appeal_reason` varchar(200) DEFAULT NULL COMMENT '申诉原因',
  `appeal_content` text COMMENT '申诉内容',
  `attachments` json DEFAULT NULL COMMENT '附件列表',
  `status` tinyint(4) DEFAULT '0' COMMENT '状态：0-待处理 1-申诉通过 2-申诉拒绝',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '处理人ID',
  `decision_reason` text COMMENT '处理决定原因',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `process_time` datetime DEFAULT NULL COMMENT '处理时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_moderation` (`moderation_id`),
  KEY `idx_user` (`user_id`,`status`),
  KEY `idx_status` (`status`,`submit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核申诉表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `moderation_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `moderation_id` bigint(20) NOT NULL,
  `moderation_type` varchar(50) DEFAULT NULL,
  `action` varchar(50) NOT NULL,
  `operator_id` bigint(20) NOT NULL,
  `operator_name` varchar(100) DEFAULT NULL,
  `action_reason` text,
  `before_status` varchar(50) DEFAULT NULL,
  `after_status` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_moderation_id` (`moderation_id`),
  KEY `idx_operator_id` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `moderation_policy` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `policy_code` varchar(50) NOT NULL COMMENT '规则编码',
  `policy_name` varchar(100) NOT NULL COMMENT '规则名称',
  `policy_content` text NOT NULL COMMENT '规则内容',
  `affect_scope` varchar(50) NOT NULL COMMENT '影响范围: all/creator/lyric/song/album',
  `reaudit_required` tinyint(4) DEFAULT '0' COMMENT '是否需要重新审核',
  `effective_time` datetime NOT NULL COMMENT '生效时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_code` (`policy_code`),
  KEY `idx_effective` (`effective_time`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核规则表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `moderation_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `target_type` varchar(50) NOT NULL COMMENT '目标类型',
  `target_id` bigint(20) NOT NULL COMMENT '目标记录ID',
  `submitter_id` bigint(20) NOT NULL COMMENT '提交者ID',
  `submitter_source` varchar(20) DEFAULT 'platform' COMMENT '提交者来源',
  `assigned_moderator_id` bigint(20) DEFAULT NULL COMMENT '分配的审核员ID',
  `assigned_time` datetime DEFAULT NULL COMMENT '分配时间',
  `moderator_online_status` int(11) DEFAULT '0' COMMENT '分配时审核员是否在线',
  `priority` int(11) DEFAULT '5' COMMENT '优先级（1-10）',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态',
  `active_slot` tinyint(1) GENERATED ALWAYS AS ((case when (`status` in ('pending','in_progress')) then 1 else NULL end)) STORED,
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核人ID',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_result` varchar(20) DEFAULT NULL COMMENT '审核结果',
  `review_reason` text COMMENT '审核原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_moderation_active_target` (`target_type`,`target_id`,`active_slot`),
  KEY `idx_target` (`target_type`,`target_id`),
  KEY `idx_moderator` (`assigned_moderator_id`,`status`),
  KEY `idx_status` (`status`),
  KEY `idx_moderation_record_reviewer_status_time` (`reviewer_id`,`status`,`review_time`),
  KEY `idx_moderation_record_status_review_time` (`status`,`review_time`),
  KEY `idx_moderation_record_status_assigned_type` (`status`,`assigned_moderator_id`,`target_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `music_achievement` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '成就ID',
  `achievement_code` varchar(50) NOT NULL COMMENT '成就编码',
  `achievement_name` varchar(100) NOT NULL COMMENT '成就名称',
  `achievement_desc` varchar(500) DEFAULT NULL COMMENT '成就描述',
  `achievement_type` varchar(20) NOT NULL COMMENT '成就类型：sign_in/listen_music/create_playlist/social/creator/vip/other',
  `condition_type` varchar(50) NOT NULL COMMENT '条件类型：count/continuous/reach_level',
  `condition_value` int(11) NOT NULL COMMENT '条件值',
  `condition_field` varchar(50) DEFAULT NULL COMMENT '条件字段（如checkin_count）',
  `reward_points` int(11) DEFAULT '0' COMMENT '奖励活跃值',
  `reward_decoration_id` bigint(20) DEFAULT NULL COMMENT '奖励装饰ID',
  `reward_medal_id` bigint(20) DEFAULT NULL COMMENT '奖励勋章ID',
  `reward_vip_days` int(11) DEFAULT '0' COMMENT '奖励VIP天数',
  `icon_locked` varchar(500) DEFAULT NULL COMMENT '未解锁图标',
  `icon_unlocked` varchar(500) DEFAULT NULL COMMENT '已解锁图标',
  `icon_completed` varchar(500) DEFAULT NULL COMMENT '已完成图标',
  `is_hidden` tinyint(4) DEFAULT '0' COMMENT '是否隐藏（达成前不显示）',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序顺序',
  `status` tinyint(4) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `achievement_code` (`achievement_code`),
  KEY `idx_type` (`achievement_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成就表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `music_catalog_data_backup_20260827` (
  `entity_type` varchar(24) NOT NULL,
  `entity_id` bigint(20) NOT NULL,
  `field_name` varchar(32) NOT NULL,
  `old_value` varchar(255) DEFAULT NULL,
  `new_value` varchar(255) DEFAULT NULL,
  `source_id` bigint(20) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`entity_type`,`entity_id`,`field_name`),
  KEY `idx_music_catalog_backup_source` (`entity_type`,`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='2026-08-27 music catalog alignment recovery snapshot';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `music_emoji` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '表情ID',
  `package_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属表情包ID，0表示系统默认',
  `code` varchar(50) NOT NULL COMMENT '表情代码，如 :smile: :heart:',
  `name` varchar(100) NOT NULL COMMENT '表情名称',
  `image_url` varchar(500) NOT NULL COMMENT '表情图片URL',
  `category` varchar(50) DEFAULT 'emotion' COMMENT '分类: emotion-情感, object-物品, symbol-符号, custom-自定义',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序权重',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用: 0-禁用, 1-启用',
  `creator_id` bigint(20) DEFAULT NULL COMMENT '创建者用户ID',
  `usage_count` bigint(20) DEFAULT '0' COMMENT '使用次数统计',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除: 0-未删除, 1-已删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_package` (`package_id`,`enabled`),
  KEY `idx_category` (`category`,`enabled`),
  KEY `idx_usage` (`usage_count`),
  KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表情表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `music_emoji_package` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '表情包ID',
  `name` varchar(100) NOT NULL COMMENT '表情包名称',
  `description` varchar(500) DEFAULT NULL COMMENT '表情包描述',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面图片URL',
  `cover_emoji_id` bigint(20) DEFAULT NULL COMMENT '包内封面表情ID',
  `type` varchar(20) NOT NULL DEFAULT 'system' COMMENT '类型: system-系统默认, custom-用户自定义',
  `category` varchar(50) DEFAULT 'emotion' COMMENT '分类: emotion-情感, animal-动物, food-食物, activity-活动, symbol-符号',
  `price` int(11) DEFAULT '0' COMMENT '兑换所需积分',
  `purchase_mode` varchar(20) NOT NULL DEFAULT 'points' COMMENT '购买方式: free/points/cash',
  `cash_price` decimal(10,2) DEFAULT NULL COMMENT '人民币售价，仅 cash 使用',
  `is_free` tinyint(1) DEFAULT '1' COMMENT '是否免费: 0-收费, 1-免费',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态: 0-下线, 1-上线',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序权重，越大越靠前',
  `creator_id` bigint(20) DEFAULT NULL COMMENT '创建者用户ID（自定义表情包）',
  `download_count` int(11) DEFAULT '0' COMMENT '下载次数',
  `item_limit` int(11) NOT NULL DEFAULT '16' COMMENT '声明容量: 8/16/24/32/40',
  `review_status` varchar(20) NOT NULL DEFAULT 'draft' COMMENT 'draft/pending/approved/rejected',
  `submit_time` datetime DEFAULT NULL,
  `reviewer_id` bigint(20) DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `review_reason` varchar(500) DEFAULT NULL,
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除: 0-未删除, 1-已删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_type_status` (`type`,`status`),
  KEY `idx_category` (`category`),
  KEY `idx_creator` (`creator_id`),
  KEY `idx_sort` (`sort_order`,`download_count`),
  KEY `idx_review_status_time` (`review_status`,`submit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表情包表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `music_feature_switch` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `feature_key` varchar(50) NOT NULL COMMENT '功能标识',
  `feature_name` varchar(100) NOT NULL COMMENT '功能名称',
  `is_enabled` tinyint(4) DEFAULT '0' COMMENT '是否启用（0-未启用，1-已启用）',
  `description` varchar(500) DEFAULT NULL COMMENT '功能描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `feature_key` (`feature_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='功能开关表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `music_local_music` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `artist_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `album_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duration` int(11) DEFAULT NULL,
  `file_size` bigint(20) DEFAULT NULL,
  `file_format` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `quality` tinyint(1) NOT NULL DEFAULT '0' COMMENT '音质',
  `file_path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `lyric_text` text COLLATE utf8mb4_unicode_ci COMMENT '歌词内容',
  `song_id` bigint(20) DEFAULT NULL,
  `resource_type` tinyint(1) NOT NULL DEFAULT '0' COMMENT '璧勬簮绫诲瀷锛?:姝屾洸, 1:MV锛',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除：0=否，1=是',
  `play_count` int(11) DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'original' COMMENT '版本类型: original-原版, live-现场版, remix-混音版, cover-翻唱版, acoustic-不插电版, instrumental-纯音乐版, demo-演示版',
  `version_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本显示名称，如"现场版"、"混音版"等',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_local_music_version_type` (`version_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `music_paid_resource` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `resource_type` varchar(20) NOT NULL COMMENT '资源类型',
  `resource_id` bigint(20) NOT NULL COMMENT '资源ID',
  `owner_id` bigint(20) DEFAULT NULL COMMENT '?????ID',
  `owner_type` varchar(20) DEFAULT 'creator' COMMENT '?????: platform/creator',
  `price` decimal(10,2) NOT NULL COMMENT '价格，单位元',
  `price_type` varchar(20) DEFAULT 'creator' COMMENT '????: platform/creator',
  `is_enabled` tinyint(4) NOT NULL DEFAULT '1' COMMENT '????',
  `vip_free` tinyint(4) DEFAULT '0' COMMENT 'VIP是否免费',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/approved/rejected',
  `sales_count` int(11) NOT NULL DEFAULT '0' COMMENT '????',
  `total_earnings` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '????????',
  `subscribe_period` int(11) DEFAULT NULL COMMENT '????????',
  `platform_fee_rate` decimal(5,4) DEFAULT '0.0200' COMMENT '??????',
  `change_type` varchar(30) DEFAULT NULL COMMENT '????',
  `change_reason` varchar(500) DEFAULT NULL COMMENT '????',
  `candidate_price` decimal(10,2) DEFAULT NULL COMMENT '待审核候选价格，单位元',
  `candidate_subscribe_period` int(11) DEFAULT NULL COMMENT '待审核候选订阅周期，单位天',
  `candidate_change_type` varchar(30) DEFAULT NULL COMMENT '候选配置变更类型',
  `candidate_change_reason` varchar(500) DEFAULT NULL COMMENT '候选配置变更原因',
  `change_review_status` varchar(20) DEFAULT NULL COMMENT '候选审核状态: pending/approved/rejected/cancelled',
  `candidate_submitted_at` datetime DEFAULT NULL COMMENT '候选配置提交时间',
  `candidate_reviewer_id` bigint(20) DEFAULT NULL COMMENT '候选配置审核人ID',
  `candidate_review_time` datetime DEFAULT NULL COMMENT '候选配置审核时间',
  `candidate_review_reason` varchar(1000) DEFAULT NULL COMMENT '候选配置审核说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource` (`resource_type`,`resource_id`),
  KEY `idx_paid_resource_lookup` (`resource_type`,`resource_id`,`is_enabled`,`status`),
  KEY `idx_paid_resource_owner` (`owner_id`,`status`,`create_time`),
  KEY `idx_paid_resource_change_review` (`change_review_status`,`candidate_submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付费资源表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `music_recommend_source` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `song_id` bigint(20) NOT NULL COMMENT '歌曲ID',
  `source_type` varchar(20) NOT NULL COMMENT '来源类型: user/collab/social/keyword/friend_listen/favorite/common_artist',
  `source_description` varchar(200) DEFAULT NULL COMMENT '来源描述',
  `score` decimal(5,2) DEFAULT NULL COMMENT '推荐分数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_song` (`user_id`,`song_id`),
  KEY `idx_source_type` (`source_type`),
  KEY `idx_score` (`score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐来源记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `music_square_work` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '投稿ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `user_name` varchar(100) DEFAULT NULL COMMENT '用户名',
  `work_type` tinyint(4) NOT NULL COMMENT '投稿类型：1-音频 2-视频 3-音频+歌词 4-纯歌词',
  `title` varchar(200) NOT NULL COMMENT '标题',
  `description` text COMMENT '描述',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面图片URL',
  `tags` varchar(500) DEFAULT NULL COMMENT '标签（逗号分隔）',
  `audio_url` varchar(500) DEFAULT NULL COMMENT '音频文件URL',
  `audio_quality` tinyint(4) DEFAULT NULL COMMENT '检测到的音质等级：1-标准 2-高品质 3-无损 4-Hi-Res 5-母带',
  `audio_size` bigint(20) DEFAULT NULL COMMENT '音频文件大小（字节）',
  `audio_duration` int(11) DEFAULT NULL COMMENT '音频时长（秒）',
  `audio_bitrate` int(11) DEFAULT NULL COMMENT '音频比特率（kbps）',
  `audio_sample_rate` int(11) DEFAULT NULL COMMENT '音频采样率（Hz）',
  `audio_format` varchar(10) DEFAULT NULL COMMENT '音频格式',
  `video_url` varchar(500) DEFAULT NULL COMMENT '视频文件URL',
  `video_quality` varchar(20) DEFAULT NULL COMMENT '视频清晰度：360p/720p/1080p/4k',
  `video_size` bigint(20) DEFAULT NULL COMMENT '视频文件大小（字节）',
  `video_duration` int(11) DEFAULT NULL COMMENT '视频时长（秒）',
  `video_format` varchar(10) DEFAULT NULL COMMENT '视频格式：mp4/mov/avi等',
  `lyric_content` text COMMENT '歌词内容',
  `lyric_file_url` varchar(500) DEFAULT NULL COMMENT '歌词文件URL（LRC/TXT）',
  `has_translation` tinyint(4) DEFAULT '0' COMMENT '是否有翻译',
  `status` tinyint(4) DEFAULT '0' COMMENT '审核状态：0-待审核 1-已发布 2-已拒绝',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核人ID',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_reason` varchar(500) DEFAULT NULL COMMENT '审核意见',
  `view_count` bigint(20) DEFAULT '0' COMMENT '浏览次数',
  `like_count` int(11) DEFAULT '0' COMMENT '点赞数',
  `comment_count` int(11) DEFAULT '0' COMMENT '评论数',
  `share_count` int(11) DEFAULT '0' COMMENT '分享数',
  `related_song_id` bigint(20) DEFAULT NULL COMMENT '关联的歌曲ID',
  `related_mv_id` bigint(20) DEFAULT NULL COMMENT '关联的MVID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_work_type` (`work_type`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_related_song_id` (`related_song_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音乐广场投稿表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `music_square_work_like` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `work_id` bigint(20) NOT NULL COMMENT '音乐广场作品ID',
  `user_id` bigint(20) NOT NULL COMMENT '点赞用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_music_square_work_like_user` (`work_id`,`user_id`),
  KEY `idx_music_square_work_like_user` (`user_id`,`create_time`),
  KEY `idx_music_square_work_like_work` (`work_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音乐广场作品点赞关系表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `music_tag` (
  `id` bigint(20) NOT NULL COMMENT '标签ID',
  `name` varchar(50) NOT NULL COMMENT '标签名称',
  `category` varchar(20) NOT NULL COMMENT '标签分类：mood-情绪, scene-场景, style-风格, decade-年代, language-语言',
  `icon` varchar(100) DEFAULT NULL COMMENT '标签图标',
  `color` varchar(20) DEFAULT NULL COMMENT '标签颜色',
  `description` varchar(200) DEFAULT NULL COMMENT '标签描述',
  `use_count` int(11) DEFAULT '0' COMMENT '使用次数',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序',
  `is_hot` tinyint(4) DEFAULT '0' COMMENT '是否热门：0-否，1-是',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  KEY `idx_is_hot` (`is_hot`),
  KEY `idx_use_count` (`use_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音乐标签表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `music_user_profile` (
  `id` bigint(20) NOT NULL COMMENT '??ID',
  `user_id` bigint(20) NOT NULL COMMENT '??ID',
  `preferred_genres` text COMMENT '???????JSON??',
  `preferred_artists` text COMMENT '?????ID?JSON??',
  `preferred_languages` text COMMENT '?????JSON??',
  `preferred_eras` text COMMENT '?????JSON??',
  `preferred_moods` text COMMENT '?????JSON??',
  `avg_daily_duration` int(11) DEFAULT '0' COMMENT '??????????',
  `peak_active_hour` int(11) DEFAULT NULL COMMENT '???????0-23',
  `device_types` text COMMENT '???????JSON??',
  `common_ip` varchar(50) DEFAULT NULL COMMENT '??IP??',
  `register_date` date DEFAULT NULL COMMENT '????',
  `first_active_date` date DEFAULT NULL COMMENT '??????',
  `last_active_date` date DEFAULT NULL COMMENT '??????',
  `churn_date` date DEFAULT NULL COMMENT '????',
  `total_active_days` int(11) DEFAULT '0' COMMENT '??????',
  `total_play_duration` int(11) DEFAULT '0' COMMENT '????????',
  `total_play_count` int(11) DEFAULT '0' COMMENT '??????',
  `user_segment` varchar(30) DEFAULT 'NEW' COMMENT '????',
  `lifecycle_stage` varchar(30) DEFAULT 'AWARENESS' COMMENT '??????',
  `ltv_score` int(11) DEFAULT '0' COMMENT '????????',
  `churn_probability` int(11) DEFAULT '0' COMMENT '??????',
  `next_month_active_probability` int(11) DEFAULT '0' COMMENT '????????',
  `total_spending` bigint(20) DEFAULT '0' COMMENT '??????????',
  `total_vip_days` int(11) DEFAULT '0' COMMENT 'VIP????',
  `first_vip_date` date DEFAULT NULL COMMENT '??VIP??',
  `last_vip_date` date DEFAULT NULL COMMENT '??VIP??',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_music_user_profile_user` (`user_id`),
  KEY `idx_music_user_profile_segment` (`user_segment`,`lifecycle_stage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户音乐画像表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `mv` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name_en` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `artist_id` bigint(20) DEFAULT NULL,
  `artist_ids` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `artist_names` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `song_id` bigint(20) DEFAULT NULL,
  `song_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cover` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `tags` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签，逗号分隔',
  `publish_date` date DEFAULT NULL,
  `duration` int(11) DEFAULT NULL,
  `url_360p` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `url_720p` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `url_1080p` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `url_2160p` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '4K瓒呴珮娓匲RL锛?160P锛孷IP涓撲韩锛',
  `size_360p` bigint(20) DEFAULT NULL,
  `size_720p` bigint(20) DEFAULT NULL,
  `size_1080p` bigint(20) DEFAULT NULL,
  `size_2160p` bigint(20) DEFAULT NULL COMMENT '4K瓒呴珮娓呭ぇ灏忥紙瀛楄妭锛',
  `play_count` bigint(20) DEFAULT '0',
  `favorite_count` bigint(20) DEFAULT '0' COMMENT '鏀惰棌娆℃暟',
  `comment_count` bigint(20) DEFAULT '0' COMMENT '璇勮?娆℃暟',
  `share_count` bigint(20) DEFAULT '0' COMMENT '鍒嗕韩娆℃暟',
  `like_count` bigint(20) DEFAULT '0' COMMENT '点赞数',
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `priority` int(11) DEFAULT '0' COMMENT '浼樺厛绾',
  `is_paid` tinyint(4) DEFAULT '0' COMMENT '鏄?惁浠樿垂',
  `paid_resource_id` bigint(20) DEFAULT NULL COMMENT '浠樿垂璧勬簮ID',
  `price` decimal(10,2) DEFAULT NULL COMMENT '浠锋牸',
  `allow_download` tinyint(1) DEFAULT '1' COMMENT '是否允许下载：0-否，1-是',
  `allow_comment` tinyint(1) DEFAULT '1' COMMENT '是否允许评论：0-否，1-是',
  `allow_share` tinyint(1) DEFAULT '1' COMMENT '是否允许分享：0-否，1-是',
  PRIMARY KEY (`id`),
  KEY `idx_artist` (`artist_id`),
  KEY `idx_song` (`song_id`),
  KEY `idx_hot_mv` (`play_count`,`status`,`deleted`),
  KEY `idx_publish_mv` (`publish_date`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `mv_favorite` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `mv_id` bigint(20) NOT NULL COMMENT 'MV ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mv_id` (`mv_id`),
  KEY `idx_user_create_time` (`user_id`,`create_time`),
  KEY `idx_user_mv_deleted` (`user_id`,`mv_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MV收藏表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `notification` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `sender_id` bigint(20) DEFAULT NULL COMMENT '发送者ID（触发通知的用户）',
  `sender_name` varchar(100) DEFAULT NULL COMMENT '发送者名称（冗余字段，提升查询性能）',
  `sender_avatar` varchar(500) DEFAULT NULL COMMENT '发送者头像（冗余字段）',
  `group_id` varchar(50) DEFAULT NULL COMMENT '聚合组ID（同一组的通知会被聚合显示）',
  `group_count` int(11) DEFAULT '1' COMMENT '组内通知数量（用于显示等N人）',
  `group_amount` decimal(10,2) DEFAULT NULL COMMENT '组内累计金额（打赏、收益通知用）',
  `metadata` text COMMENT '元数据（JSON格式：金额、资源名、留言等）',
  `business_key` varchar(128) DEFAULT NULL COMMENT '可靠通知稳定业务事件键',
  `type` varchar(50) NOT NULL,
  `title` varchar(200) DEFAULT NULL,
  `content` text,
  `link` varchar(500) DEFAULT NULL,
  `related_id` bigint(20) DEFAULT NULL,
  `is_read` tinyint(4) DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_business_key` (`business_key`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_type` (`type`),
  KEY `idx_is_read` (`is_read`),
  KEY `idx_group_id` (`group_id`),
  KEY `idx_sender_id` (`sender_id`),
  KEY `idx_user_group` (`user_id`,`group_id`),
  KEY `idx_notification_type_read_deleted` (`type`,`is_read`,`deleted`),
  KEY `idx_notification_recipient_group_window` (`user_id`,`group_id`,`is_read`,`deleted`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `notification_broadcast_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `task_id` varchar(96) NOT NULL COMMENT '??????ID',
  `operator_id` bigint(20) NOT NULL COMMENT '????????ID',
  `title` varchar(120) NOT NULL COMMENT '????',
  `content` varchar(2000) NOT NULL COMMENT '????',
  `link` varchar(500) DEFAULT NULL COMMENT '????',
  `cover_url` varchar(1000) DEFAULT NULL,
  `cursor_user_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '?????????ID??',
  `total_users` int(11) NOT NULL DEFAULT '0' COMMENT '??????????',
  `success_count` int(11) NOT NULL DEFAULT '0' COMMENT '?????',
  `fail_count` int(11) NOT NULL DEFAULT '0' COMMENT '??????',
  `batch_count` int(11) NOT NULL DEFAULT '0' COMMENT '??????',
  `status` varchar(16) NOT NULL COMMENT 'pending/running/success/failed',
  `attempt_count` int(11) NOT NULL DEFAULT '0' COMMENT '???????',
  `max_attempts` int(11) NOT NULL DEFAULT '3' COMMENT '??????',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '??????',
  `next_retry_time` datetime DEFAULT NULL COMMENT '?????????',
  `started_at` datetime DEFAULT NULL COMMENT '??????',
  `completed_at` datetime DEFAULT NULL COMMENT '??????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_broadcast_task_id` (`task_id`),
  KEY `idx_notification_broadcast_due` (`status`,`next_retry_time`,`attempt_count`,`create_time`),
  KEY `idx_notification_broadcast_running` (`status`,`started_at`,`attempt_count`),
  KEY `idx_notification_broadcast_updated` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='???????????';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `notification_delivery_outbox` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '内部主键',
  `event_id` char(36) NOT NULL COMMENT '稳定投递事件ID',
  `notification_id` bigint(20) NOT NULL COMMENT 'notification inbox事实ID',
  `recipient_id` bigint(20) NOT NULL COMMENT '接收用户ID',
  `event_type` varchar(64) NOT NULL COMMENT '固定低基数事件类型',
  `unread_delta` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否增加未读数',
  `status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/processing/success/failed',
  `attempt_count` int(11) NOT NULL DEFAULT '0' COMMENT '领取次数',
  `max_attempts` int(11) NOT NULL DEFAULT '8' COMMENT '最大领取次数',
  `worker_id` char(36) DEFAULT NULL COMMENT '当前领取者',
  `lease_until` datetime DEFAULT NULL COMMENT '领取租约截止',
  `error_category` varchar(64) DEFAULT NULL COMMENT '固定低基数失败分类',
  `error_message` varchar(500) DEFAULT NULL COMMENT '截断后的诊断信息',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `delivered_at` datetime DEFAULT NULL COMMENT '服务端投递完成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_delivery_event` (`event_id`),
  KEY `idx_notification_delivery_due` (`status`,`next_retry_time`,`lease_until`),
  KEY `idx_notification_delivery_failures` (`status`,`update_time`),
  KEY `idx_notification_delivery_notification` (`notification_id`),
  KEY `idx_notification_delivery_recipient` (`recipient_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知实时投递事务outbox';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `order_resource` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) DEFAULT NULL COMMENT '???????',
  `payment_order_id` bigint(20) DEFAULT NULL COMMENT '????ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `resource_type` varchar(20) NOT NULL COMMENT '资源类型',
  `owner_id` bigint(20) DEFAULT NULL COMMENT '???ID',
  `owner_type` varchar(20) DEFAULT 'platform' COMMENT '?????: platform/creator',
  `resource_id` bigint(20) NOT NULL COMMENT '资源ID',
  `amount` decimal(10,2) NOT NULL COMMENT '????????',
  `platform_fee` decimal(10,2) DEFAULT '0.00' COMMENT '?????????',
  `creator_earnings` decimal(10,2) DEFAULT '0.00' COMMENT '?????????',
  `status` varchar(20) DEFAULT 'pending' COMMENT '??: pending/active/refunded',
  `purchase_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `refund_time` datetime DEFAULT NULL COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`payment_order_id`),
  KEY `idx_user_resource` (`user_id`,`resource_type`,`resource_id`),
  KEY `idx_order_resource_user` (`user_id`,`status`,`purchase_time`),
  KEY `idx_order_resource_owner` (`owner_id`,`owner_type`,`status`),
  KEY `idx_order_resource_target` (`resource_type`,`resource_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源购买记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `order_vip` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `vip_type` varchar(20) NOT NULL COMMENT 'VIP类型',
  `days` int(11) NOT NULL COMMENT 'VIP天数',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT 'legacy: 原价，单位元',
  `amount` decimal(10,2) NOT NULL COMMENT '支付金额，单位元',
  `payment_type` varchar(20) DEFAULT NULL COMMENT '支付方式',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态',
  `payment_order_id` bigint(20) DEFAULT NULL COMMENT '关联支付订单ID',
  `start_time` datetime DEFAULT NULL COMMENT 'VIP开始时间',
  `end_time` datetime DEFAULT NULL COMMENT 'VIP结束时间',
  `is_creator_apply` tinyint(4) DEFAULT '0' COMMENT '是否创作者申请',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `transaction_id` varchar(128) DEFAULT NULL COMMENT '第三方交易流水号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_order_vip_user_status_time` (`user_id`,`status`,`create_time`),
  KEY `idx_order_vip_payment_order` (`payment_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VIP订单表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `order_vip_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `order_id` bigint(20) DEFAULT NULL COMMENT '关联订单ID',
  `vip_level` int(11) DEFAULT '1' COMMENT 'VIP等级',
  `vip_days` int(11) DEFAULT NULL COMMENT 'VIP天数',
  `payment_type` varchar(20) DEFAULT NULL COMMENT '支付方式: alipay/wechat/points/activity',
  `payment_amount` int(11) DEFAULT NULL COMMENT '支付金额（分）',
  `start_time` datetime DEFAULT NULL COMMENT 'VIP开始时间',
  `end_time` datetime DEFAULT NULL COMMENT 'VIP结束时间',
  `is_gift` tinyint(4) DEFAULT '0' COMMENT '是否赠送（0-否，1-是）',
  `gift_from` bigint(20) DEFAULT NULL COMMENT '赠送者ID',
  `gift_message` varchar(200) DEFAULT NULL COMMENT '赠送留言',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_vip_record_order` (`order_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VIP购买记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `paid_entitlement_grant` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `payment_order_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `resource_type` varchar(20) NOT NULL,
  `resource_id` bigint(20) NOT NULL,
  `paid_resource_id` bigint(20) DEFAULT NULL,
  `grant_type` varchar(20) NOT NULL,
  `starts_at` datetime NOT NULL,
  `expires_at` datetime DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'active',
  `refund_id` bigint(20) DEFAULT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paid_entitlement_grant_order` (`payment_order_id`),
  KEY `idx_paid_entitlement_grant_effective` (`user_id`,`resource_type`,`resource_id`,`status`,`expires_at`),
  KEY `idx_paid_entitlement_grant_refund` (`refund_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按支付订单保存的付费权益授予事实';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `paid_resource_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `resource_type` varchar(20) NOT NULL,
  `resource_id` bigint(20) NOT NULL,
  `owner_id` bigint(20) NOT NULL,
  `owner_type` varchar(20) DEFAULT 'creator',
  `price` decimal(10,2) NOT NULL,
  `price_type` varchar(20) DEFAULT 'creator',
  `is_enabled` tinyint(1) DEFAULT '1',
  `status` varchar(20) DEFAULT 'pending',
  `sales_count` int(11) DEFAULT '0',
  `total_earnings` decimal(10,2) DEFAULT '0.00',
  `subscribe_period` int(11) DEFAULT NULL,
  `platform_fee_rate` decimal(5,4) DEFAULT '0.0200',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource` (`resource_type`,`resource_id`),
  KEY `idx_owner` (`owner_id`,`owner_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付费资源配置表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `payment_code_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_id` bigint(20) DEFAULT NULL COMMENT '?????ID',
  `operator_id` bigint(20) DEFAULT NULL COMMENT '???ID',
  `action_type` varchar(20) DEFAULT NULL COMMENT 'create/update/disable/delete/scan',
  `old_url` varchar(500) DEFAULT NULL COMMENT '????URL',
  `new_url` varchar(500) DEFAULT NULL COMMENT '????URL',
  `old_md5` varchar(64) DEFAULT NULL COMMENT '???MD5',
  `new_md5` varchar(64) DEFAULT NULL COMMENT '???MD5',
  `verify_code` varchar(128) DEFAULT NULL COMMENT '付款码验证码HMAC，不保存明文',
  `remark` varchar(500) DEFAULT NULL COMMENT '????',
  `ip_address` varchar(50) DEFAULT NULL COMMENT '??IP',
  `user_id` bigint(20) DEFAULT NULL COMMENT 'legacy: ?????ID?????????',
  `code` varchar(20) DEFAULT NULL COMMENT 'legacy: ????????????',
  `reward_type` varchar(20) DEFAULT NULL COMMENT 'legacy: ?????????????',
  `reward_amount` int(11) DEFAULT NULL COMMENT 'legacy: ?????????????',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态',
  `use_time` datetime DEFAULT NULL COMMENT '使用时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  KEY `idx_code` (`code`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_payment_code_log_config_time` (`config_id`,`create_time`),
  KEY `idx_payment_code_log_action` (`action_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付代码日志表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `payment_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) DEFAULT NULL COMMENT '????ID?NULL????',
  `user_type` varchar(20) DEFAULT 'platform' COMMENT 'platform/creator',
  `payment_type` varchar(20) NOT NULL COMMENT '??/?????: wechat/alipay',
  `qr_code_url` varchar(500) DEFAULT NULL COMMENT '?????URL',
  `qr_code_with_verify` varchar(500) DEFAULT NULL COMMENT '??????????URL',
  `verify_code` varchar(128) DEFAULT NULL COMMENT '付款码验证码HMAC，不保存明文',
  `md5_hash` varchar(64) DEFAULT NULL COMMENT '?????MD5',
  `is_enabled` tinyint(4) DEFAULT '1' COMMENT '????: 1??/0??',
  `daily_limit` decimal(12,2) DEFAULT '999999.00' COMMENT '??????????',
  `today_received` decimal(12,2) DEFAULT '0.00' COMMENT '???????????',
  `last_reset_date` date DEFAULT NULL COMMENT '??????????',
  `last_scan_time` datetime DEFAULT NULL COMMENT '??????',
  `config_data` text COMMENT '配置数据（JSON）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `owner_key` bigint(20) GENERATED ALWAYS AS (ifnull(`user_id`,0)) STORED COMMENT '??????0????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_config_owner_type` (`owner_key`,`payment_type`),
  KEY `idx_payment_config_lookup` (`user_id`,`payment_type`,`is_enabled`),
  KEY `idx_payment_config_md5` (`md5_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付配置表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `payment_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `payee_id` bigint(20) DEFAULT NULL COMMENT '???ID?NULL??????',
  `payment_type` varchar(20) NOT NULL DEFAULT 'wechat' COMMENT '??/?????: wechat/alipay',
  `business_type` varchar(50) NOT NULL COMMENT '????: vip/purchase/reward/subscribe',
  `business_id` bigint(20) DEFAULT NULL COMMENT '????ID',
  `amount` decimal(10,2) NOT NULL COMMENT '????????',
  `currency` varchar(3) NOT NULL DEFAULT 'CNY' COMMENT '???????????????????CNY',
  `order_title` varchar(200) DEFAULT NULL COMMENT '订单标题',
  `order_desc` varchar(500) DEFAULT NULL COMMENT '订单描述',
  `transaction_id` varchar(128) DEFAULT NULL COMMENT '第三方交易流水号',
  `prepay_id` varchar(128) DEFAULT NULL COMMENT '预支付ID（微信）',
  `qr_code_url` varchar(500) DEFAULT NULL COMMENT '二维码URL（支付宝）',
  `status` varchar(20) DEFAULT 'pending' COMMENT 'pending/submitted/paid/rejected/cancelled/expired/refunded',
  `completion_status` varchar(20) DEFAULT NULL COMMENT '??????: pending/completed/blocked/failed',
  `completion_time` datetime DEFAULT NULL COMMENT '????????',
  `completion_error` varchar(500) DEFAULT NULL COMMENT '????????????',
  `completion_attempt_count` int(11) NOT NULL DEFAULT '0' COMMENT '自动权益履约已领取次数',
  `completion_last_attempt_time` datetime DEFAULT NULL COMMENT '最近自动履约领取时间',
  `completion_next_retry_time` datetime DEFAULT NULL COMMENT '下一次允许自动履约时间',
  `completion_lease_owner` varchar(64) DEFAULT NULL COMMENT '自动履约租约持有实例',
  `completion_lease_until` datetime DEFAULT NULL COMMENT '自动履约租约到期时间',
  `completion_dead_letter_time` datetime DEFAULT NULL COMMENT '自动重试达到上限时间',
  `payment_proof` varchar(1000) DEFAULT NULL COMMENT '?????????URL',
  `user_remark` varchar(500) DEFAULT NULL COMMENT '??????',
  `verify_code` varchar(128) DEFAULT NULL COMMENT '订单上下文HMAC，不保存验证码明文',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '???ID',
  `review_time` datetime DEFAULT NULL COMMENT '????',
  `review_reason` varchar(500) DEFAULT NULL COMMENT '????',
  `product_info` text COMMENT '商品信息JSON',
  `idempotency_key` varchar(128) DEFAULT NULL COMMENT '同一用户范围内的下单幂等键',
  `request_hash` char(64) DEFAULT NULL COMMENT '服务端商品快照和请求关键字段SHA-256',
  `notify_time` datetime DEFAULT NULL COMMENT '支付回调时间',
  `notify_content` text COMMENT '回调内容',
  `expire_time` datetime DEFAULT NULL COMMENT '订单过期时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '?????????????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `order_no` (`order_no`),
  UNIQUE KEY `uk_payment_order_user_idempotency` (`user_id`,`idempotency_key`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_payment_type` (`payment_type`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_payment_order_user_status_time` (`user_id`,`status`,`create_time`),
  KEY `idx_payment_order_review_queue` (`status`,`create_time`),
  KEY `idx_payment_order_payee_status_time` (`payee_id`,`status`,`create_time`),
  KEY `idx_payment_order_business` (`business_type`,`business_id`),
  KEY `idx_payment_order_status_review_deleted` (`status`,`review_time`,`deleted`),
  KEY `idx_payment_order_completion_queue` (`status`,`completion_status`,`update_time`,`deleted`),
  KEY `idx_payment_completion_retry_due` (`completion_status`,`completion_next_retry_time`,`status`,`deleted`,`id`),
  KEY `idx_payment_completion_lease_due` (`completion_status`,`completion_lease_until`,`status`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `payment_proof_asset` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_id` varchar(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `order_id` bigint(20) NOT NULL,
  `owner_id` bigint(20) NOT NULL,
  `proof_reference` varchar(512) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `reference_digest` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `active_order_id` bigint(20) GENERATED ALWAYS AS ((case when (`status` = 'active') then `order_id` else NULL end)) STORED,
  `attempt_count` int(11) NOT NULL DEFAULT '0',
  `max_attempts` int(11) NOT NULL DEFAULT '8',
  `worker_id` varchar(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `lease_until` datetime DEFAULT NULL,
  `error_category` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `next_retry_time` datetime DEFAULT NULL,
  `cleaned_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_proof_event` (`event_id`),
  UNIQUE KEY `uk_payment_proof_reference` (`proof_reference`),
  UNIQUE KEY `uk_payment_proof_active_order` (`active_order_id`),
  KEY `idx_payment_proof_cleanup_due` (`status`,`next_retry_time`,`lease_until`,`attempt_count`,`id`),
  KEY `idx_payment_proof_order_history` (`order_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款凭证版本和旧文件回收事实';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `play_event_dead_letter` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `event_id` varchar(96) DEFAULT NULL COMMENT '??????ID',
  `topic` varchar(128) NOT NULL COMMENT 'Kafka??',
  `partition_id` int(11) NOT NULL COMMENT 'Kafka??',
  `offset_value` bigint(20) NOT NULL COMMENT 'Kafka???',
  `message_key` varchar(255) DEFAULT NULL COMMENT 'Kafka???',
  `payload` longtext COMMENT '??????',
  `retry_count` int(11) NOT NULL DEFAULT '0' COMMENT '??????',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '??????',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED/IGNORED',
  `handler_id` bigint(20) DEFAULT NULL COMMENT '?????ID',
  `handle_reason` varchar(500) DEFAULT NULL COMMENT '????',
  `first_failed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '??????',
  `last_failed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '??????',
  `resolved_at` datetime DEFAULT NULL COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_play_event_dead_letter_offset` (`topic`,`partition_id`,`offset_value`),
  KEY `idx_play_event_dead_letter_status_time` (`status`,`last_failed_at`),
  KEY `idx_play_event_dead_letter_event_id` (`event_id`),
  KEY `idx_play_event_dead_letter_status_update` (`status`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='???? Kafka ??';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `play_event_receipt` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `event_id` varchar(96) NOT NULL COMMENT '??????ID',
  `user_id` bigint(20) DEFAULT NULL COMMENT '??ID',
  `song_id` varchar(96) DEFAULT NULL COMMENT '???????ID',
  `is_local` tinyint(1) NOT NULL DEFAULT '0' COMMENT '??????',
  `status` varchar(16) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/PROCESSED',
  `processed_at` datetime DEFAULT NULL COMMENT '??????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_play_event_receipt_event_id` (`event_id`),
  KEY `idx_play_event_receipt_status_time` (`status`,`update_time`),
  KEY `idx_play_event_receipt_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='????????';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `playlist` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `creator_id` bigint(20) DEFAULT NULL COMMENT '创作者ID',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cover` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` tinyint(4) NOT NULL DEFAULT '1',
  `language` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '歌单语言',
  `is_public` tinyint(4) NOT NULL DEFAULT '1',
  `tags` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int(11) DEFAULT '0',
  `song_count` bigint(20) DEFAULT '0' COMMENT '姝屾洸鏁',
  `play_count` bigint(20) DEFAULT '0',
  `favorite_count` bigint(20) DEFAULT '0' COMMENT '鏀惰棌娆℃暟',
  `subscriber_count` bigint(20) DEFAULT '0' COMMENT '订阅数',
  `active_subscribers` int(11) DEFAULT '0' COMMENT '活跃订阅数',
  `monthly_revenue` decimal(10,2) DEFAULT '0.00' COMMENT '本月收入（分）',
  `total_revenue` decimal(10,2) DEFAULT '0.00' COMMENT '累计收入（分）',
  `visit_count` bigint(20) DEFAULT '0' COMMENT '璁块棶娆℃暟',
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `is_featured` tinyint(1) DEFAULT '0' COMMENT '是否精选',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `priority` int(11) DEFAULT '0' COMMENT '浼樺厛绾э紙0-鏅??锛?-涓?骇锛?-浜岀骇锛?-涓夌骇锛',
  `is_paid` tinyint(4) DEFAULT '0' COMMENT '鏄?惁浠樿垂锛?-鍏嶈垂锛?-浠樿垂',
  `paid_resource_id` bigint(20) DEFAULT NULL COMMENT '鍏宠仈鐨勪粯璐硅祫婧怚D',
  `price` decimal(10,2) DEFAULT NULL COMMENT '姝屽崟浠锋牸锛堝啑浣欏瓧娈碉級',
  `subscribe_period` int(11) DEFAULT NULL COMMENT '璁㈤槄鍛ㄦ湡锛堝ぉ鏁帮級',
  `allow_download` tinyint(1) DEFAULT '1' COMMENT '是否允许下载（0-否，1-是）',
  `allow_comment` tinyint(1) DEFAULT '1' COMMENT '是否允许评论（0-否，1-是）',
  `allow_share` tinyint(1) DEFAULT '1' COMMENT '是否允许分享（0-否，1是）',
  `intro` text COLLATE utf8mb4_unicode_ci COMMENT '姝屽崟璇︾粏浠嬬粛锛堟敮鎸佸瘜鏂囨湰锛',
  `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '姝屽崟鍒嗙被锛堟祦琛?鎽囨粴/姘戣埃/鐢靛瓙/鐖靛＋/鍙ゅ吀绛夛級',
  PRIMARY KEY (`id`),
  KEY `idx_user_public` (`user_id`,`is_public`,`deleted`),
  KEY `idx_hot_playlist` (`play_count`,`favorite_count`,`deleted`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_paid_permissions` (`is_paid`,`allow_download`,`allow_comment`,`allow_share`),
  KEY `idx_playlist_status_deleted_hot` (`status`,`deleted`,`play_count`,`favorite_count`),
  KEY `idx_playlist_user_type_deleted` (`user_id`,`type`,`deleted`),
  FULLTEXT KEY `ft_playlist` (`name`,`description`) WITH PARSER `ngram`  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `playlist_collaboration_audit` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '内部主键',
  `event_id` char(36) NOT NULL COMMENT '稳定协作事件ID',
  `playlist_id` bigint(20) NOT NULL COMMENT '歌单ID',
  `actor_id` bigint(20) NOT NULL COMMENT '认证上下文操作者ID',
  `target_user_id` bigint(20) DEFAULT NULL COMMENT '受影响协作者ID',
  `event_type` varchar(64) NOT NULL COMMENT '协作变更类型',
  `before_summary` varchar(2000) DEFAULT NULL COMMENT '变更前最小摘要',
  `after_summary` varchar(2000) DEFAULT NULL COMMENT '变更后最小摘要',
  `reason` varchar(500) DEFAULT NULL COMMENT '受控原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_playlist_collaboration_audit_event` (`event_id`),
  KEY `idx_playlist_collaboration_audit_playlist` (`playlist_id`,`create_time`),
  KEY `idx_playlist_collaboration_audit_actor` (`actor_id`,`create_time`),
  KEY `idx_playlist_collaboration_audit_target` (`target_user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协作歌单只追加审计事件';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `playlist_collaborator` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `playlist_id` bigint(20) NOT NULL COMMENT '歌单ID',
  `user_id` bigint(20) NOT NULL COMMENT '协作者用户ID',
  `role` varchar(20) DEFAULT 'viewer' COMMENT '角色：owner-所有者, editor-编辑者, viewer-查看者',
  `can_add` tinyint(4) DEFAULT '0' COMMENT '是否可以添加歌曲：0-否，1-是',
  `can_remove` tinyint(4) DEFAULT '0' COMMENT '是否可以删除歌曲：0-否，1-是',
  `can_edit` tinyint(4) DEFAULT '0' COMMENT '是否可以编辑歌单信息：0-否，1-是',
  `joined_time` datetime DEFAULT NULL COMMENT '加入时间',
  `invited_by` bigint(20) DEFAULT NULL COMMENT '邀请人ID',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态：pending-待接受, accepted-已接受, rejected-已拒绝',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_playlist_collaborator_active` (`playlist_id`,`user_id`,`deleted`),
  KEY `idx_playlist_id` (`playlist_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌单协作者表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `playlist_favorite` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `user_id` bigint(20) NOT NULL COMMENT '鐢ㄦ埛ID',
  `playlist_id` bigint(20) NOT NULL COMMENT '鏀惰棌鐨勬瓕鍗旾D',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '删除标记（0-未删除，1-已删除）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鏀惰棌鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（方案B：恢复记录时更新）',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_playlist_id` (`playlist_id`),
  KEY `idx_user_playlist_deleted` (`user_id`,`playlist_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢ㄦ埛鏀惰棌姝屽崟琛';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `playlist_operation_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `playlist_id` bigint(20) NOT NULL COMMENT '歌单ID',
  `user_id` bigint(20) NOT NULL COMMENT '操作用户ID',
  `operation_type` varchar(50) NOT NULL COMMENT '操作类型：add-添加, remove-删除, edit-编辑, invite-邀请, accept-接受',
  `song_id` bigint(20) DEFAULT NULL COMMENT '歌曲ID',
  `song_name` varchar(200) DEFAULT NULL COMMENT '歌曲名称（快照）',
  `description` varchar(500) DEFAULT NULL COMMENT '操作描述',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_playlist_id` (`playlist_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌单操作日志表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `playlist_revenue` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `playlist_id` bigint(20) NOT NULL COMMENT '歌单ID',
  `creator_id` bigint(20) NOT NULL COMMENT '创作者ID',
  `user_id` bigint(20) NOT NULL COMMENT '订阅用户ID',
  `subscribe_id` bigint(20) NOT NULL COMMENT '订阅记录ID',
  `order_id` bigint(20) NOT NULL COMMENT '订单ID',
  `amount` decimal(10,2) NOT NULL COMMENT '交易金额（分）',
  `creator_earnings` decimal(10,2) NOT NULL COMMENT '创作者收益（分）',
  `platform_fee` decimal(10,2) NOT NULL COMMENT '平台费用（分）',
  `platform_fee_rate` decimal(5,4) DEFAULT '0.0200' COMMENT '平台费率',
  `revenue_type` varchar(20) DEFAULT 'subscribe' COMMENT '收益类型',
  `is_settled` tinyint(1) DEFAULT '0' COMMENT '是否已结算',
  `settled_time` datetime DEFAULT NULL COMMENT '结算时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_playlist_id` (`playlist_id`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_is_settled` (`is_settled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌单收益记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `playlist_song` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `playlist_id` bigint(20) NOT NULL,
  `song_id` bigint(20) NOT NULL,
  `sort_order` int(11) DEFAULT '0',
  `add_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_playlist_song` (`playlist_id`,`song_id`,`deleted`),
  KEY `idx_sort_order` (`playlist_id`,`sort_order`),
  KEY `idx_playlist_song_order` (`playlist_id`,`sort_order`,`deleted`),
  KEY `idx_playlist_sort_deleted` (`playlist_id`,`sort_order`,`deleted`),
  KEY `idx_playlist_song_playlist_deleted` (`playlist_id`,`deleted`),
  KEY `idx_playlist_song_pair_deleted` (`playlist_id`,`song_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `playlist_subscribe` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `playlist_id` bigint(20) NOT NULL COMMENT '歌单ID',
  `user_id` bigint(20) NOT NULL COMMENT '订阅用户ID',
  `creator_id` bigint(20) NOT NULL COMMENT '创作者ID',
  `subscribe_type` varchar(20) DEFAULT 'monthly' COMMENT '订阅类型',
  `price` decimal(10,2) NOT NULL COMMENT '订阅价格（分）',
  `start_time` datetime NOT NULL COMMENT '订阅开始时间',
  `expire_time` datetime NOT NULL COMMENT '订阅到期时间',
  `auto_renew` tinyint(1) DEFAULT '0' COMMENT '是否自动续费',
  `status` varchar(20) DEFAULT 'active' COMMENT '状态',
  `payment_order_id` bigint(20) DEFAULT NULL COMMENT '支付订单ID',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_playlist_user` (`playlist_id`,`user_id`,`deleted`),
  UNIQUE KEY `uk_playlist_subscribe_payment_order` (`payment_order_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_status` (`status`),
  KEY `idx_expire_time` (`expire_time`),
  KEY `idx_playlist_subscribe_playlist_deleted_start` (`playlist_id`,`deleted`,`start_time`),
  KEY `idx_playlist_subscribe_playlist_deleted_status_expire` (`playlist_id`,`deleted`,`status`,`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌单订阅表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `playlist_subscribe_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '订阅用户ID',
  `playlist_id` bigint(20) NOT NULL COMMENT '歌单ID',
  `creator_id` bigint(20) NOT NULL COMMENT '歌单创作者ID',
  `subscribe_type` varchar(20) NOT NULL COMMENT 'month/quarter/year',
  `days` int(11) NOT NULL COMMENT '服务端冻结的订阅天数',
  `amount` decimal(10,2) NOT NULL COMMENT '服务端冻结的支付金额，单位元',
  `auto_renew` tinyint(1) NOT NULL DEFAULT '0' COMMENT '支付完成后是否记录续费意愿',
  `status` varchar(20) NOT NULL DEFAULT 'pending_payment' COMMENT 'pending_payment/paid/cancelled/failed',
  `payment_order_id` bigint(20) DEFAULT NULL COMMENT '关联支付订单ID',
  `idempotency_key` varchar(128) NOT NULL COMMENT '用户范围内的订阅业务幂等键',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_playlist_subscribe_order_user_idem` (`user_id`,`idempotency_key`),
  UNIQUE KEY `uk_playlist_subscribe_order_payment` (`payment_order_id`),
  KEY `idx_playlist_subscribe_order_user_status` (`user_id`,`status`,`deleted`,`create_time`),
  KEY `idx_playlist_subscribe_order_playlist_status` (`playlist_id`,`status`,`deleted`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌单付费订阅业务单';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `post` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '动态ID',
  `user_id` bigint(20) NOT NULL COMMENT '发布用户ID',
  `content` text COMMENT '动态内容',
  `images` varchar(1000) DEFAULT NULL COMMENT '图片列表(JSON)',
  `image_info` json DEFAULT NULL COMMENT '图片信息（压缩版/原图URL）',
  `video_info` json DEFAULT NULL COMMENT '视频信息（多清晰度URL）',
  `resource_type` varchar(50) DEFAULT NULL COMMENT '关联资源类型',
  `resource_id` bigint(20) DEFAULT NULL COMMENT '关联资源ID',
  `topics` varchar(500) DEFAULT NULL COMMENT '话题标签数组(JSON)',
  `post_type` varchar(50) DEFAULT NULL COMMENT '动态类型',
  `visibility` varchar(50) DEFAULT 'public' COMMENT '可见性',
  `is_listen_diary` tinyint(1) DEFAULT '0' COMMENT '是否听歌日记',
  `listen_data` varchar(500) DEFAULT NULL COMMENT '听书数据(JSON)',
  `like_count` int(11) DEFAULT '0' COMMENT '点赞数',
  `comment_count` int(11) DEFAULT '0' COMMENT '评论数',
  `allow_comment` tinyint(1) NOT NULL DEFAULT '1' COMMENT '投稿人是否允许评论: 1允许 0关闭',
  `official_comment_closed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '官方是否强制关闭评论',
  `share_count` int(11) DEFAULT '0' COMMENT '分享数',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_post_type` (`post_type`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_is_deleted` (`is_deleted`),
  KEY `idx_post_user_deleted_stats` (`user_id`,`is_deleted`),
  KEY `idx_post_user_video_visibility` (`user_id`,`post_type`,`is_deleted`,`visibility`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音乐动态表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `post_comment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `post_id` bigint(20) NOT NULL COMMENT '动态ID',
  `user_id` bigint(20) NOT NULL COMMENT '评论用户ID',
  `parent_id` bigint(20) DEFAULT '0' COMMENT '父评论ID',
  `reply_user_id` bigint(20) DEFAULT NULL COMMENT '被回复用户ID',
  `content` text NOT NULL COMMENT '评论内容',
  `like_count` int(11) DEFAULT '0' COMMENT '点赞数',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态评论表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `post_like` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '点赞ID',
  `post_id` bigint(20) NOT NULL COMMENT '动态ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`,`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态点赞表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `push_notification` (
  `id` bigint(20) NOT NULL COMMENT 'primary key',
  `push_id` varchar(64) NOT NULL COMMENT 'unique push id',
  `type` varchar(32) NOT NULL COMMENT 'type: announcement, activity, newsong, topic',
  `title` varchar(128) NOT NULL COMMENT 'title',
  `description` varchar(2000) DEFAULT NULL,
  `badge` varchar(32) DEFAULT NULL COMMENT 'badge text',
  `cover_url` varchar(512) DEFAULT NULL COMMENT 'cover image url',
  `link` varchar(512) DEFAULT NULL COMMENT 'link url',
  `fallback_link` varchar(512) DEFAULT NULL COMMENT '澶囩敤璺宠浆閾炬帴',
  `priority` int(11) DEFAULT '0' COMMENT 'priority',
  `start_time` datetime DEFAULT NULL COMMENT 'start time',
  `end_time` datetime DEFAULT NULL COMMENT 'end time',
  `status` tinyint(4) DEFAULT '1' COMMENT 'status 0-disabled 1-enabled',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `deleted` tinyint(4) DEFAULT '0' COMMENT 'deleted 0-no 1-yes',
  `review_status` tinyint(4) DEFAULT '1' COMMENT '瀹℃牳鐘舵€?0寰呭鏍?1閫氳繃,2鎷掔粷',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '瀹℃牳浜篒D',
  `review_time` datetime DEFAULT NULL COMMENT '瀹℃牳鏃堕棿',
  `review_remark` varchar(500) DEFAULT NULL COMMENT '瀹℃牳澶囨敞',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_push_id` (`push_id`),
  KEY `idx_type_status` (`type`,`status`),
  KEY `idx_priority` (`priority`),
  KEY `idx_time_range` (`start_time`,`end_time`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_push_news_review_time` (`type`,`status`,`review_status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='push notification';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `qualified_play_fact` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_id` varchar(96) NOT NULL COMMENT '原播放事件幂等键',
  `user_id` bigint(20) NOT NULL,
  `song_id` bigint(20) NOT NULL,
  `progress_seconds` int(11) NOT NULL,
  `duration_seconds` int(11) DEFAULT NULL,
  `policy_version` varchar(32) NOT NULL,
  `fact_status` varchar(16) NOT NULL DEFAULT 'valid' COMMENT 'valid/revoked',
  `revoke_reason` varchar(500) DEFAULT NULL,
  `occurred_at` datetime NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qualified_play_event` (`event_id`),
  KEY `idx_qualified_play_window_song` (`fact_status`,`occurred_at`,`song_id`),
  KEY `idx_qualified_play_user_window` (`user_id`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公开统计合格播放不可变事实';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `ranking_build_lock` (
  `ranking_type` varchar(32) NOT NULL,
  `partition_key` varchar(64) NOT NULL DEFAULT 'all',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`ranking_type`,`partition_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排行榜生成单写者锁';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `ranking_snapshot` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `snapshot_id` char(36) NOT NULL,
  `ranking_type` varchar(32) NOT NULL,
  `partition_key` varchar(64) NOT NULL DEFAULT 'all',
  `rule_version` varchar(32) NOT NULL,
  `window_start` datetime NOT NULL,
  `window_end` datetime NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'building' COMMENT 'building/ready/failed/retired',
  `is_active` tinyint(4) NOT NULL DEFAULT '0',
  `source_fact_count` bigint(20) NOT NULL DEFAULT '0',
  `item_count` int(11) NOT NULL DEFAULT '0',
  `content_checksum` char(64) DEFAULT NULL,
  `error_category` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ranking_snapshot_id` (`snapshot_id`),
  KEY `idx_ranking_snapshot_active` (`ranking_type`,`partition_key`,`is_active`,`completed_at`),
  KEY `idx_ranking_snapshot_window` (`ranking_type`,`window_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可回放排行榜冻结快照';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `ranking_snapshot_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `snapshot_id` char(36) NOT NULL,
  `rank_position` int(11) NOT NULL,
  `item_type` varchar(32) NOT NULL,
  `item_id` bigint(20) NOT NULL,
  `score` decimal(20,6) NOT NULL,
  `valid_fact_count` bigint(20) NOT NULL,
  `tie_breaker` varchar(128) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ranking_snapshot_rank` (`snapshot_id`,`rank_position`),
  UNIQUE KEY `uk_ranking_snapshot_item` (`snapshot_id`,`item_type`,`item_id`),
  KEY `idx_ranking_snapshot_item_lookup` (`item_type`,`item_id`,`snapshot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排行榜快照项目';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `refund_credit` (
  `id` bigint(20) NOT NULL COMMENT '??ID',
  `user_id` bigint(20) NOT NULL COMMENT '??ID',
  `credit_score` int(11) NOT NULL DEFAULT '100' COMMENT '???????',
  `total_refund_count` int(11) NOT NULL DEFAULT '0' COMMENT '?????',
  `unreasonable_refund_count` int(11) NOT NULL DEFAULT '0' COMMENT '???????',
  `last_update_time` datetime DEFAULT NULL COMMENT '??????',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_credit_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款信用分表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `refund_credit_log` (
  `id` bigint(20) NOT NULL COMMENT '??ID',
  `user_id` bigint(20) NOT NULL COMMENT '??ID',
  `refund_id` bigint(20) DEFAULT NULL COMMENT '??ID',
  `change_type` varchar(20) NOT NULL COMMENT '????',
  `score` int(11) NOT NULL DEFAULT '0' COMMENT '????',
  `reason` varchar(500) DEFAULT NULL COMMENT '????',
  `after_score` int(11) DEFAULT NULL COMMENT '?????',
  `credit_period` varchar(20) DEFAULT NULL COMMENT '????',
  `period_start_time` datetime DEFAULT NULL COMMENT '??????',
  `period_end_time` datetime DEFAULT NULL COMMENT '??????',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  KEY `idx_refund_credit_log_user` (`user_id`,`credit_period`,`create_time`),
  KEY `idx_refund_credit_log_refund` (`refund_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款信用分变动日志表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `refund_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `refund_no` varchar(64) DEFAULT NULL COMMENT '退款单号',
  `order_id` bigint(20) NOT NULL COMMENT '订单ID',
  `original_order_status` varchar(32) NOT NULL DEFAULT 'paid' COMMENT '进入退款中之前的订单状态，用于拒绝或取消时安全恢复',
  `order_type` varchar(30) DEFAULT NULL COMMENT '订单类型: vip/purchase/reward/subscribe',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '退款金额，单位元',
  `reason` varchar(500) DEFAULT NULL COMMENT '退款原因',
  `description` text COMMENT '详细说明',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `refund_amount` int(11) DEFAULT NULL COMMENT 'legacy: 退款金额（分），当前实体使用 amount',
  `refund_reason` varchar(500) DEFAULT NULL COMMENT '退款原因',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核人ID',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_reason` varchar(500) DEFAULT NULL COMMENT '审核说明',
  `completed_time` datetime DEFAULT NULL COMMENT '完成时间',
  `feedback_id` bigint(20) DEFAULT NULL COMMENT '关联反馈ID',
  `is_unreasonable` tinyint(4) DEFAULT '0' COMMENT '是否不合理退款',
  `unreasonable_reason` varchar(500) DEFAULT NULL COMMENT '不合理退款原因',
  `handler_id` bigint(20) DEFAULT NULL COMMENT '处理人ID',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_refund_record_user_status_time` (`user_id`,`status`,`create_time`),
  KEY `idx_refund_record_order` (`order_id`,`order_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `relation_migration_review` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `migration_scope` varchar(32) NOT NULL,
  `legacy_table` varchar(64) NOT NULL,
  `legacy_id` bigint(20) NOT NULL,
  `issue_type` varchar(64) NOT NULL,
  `evidence_summary` varchar(500) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/resolved/ignored',
  `reviewer_id` bigint(20) DEFAULT NULL,
  `resolution_reason` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_relation_migration_issue` (`migration_scope`,`legacy_table`,`legacy_id`),
  KEY `idx_relation_migration_pending` (`migration_scope`,`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可自动判定关系的人工复核队列';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `report` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `reporter_id` bigint(20) NOT NULL COMMENT '举报人ID',
  `target_type` varchar(20) NOT NULL COMMENT '举报目标类型',
  `target_id` bigint(20) NOT NULL COMMENT '举报目标ID',
  `report_type` varchar(20) DEFAULT NULL COMMENT '举报类型',
  `reason` varchar(200) DEFAULT NULL COMMENT '举报原因',
  `description` text COMMENT '详细描述',
  `attachment_urls` text COMMENT '附件URLs',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态',
  `handler_id` bigint(20) DEFAULT NULL COMMENT '处理人ID',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `handle_result` varchar(500) DEFAULT NULL COMMENT '处理结果',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核人ID',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_result` varchar(500) DEFAULT NULL COMMENT '审核结果',
  `action` varchar(50) DEFAULT NULL COMMENT '处理措施',
  `is_rewarded` tinyint(4) DEFAULT '0' COMMENT '是否已发放奖励',
  PRIMARY KEY (`id`),
  KEY `idx_target` (`target_type`,`target_id`),
  KEY `idx_reporter` (`reporter_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `report_credit` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `period` varchar(7) NOT NULL COMMENT '周期（格式：yyyyMM）',
  `report_count` int(11) DEFAULT '0' COMMENT '举报次数',
  `approved_count` int(11) DEFAULT '0' COMMENT '通过次数',
  `rejected_count` int(11) DEFAULT '0' COMMENT '驳回次数',
  `credit_score` int(11) DEFAULT '100' COMMENT '信用分数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_period` (`user_id`,`period`),
  KEY `idx_credit_score` (`credit_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报信用分表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `report_reward` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `report_id` bigint(20) NOT NULL COMMENT '举报ID',
  `reward_type` varchar(20) NOT NULL COMMENT '奖励类型',
  `reward_amount` int(11) DEFAULT '0' COMMENT '奖励数量',
  `reward_status` varchar(20) DEFAULT 'pending' COMMENT '奖励状态',
  `issue_time` datetime DEFAULT NULL COMMENT '发放时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_reward_report_id` (`report_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_report_id` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报奖励表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `reward_alert` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `creator_id` bigint(20) DEFAULT NULL COMMENT '关联的创作者ID',
  `alert_type` varchar(20) NOT NULL COMMENT '预警类型：daily-每日超限，continuous-连续多日',
  `alert_level` varchar(20) NOT NULL COMMENT '预警级别：warning-警告，danger-危险',
  `alert_days` int(11) DEFAULT NULL COMMENT '连续天数',
  `alert_amount` decimal(10,2) DEFAULT NULL COMMENT '预警金额',
  `alert_detail` text COMMENT '预警详情',
  `is_handled` tinyint(1) DEFAULT '0' COMMENT '是否已处理',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `handle_by` varchar(50) DEFAULT NULL COMMENT '处理人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_alert_type` (`alert_type`),
  KEY `idx_is_handled` (`is_handled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打赏预警记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `reward_daily_limit` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `limit_date` date NOT NULL COMMENT '限制日期',
  `reward_count` int(11) NOT NULL DEFAULT '0' COMMENT '打赏次数',
  `reward_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '打赏总金额',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`,`limit_date`),
  KEY `idx_limit_date` (`limit_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日打赏限制表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `reward_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `user_id` bigint(20) NOT NULL COMMENT '打赏用户ID',
  `creator_id` bigint(20) NOT NULL COMMENT '创作者ID',
  `resource_id` bigint(20) DEFAULT NULL COMMENT '关联资源ID',
  `resource_type` varchar(50) DEFAULT NULL COMMENT '资源类型(song/album/playlist)',
  `amount` decimal(10,2) NOT NULL COMMENT '打赏金额',
  `message` varchar(500) DEFAULT NULL COMMENT '打赏留言',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending-待支付，paid-已支付',
  `is_anonymous` tinyint(1) DEFAULT '0' COMMENT '是否匿名',
  `payment_order_id` bigint(20) DEFAULT NULL COMMENT '支付订单ID',
  `platform_fee` decimal(10,2) DEFAULT NULL COMMENT '平台手续费',
  `creator_earnings` decimal(10,2) DEFAULT NULL COMMENT '创作者收益',
  `ip` varchar(50) DEFAULT NULL COMMENT '打赏IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '用户代理',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_reward_record_payment_order` (`payment_order_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_reward_resource_status_time` (`resource_type`,`resource_id`,`status`,`create_time`),
  KEY `idx_reward_creator_time` (`creator_id`,`create_time`),
  KEY `idx_reward_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打赏记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `reward_record_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `reward_type` varchar(20) NOT NULL COMMENT '奖励类型',
  `reward_amount` int(11) DEFAULT '0' COMMENT '奖励数量',
  `reward_reason` varchar(200) DEFAULT NULL COMMENT '奖励原因',
  `related_id` bigint(20) DEFAULT NULL COMMENT '关联ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_type` (`reward_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖励记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `scenario_preset` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍦烘櫙浠ｇ爜 workout/sleep/study',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍦烘櫙鍚嶇О',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鍦烘櫙鎻忚堪',
  `min_danceability` decimal(4,3) DEFAULT NULL COMMENT '鏈?皬鑸炴洸鎰',
  `max_danceability` decimal(4,3) DEFAULT NULL COMMENT '鏈?ぇ鑸炴洸鎰',
  `min_energy` decimal(4,3) DEFAULT NULL COMMENT '鏈?皬鑳介噺',
  `max_energy` decimal(4,3) DEFAULT NULL COMMENT '鏈?ぇ鑳介噺',
  `min_valence` decimal(4,3) DEFAULT NULL COMMENT '鏈?皬鎯呯华',
  `max_valence` decimal(4,3) DEFAULT NULL COMMENT '鏈?ぇ鎯呯华',
  `min_tempo` decimal(7,2) DEFAULT NULL COMMENT '鏈?皬BPM',
  `max_tempo` decimal(7,2) DEFAULT NULL COMMENT '鏈?ぇBPM',
  `min_acousticness` decimal(4,3) DEFAULT NULL COMMENT '鏈?皬鍘熷０绋嬪害',
  `max_acousticness` decimal(4,3) DEFAULT NULL COMMENT '鏈?ぇ鍘熷０绋嬪害',
  `min_instrumentalness` decimal(4,3) DEFAULT NULL COMMENT '鏈?皬绾?煶涔愮▼搴',
  `max_instrumentalness` decimal(4,3) DEFAULT NULL COMMENT '鏈?ぇ绾?煶涔愮▼搴',
  `sort_field` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'hot_score' COMMENT '鎺掑簭瀛楁?',
  `sort_order` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT 'DESC' COMMENT '鎺掑簭鏂瑰悜',
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鍦烘櫙鎺ㄨ崘棰勮?';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `schema_migration_ledger` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `script_name` varchar(255) NOT NULL COMMENT 'SQL?????',
  `checksum` varchar(128) DEFAULT NULL COMMENT '?????????????',
  `applied_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '???????',
  `applied_by` varchar(128) DEFAULT NULL COMMENT '???????',
  `note` varchar(1000) DEFAULT NULL COMMENT '??',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schema_migration_script` (`script_name`),
  KEY `idx_schema_migration_applied_at` (`applied_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='?????????';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `search_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `keyword` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `search_type` int(11) DEFAULT '1' COMMENT '搜索类型（1-全部，2-单曲，3-专辑，4-歌手，5-歌单，6-MV，7-用户）',
  `result_count` int(11) DEFAULT '0' COMMENT '结果数量',
  `deleted` int(11) DEFAULT '0' COMMENT '逻辑删除（0-未删除，1-已删除）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（搜索时间）',
  `search_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`search_time`),
  KEY `idx_search_type` (`search_type`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `search_index_sync_outbox` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_id` varchar(36) NOT NULL,
  `resource_type` varchar(20) NOT NULL,
  `resource_id` bigint(20) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'pending',
  `attempt_count` int(11) NOT NULL DEFAULT '0',
  `max_attempts` int(11) NOT NULL DEFAULT '8',
  `worker_id` varchar(36) DEFAULT NULL,
  `lease_until` datetime DEFAULT NULL,
  `error_category` varchar(64) DEFAULT NULL,
  `next_retry_time` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_search_index_sync_event` (`event_id`),
  KEY `idx_search_index_sync_due` (`deleted`,`status`,`next_retry_time`,`lease_until`,`attempt_count`,`id`),
  KEY `idx_search_index_sync_resource` (`resource_type`,`resource_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索索引增量同步事务outbox';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `search_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) DEFAULT NULL COMMENT '用户ID（可为空，表示未登录）',
  `query` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '搜索词',
  `corrected_query` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '纠错后的搜索词',
  `search_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'all' COMMENT '搜索类型',
  `result_count` int(11) DEFAULT '0' COMMENT '结果数量',
  `clicked_item_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '点击的物品类型',
  `clicked_item_id` bigint(20) DEFAULT NULL COMMENT '点击的物品ID',
  `click_position` int(11) DEFAULT NULL COMMENT '点击位置',
  `has_click` tinyint(4) DEFAULT '0' COMMENT '是否有点击',
  `search_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '搜索时间',
  `click_time` datetime DEFAULT NULL COMMENT '点击时间',
  `device_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备类型',
  `platform` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '平台',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`search_time`),
  KEY `idx_query` (`query`),
  KEY `idx_search_time` (`search_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索日志表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `search_query_stats` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `query` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `search_count` int(11) DEFAULT '1' COMMENT '搜索次数',
  `user_count` int(11) DEFAULT '1' COMMENT '搜索人数',
  `click_count` int(11) DEFAULT '0' COMMENT '点击次数',
  `click_rate` decimal(5,4) DEFAULT '0.0000' COMMENT '点击率',
  `avg_result_count` decimal(8,2) DEFAULT '0.00' COMMENT '平均结果数',
  `avg_position` decimal(5,2) DEFAULT '0.00' COMMENT '平均点击位置',
  `last_search_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `first_search_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `query` (`query`),
  KEY `idx_search_count` (`search_count`),
  KEY `idx_user_count` (`user_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索词统计表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `sensitive_data_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `user_id` bigint(20) NOT NULL COMMENT '璁块棶鐢ㄦ埛ID',
  `operator_id` bigint(20) DEFAULT NULL COMMENT '鎿嶄綔鍛業D锛堢?鐞嗗憳鎿嶄綔鏃讹級',
  `operation_type` varchar(20) NOT NULL COMMENT '鎿嶄綔绫诲瀷: read/update/delete/export',
  `data_type` varchar(50) DEFAULT NULL COMMENT '鏁版嵁绫诲瀷: user_phone/user_email/user_idcard绛',
  `target_id` bigint(20) DEFAULT NULL COMMENT '鐩?爣鏁版嵁ID',
  `request_uri` varchar(500) DEFAULT NULL COMMENT '璇锋眰URI',
  `ip_address` varchar(50) DEFAULT NULL COMMENT 'IP鍦板潃',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '鐢ㄦ埛浠ｇ悊',
  `result` varchar(20) NOT NULL COMMENT '缁撴灉: success/failed/blocked',
  `error_message` varchar(500) DEFAULT NULL COMMENT '閿欒?淇℃伅',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_data_type` (`data_type`),
  KEY `idx_result` (`result`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鏁忔劅鏁版嵁璁块棶鏃ュ織琛';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `share_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `user_id` bigint(20) NOT NULL COMMENT '鍒嗕韩鐢ㄦ埛ID',
  `share_type` varchar(20) NOT NULL COMMENT '鍒嗕韩绫诲瀷: song/album/artist/playlist/mv/post',
  `target_id` bigint(20) NOT NULL COMMENT '鍒嗕韩鐩?爣ID',
  `share_channel` varchar(20) DEFAULT NULL COMMENT '鍒嗕韩娓犻亾: wechat/weibo/qq/link/copy',
  `share_title` varchar(200) DEFAULT NULL COMMENT '鍒嗕韩鏍囬?',
  `share_description` varchar(500) DEFAULT NULL COMMENT '鍒嗕韩鎻忚堪',
  `share_url` varchar(500) DEFAULT NULL COMMENT '鍒嗕韩閾炬帴',
  `view_count` int(11) DEFAULT '0' COMMENT '琚?煡鐪嬫?鏁',
  `click_count` int(11) DEFAULT '0' COMMENT '琚?偣鍑绘?鏁',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '閫昏緫鍒犻櫎锛?-鏈?垹闄わ紝1-宸插垹闄わ級',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_target` (`share_type`,`target_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鍒嗕韩璁板綍琛';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `signin_achievement` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '成就ID',
  `days` int(11) NOT NULL COMMENT '签到天数',
  `achievement_name` varchar(100) NOT NULL COMMENT '成就名称',
  `achievement_desc` varchar(200) DEFAULT NULL COMMENT '成就描述',
  `reward_points` int(11) DEFAULT '0' COMMENT '奖励活跃值',
  `reward_decoration_id` bigint(20) DEFAULT NULL COMMENT '奖励装饰ID',
  `reward_badge_id` bigint(20) DEFAULT NULL COMMENT '奖励徽章ID',
  `reward_vip_days` int(11) DEFAULT '0' COMMENT '奖励VIP天数',
  `icon_url` varchar(500) DEFAULT NULL COMMENT '图标URL',
  `badge_url` varchar(500) DEFAULT NULL COMMENT '徽章图片URL',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序',
  `status` tinyint(4) DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `days` (`days`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到成就表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `social_recommend_preference` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `enabled` tinyint(4) DEFAULT '0' COMMENT '是否启用社交推荐（0-否，1-是）',
  `show_source` tinyint(4) DEFAULT '1' COMMENT '是否显示推荐来源（0-否，1-是）',
  `allow_shared` tinyint(4) DEFAULT '0' COMMENT '是否允许自己的行为被推荐给好友（0-否，1-是）',
  `keyword_retention_days` int(11) DEFAULT '7' COMMENT '关键词保留天数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社交推荐用户偏好表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `song` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name_en` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '英文名',
  `original_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原名/外文名',
  `artist_id` bigint(20) DEFAULT NULL,
  `artist_ids` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `artist_names` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `album_id` bigint(20) DEFAULT NULL,
  `mv_id` bigint(20) DEFAULT NULL COMMENT '关联的MVID',
  `album_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `album_track_no` int(11) NOT NULL DEFAULT '0' COMMENT '公开专辑内曲序',
  `duration` int(11) DEFAULT NULL,
  `main_genre` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `main_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sub_genres` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tags` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '歌曲标签',
  `sub_types` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `language` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '歌曲描述',
  `release_date` date DEFAULT NULL,
  `cover` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `url_standard` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `url_high` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `url_lossless` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `url_hires` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Hi-Res闊宠川URL (24bit/48kHz-192kHz)',
  `url_master` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '姣嶅甫闊宠川URL (24bit/96kHz+ 鍘熷?姣嶅甫锛孷IP涓撲韩)',
  `url_instrumental` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鍘熻建/浼村?闊宠川URL (鍚勯煶璐ㄧ殑浼村?鐗堟湰)',
  `size_standard` bigint(20) DEFAULT NULL,
  `size_high` bigint(20) DEFAULT NULL,
  `size_lossless` bigint(20) DEFAULT NULL,
  `size_hires` bigint(20) DEFAULT NULL COMMENT 'Hi-Res闊宠川澶у皬锛堝瓧鑺傦級',
  `size_master` bigint(20) DEFAULT NULL COMMENT '姣嶅甫闊宠川澶у皬锛堝瓧鑺傦級',
  `size_instrumental` bigint(20) DEFAULT NULL COMMENT '鍘熻建/浼村?闊宠川澶у皬锛堝瓧鑺傦級',
  `favorite_count` bigint(20) DEFAULT '0' COMMENT '鏀惰棌娆℃暟',
  `play_count` bigint(20) DEFAULT '0',
  `comment_count` bigint(20) DEFAULT '0' COMMENT '璇勮?娆℃暟',
  `like_count` bigint(20) DEFAULT '0' COMMENT '点赞数',
  `reply_count` bigint(20) DEFAULT '0' COMMENT '回复数',
  `share_count` bigint(20) DEFAULT '0' COMMENT '鍒嗕韩娆℃暟',
  `download_count` bigint(20) DEFAULT '0' COMMENT '涓嬭浇娆℃暟',
  `hot_score` int(11) DEFAULT '0',
  `avg_rating` decimal(3,1) DEFAULT '0.0',
  `rating_count` bigint(20) DEFAULT '0' COMMENT '璇勫垎娆℃暟',
  `is_new` tinyint(4) DEFAULT '0',
  `is_hot` tinyint(4) DEFAULT '0',
  `is_vip_only` tinyint(1) DEFAULT '0' COMMENT '是否VIP专属',
  `is_single` tinyint(4) DEFAULT '0',
  `has_lyric` tinyint(4) DEFAULT '0',
  `lyric_language` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lyrics` text COLLATE utf8mb4_unicode_ci COMMENT '歌词内容',
  `lyrics_file` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '歌词文件路径',
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `copyright_info` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版权信息',
  `publish_status` tinyint(1) DEFAULT '1' COMMENT '发布状态',
  `review_status` tinyint(1) DEFAULT '0' COMMENT '审核状态',
  `review_comment` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核评论',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '审核人ID',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'original' COMMENT '版本类型: original-原版, live-现场版, remix-混音版, cover-翻唱版, acoustic-不插电版, instrumental-纯音乐版, demo-演示版',
  `version_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本显示名称，如"现场版"、"混音版"等',
  `priority` int(11) DEFAULT '0' COMMENT '浼樺厛绾',
  `is_paid` tinyint(4) DEFAULT '0' COMMENT '鏄?惁浠樿垂',
  `paid_resource_id` bigint(20) DEFAULT NULL COMMENT '浠樿垂璧勬簮ID',
  `price` decimal(10,2) DEFAULT NULL COMMENT '浠锋牸',
  `preview_duration` int(11) DEFAULT '0' COMMENT '试听时长(秒)',
  `allow_download` tinyint(1) DEFAULT '1' COMMENT '鏄?惁鍏佽?涓嬭浇锛?-鍚︼紝1-鏄?級',
  `allow_comment` tinyint(1) DEFAULT '1' COMMENT '鏄?惁鍏佽?璇勮?锛?-鍚︼紝1-鏄?級',
  `allow_share` tinyint(1) DEFAULT '1' COMMENT '鏄?惁鍏佽?鍒嗕韩锛?-鍚︼紝1-鏄?級',
  `uploader_id` bigint(20) DEFAULT NULL COMMENT '涓婁紶鑰匢D锛堟櫘閫氱敤鎴锋垨鍒涗綔鑰咃級',
  `uploader_type` tinyint(1) DEFAULT '0' COMMENT '涓婁紶鑰呯被鍨嬶細0-骞冲彴锛?-鏅??鐢ㄦ埛锛?-鍒涗綔鑰',
  `uploader_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '涓婁紶鑰呭悕绉帮紙鍐椾綑瀛楁?锛',
  `source_work_id` bigint(20) DEFAULT NULL COMMENT '鏉ユ簮浣滃搧ID锛堝叧鑱攗ser_work鎴朿reator_work锛',
  `source_work_type` tinyint(1) DEFAULT '0' COMMENT '鏉ユ簮浣滃搧绫诲瀷锛?-骞冲彴涓婁紶锛?-鐢ㄦ埛鎶曠?锛?-鍒涗綔鑰呬綔鍝',
  `danceability` decimal(4,3) DEFAULT NULL COMMENT '鑸炴洸鎰?0-1锛岄?鍚堣烦鑸炵▼搴',
  `energy` decimal(4,3) DEFAULT NULL COMMENT '鑳介噺 0-1锛屾縺鐑堢▼搴',
  `valence` decimal(4,3) DEFAULT NULL COMMENT '鎯呯华 0-1锛屾秷鏋佸埌绉?瀬',
  `tempo` decimal(7,2) DEFAULT NULL COMMENT '鑺傚?閫熷害 BPM',
  `acousticness` decimal(4,3) DEFAULT NULL COMMENT '鍘熷０绋嬪害 0-1',
  `instrumentalness` decimal(4,3) DEFAULT NULL COMMENT '绾?煶涔愮▼搴?0-1',
  `speechiness` decimal(4,3) DEFAULT NULL COMMENT '浜哄０姣斾緥 0-1',
  `liveness` decimal(4,3) DEFAULT NULL COMMENT '鐜板満鎰?0-1',
  `audio_key` tinyint(4) DEFAULT NULL COMMENT '闊宠皟 0=C, 1=C#, ..., 11=B',
  `loudness` decimal(5,2) DEFAULT NULL COMMENT '鍝嶅害 dB',
  `mode` tinyint(4) DEFAULT NULL COMMENT '璋冨紡 0=灏忚皟, 1=澶ц皟',
  `time_signature` tinyint(4) DEFAULT NULL COMMENT '鎷嶅彿 3=3/4, 4=4/4',
  `audio_features_updated` datetime DEFAULT NULL COMMENT '闊抽?鐗瑰緛鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_artist` (`artist_id`),
  KEY `idx_album` (`album_id`),
  KEY `idx_status_deleted` (`status`,`deleted`),
  KEY `idx_release_status` (`release_date`,`status`,`deleted`),
  KEY `idx_mv_id` (`mv_id`),
  KEY `idx_play_count` (`play_count`),
  KEY `idx_artist_album` (`artist_id`,`album_id`,`status`,`deleted`),
  KEY `idx_hot_song` (`play_count`,`favorite_count`,`status`,`deleted`),
  KEY `idx_album_status_deleted` (`album_id`,`status`,`deleted`),
  KEY `idx_song_url_master` (`url_master`(255)),
  KEY `idx_song_url_hires` (`url_hires`(255)),
  KEY `idx_danceability` (`danceability`),
  KEY `idx_energy` (`energy`),
  KEY `idx_valence` (`valence`),
  KEY `idx_tempo` (`tempo`),
  KEY `idx_create_time_status` (`create_time`,`status`,`deleted`),
  KEY `idx_song_status_deleted_album` (`status`,`deleted`,`album_id`),
  KEY `idx_song_status_deleted_hot_play` (`status`,`deleted`,`hot_score`,`play_count`),
  KEY `idx_song_type_hot_play` (`status`,`deleted`,`main_type`,`hot_score`,`play_count`),
  KEY `idx_song_language_hot_play` (`status`,`deleted`,`language`,`hot_score`,`play_count`),
  KEY `idx_song_album_track` (`album_id`,`album_track_no`,`id`),
  KEY `idx_song_public_play` (`status`,`deleted`,`play_count`),
  KEY `idx_song_public_hot_play` (`status`,`deleted`,`is_hot`,`play_count`),
  FULLTEXT KEY `ft_name` (`name`) WITH PARSER `ngram`  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `song_artist` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `song_id` bigint(20) NOT NULL COMMENT '??ID',
  `artist_id` bigint(20) NOT NULL COMMENT '??ID',
  `artist_name` varchar(200) DEFAULT NULL COMMENT '??????',
  `type` tinyint(4) NOT NULL DEFAULT '1' COMMENT '??',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '??',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_song_artist_role` (`song_id`,`artist_id`,`type`),
  KEY `idx_song_artist_song` (`song_id`,`type`,`sort_order`),
  KEY `idx_song_artist_artist` (`artist_id`,`type`),
  KEY `idx_song_artist_artist_type_song` (`artist_id`,`type`,`song_id`),
  KEY `idx_song_artist_song_type_sort` (`song_id`,`type`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌曲歌手关联表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `song_credit` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `song_id` bigint(20) NOT NULL,
  `artist_id` bigint(20) NOT NULL COMMENT '不可变 artist profile',
  `role_code` varchar(32) NOT NULL COMMENT 'primary/co_artist/lyricist/composer/producer等',
  `credit_version` int(11) NOT NULL,
  `display_name_snapshot` varchar(200) DEFAULT NULL,
  `sort_order` int(11) NOT NULL DEFAULT '0',
  `source_type` varchar(32) NOT NULL,
  `source_id` bigint(20) NOT NULL,
  `acceptance_status` varchar(16) NOT NULL COMMENT 'pending/accepted/rejected/revoked',
  `rights_scope` varchar(32) NOT NULL DEFAULT 'display_only',
  `revenue_share_reference` varchar(128) DEFAULT NULL COMMENT '只引用独立分账契约，不直接保存比例',
  `reviewed_by` bigint(20) DEFAULT NULL,
  `change_reason` varchar(500) NOT NULL,
  `valid_from` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `valid_to` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_song_credit_version` (`song_id`,`artist_id`,`role_code`,`credit_version`),
  KEY `idx_song_credit_public` (`song_id`,`acceptance_status`,`valid_to`,`sort_order`),
  KEY `idx_song_credit_artist` (`artist_id`,`acceptance_status`,`song_id`),
  KEY `idx_song_credit_source` (`source_type`,`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本化歌曲展示署名';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `song_like` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `song_id` bigint(20) NOT NULL,
  `is_favorite` tinyint(1) DEFAULT '0' COMMENT '是否收藏（0-否，1-是）',
  `is_like` tinyint(1) DEFAULT '0' COMMENT '是否点赞（0-否，1-是）',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_fav` (`user_id`,`is_favorite`,`deleted`),
  KEY `idx_song_fav` (`song_id`,`deleted`),
  KEY `idx_user_favorite_create` (`user_id`,`is_favorite`,`create_time`),
  KEY `idx_user_song_deleted` (`user_id`,`song_id`,`deleted`),
  KEY `idx_song_like_song_favorite_deleted` (`song_id`,`is_favorite`,`deleted`),
  KEY `idx_song_like_song_like_deleted` (`song_id`,`is_like`,`deleted`),
  KEY `idx_song_like_user_favorite_deleted_song` (`user_id`,`is_favorite`,`deleted`,`song_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client      = @@character_set_client  ;
SET @saved_cs_results     = @@character_set_results  ;
SET @saved_col_connection = @@collation_connection  ;
SET character_set_client  = utf8mb4  ;
SET character_set_results = utf8mb4  ;
SET collation_connection  = utf8mb4_general_ci  ;
SET @saved_sql_mode       = @@sql_mode  ;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;
DELIMITER ;;
CREATE DEFINER=CURRENT_USER TRIGGER trg_song_like_insert
AFTER INSERT ON song_like
FOR EACH ROW
BEGIN
    IF NEW.is_favorite = 1 AND NEW.deleted = 0 THEN
        UPDATE song SET favorite_count = favorite_count + 1 WHERE id = NEW.song_id;
    END IF;
END ;;
DELIMITER ;
SET sql_mode              = @saved_sql_mode  ;
SET character_set_client  = @saved_cs_client  ;
SET character_set_results = @saved_cs_results  ;
SET collation_connection  = @saved_col_connection  ;
SET @saved_cs_client      = @@character_set_client  ;
SET @saved_cs_results     = @@character_set_results  ;
SET @saved_col_connection = @@collation_connection  ;
SET character_set_client  = utf8mb4  ;
SET character_set_results = utf8mb4  ;
SET collation_connection  = utf8mb4_general_ci  ;
SET @saved_sql_mode       = @@sql_mode  ;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;
DELIMITER ;;
CREATE DEFINER=CURRENT_USER TRIGGER trg_song_like_update
AFTER UPDATE ON song_like
FOR EACH ROW
BEGIN
    IF OLD.is_favorite = 1 AND NEW.is_favorite = 0 AND NEW.deleted = 0 THEN
        UPDATE song SET favorite_count = GREATEST(favorite_count - 1, 0) WHERE id = NEW.song_id;
    END IF;
    IF NEW.is_favorite = 1 AND OLD.is_favorite = 0 AND NEW.deleted = 0 THEN
        UPDATE song SET favorite_count = favorite_count + 1 WHERE id = NEW.song_id;
    END IF;
END ;;
DELIMITER ;
SET sql_mode              = @saved_sql_mode  ;
SET character_set_client  = @saved_cs_client  ;
SET character_set_results = @saved_cs_results  ;
SET collation_connection  = @saved_col_connection  ;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
CREATE VIEW `song_play_record` AS SELECT 
 1 AS `id`,
 1 AS `song_id`,
 1 AS `user_id`,
 1 AS `play_duration`,
 1 AS `play_time`,
 1 AS `created_time`;
SET character_set_client = @saved_cs_client;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `song_rating` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `song_id` bigint(20) NOT NULL,
  `rating` tinyint(4) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_song` (`song_id`),
  KEY `idx_song_rating_song_deleted` (`song_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `song_resource_request` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `song_name` varchar(200) NOT NULL COMMENT '歌曲名称',
  `artist_name` varchar(200) NOT NULL COMMENT '歌手名称',
  `album_name` varchar(200) DEFAULT NULL COMMENT '专辑名称',
  `version_info` varchar(100) DEFAULT '原版' COMMENT '版本信息',
  `file_url` varchar(500) DEFAULT NULL COMMENT '上传的音频文件URL',
  `detected_quality` tinyint(4) DEFAULT NULL COMMENT '检测到的音质等级：1-标准 2-高品质 3-无损 4-Hi-Res 5-母带',
  `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小（字节）',
  `duration` int(11) DEFAULT NULL COMMENT '音频时长（秒）',
  `bitrate` int(11) DEFAULT NULL COMMENT '比特率（kbps）',
  `sample_rate` int(11) DEFAULT NULL COMMENT '采样率（Hz）',
  `format` varchar(10) DEFAULT NULL COMMENT '文件格式：mp3/flac/wav等',
  `source_description` varchar(200) DEFAULT NULL COMMENT '来源描述（本地收藏/CD/黑胶等）',
  `remark` text COMMENT '备注说明',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态：pending-待处理,processing-处理中,completed-已完成,rejected-已拒绝',
  `handler_id` bigint(20) DEFAULT NULL COMMENT '处理人ID（管理员）',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `handle_result` text COMMENT '处理结果说明',
  `matched_song_id` bigint(20) DEFAULT NULL COMMENT '匹配到的平台歌曲ID',
  `auto_song_id` bigint(20) DEFAULT NULL COMMENT '自动创建的歌曲ID',
  `notified` tinyint(4) DEFAULT '0' COMMENT '是否已通知用户',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_song_artist` (`song_name`,`artist_name`),
  KEY `idx_song_resource_request_user_day` (`user_id`,`deleted`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌曲资源申请表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `song_similarity_cache` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `song_id` bigint(20) NOT NULL COMMENT '姝屾洸ID',
  `similar_song_id` bigint(20) NOT NULL COMMENT '鐩镐技姝屾洸ID',
  `similarity` decimal(5,4) NOT NULL COMMENT '鐩镐技搴?0-1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pair` (`song_id`,`similar_song_id`),
  KEY `idx_similarity` (`similarity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='姝屾洸闊抽?鐗瑰緛鐩镐技搴︾紦瀛';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `song_tag_relation` (
  `id` bigint(20) NOT NULL COMMENT '关联ID',
  `song_id` bigint(20) NOT NULL COMMENT '歌曲ID',
  `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
  `user_id` bigint(20) DEFAULT NULL COMMENT '添加用户ID',
  `source` varchar(20) DEFAULT 'system' COMMENT '来源：system-系统, user-用户, ai-AI',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_song_tag` (`song_id`,`tag_id`,`deleted`),
  KEY `idx_song_id` (`song_id`),
  KEY `idx_tag_id` (`tag_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌曲标签关联表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `song_vote` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '投票ID',
  `song_id` bigint(20) NOT NULL COMMENT '歌曲ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `vote_date` date NOT NULL COMMENT '投票日期',
  `vote_count` int(11) DEFAULT '1' COMMENT '投票次数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_song_user_date` (`song_id`,`user_id`,`vote_date`),
  KEY `idx_vote_date` (`vote_date`),
  KEY `idx_song_id` (`song_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='歌曲投票表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `store_product_policy` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_type` varchar(40) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  `seller_id` bigint(20) DEFAULT NULL,
  `sale_status` varchar(30) NOT NULL DEFAULT 'on_sale',
  `visibility` varchar(30) NOT NULL DEFAULT 'public',
  `entitlement_policy` varchar(30) NOT NULL DEFAULT 'retain',
  `settlement_status` varchar(30) NOT NULL DEFAULT 'normal',
  `version` int(11) NOT NULL DEFAULT '0',
  `last_operator_id` bigint(20) DEFAULT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_store_product` (`product_type`,`product_id`),
  KEY `idx_store_product_catalog` (`product_type`,`sale_status`,`visibility`),
  KEY `idx_store_product_seller` (`seller_id`,`product_type`,`sale_status`),
  KEY `idx_store_product_settlement` (`settlement_status`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商店商品统一治理策略';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `store_product_policy_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `policy_id` bigint(20) NOT NULL,
  `product_type` varchar(40) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  `actor_type` varchar(20) NOT NULL,
  `action` varchar(40) NOT NULL,
  `before_state` varchar(1000) NOT NULL,
  `after_state` varchar(1000) NOT NULL,
  `operator_id` bigint(20) NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_store_policy_log_product` (`product_type`,`product_id`,`create_time`),
  KEY `idx_store_policy_log_operator` (`operator_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商店商品治理审计日志';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `subject_follow` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `follower_user_id` bigint(20) NOT NULL,
  `target_type` varchar(32) NOT NULL COMMENT 'user/artist_profile/creator/topic等',
  `target_id` bigint(20) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'active' COMMENT 'active/cancelled',
  `source_type` varchar(32) NOT NULL,
  `source_id` bigint(20) DEFAULT NULL,
  `contributes_public_stats` tinyint(4) NOT NULL DEFAULT '0' COMMENT '该关系激活时是否计入公开粉丝数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_follow_subject` (`follower_user_id`,`target_type`,`target_id`),
  KEY `idx_subject_follow_target` (`target_type`,`target_id`,`status`),
  KEY `idx_subject_follow_follower` (`follower_user_id`,`target_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='带目标类型的关注关系';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `topic` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '话题ID',
  `name` varchar(100) NOT NULL COMMENT '话题名称',
  `description` varchar(500) DEFAULT NULL COMMENT '话题描述',
  `cover` varchar(500) DEFAULT NULL COMMENT '话题封面',
  `category` varchar(50) DEFAULT NULL COMMENT '分类',
  `post_count` int(11) DEFAULT '0' COMMENT '动态数',
  `follower_count` int(11) DEFAULT '0' COMMENT '关注数',
  `is_hot` tinyint(1) DEFAULT '0' COMMENT '是否热门',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_category` (`category`),
  KEY `idx_is_hot` (`is_hot`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音乐话题表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `topic_follow` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '关注ID',
  `topic_id` bigint(20) NOT NULL COMMENT '话题ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_topic_user` (`topic_id`,`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='话题关注表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nickname` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `avatar` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gender` tinyint(4) DEFAULT '0',
  `birthday` date DEFAULT NULL,
  `province` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `city` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `introduction` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `is_banned` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'report ban flag: 0 no, 1 yes',
  `ban_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ban reason',
  `ban_start_time` datetime DEFAULT NULL COMMENT 'ban start time',
  `ban_end_time` datetime DEFAULT NULL COMMENT 'ban end time, NULL means permanent',
  `user_type` int(11) DEFAULT '0' COMMENT '用户类型 0-正常 1-活跃 2-VIP 3-超级 10-不活跃 11-可疑 12-机器人 13-封禁',
  `user_type_update_time` datetime DEFAULT NULL COMMENT '用户类型更新时间',
  `risk_score` int(11) DEFAULT '0' COMMENT '风险评分 0-100',
  `last_active_time` datetime DEFAULT NULL COMMENT '最后活跃时间',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'USER',
  `last_login_time` datetime DEFAULT NULL,
  `last_login_ip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fans_count` int(11) DEFAULT '0',
  `following_count` int(11) DEFAULT '0',
  `privacy_settings` text COLLATE utf8mb4_unicode_ci,
  `local_music_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `wallpaper` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_moderator` tinyint(1) DEFAULT '0' COMMENT '是否为审核员：0-否，1-是',
  `moderator_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'inactive' COMMENT '审核员状态：active-活跃，inactive-非活跃，suspended-暂停',
  `moderator_note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核员备注（如专长领域）',
  `user_source` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'platform' COMMENT '用户来源：platform-本平台，external-外来',
  `is_online` tinyint(1) DEFAULT '0' COMMENT '是否在线：0-否，1-是',
  `last_online_time` datetime DEFAULT NULL COMMENT '最后在线时间',
  `today_review_count` int(11) DEFAULT '0' COMMENT '今日审核数量',
  `total_review_count` int(11) DEFAULT '0' COMMENT '总审核数量',
  `daily_quota` int(11) DEFAULT '100' COMMENT '每日审核配额',
  `last_review_time` datetime DEFAULT NULL COMMENT '最后审核时间',
  `is_creator` tinyint(1) DEFAULT '0' COMMENT '是否为创作者：0-否，1-是',
  `creator_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'inactive' COMMENT '创作者状态：pending/active/suspended',
  `creator_eligibility_version` bigint(20) NOT NULL DEFAULT '0' COMMENT '创作者资格单调版本',
  `credit_score` int(11) DEFAULT '100' COMMENT '用户信用分（0-1000）',
  `creator_apply_time` datetime DEFAULT NULL COMMENT '创作者申请时间',
  `creator_note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创作者备注',
  `creator_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创作者类型：independent-独立，signed-签约',
  `external_source` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '外部来源（如其他平台名称）',
  `cooperation_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '合作类型：independent-独立，contract-合作，exclusive-独家',
  `contract_start_date` date DEFAULT NULL COMMENT '合作开始日期',
  `contract_end_date` date DEFAULT NULL COMMENT '合作结束日期',
  `fee_rate` decimal(5,4) DEFAULT '0.0200' COMMENT '手续费率（默认2%）',
  `user_badge` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户徽章：creator/vip',
  `vip_expire_time` datetime DEFAULT NULL COMMENT 'VIP过期时间',
  `vip_download_quota` int(11) DEFAULT '50' COMMENT 'VIP每日下载配额',
  `normal_download_quota` int(11) DEFAULT '10' COMMENT '普通用户每日下载配额',
  `used_download_quota` int(11) DEFAULT '0' COMMENT '今日已用下载配额',
  `last_download_reset` date DEFAULT NULL COMMENT '最后重置下载配额日期',
  `total_earnings` decimal(10,2) DEFAULT '0.00' COMMENT '创作者总收益',
  `withdrawn_earnings` decimal(10,2) DEFAULT '0.00' COMMENT '已提现收益',
  `pending_earnings` decimal(10,2) DEFAULT '0.00' COMMENT '待提现收益',
  `is_official` tinyint(4) DEFAULT '0' COMMENT '鏄?惁瀹樻柟璁よ瘉锛?-鍚︼紝1-鏄?級',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  UNIQUE KEY `uk_user_phone` (`phone`),
  KEY `idx_user_type` (`user_type`),
  KEY `idx_last_active` (`last_active_time`),
  KEY `idx_last_login_time` (`last_login_time`),
  KEY `idx_status_login` (`status`,`last_login_time`),
  KEY `idx_type_status` (`user_type`,`status`,`deleted`),
  KEY `idx_creator_status` (`is_creator`,`creator_status`,`deleted`),
  KEY `idx_user_deleted_id` (`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_achievement` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `achievement_id` bigint(20) NOT NULL COMMENT '成就ID',
  `current_value` int(11) DEFAULT '0' COMMENT '当前值',
  `target_value` int(11) NOT NULL COMMENT '目标值',
  `progress` decimal(5,2) DEFAULT '0.00' COMMENT '进度百分比',
  `is_unlocked` tinyint(4) DEFAULT '0' COMMENT '是否已解锁（达成条件）',
  `is_completed` tinyint(4) DEFAULT '0' COMMENT '是否已完成（领取奖励）',
  `unlock_time` datetime DEFAULT NULL COMMENT '解锁时间',
  `complete_time` datetime DEFAULT NULL COMMENT '完成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_achievement` (`user_id`,`achievement_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_achievement_id` (`achievement_id`),
  KEY `idx_achievement_progress` (`user_id`,`is_completed`,`is_unlocked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户成就表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_activity` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `user_id` bigint(20) NOT NULL COMMENT '鐢ㄦ埛ID',
  `activity_type` varchar(20) NOT NULL COMMENT '娲诲姩绫诲瀷: post/comment/like/favorite/share',
  `target_type` varchar(20) DEFAULT NULL COMMENT '鐩?爣绫诲瀷: song/album/artist/playlist/mv/post/user',
  `target_id` bigint(20) DEFAULT NULL COMMENT '鐩?爣ID',
  `content` text COMMENT '娲诲姩鍐呭?',
  `extra_data` text COMMENT '棰濆?鏁版嵁锛圝SON鏍煎紡锛',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '閫昏緫鍒犻櫎锛?-鏈?垹闄わ紝1-宸插垹闄わ級',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_activity_type` (`activity_type`),
  KEY `idx_target` (`target_type`,`target_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_user_activity_user_type_deleted_time` (`user_id`,`activity_type`,`deleted`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢ㄦ埛娲诲姩璁板綍琛';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_badge` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `badge_type` varchar(50) NOT NULL COMMENT '徽章类型',
  `badge_level` int(11) DEFAULT '1' COMMENT '徽章等级',
  `badge_name` varchar(50) NOT NULL COMMENT '徽章名称',
  `badge_icon` varchar(500) DEFAULT NULL COMMENT '徽章图标',
  `badge_color` varchar(20) DEFAULT NULL COMMENT '徽章颜色',
  `position` varchar(20) DEFAULT 'name' COMMENT '显示位置：avatar-头像旁，name-名称旁，both-两者都显示',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `badge_description` varchar(200) DEFAULT NULL COMMENT '徽章描述',
  `is_equipped` tinyint(4) DEFAULT '0' COMMENT '是否装备',
  `obtain_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
  `rule_id` bigint(20) DEFAULT NULL COMMENT '授予时规则ID',
  `rule_version` int(11) DEFAULT NULL COMMENT '授予时规则版本',
  `grant_event_id` char(36) DEFAULT NULL COMMENT '授予事实事件ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_badge_type` (`user_id`,`badge_type`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_badge_type` (`badge_type`),
  KEY `idx_is_equipped` (`is_equipped`),
  KEY `idx_user_badge_rule` (`rule_id`),
  KEY `idx_user_badge_grant_event` (`grant_event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户徽章表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_behavior_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `behavior_type` varchar(50) NOT NULL COMMENT '行为类型',
  `target_type` varchar(20) DEFAULT NULL COMMENT '目标类型',
  `target_id` bigint(20) DEFAULT NULL COMMENT '目标ID',
  `duration` int(11) DEFAULT '0' COMMENT '??????',
  `device_type` varchar(20) DEFAULT 'web' COMMENT '????',
  `client_type` varchar(50) DEFAULT NULL COMMENT '?????',
  `ip_address` varchar(50) DEFAULT NULL COMMENT 'IP??',
  `behavior_time` datetime DEFAULT NULL COMMENT '????',
  `behavior_data` text COMMENT '行为数据（JSON）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '????',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_behavior_type` (`behavior_type`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_user_behavior_user_time` (`user_id`,`behavior_time`),
  KEY `idx_user_behavior_target_public` (`target_type`,`target_id`,`deleted`,`behavior_type`,`user_id`),
  KEY `idx_user_behavior_hot_public` (`target_type`,`behavior_type`,`deleted`,`target_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_blacklist` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `blocked_user_id` bigint(20) NOT NULL COMMENT '被拉黑用户ID',
  `reason` varchar(200) DEFAULT NULL COMMENT '拉黑原因',
  `is_mutual` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否双向拉黑：0-否，1-是',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间，NULL 表示永久',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_blocked` (`user_id`,`blocked_user_id`),
  KEY `idx_blocked_user` (`blocked_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户黑名单表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_checkin` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户名',
  `checkin_date` date NOT NULL COMMENT '签到日期',
  `continuous_days` int(11) DEFAULT '1' COMMENT '连续签到天数',
  `reward_points` int(11) DEFAULT '0' COMMENT '奖励积分',
  `reward_vip_days` int(11) DEFAULT '0' COMMENT '奖励VIP天数',
  `checkin_type` tinyint(1) DEFAULT '1' COMMENT '签到类型：1-正常签到，2-补签',
  `ip_address` varchar(50) DEFAULT NULL COMMENT '签到IP地址',
  `checkin_time` datetime DEFAULT NULL COMMENT '签到时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除（0-未删除，1-已删除）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`,`checkin_date`),
  KEY `idx_checkin_date` (`checkin_date`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户签到表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_credit` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `credit_score` int(11) DEFAULT '100' COMMENT '信用分数',
  `credit_level` varchar(20) DEFAULT 'normal' COMMENT '信用等级',
  `total_report_count` int(11) DEFAULT '0' COMMENT '总举报次数',
  `approved_report_count` int(11) DEFAULT '0' COMMENT '通过举报次数',
  `rejected_report_count` int(11) DEFAULT '0' COMMENT '驳回举报次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_credit_score` (`credit_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信用表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_decoration` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `decoration_type` varchar(20) NOT NULL COMMENT '装饰类型: avatar_frame/comment_bar/player/dialog_box/theme/badge',
  `decoration_id` varchar(50) NOT NULL COMMENT '装饰ID',
  `decoration_name` varchar(50) DEFAULT NULL COMMENT '装饰名称',
  `is_equipped` tinyint(4) DEFAULT '0' COMMENT '是否装备（0-未装备，1-已装备）',
  `obtain_time` datetime DEFAULT NULL COMMENT '获取时间',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间（null表示永久）',
  `source` varchar(50) DEFAULT NULL COMMENT '来源: sign/activity/vip/achievement/points',
  `source_description` varchar(200) DEFAULT NULL COMMENT '来源描述',
  `rarity` varchar(20) DEFAULT 'common' COMMENT '稀有度: common/rare/epic/legendary',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_decoration_fact` (`user_id`,`decoration_type`,`decoration_id`,`deleted`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_type_equipped` (`decoration_type`,`is_equipped`),
  KEY `idx_decoration` (`decoration_type`,`decoration_id`),
  KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户装饰表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_emoji` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `user_id` bigint(20) NOT NULL COMMENT '鐢ㄦ埛ID',
  `emoji_package_id` bigint(20) NOT NULL COMMENT '琛ㄦ儏鍖匢D',
  `is_purchased` tinyint(4) DEFAULT '0' COMMENT '鏄?惁璐?拱锛?-鍚︼紝1-鏄?級',
  `is_favorited` tinyint(4) DEFAULT '0' COMMENT '鏄?惁鏀惰棌锛?-鍚︼紝1-鏄?級',
  `purchase_time` datetime DEFAULT NULL COMMENT '璐?拱鏃堕棿',
  `favorite_time` datetime DEFAULT NULL COMMENT '鏀惰棌鏃堕棿',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '閫昏緫鍒犻櫎锛?-鏈?垹闄わ紝1-宸插垹闄わ級',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_package` (`user_id`,`emoji_package_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_emoji_package_id` (`emoji_package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢ㄦ埛琛ㄦ儏鍏崇郴琛';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_equipment_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `avatar_frame_id` bigint(20) DEFAULT NULL COMMENT '装备的头像框装饰ID',
  `profile_card_id` bigint(20) DEFAULT NULL COMMENT '装备的名片装饰ID',
  `profile_card_style` varchar(50) DEFAULT NULL COMMENT '名片风格',
  `badge_1_id` bigint(20) DEFAULT NULL COMMENT '徽章位置1',
  `badge_2_id` bigint(20) DEFAULT NULL COMMENT '徽章位置2',
  `badge_3_id` bigint(20) DEFAULT NULL COMMENT '徽章位置3',
  `badge_4_id` bigint(20) DEFAULT NULL COMMENT '徽章位置4',
  `badge_5_id` bigint(20) DEFAULT NULL COMMENT '徽章位置5',
  `badge_6_id` bigint(20) DEFAULT NULL COMMENT '徽章位置6',
  `badge_7_id` bigint(20) DEFAULT NULL COMMENT '徽章位置7',
  `badge_8_id` bigint(20) DEFAULT NULL COMMENT '徽章位置8',
  `badge_9_id` bigint(20) DEFAULT NULL COMMENT '徽章位置9',
  `badge_10_id` bigint(20) DEFAULT NULL COMMENT '徽章位置10',
  `chat_bubble_id` bigint(20) DEFAULT NULL COMMENT '聊天气泡装饰ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户装备配置表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_extension` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `is_creator` tinyint(4) DEFAULT '0' COMMENT '是否为创作者：0-否，1-是',
  `creator_type` tinyint(4) DEFAULT NULL COMMENT '创作者类型：1-歌手，2-制作人，3-词作者',
  `verified` tinyint(4) DEFAULT '0' COMMENT '是否已认证：0-未认证，1-已认证',
  `verification_badge` varchar(50) DEFAULT NULL COMMENT '认证标识',
  `follower_count` int(11) DEFAULT '0' COMMENT '粉丝数',
  `following_count` int(11) DEFAULT '0' COMMENT '关注数',
  `works_count` int(11) DEFAULT '0' COMMENT '作品数量',
  `total_plays` bigint(20) DEFAULT '0' COMMENT '总播放量',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_is_creator` (`is_creator`),
  KEY `idx_creator_type` (`creator_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户扩展信息表（创作者相关）';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
CREATE VIEW `user_favorite` AS SELECT 
 1 AS `id`,
 1 AS `user_id`,
 1 AS `target_type`,
 1 AS `target_id`,
 1 AS `created_time`;
SET character_set_client = @saved_cs_client;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_feedback` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `feedback_type` varchar(20) NOT NULL COMMENT '反馈类型',
  `order_id` bigint(20) DEFAULT NULL COMMENT '关联订单ID',
  `order_type` varchar(20) DEFAULT NULL COMMENT '订单类型',
  `title` varchar(200) DEFAULT NULL COMMENT '反馈标题',
  `content` text COMMENT '反馈内容',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态',
  `handler_id` bigint(20) DEFAULT NULL COMMENT '处理人ID',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `handle_result` varchar(500) DEFAULT NULL COMMENT '处理结果',
  `attachment_urls` varchar(1000) DEFAULT NULL COMMENT '附件URL',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_type_status` (`feedback_type`,`status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_user_feedback_user_status_deleted` (`user_id`,`status`,`deleted`),
  KEY `idx_user_feedback_handler_time` (`handler_id`,`create_time`,`deleted`),
  KEY `idx_user_feedback_status_time_type` (`status`,`create_time`,`feedback_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户反馈表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_follow` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `follower_id` bigint(20) NOT NULL,
  `followee_id` bigint(20) NOT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow` (`follower_id`,`followee_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_friend` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `friend_id` bigint(20) NOT NULL COMMENT '好友用户ID',
  `friend_group` varchar(50) DEFAULT NULL COMMENT '好友分组',
  `remark` varchar(100) DEFAULT NULL COMMENT '好友备注',
  `status` varchar(20) DEFAULT 'pending' COMMENT '好友状态',
  `special_mark` varchar(20) DEFAULT NULL COMMENT '特殊标记',
  `friend_since` datetime DEFAULT NULL COMMENT '成为好友时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_friend` (`user_id`,`friend_id`),
  KEY `idx_status` (`status`),
  KEY `idx_friend_since` (`friend_since`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户好友表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_interaction` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `target_user_id` bigint(20) NOT NULL COMMENT '目标用户ID',
  `interaction_type` varchar(20) NOT NULL COMMENT '互动类型',
  `target_type` varchar(20) DEFAULT NULL COMMENT '目标类型',
  `target_id` bigint(20) DEFAULT NULL COMMENT '目标ID',
  `interaction_time` datetime DEFAULT NULL COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`,`create_time`),
  KEY `idx_target_user` (`target_user_id`),
  KEY `idx_type` (`interaction_type`),
  KEY `idx_user_interaction_user_time` (`user_id`,`interaction_time`),
  KEY `idx_user_interaction_pair_time` (`user_id`,`target_user_id`,`interaction_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户互动记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_level` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `level` int(11) DEFAULT '1' COMMENT '等级',
  `exp` int(11) DEFAULT '0' COMMENT '经验值',
  `exp_to_next` int(11) DEFAULT '100' COMMENT '升级所需经验',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户等级表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_music_daily_summary` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `user_id` bigint(20) NOT NULL COMMENT '??ID',
  `stat_date` date NOT NULL COMMENT '????',
  `play_count` int(11) NOT NULL DEFAULT '0' COMMENT '????',
  `valid_play_count` int(11) NOT NULL DEFAULT '0' COMMENT '??????',
  `play_seconds` bigint(20) NOT NULL DEFAULT '0' COMMENT '?????',
  `unique_song_count` int(11) NOT NULL DEFAULT '0' COMMENT '?????',
  `avg_valence` decimal(6,4) DEFAULT NULL COMMENT '?????',
  `avg_energy` decimal(6,4) DEFAULT NULL COMMENT '????',
  `active_minutes` int(11) NOT NULL DEFAULT '0' COMMENT '???????10?????',
  `top_song_id` bigint(20) DEFAULT NULL COMMENT '????????ID',
  `top_song_name` varchar(255) DEFAULT NULL COMMENT '???????????',
  `top_song_artist_names` varchar(512) DEFAULT NULL COMMENT '????????????',
  `public_stats_eligible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '??????????????',
  `feature_coverage` decimal(6,4) NOT NULL DEFAULT '0.0000' COMMENT '???????',
  `calculation_version` varchar(32) NOT NULL DEFAULT 'music-summary-v1' COMMENT '????',
  `data_until` datetime DEFAULT NULL COMMENT '??????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_music_daily` (`user_id`,`stat_date`),
  KEY `idx_user_music_daily_date_user` (`stat_date`,`user_id`),
  KEY `idx_user_music_daily_public` (`public_stats_eligible`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='?????????';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_music_monthly_summary` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `user_id` bigint(20) NOT NULL COMMENT '??ID',
  `stat_month` date NOT NULL COMMENT '??????????1?',
  `play_count` int(11) NOT NULL DEFAULT '0' COMMENT '????',
  `valid_play_count` int(11) NOT NULL DEFAULT '0' COMMENT '??????',
  `play_seconds` bigint(20) NOT NULL DEFAULT '0' COMMENT '?????',
  `unique_song_count` int(11) NOT NULL DEFAULT '0' COMMENT '?????',
  `active_days` int(11) NOT NULL DEFAULT '0' COMMENT '????',
  `avg_valence` decimal(6,4) DEFAULT NULL COMMENT '?????',
  `avg_energy` decimal(6,4) DEFAULT NULL COMMENT '????',
  `active_minutes` int(11) NOT NULL DEFAULT '0' COMMENT '???????10?????',
  `top_song_id` bigint(20) DEFAULT NULL COMMENT '????????ID',
  `top_song_name` varchar(255) DEFAULT NULL COMMENT '???????????',
  `top_song_artist_names` varchar(512) DEFAULT NULL COMMENT '????????????',
  `public_stats_eligible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '??????????????',
  `feature_coverage` decimal(6,4) NOT NULL DEFAULT '0.0000' COMMENT '???????',
  `calculation_version` varchar(32) NOT NULL DEFAULT 'music-summary-v1' COMMENT '????',
  `data_until` datetime DEFAULT NULL COMMENT '??????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_music_monthly` (`user_id`,`stat_month`),
  KEY `idx_user_music_monthly_month_user` (`stat_month`,`user_id`),
  KEY `idx_user_music_monthly_public` (`public_stats_eligible`,`stat_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='?????????';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_music_report_refresh_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `task_id` varchar(96) NOT NULL COMMENT '????ID',
  `business_key` varchar(192) DEFAULT NULL COMMENT '??/????/?????',
  `user_id` bigint(20) NOT NULL COMMENT '??????ID',
  `operator_id` bigint(20) NOT NULL COMMENT '?????????ID',
  `report_type` varchar(64) NOT NULL COMMENT '????',
  `period_key` varchar(64) NOT NULL COMMENT '?????',
  `status` varchar(16) NOT NULL COMMENT 'pending/running/success/failed',
  `attempt_count` int(11) NOT NULL DEFAULT '0' COMMENT '???????',
  `max_attempts` int(11) NOT NULL DEFAULT '3' COMMENT '??????',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '??????',
  `result_json` json DEFAULT NULL COMMENT '????????????',
  `next_retry_time` datetime DEFAULT NULL COMMENT '?????????',
  `started_at` datetime DEFAULT NULL COMMENT '??????',
  `worker_id` varchar(64) DEFAULT NULL,
  `lease_until` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL COMMENT '??????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_music_report_task_id` (`task_id`),
  UNIQUE KEY `uk_user_music_report_business_key` (`business_key`),
  KEY `idx_user_music_report_task_due` (`status`,`next_retry_time`,`attempt_count`,`create_time`),
  KEY `idx_user_music_report_task_user` (`user_id`,`report_type`,`period_key`,`create_time`),
  KEY `idx_user_music_report_task_updated` (`update_time`),
  KEY `idx_user_music_report_task_running` (`status`,`started_at`,`attempt_count`),
  KEY `idx_report_refresh_status_lease` (`status`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='?????????????';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_music_report_snapshot` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `user_id` bigint(20) NOT NULL COMMENT '??ID',
  `report_type` varchar(64) NOT NULL COMMENT '????',
  `period_key` varchar(64) NOT NULL COMMENT '?????2026-08-16?2026',
  `content_json` longtext NOT NULL COMMENT '??JSON??',
  `generated_at` datetime NOT NULL COMMENT '????',
  `data_until` datetime DEFAULT NULL COMMENT '??????',
  `calculation_version` varchar(32) NOT NULL DEFAULT 'music-summary-v1' COMMENT '????',
  `expire_time` datetime DEFAULT NULL COMMENT '??????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_music_report_period` (`user_id`,`report_type`,`period_key`),
  KEY `idx_user_music_report_expire` (`expire_time`),
  KEY `idx_user_music_report_type_period` (`report_type`,`period_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='????????';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_music_summary_backfill_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `task_id` varchar(96) NOT NULL COMMENT '????ID',
  `business_key` varchar(192) NOT NULL COMMENT '????????????',
  `operator_id` bigint(20) NOT NULL COMMENT '?????????ID',
  `summary_type` varchar(16) NOT NULL COMMENT 'daily/monthly',
  `start_period` varchar(16) NOT NULL COMMENT '??????',
  `end_period` varchar(16) NOT NULL COMMENT '??????',
  `cursor_period` varchar(16) NOT NULL COMMENT '????????',
  `total_periods` int(11) NOT NULL DEFAULT '0' COMMENT '????',
  `completed_periods` int(11) NOT NULL DEFAULT '0' COMMENT '??????',
  `affected_rows` int(11) NOT NULL DEFAULT '0' COMMENT '??????',
  `failed_periods_json` json DEFAULT NULL COMMENT '?????????',
  `status` varchar(16) NOT NULL COMMENT 'pending/running/success/failed',
  `attempt_count` int(11) NOT NULL DEFAULT '0' COMMENT '???????',
  `max_attempts` int(11) NOT NULL DEFAULT '3' COMMENT '??????',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '??????',
  `next_retry_time` datetime DEFAULT NULL COMMENT '?????????',
  `started_at` datetime DEFAULT NULL COMMENT '??????',
  `worker_id` varchar(64) DEFAULT NULL,
  `lease_until` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL COMMENT '??????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_music_summary_backfill_task_id` (`task_id`),
  UNIQUE KEY `uk_user_music_summary_backfill_business_key` (`business_key`),
  KEY `idx_user_music_summary_backfill_due` (`status`,`next_retry_time`,`attempt_count`,`create_time`),
  KEY `idx_user_music_summary_backfill_running` (`status`,`started_at`,`attempt_count`),
  KEY `idx_user_music_summary_backfill_updated` (`update_time`),
  KEY `idx_summary_backfill_status_lease` (`status`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='????????????';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_music_top_item_summary` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `user_id` bigint(20) NOT NULL COMMENT '??ID',
  `period_type` varchar(16) NOT NULL COMMENT '???? daily/monthly',
  `period_key` date NOT NULL COMMENT '??????',
  `item_type` varchar(16) NOT NULL COMMENT '???? song/artist',
  `item_id` bigint(20) NOT NULL COMMENT '?????ID',
  `item_name` varchar(255) DEFAULT NULL COMMENT '??????',
  `artist_names` varchar(512) DEFAULT NULL COMMENT '????????',
  `item_cover` varchar(1024) DEFAULT NULL COMMENT '???????????',
  `play_count` bigint(20) NOT NULL DEFAULT '0' COMMENT '????',
  `public_stats_eligible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '??????????????',
  `calculation_version` varchar(32) NOT NULL DEFAULT 'music-summary-v1' COMMENT '????',
  `data_until` datetime DEFAULT NULL COMMENT '??????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_music_top_item_period` (`user_id`,`period_type`,`period_key`,`item_type`,`item_id`),
  KEY `idx_user_music_top_period_item` (`user_id`,`period_type`,`period_key`,`item_type`,`play_count`),
  KEY `idx_user_music_top_public` (`period_type`,`period_key`,`item_type`,`public_stats_eligible`,`play_count`),
  KEY `idx_user_music_top_status` (`period_type`,`period_key`,`deleted`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='????????Top??';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_password` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '???',
  `password_plain` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '???????????',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??',
  `question` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '???????????????????',
  `answer` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '???????????????????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '????',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_user_password_user_deleted` (`user_id`,`deleted`),
  KEY `idx_user_password_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_points` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `total_points` int(11) DEFAULT '0' COMMENT '总积分',
  `available_points` int(11) DEFAULT '0' COMMENT '可用积分',
  `frozen_points` int(11) DEFAULT '0' COMMENT '冻结积分',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_points_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `change_type` varchar(20) NOT NULL COMMENT '变动类型：earn-获得，spend-消费',
  `change_amount` int(11) NOT NULL COMMENT '变动数量',
  `before_points` int(11) DEFAULT '0' COMMENT '变化前积分',
  `after_points` int(11) NOT NULL COMMENT '变动后积分',
  `reason` varchar(200) DEFAULT NULL COMMENT '变动原因',
  `related_id` bigint(20) DEFAULT NULL COMMENT '关联ID',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_user_points_record_user_time_type` (`user_id`,`create_time`,`change_type`,`change_amount`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_private` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `profile_visible` tinyint(4) DEFAULT '1' COMMENT '资料可见性：0-私密，1-公开',
  `following_visible` tinyint(4) DEFAULT '1' COMMENT '关注列表可见性',
  `follower_visible` tinyint(4) DEFAULT '1' COMMENT '粉丝列表可见性',
  `playlist_visible` tinyint(4) DEFAULT '1' COMMENT '歌单可见性',
  `history_visible` tinyint(4) DEFAULT '0' COMMENT '听歌历史可见性',
  `allow_stranger_message` tinyint(4) DEFAULT '1' COMMENT '允许陌生人私信',
  `allow_recommend` tinyint(4) DEFAULT '1' COMMENT '允许推荐',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户隐私表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_profile` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `id_card` varchar(18) DEFAULT NULL COMMENT '身份证号（加密）',
  `province` varchar(50) DEFAULT NULL COMMENT '省份',
  `city` varchar(50) DEFAULT NULL COMMENT '城市',
  `address` varchar(200) DEFAULT NULL COMMENT '详细地址',
  `occupation` varchar(50) DEFAULT NULL COMMENT '职业',
  `company` varchar(100) DEFAULT NULL COMMENT '公司',
  `school` varchar(100) DEFAULT NULL COMMENT '学校',
  `interests` varchar(500) DEFAULT NULL COMMENT '兴趣爱好（JSON）',
  `bio` text COMMENT '个人简介',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资料表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_purchased` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `resource_type` varchar(20) NOT NULL COMMENT '资源类型',
  `resource_id` bigint(20) NOT NULL COMMENT '资源ID',
  `purchase_order_id` bigint(20) DEFAULT NULL COMMENT '支付订单ID',
  `purchase_type` varchar(20) NOT NULL COMMENT '购买类型',
  `purchase_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '购买时间',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_resource` (`user_id`,`resource_type`,`resource_id`),
  UNIQUE KEY `uk_user_purchased_payment_order` (`purchase_order_id`),
  KEY `idx_expire_time` (`expire_time`),
  KEY `idx_user_purchased_order` (`purchase_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户已购资源表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_quick_phrase` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `phrase` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '常用语内容',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '排序顺序（数字越小越靠前）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_phrase` (`user_id`,`phrase`),
  KEY `idx_user_sort` (`user_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户常用语表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_reward_claim` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `claim_type` varchar(64) NOT NULL,
  `claim_period` char(7) NOT NULL COMMENT 'Asia/Shanghai yyyy-MM',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/granted',
  `granted_points` int(11) NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_reward_claim_period` (`user_id`,`claim_type`,`claim_period`),
  KEY `idx_user_reward_claim_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户周期奖励领取幂等记录';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_sensitive_profile` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `real_name` blob COMMENT 'AES ciphertext',
  `id_card` blob COMMENT 'encrypted identity number',
  `phone_encrypted` blob,
  `email_encrypted` blob,
  `province_code` varchar(20) DEFAULT NULL,
  `city_code` varchar(20) DEFAULT NULL,
  `district_code` varchar(20) DEFAULT NULL,
  `address_detail` blob,
  `real_name_verified` tinyint(4) NOT NULL DEFAULT '0',
  `verify_time` datetime DEFAULT NULL,
  `verify_method` varchar(20) DEFAULT NULL,
  `bank_name` blob,
  `bank_account` blob,
  `bank_account_name` blob,
  `security_question` blob,
  `security_answer` blob,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sensitive_profile_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户敏感身份资料，与隐私可见性设置分表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_signin_achievement` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `achievement_id` bigint(20) NOT NULL COMMENT '签到成就ID',
  `days` int(11) NOT NULL COMMENT '签到天数',
  `is_rewarded` tinyint(4) DEFAULT '0' COMMENT '是否已领取奖励',
  `reward_time` datetime DEFAULT NULL COMMENT '领取时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_achievement` (`user_id`,`achievement_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户签到成就记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_statistics` (
  `id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户名（冗余字段）',
  `stat_date` date NOT NULL COMMENT '统计日期',
  `play_count` int(11) DEFAULT '0' COMMENT '播放次数',
  `play_duration` int(11) DEFAULT '0' COMMENT '播放时长（秒）',
  `unique_song_count` int(11) DEFAULT '0' COMMENT '去重歌曲数',
  `complete_play_count` int(11) DEFAULT '0' COMMENT '完整播放次数',
  `like_count` int(11) DEFAULT '0' COMMENT '点赞次数',
  `favorite_count` int(11) DEFAULT '0' COMMENT '收藏次数',
  `unfavorite_count` int(11) DEFAULT '0' COMMENT '取消收藏次数',
  `comment_count` int(11) DEFAULT '0' COMMENT '评论次数',
  `share_count` int(11) DEFAULT '0' COMMENT '分享次数',
  `download_count` int(11) DEFAULT '0' COMMENT '下载次数',
  `active_duration` int(11) DEFAULT '0' COMMENT '活跃时长（秒）',
  `login_count` int(11) DEFAULT '0' COMMENT '登录次数',
  `search_count` int(11) DEFAULT '0' COMMENT '搜索次数',
  `create_playlist_count` int(11) DEFAULT '0' COMMENT '创建歌单数',
  `follow_artist_count` int(11) DEFAULT '0' COMMENT '关注歌手数',
  `is_abnormal` tinyint(4) DEFAULT '0' COMMENT '是否异常（0-正常，1-异常）',
  `abnormal_reason` varchar(500) DEFAULT NULL COMMENT '异常原因（JSON格式）',
  `risk_score` int(11) DEFAULT '0' COMMENT '风险评分（0-100）',
  `device_type` varchar(50) DEFAULT NULL COMMENT '使用的设备类型',
  `ip_address` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` int(11) DEFAULT '0' COMMENT '删除标记（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`,`stat_date`,`deleted`),
  KEY `idx_stat_date` (`stat_date`),
  KEY `idx_is_abnormal` (`is_abnormal`),
  KEY `idx_risk_score` (`risk_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为统计表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_tag_preference` (
  `id` bigint(20) NOT NULL COMMENT 'ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
  `score` decimal(5,2) DEFAULT '1.00' COMMENT '偏好分数',
  `play_count` int(11) DEFAULT '0' COMMENT '播放次数',
  `last_play_time` datetime DEFAULT NULL COMMENT '最后播放时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tag` (`user_id`,`tag_id`,`deleted`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_tag_id` (`tag_id`),
  KEY `idx_score` (`score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户标签偏好表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_verification` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) DEFAULT NULL COMMENT '用户ID，找回密码时可为空',
  `verification_type` int(11) NOT NULL COMMENT '验证类型: 0邮箱 1手机 2找回密码 3改手机 4改邮箱',
  `target` varchar(100) NOT NULL DEFAULT '' COMMENT '???????????',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态: 0未验证 1已验证 2已过期',
  `is_used` tinyint(4) NOT NULL DEFAULT '0' COMMENT '?????',
  `used_time` datetime DEFAULT NULL COMMENT '????',
  `code` varchar(20) DEFAULT NULL COMMENT '???',
  `code_hash` varchar(128) DEFAULT NULL COMMENT '?????',
  `verify_data` text COMMENT '认证数据（JSON）',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
  `send_count` int(11) NOT NULL DEFAULT '1' COMMENT '????',
  `last_send_time` datetime DEFAULT NULL COMMENT '??????',
  `client_ip` varchar(50) DEFAULT NULL COMMENT '???IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `fail_count` int(11) NOT NULL DEFAULT '0' COMMENT '??????',
  `is_locked` tinyint(4) NOT NULL DEFAULT '0' COMMENT '????',
  `verified_time` datetime DEFAULT NULL COMMENT '认证时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '????',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_user_type` (`user_id`,`verification_type`),
  KEY `idx_status` (`status`),
  KEY `idx_user_verification_target` (`target`,`verification_type`,`status`,`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户认证表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_violation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `violation_type` varchar(50) DEFAULT NULL COMMENT '违规类型',
  `violation_level` int(11) DEFAULT NULL COMMENT '违规等级：1-轻微 2-一般 3-严重',
  `content_type` varchar(50) DEFAULT NULL COMMENT '内容类型',
  `content_id` bigint(20) DEFAULT NULL COMMENT '内容ID',
  `description` text COMMENT '违规描述',
  `handler_id` bigint(20) DEFAULT NULL COMMENT '处理人ID',
  `penalty_type` varchar(50) DEFAULT NULL COMMENT '处罚类型',
  `penalty_value` varchar(200) DEFAULT NULL COMMENT '处罚内容',
  `is_resolved` tinyint(4) DEFAULT '0' COMMENT '是否已解决',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`,`create_time`),
  KEY `idx_type_level` (`violation_type`,`violation_level`),
  KEY `idx_resolved` (`is_resolved`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户违规记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_vip` (
  `id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户名（冗余字段）',
  `vip_level` int(11) DEFAULT '0' COMMENT 'VIP等级（0-免费，1-月度，2-季度，3-年度，4-终身）',
  `vip_status` int(11) DEFAULT '0' COMMENT 'VIP状态（0-未激活，1-生效中，2-已过期）',
  `vip_start_time` datetime DEFAULT NULL COMMENT 'VIP开始时间',
  `vip_expire_time` datetime DEFAULT NULL COMMENT 'VIP到期时间（终身VIP为null）',
  `auto_renew` int(11) DEFAULT '0' COMMENT '自动续费状态（0-关闭，1-开启）',
  `renew_cycle` int(11) DEFAULT '0' COMMENT '续费周期（0-无，1-月度，2-季度，3-年度）',
  `payment_amount` bigint(20) DEFAULT '0' COMMENT '本次支付金额（分）',
  `payment_method` varchar(20) DEFAULT NULL COMMENT '支付方式',
  `payment_order_no` varchar(100) DEFAULT NULL COMMENT '支付订单号',
  `payment_time` datetime DEFAULT NULL COMMENT '支付时间',
  `total_vip_days` int(11) DEFAULT '0' COMMENT '累计VIP天数',
  `total_spending` bigint(20) DEFAULT '0' COMMENT '累计消费金额（分）',
  `first_purchase_time` datetime DEFAULT NULL COMMENT '首次购买VIP时间',
  `last_purchase_time` datetime DEFAULT NULL COMMENT '最后购买VIP时间',
  `high_quality_play_count` int(11) DEFAULT '0' COMMENT '已使用高音质播放次数',
  `lossless_download_count` int(11) DEFAULT '0' COMMENT '已下载无损歌曲数',
  `exclusive_playlist_count` int(11) DEFAULT '0' COMMENT '已创建专属歌单数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` int(11) DEFAULT '0' COMMENT '删除标记（0-未删除，1-已删除）',
  `source` varchar(20) DEFAULT 'unknown' COMMENT 'VIP来源：payment-付费/redeem-兑换/sign-签到/achievement-成就/gift-赠送/unknown-未知',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_vip_user_id` (`user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_vip_status` (`vip_status`),
  KEY `idx_vip_expire_time` (`vip_expire_time`),
  KEY `idx_user_vip_autorenew_due` (`vip_status`,`auto_renew`,`vip_expire_time`,`id`),
  KEY `idx_user_vip_active_lookup` (`vip_status`,`deleted`,`vip_expire_time`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户VIP信息表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_vip_exchange_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `request_id` varchar(64) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `package_id` bigint(20) NOT NULL,
  `package_code` varchar(32) NOT NULL,
  `rule_version` int(11) NOT NULL,
  `vip_level` int(11) NOT NULL,
  `vip_days` int(11) NOT NULL,
  `cost_points` int(11) NOT NULL,
  `before_points` int(11) NOT NULL,
  `after_points` int(11) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/granted',
  `granted_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_vip_exchange_request` (`user_id`,`request_id`),
  KEY `idx_user_vip_exchange_user_status_time` (`user_id`,`status`,`create_time`),
  KEY `idx_user_vip_exchange_package` (`package_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VIP活跃值兑换幂等事实与规则快照';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_visit` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `visitor_id` bigint(20) DEFAULT NULL COMMENT '访问者ID（游客为NULL）',
  `visited_user_id` bigint(20) NOT NULL COMMENT '被访问用户ID',
  `visit_type` varchar(20) DEFAULT 'profile' COMMENT '访问类型',
  `visit_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_visited_user` (`visited_user_id`,`visit_time`),
  KEY `idx_visitor` (`visitor_id`),
  KEY `idx_user_visit_visitor_visited` (`visitor_id`,`visited_user_id`),
  KEY `idx_user_visit_visited_visitor` (`visited_user_id`,`visitor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户访问记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `user_work` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '鎶曠?ID',
  `user_id` bigint(20) NOT NULL COMMENT '鐢ㄦ埛ID',
  `nickname` varchar(100) DEFAULT NULL COMMENT '鐢ㄦ埛鏄电О',
  `avatar` varchar(500) DEFAULT NULL COMMENT '鐢ㄦ埛澶村儚',
  `work_type` tinyint(4) DEFAULT '1' COMMENT '浣滃搧绫诲瀷锛?-鍗曟洸锛?-涓撹緫锛?-EP锛?-Remix',
  `work_name` varchar(200) NOT NULL COMMENT '浣滃搧鍚嶇О',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '灏侀潰鍥剧墖',
  `file_url` varchar(500) NOT NULL COMMENT '闊抽?鏂囦欢URL',
  `file_urls` text COMMENT '多文件URL（JSON数组格式）',
  `zip_file_url` varchar(500) DEFAULT NULL COMMENT '压缩包文件URL',
  `lyric_file_url` varchar(500) DEFAULT NULL COMMENT '歌词文件URL（LRC/TXT）',
  `upload_type` tinyint(1) DEFAULT '1' COMMENT '上传方式：1-单文件 2-多文件 3-压缩包',
  `quality_type` tinyint(1) DEFAULT '1' COMMENT '音质等级：1-标准，2-高品质，3-无损（系统自动检测）',
  `version_type` varchar(20) DEFAULT 'original' COMMENT '风格版本：original-原版，remix-混音版，live-现场版，instrumental-伴奏版，acoustic-不插电版，extended-扩展版',
  `production_type` varchar(20) DEFAULT 'official' COMMENT '制作版本：demo-Demo版，official-正式版，remastered-重制版，deluxe-豪华版',
  `file_size` bigint(20) DEFAULT NULL COMMENT '鏂囦欢澶у皬锛堝瓧鑺傦級',
  `duration` int(11) DEFAULT NULL COMMENT '闊抽?鏃堕暱锛堢?锛',
  `description` text COMMENT '浣滃搧鎻忚堪',
  `tags` varchar(500) DEFAULT NULL COMMENT '鏍囩?锛堥?鍙峰垎闅旓級',
  `language` tinyint(4) DEFAULT '1' COMMENT '璇?█绫诲瀷锛?-涓?枃锛?-鑻辫?锛?-鏃ヨ?锛?-闊╄?锛?-鍏朵粬',
  `lyric` text COMMENT '姝岃瘝鍐呭?锛圠RC鏍煎紡锛',
  `show_real_name` tinyint(1) DEFAULT '0' COMMENT '鏄?惁瀹炲悕灞曠ず',
  `source` tinyint(4) DEFAULT '1' COMMENT '鎶曠?鏉ユ簮锛?-缃戦〉锛?-绉诲姩绔?紝3-API',
  `status` tinyint(4) DEFAULT '0' COMMENT '瀹℃牳鐘舵?锛?-寰呭?鏍革紝1-宸插彂甯冿紝2-宸叉嫆缁',
  `reviewer_id` bigint(20) DEFAULT NULL COMMENT '瀹℃牳浜篒D',
  `reviewer_name` varchar(100) DEFAULT NULL COMMENT '瀹℃牳浜烘樀绉',
  `review_time` datetime DEFAULT NULL COMMENT '瀹℃牳鏃堕棿',
  `review_reason` varchar(500) DEFAULT NULL COMMENT '瀹℃牳鎰忚?',
  `publish_time` datetime DEFAULT NULL COMMENT '鍙戝竷鏃堕棿',
  `song_id` bigint(20) DEFAULT NULL COMMENT '鍏宠仈姝屾洸ID',
  `play_count` bigint(20) DEFAULT '0' COMMENT '鎾?斁娆℃暟',
  `like_count` int(11) DEFAULT '0' COMMENT '鐐硅禐鏁',
  `collect_count` int(11) DEFAULT '0' COMMENT '鏀惰棌鏁',
  `share_count` int(11) DEFAULT '0' COMMENT '鍒嗕韩鏁',
  `download_count` int(11) DEFAULT '0' COMMENT '涓嬭浇娆℃暟',
  `virus_scanned` tinyint(1) DEFAULT '0' COMMENT '鏄?惁宸茬梾姣掓壂鎻',
  `virus_scan_result` varchar(500) DEFAULT NULL COMMENT '鐥呮瘨鎵?弿缁撴灉',
  `reward_points` int(11) DEFAULT '0' COMMENT '濂栧姳绉?垎',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  `allow_download` tinyint(1) DEFAULT '1' COMMENT '鏄?惁鍏佽?涓嬭浇锛?-鍚︼紝1-鏄?級',
  `allow_comment` tinyint(1) DEFAULT '1' COMMENT '鏄?惁鍏佽?璇勮?锛?-鍚︼紝1-鏄?級',
  `allow_share` tinyint(1) DEFAULT '1' COMMENT '鏄?惁鍏佽?鍒嗕韩锛?-鍚︼紝1-鏄?級',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_upload_type` (`upload_type`),
  KEY `idx_user_work_user_deleted_time` (`user_id`,`deleted`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢ㄦ埛鎶曠?琛';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
CREATE VIEW `v_hot_albums` AS SELECT 
 1 AS `id`,
 1 AS `name`,
 1 AS `artist_names`,
 1 AS `genres`,
 1 AS `language`,
 1 AS `cover`,
 1 AS `play_count`,
 1 AS `favorite_count`,
 1 AS `hot_score`;
SET character_set_client = @saved_cs_client;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
CREATE VIEW `v_hot_artists` AS SELECT 
 1 AS `id`,
 1 AS `name`,
 1 AS `avatar`,
 1 AS `fans_count`,
 1 AS `song_count`,
 1 AS `album_count`;
SET character_set_client = @saved_cs_client;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
CREATE VIEW `v_hot_songs` AS SELECT 
 1 AS `id`,
 1 AS `name`,
 1 AS `artist_names`,
 1 AS `main_type`,
 1 AS `language`,
 1 AS `play_count`,
 1 AS `favorite_count`,
 1 AS `avg_rating`,
 1 AS `hot_score`;
SET character_set_client = @saved_cs_client;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
CREATE VIEW `v_playlist_stats` AS SELECT 
 1 AS `id`,
 1 AS `name`,
 1 AS `cover`,
 1 AS `creator_id`,
 1 AS `creator_name`,
 1 AS `song_count`,
 1 AS `play_count`,
 1 AS `favorite_count`,
 1 AS `is_public`,
 1 AS `create_time`;
SET character_set_client = @saved_cs_client;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
CREATE VIEW `v_song_detail` AS SELECT 
 1 AS `id`,
 1 AS `name`,
 1 AS `artist_names`,
 1 AS `artist_id`,
 1 AS `album_id`,
 1 AS `album_name`,
 1 AS `main_type`,
 1 AS `language`,
 1 AS `duration`,
 1 AS `cover`,
 1 AS `play_count`,
 1 AS `favorite_count`,
 1 AS `avg_rating`,
 1 AS `comment_count`,
 1 AS `has_lyric`;
SET character_set_client = @saved_cs_client;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
CREATE VIEW `v_user_listen_stats` AS SELECT 
 1 AS `user_id`,
 1 AS `total_plays`,
 1 AS `unique_songs`,
 1 AS `total_duration`,
 1 AS `last_listen_time`;
SET character_set_client = @saved_cs_client;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
CREATE VIEW `v_user_music_preference` AS SELECT 
 1 AS `user_id`,
 1 AS `username`,
 1 AS `favorite_genre`,
 1 AS `total_plays`,
 1 AS `unique_songs_played`,
 1 AS `total_favorites`;
SET character_set_client = @saved_cs_client;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `vip_change_record` (
  `id` bigint(20) NOT NULL COMMENT '??ID',
  `user_id` bigint(20) NOT NULL COMMENT '??ID',
  `change_type` varchar(30) NOT NULL COMMENT '????',
  `change_days` int(11) NOT NULL DEFAULT '0' COMMENT '????',
  `before_expire_time` datetime DEFAULT NULL COMMENT '???????',
  `after_expire_time` datetime DEFAULT NULL COMMENT '???????',
  `related_id` bigint(20) DEFAULT NULL COMMENT '????ID???ID',
  `reason` varchar(500) DEFAULT NULL COMMENT '????',
  `vip_level_before` varchar(30) DEFAULT NULL COMMENT '???VIP??',
  `vip_level_after` varchar(30) DEFAULT NULL COMMENT '???VIP??',
  `operator` varchar(50) DEFAULT NULL COMMENT '???',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vip_change_type_related` (`change_type`,`related_id`),
  KEY `idx_vip_change_record_user` (`user_id`,`create_time`),
  KEY `idx_vip_change_record_related` (`related_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VIP变动历史记录表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `vip_exchange_package` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `package_code` varchar(32) NOT NULL,
  `package_name` varchar(50) NOT NULL,
  `vip_level` int(11) NOT NULL,
  `vip_days` int(11) NOT NULL,
  `cost_points` int(11) NOT NULL,
  `rule_version` int(11) NOT NULL DEFAULT '1',
  `status` tinyint(4) NOT NULL DEFAULT '1',
  `sort_order` int(11) NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vip_exchange_package_code` (`package_code`),
  KEY `idx_vip_exchange_package_status_sort` (`status`,`sort_order`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务端权威VIP活跃值兑换套餐';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `vip_gift_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `gift_order_id` bigint(20) NOT NULL COMMENT '????ID',
  `payment_order_id` bigint(20) DEFAULT NULL COMMENT '????ID',
  `giver_id` bigint(20) NOT NULL COMMENT '???ID',
  `receiver_id` bigint(20) NOT NULL COMMENT '???ID',
  `vip_type` varchar(32) NOT NULL COMMENT 'VIP??',
  `vip_level` int(11) NOT NULL COMMENT 'VIP??',
  `vip_days` int(11) NOT NULL COMMENT 'VIP??',
  `start_time` datetime DEFAULT NULL COMMENT 'VIP????',
  `end_time` datetime DEFAULT NULL COMMENT 'VIP????',
  `status` varchar(20) NOT NULL COMMENT 'pending/applied/blocked/failed',
  `apply_time` datetime DEFAULT NULL COMMENT '????',
  `operator_id` bigint(20) DEFAULT NULL COMMENT '?????ID',
  `error_message` varchar(500) DEFAULT NULL COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vip_gift_order` (`gift_order_id`),
  KEY `idx_vip_gift_payment` (`payment_order_id`,`deleted`),
  KEY `idx_vip_gift_receiver_status` (`receiver_id`,`status`,`deleted`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VIP????????';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `virus_scan_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'Scan record id',
  `user_id` bigint(20) DEFAULT NULL COMMENT 'Current user id when available',
  `client_ip` varchar(64) DEFAULT NULL COMMENT 'Client IP when available',
  `business_type` varchar(64) DEFAULT NULL COMMENT 'Business source such as upload/payment/post',
  `scan_target` varchar(32) NOT NULL COMMENT 'file/input_stream/bytes',
  `file_name` varchar(255) DEFAULT NULL COMMENT 'Original or temporary file name',
  `file_path` varchar(1000) DEFAULT NULL COMMENT 'Local file path when scan target is file',
  `file_size` bigint(20) DEFAULT NULL COMMENT 'File size in bytes',
  `result` varchar(32) NOT NULL COMMENT 'clean/infected/error/skipped',
  `threat_name` varchar(255) DEFAULT NULL COMMENT 'Threat name returned by ClamAV',
  `scanner_host` varchar(128) DEFAULT NULL COMMENT 'ClamAV host',
  `scanner_port` int(11) DEFAULT NULL COMMENT 'ClamAV port',
  `quarantined` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Whether the file was moved to quarantine',
  `quarantine_path` varchar(1000) DEFAULT NULL COMMENT 'Quarantine path when available',
  `error_message` varchar(1000) DEFAULT NULL COMMENT 'Error message for scan failure',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT 'Scan duration in milliseconds',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_result_time` (`result`,`create_time`),
  KEY `idx_business_time` (`business_type`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Antivirus scan audit records';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `withdraw_apply` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `creator_id` bigint(20) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `withdraw_type` varchar(20) NOT NULL,
  `withdraw_account` varchar(200) NOT NULL,
  `withdraw_name` varchar(100) NOT NULL,
  `status` varchar(20) DEFAULT 'pending',
  `reviewer_id` bigint(20) DEFAULT NULL,
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_reason` varchar(500) DEFAULT NULL,
  `transaction_id` varchar(200) DEFAULT NULL,
  `completed_time` datetime DEFAULT NULL COMMENT '完成时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_withdraw_transaction_id` (`transaction_id`),
  KEY `idx_creator_status` (`creator_id`,`status`),
  KEY `idx_withdraw_apply_creator_status_time` (`creator_id`,`status`,`create_time`),
  KEY `idx_withdraw_apply_status_time` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现申请表';
SET character_set_client = @saved_cs_client ;
SET @saved_cs_client     = @@character_set_client ;
SET character_set_client = utf8 ;
CREATE TABLE `withdraw_freeze` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '创作者用户ID',
  `refund_id` bigint(20) DEFAULT NULL,
  `withdraw_apply_id` bigint(20) NOT NULL COMMENT '提现申请ID',
  `freeze_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `unfreeze_time` datetime DEFAULT NULL COMMENT '解冻时间',
  `status` varchar(20) DEFAULT 'frozen' COMMENT '状态',
  `reason` varchar(500) DEFAULT NULL,
  `handle_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_withdraw_freeze_refund_fact` (`withdraw_apply_id`,`refund_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_withdraw_apply` (`withdraw_apply_id`),
  KEY `idx_withdraw_freeze_user_status` (`user_id`,`status`),
  KEY `idx_withdraw_freeze_refund_status` (`refund_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现冻结表';
SET character_set_client = @saved_cs_client ;
SET @save_time_zone= @@TIME_ZONE  ;
DELIMITER ;;
SET @saved_cs_client      = @@character_set_client  ;;
SET @saved_cs_results     = @@character_set_results  ;;
SET @saved_col_connection = @@collation_connection  ;;
SET character_set_client  = utf8mb4  ;;
SET character_set_results = utf8mb4  ;;
SET collation_connection  = utf8mb4_general_ci  ;;
SET @saved_sql_mode       = @@sql_mode  ;;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;;
SET @saved_time_zone      = @@time_zone  ;;
SET time_zone             = '+08:00'  ;;
CREATE DEFINER=CURRENT_USER EVENT `evt_cleanup_search_logs` ON SCHEDULE EVERY 1 WEEK STARTS '2026-04-29 04:00:00' ON COMPLETION NOT PRESERVE ENABLE DO DELETE FROM search_log WHERE create_time < DATE_SUB(NOW(), INTERVAL 30 DAY)  ;;
SET time_zone             = @saved_time_zone  ;;
SET sql_mode              = @saved_sql_mode  ;;
SET character_set_client  = @saved_cs_client  ;;
SET character_set_results = @saved_cs_results  ;;
SET collation_connection  = @saved_col_connection  ;;
DELIMITER ;;
SET @saved_cs_client      = @@character_set_client  ;;
SET @saved_cs_results     = @@character_set_results  ;;
SET @saved_col_connection = @@collation_connection  ;;
SET character_set_client  = utf8mb4  ;;
SET character_set_results = utf8mb4  ;;
SET collation_connection  = utf8mb4_general_ci  ;;
SET @saved_sql_mode       = @@sql_mode  ;;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;;
SET @saved_time_zone      = @@time_zone  ;;
SET time_zone             = '+08:00'  ;;
CREATE DEFINER=CURRENT_USER EVENT `evt_update_album_hot_scores` ON SCHEDULE EVERY 1 DAY STARTS '2026-04-29 03:00:00' ON COMPLETION NOT PRESERVE ENABLE DO UPDATE album
  SET hot_score = (play_count * 1.0 + favorite_count * 3.0)
  WHERE status = 1 AND deleted = 0  ;;
SET time_zone             = @saved_time_zone  ;;
SET sql_mode              = @saved_sql_mode  ;;
SET character_set_client  = @saved_cs_client  ;;
SET character_set_results = @saved_cs_results  ;;
SET collation_connection  = @saved_col_connection  ;;
DELIMITER ;;
SET @saved_cs_client      = @@character_set_client  ;;
SET @saved_cs_results     = @@character_set_results  ;;
SET @saved_col_connection = @@collation_connection  ;;
SET character_set_client  = utf8mb4  ;;
SET character_set_results = utf8mb4  ;;
SET collation_connection  = utf8mb4_general_ci  ;;
SET @saved_sql_mode       = @@sql_mode  ;;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;;
SET @saved_time_zone      = @@time_zone  ;;
SET time_zone             = '+08:00'  ;;
CREATE DEFINER=CURRENT_USER EVENT `evt_update_hot_scores` ON SCHEDULE EVERY 1 DAY STARTS '2026-04-29 02:00:00' ON COMPLETION NOT PRESERVE ENABLE DO UPDATE song
  SET hot_score = (play_count * 1.0 + favorite_count * 5.0 + COALESCE(comment_count, 0) * 2.0)
  WHERE status = 1 AND deleted = 0  ;;
SET time_zone             = @saved_time_zone  ;;
SET sql_mode              = @saved_sql_mode  ;;
SET character_set_client  = @saved_cs_client  ;;
SET character_set_results = @saved_cs_results  ;;
SET collation_connection  = @saved_col_connection  ;;
DELIMITER ;;
SET @saved_cs_client      = @@character_set_client  ;;
SET @saved_cs_results     = @@character_set_results  ;;
SET @saved_col_connection = @@collation_connection  ;;
SET character_set_client  = utf8mb4  ;;
SET character_set_results = utf8mb4  ;;
SET collation_connection  = utf8mb4_general_ci  ;;
SET @saved_sql_mode       = @@sql_mode  ;;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;;
SET @saved_time_zone      = @@time_zone  ;;
SET time_zone             = '+08:00'  ;;
CREATE DEFINER=CURRENT_USER EVENT `evt_update_user_stats` ON SCHEDULE EVERY 1 HOUR STARTS '2026-04-28 21:28:21' ON COMPLETION NOT PRESERVE ENABLE DO UPDATE user u
  SET last_active_time = (
      SELECT MAX(listen_time)
      FROM listen_history
      WHERE user_id = u.id AND deleted = 0
  )
  WHERE EXISTS (
      SELECT 1 FROM listen_history lh
      WHERE lh.user_id = u.id AND lh.deleted = 0
      LIMIT 1
  )  ;;
SET time_zone             = @saved_time_zone  ;;
SET sql_mode              = @saved_sql_mode  ;;
SET character_set_client  = @saved_cs_client  ;;
SET character_set_results = @saved_cs_results  ;;
SET collation_connection  = @saved_col_connection  ;;
DELIMITER ;
SET TIME_ZONE= @save_time_zone  ;
SET @saved_cs_client      = @@character_set_client  ;
SET @saved_cs_results     = @@character_set_results  ;
SET @saved_col_connection = @@collation_connection  ;
SET character_set_client  = utf8mb4  ;
SET character_set_results = utf8mb4  ;
SET collation_connection  = utf8mb4_general_ci  ;
SET @saved_sql_mode       = @@sql_mode  ;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;
DELIMITER ;;
CREATE DEFINER=CURRENT_USER FUNCTION `fn_calculate_genre_preference`(
    p_user_id BIGINT,
    p_genre VARCHAR(50)
) RETURNS decimal(5,2)
    READS SQL DATA
BEGIN
    DECLARE v_total_plays INT DEFAULT 0;
    DECLARE v_genre_plays INT DEFAULT 0;
    DECLARE v_preference DECIMAL(5,2) DEFAULT 0;

    SELECT COUNT(*) INTO v_total_plays
    FROM listen_history lh
    WHERE lh.user_id = p_user_id AND lh.deleted = 0;

    SELECT COUNT(*) INTO v_genre_plays
    FROM listen_history lh
    INNER JOIN song s ON lh.song_id = s.id
    WHERE lh.user_id = p_user_id AND s.main_type = p_genre AND lh.deleted = 0;

    IF v_total_plays > 0 THEN
        SET v_preference = (v_genre_plays * 100.0 / v_total_plays);
        IF v_preference > 100 THEN SET v_preference = 100; END IF;
    END IF;

    RETURN v_preference;
END ;;
DELIMITER ;
SET sql_mode              = @saved_sql_mode  ;
SET character_set_client  = @saved_cs_client  ;
SET character_set_results = @saved_cs_results  ;
SET collation_connection  = @saved_col_connection  ;
SET @saved_cs_client      = @@character_set_client  ;
SET @saved_cs_results     = @@character_set_results  ;
SET @saved_col_connection = @@collation_connection  ;
SET character_set_client  = utf8mb4  ;
SET character_set_results = utf8mb4  ;
SET collation_connection  = utf8mb4_general_ci  ;
SET @saved_sql_mode       = @@sql_mode  ;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;
DELIMITER ;;
CREATE DEFINER=CURRENT_USER FUNCTION `fn_calculate_song_hot_score`(p_song_id BIGINT) RETURNS decimal(10,2)
    READS SQL DATA
BEGIN
    DECLARE v_play_count BIGINT;
    DECLARE v_favorite_count INT;
    DECLARE v_comment_count INT;
    DECLARE v_score DECIMAL(10,2);

    SELECT play_count, favorite_count, COALESCE(comment_count, 0)
    INTO v_play_count, v_favorite_count, v_comment_count
    FROM song
    WHERE id = p_song_id AND deleted = 0;

    SET v_score = v_play_count * 1.0 + v_favorite_count * 5.0 + v_comment_count * 2.0;

    RETURN v_score;
END ;;
DELIMITER ;
SET sql_mode              = @saved_sql_mode  ;
SET character_set_client  = @saved_cs_client  ;
SET character_set_results = @saved_cs_results  ;
SET collation_connection  = @saved_col_connection  ;
SET @saved_cs_client      = @@character_set_client  ;
SET @saved_cs_results     = @@character_set_results  ;
SET @saved_col_connection = @@collation_connection  ;
SET character_set_client  = utf8mb4  ;
SET character_set_results = utf8mb4  ;
SET collation_connection  = utf8mb4_general_ci  ;
SET @saved_sql_mode       = @@sql_mode  ;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;
DELIMITER ;;
CREATE DEFINER=CURRENT_USER FUNCTION `fn_get_favorite_genre`(p_user_id BIGINT) RETURNS varchar(50) CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci
    READS SQL DATA
BEGIN
    DECLARE v_genre VARCHAR(50);

    SELECT s.main_type INTO v_genre
    FROM listen_history lh
    INNER JOIN song s ON lh.song_id = s.id
    WHERE lh.user_id = p_user_id AND lh.deleted = 0
    GROUP BY s.main_type
    ORDER BY COUNT(*) DESC
    LIMIT 1;

    RETURN IFNULL(v_genre, 'Pop');
END ;;
DELIMITER ;
SET sql_mode              = @saved_sql_mode  ;
SET character_set_client  = @saved_cs_client  ;
SET character_set_results = @saved_cs_results  ;
SET collation_connection  = @saved_col_connection  ;
SET @saved_cs_client      = @@character_set_client  ;
SET @saved_cs_results     = @@character_set_results  ;
SET @saved_col_connection = @@collation_connection  ;
SET character_set_client  = utf8mb4  ;
SET character_set_results = utf8mb4  ;
SET collation_connection  = utf8mb4_general_ci  ;
SET @saved_sql_mode       = @@sql_mode  ;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;
DELIMITER ;;
CREATE DEFINER=CURRENT_USER FUNCTION `fn_get_favorite_language`(p_user_id BIGINT) RETURNS varchar(10) CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci
    READS SQL DATA
BEGIN
    DECLARE v_language VARCHAR(10);

    SELECT s.language INTO v_language
    FROM listen_history lh
    INNER JOIN song s ON lh.song_id = s.id
    WHERE lh.user_id = p_user_id AND lh.deleted = 0
    GROUP BY s.language
    ORDER BY COUNT(*) DESC
    LIMIT 1;

    RETURN IFNULL(v_language, 'zh');
END ;;
DELIMITER ;
SET sql_mode              = @saved_sql_mode  ;
SET character_set_client  = @saved_cs_client  ;
SET character_set_results = @saved_cs_results  ;
SET collation_connection  = @saved_col_connection  ;
SET @saved_cs_client      = @@character_set_client  ;
SET @saved_cs_results     = @@character_set_results  ;
SET @saved_col_connection = @@collation_connection  ;
SET character_set_client  = utf8mb4  ;
SET character_set_results = utf8mb4  ;
SET collation_connection  = utf8mb4_general_ci  ;
SET @saved_sql_mode       = @@sql_mode  ;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;
DELIMITER ;;
CREATE DEFINER=CURRENT_USER FUNCTION `match_tag`(tags VARCHAR(500), target VARCHAR(50)) RETURNS int(11)
    DETERMINISTIC
BEGIN

    IF tags LIKE '[%]' THEN
        RETURN IF(tags LIKE CONCAT('%"', target, '"%'), 1, 0);
    END IF;


    IF tags LIKE '%,%' THEN
        RETURN IF(FIND_IN_SET(target, tags) > 0, 1, 0);
    END IF;


    RETURN IF(tags = target, 1, 0);
END ;;
DELIMITER ;
SET sql_mode              = @saved_sql_mode  ;
SET character_set_client  = @saved_cs_client  ;
SET character_set_results = @saved_cs_results  ;
SET collation_connection  = @saved_col_connection  ;
SET @saved_cs_client      = @@character_set_client  ;
SET @saved_cs_results     = @@character_set_results  ;
SET @saved_col_connection = @@collation_connection  ;
SET character_set_client  = gbk  ;
SET character_set_results = gbk  ;
SET collation_connection  = gbk_chinese_ci  ;
SET @saved_sql_mode       = @@sql_mode  ;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;
DELIMITER ;;
CREATE DEFINER=CURRENT_USER PROCEDURE `sp_add_credit_record`(
  IN p_user_id BIGINT,
  IN p_credit_type VARCHAR(50),
  IN p_score INT,
  IN p_reason VARCHAR(500),
  IN p_operator_id BIGINT
)
BEGIN
  DECLARE v_current_score INT DEFAULT 100;
  DECLARE v_new_score INT DEFAULT 0;
  DECLARE v_credit_level VARCHAR(20);

  SELECT COALESCE(`total_credit`, 100) INTO v_current_score
  FROM `user_credit`
  WHERE `user_id` = p_user_id
  LIMIT 1;

  SET v_new_score = GREATEST(0, LEAST(100, v_current_score + p_score));

  SET v_credit_level = CASE
    WHEN v_new_score >= 90 THEN 'excellent'
    WHEN v_new_score >= 80 THEN 'good'
    WHEN v_new_score >= 70 THEN 'fair'
    WHEN v_new_score >= 60 THEN 'poor'
    ELSE 'bad'
  END;

  INSERT INTO `user_credit` (`user_id`, `credit_score`, `total_credit`, `credit_level`, `report_credit`, `refund_credit`)
  VALUES (p_user_id, v_new_score, v_new_score, v_credit_level, v_new_score, v_new_score)
  ON DUPLICATE KEY UPDATE
    `credit_score` = v_new_score,
    `total_credit` = v_new_score,
    `credit_level` = v_credit_level,
    `report_credit` = v_new_score,
    `refund_credit` = v_new_score;

  INSERT INTO `credit_record` (`user_id`, `credit_type`, `score`, `reason`, `operator_id`)
  VALUES (p_user_id, p_credit_type, p_score, p_reason, p_operator_id);

  SELECT v_new_score AS new_score, v_credit_level AS credit_level;
END ;;
DELIMITER ;
SET sql_mode              = @saved_sql_mode  ;
SET character_set_client  = @saved_cs_client  ;
SET character_set_results = @saved_cs_results  ;
SET collation_connection  = @saved_col_connection  ;
SET @saved_cs_client      = @@character_set_client  ;
SET @saved_cs_results     = @@character_set_results  ;
SET @saved_col_connection = @@collation_connection  ;
SET character_set_client  = utf8mb4  ;
SET character_set_results = utf8mb4  ;
SET collation_connection  = utf8mb4_general_ci  ;
SET @saved_sql_mode       = @@sql_mode  ;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;
DELIMITER ;;
CREATE DEFINER=CURRENT_USER PROCEDURE `sp_get_hot_keywords`(
    IN p_days INT,
    IN p_limit INT
)
BEGIN
    SELECT keyword, COUNT(*) AS search_count
    FROM search_log
    WHERE create_time >= DATE_SUB(NOW(), INTERVAL p_days DAY)
    GROUP BY keyword
    ORDER BY search_count DESC
    LIMIT p_limit;
END ;;
DELIMITER ;
SET sql_mode              = @saved_sql_mode  ;
SET character_set_client  = @saved_cs_client  ;
SET character_set_results = @saved_cs_results  ;
SET collation_connection  = @saved_col_connection  ;
SET @saved_cs_client      = @@character_set_client  ;
SET @saved_cs_results     = @@character_set_results  ;
SET @saved_col_connection = @@collation_connection  ;
SET character_set_client  = utf8mb4  ;
SET character_set_results = utf8mb4  ;
SET collation_connection  = utf8mb4_general_ci  ;
SET @saved_sql_mode       = @@sql_mode  ;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;
DELIMITER ;;
CREATE DEFINER=CURRENT_USER PROCEDURE `sp_get_hot_songs_by_genre`(
    IN p_genre VARCHAR(50),
    IN p_limit INT
)
BEGIN
    SELECT s.*
    FROM song s
    WHERE s.main_type = p_genre AND s.status = 1 AND s.deleted = 0
    ORDER BY s.play_count DESC, s.favorite_count DESC
    LIMIT p_limit;
END ;;
DELIMITER ;
SET sql_mode              = @saved_sql_mode  ;
SET character_set_client  = @saved_cs_client  ;
SET character_set_results = @saved_cs_results  ;
SET collation_connection  = @saved_col_connection  ;
SET @saved_cs_client      = @@character_set_client  ;
SET @saved_cs_results     = @@character_set_results  ;
SET @saved_col_connection = @@collation_connection  ;
SET character_set_client  = utf8mb4  ;
SET character_set_results = utf8mb4  ;
SET collation_connection  = utf8mb4_general_ci  ;
SET @saved_sql_mode       = @@sql_mode  ;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;
DELIMITER ;;
CREATE DEFINER=CURRENT_USER PROCEDURE `sp_get_personalized_songs`(
    IN p_user_id BIGINT,
    IN p_limit INT
)
BEGIN
    DECLARE v_favorite_genre VARCHAR(50);
    DECLARE v_favorite_language VARCHAR(10);

    SET v_favorite_genre = fn_get_favorite_genre(p_user_id);

    SELECT s.language INTO v_favorite_language
    FROM listen_history lh
    INNER JOIN song s ON lh.song_id = s.id
    WHERE lh.user_id = p_user_id AND lh.deleted = 0
    GROUP BY s.language
    ORDER BY COUNT(*) DESC
    LIMIT 1;

    SELECT s.*,
           (s.play_count * 1.0 + s.favorite_count * 5.0) AS hot_score,
           CASE
               WHEN s.main_type = v_favorite_genre THEN 100
               WHEN s.language = v_favorite_language THEN 50
               ELSE 0
           END AS preference_score
    FROM song s
    WHERE s.status = 1 AND s.deleted = 0
    ORDER BY preference_score DESC, hot_score DESC
    LIMIT p_limit;
END ;;
DELIMITER ;
SET sql_mode              = @saved_sql_mode  ;
SET character_set_client  = @saved_cs_client  ;
SET character_set_results = @saved_cs_results  ;
SET collation_connection  = @saved_col_connection  ;
SET @saved_cs_client      = @@character_set_client  ;
SET @saved_cs_results     = @@character_set_results  ;
SET @saved_col_connection = @@collation_connection  ;
SET character_set_client  = utf8mb4  ;
SET character_set_results = utf8mb4  ;
SET collation_connection  = utf8mb4_general_ci  ;
SET @saved_sql_mode       = @@sql_mode  ;
SET sql_mode              = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'  ;
DELIMITER ;;
CREATE DEFINER=CURRENT_USER PROCEDURE `sp_get_similar_songs`(
    IN p_song_id BIGINT,
    IN p_limit INT
)
BEGIN
    DECLARE v_main_type VARCHAR(50);
    DECLARE v_language VARCHAR(10);
    DECLARE v_artist_id BIGINT;

    SELECT main_type, language, artist_id
    INTO v_main_type, v_language, v_artist_id
    FROM song
    WHERE id = p_song_id AND deleted = 0;

    SELECT s.*,
           CASE
               WHEN s.artist_id = v_artist_id THEN 100
               WHEN s.main_type = v_main_type AND s.language = v_language THEN 80
               WHEN s.main_type = v_main_type THEN 60
               WHEN s.language = v_language THEN 40
               ELSE 20
           END AS similarity_score
    FROM song s
    WHERE s.id != p_song_id AND s.status = 1 AND s.deleted = 0
      AND (s.artist_id = v_artist_id OR s.main_type = v_main_type OR s.language = v_language)
    ORDER BY similarity_score DESC, s.play_count DESC
    LIMIT p_limit;
END ;;
DELIMITER ;
SET sql_mode              = @saved_sql_mode  ;
SET character_set_client  = @saved_cs_client  ;
SET character_set_results = @saved_cs_results  ;
SET collation_connection  = @saved_col_connection  ;

USE `haoranmusic_bus`;
DROP VIEW IF EXISTS `song_play_record`;
SET @saved_cs_client          = @@character_set_client ;
SET @saved_cs_results         = @@character_set_results ;
SET @saved_col_connection     = @@collation_connection ;
SET character_set_client      = utf8mb4 ;
SET character_set_results     = utf8mb4 ;
SET collation_connection      = utf8mb4_general_ci ;
CREATE ALGORITHM=UNDEFINED 
DEFINER=CURRENT_USER SQL SECURITY DEFINER 
VIEW `song_play_record` AS select `listen_history`.`id` AS `id`,`listen_history`.`song_id` AS `song_id`,`listen_history`.`user_id` AS `user_id`,`listen_history`.`duration` AS `play_duration`,`listen_history`.`listen_time` AS `play_time`,`listen_history`.`create_time` AS `created_time` from `listen_history` where (`listen_history`.`deleted` = 0) ;
SET character_set_client      = @saved_cs_client ;
SET character_set_results     = @saved_cs_results ;
SET collation_connection      = @saved_col_connection ;
DROP VIEW IF EXISTS `user_favorite`;
SET @saved_cs_client          = @@character_set_client ;
SET @saved_cs_results         = @@character_set_results ;
SET @saved_col_connection     = @@collation_connection ;
SET character_set_client      = utf8mb4 ;
SET character_set_results     = utf8mb4 ;
SET collation_connection      = utf8mb4_general_ci ;
CREATE ALGORITHM=UNDEFINED 
DEFINER=CURRENT_USER SQL SECURITY DEFINER 
VIEW `user_favorite` AS select `favorite_history`.`id` AS `id`,`favorite_history`.`user_id` AS `user_id`,'song' AS `target_type`,`favorite_history`.`song_id` AS `target_id`,`favorite_history`.`create_time` AS `created_time` from `favorite_history` where ((`favorite_history`.`action_type` = 1) and (`favorite_history`.`deleted` = 0)) union all select `album_favorite`.`id` AS `id`,`album_favorite`.`user_id` AS `user_id`,'album' AS `target_type`,`album_favorite`.`album_id` AS `target_id`,`album_favorite`.`create_time` AS `created_time` from `album_favorite` where (`album_favorite`.`deleted` = 0) union all select `playlist_favorite`.`id` AS `id`,`playlist_favorite`.`user_id` AS `user_id`,'playlist' AS `target_type`,`playlist_favorite`.`playlist_id` AS `target_id`,`playlist_favorite`.`create_time` AS `created_time` from `playlist_favorite` where (`playlist_favorite`.`deleted` = 0) union all select `mv_favorite`.`id` AS `id`,`mv_favorite`.`user_id` AS `user_id`,'mv' AS `target_type`,`mv_favorite`.`mv_id` AS `target_id`,`mv_favorite`.`create_time` AS `created_time` from `mv_favorite` where (`mv_favorite`.`deleted` = 0) union all select `marketplace_favorite`.`id` AS `id`,`marketplace_favorite`.`user_id` AS `user_id`,'marketplace' AS `target_type`,`marketplace_favorite`.`item_id` AS `target_id`,`marketplace_favorite`.`create_time` AS `created_time` from `marketplace_favorite` where (`marketplace_favorite`.`deleted` = 0) ;
SET character_set_client      = @saved_cs_client ;
SET character_set_results     = @saved_cs_results ;
SET collation_connection      = @saved_col_connection ;
DROP VIEW IF EXISTS `v_hot_albums`;
SET @saved_cs_client          = @@character_set_client ;
SET @saved_cs_results         = @@character_set_results ;
SET @saved_col_connection     = @@collation_connection ;
SET character_set_client      = utf8mb4 ;
SET character_set_results     = utf8mb4 ;
SET collation_connection      = utf8mb4_general_ci ;
CREATE ALGORITHM=UNDEFINED 
DEFINER=CURRENT_USER SQL SECURITY DEFINER 
VIEW `v_hot_albums` AS select `a`.`id` AS `id`,`a`.`name` AS `name`,`a`.`artist_names` AS `artist_names`,`a`.`genres` AS `genres`,`a`.`language` AS `language`,`a`.`cover` AS `cover`,`a`.`play_count` AS `play_count`,`a`.`favorite_count` AS `favorite_count`,((`a`.`play_count` * 1.0) + (`a`.`favorite_count` * 3.0)) AS `hot_score` from `album` `a` where ((`a`.`status` = 1) and (`a`.`deleted` = 0)) ;
SET character_set_client      = @saved_cs_client ;
SET character_set_results     = @saved_cs_results ;
SET collation_connection      = @saved_col_connection ;
DROP VIEW IF EXISTS `v_hot_artists`;
SET @saved_cs_client          = @@character_set_client ;
SET @saved_cs_results         = @@character_set_results ;
SET @saved_col_connection     = @@collation_connection ;
SET character_set_client      = utf8mb4 ;
SET character_set_results     = utf8mb4 ;
SET collation_connection      = utf8mb4_general_ci ;
CREATE ALGORITHM=UNDEFINED 
DEFINER=CURRENT_USER SQL SECURITY DEFINER 
VIEW `v_hot_artists` AS select `ar`.`id` AS `id`,`ar`.`name` AS `name`,`ar`.`avatar` AS `avatar`,`ar`.`fans_count` AS `fans_count`,(select count(0) from `song` where ((`song`.`artist_id` = `ar`.`id`) and (`song`.`deleted` = 0))) AS `song_count`,(select count(0) from `album` where ((`album`.`artist_id` = `ar`.`id`) and (`album`.`deleted` = 0))) AS `album_count` from `artist` `ar` where (`ar`.`deleted` = 0) ;
SET character_set_client      = @saved_cs_client ;
SET character_set_results     = @saved_cs_results ;
SET collation_connection      = @saved_col_connection ;
DROP VIEW IF EXISTS `v_hot_songs`;
SET @saved_cs_client          = @@character_set_client ;
SET @saved_cs_results         = @@character_set_results ;
SET @saved_col_connection     = @@collation_connection ;
SET character_set_client      = utf8mb4 ;
SET character_set_results     = utf8mb4 ;
SET collation_connection      = utf8mb4_general_ci ;
CREATE ALGORITHM=UNDEFINED 
DEFINER=CURRENT_USER SQL SECURITY DEFINER 
VIEW `v_hot_songs` AS select `s`.`id` AS `id`,`s`.`name` AS `name`,`s`.`artist_names` AS `artist_names`,`s`.`main_type` AS `main_type`,`s`.`language` AS `language`,`s`.`play_count` AS `play_count`,`s`.`favorite_count` AS `favorite_count`,`s`.`avg_rating` AS `avg_rating`,(((`s`.`play_count` * 1.0) + (`s`.`favorite_count` * 5.0)) + (coalesce(`s`.`comment_count`,0) * 2.0)) AS `hot_score` from `song` `s` where ((`s`.`status` = 1) and (`s`.`deleted` = 0)) ;
SET character_set_client      = @saved_cs_client ;
SET character_set_results     = @saved_cs_results ;
SET collation_connection      = @saved_col_connection ;
DROP VIEW IF EXISTS `v_playlist_stats`;
SET @saved_cs_client          = @@character_set_client ;
SET @saved_cs_results         = @@character_set_results ;
SET @saved_col_connection     = @@collation_connection ;
SET character_set_client      = utf8mb4 ;
SET character_set_results     = utf8mb4 ;
SET collation_connection      = utf8mb4_general_ci ;
CREATE ALGORITHM=UNDEFINED 
DEFINER=CURRENT_USER SQL SECURITY DEFINER 
VIEW `v_playlist_stats` AS select `p`.`id` AS `id`,`p`.`name` AS `name`,`p`.`cover` AS `cover`,`p`.`user_id` AS `creator_id`,`u`.`username` AS `creator_name`,(select count(0) from `playlist_song` where (`playlist_song`.`playlist_id` = `p`.`id`)) AS `song_count`,`p`.`play_count` AS `play_count`,`p`.`favorite_count` AS `favorite_count`,`p`.`is_public` AS `is_public`,`p`.`create_time` AS `create_time` from (`playlist` `p` left join `user` `u` on((`p`.`user_id` = `u`.`id`))) where (`p`.`deleted` = 0) ;
SET character_set_client      = @saved_cs_client ;
SET character_set_results     = @saved_cs_results ;
SET collation_connection      = @saved_col_connection ;
DROP VIEW IF EXISTS `v_song_detail`;
SET @saved_cs_client          = @@character_set_client ;
SET @saved_cs_results         = @@character_set_results ;
SET @saved_col_connection     = @@collation_connection ;
SET character_set_client      = utf8mb4 ;
SET character_set_results     = utf8mb4 ;
SET collation_connection      = utf8mb4_general_ci ;
CREATE ALGORITHM=UNDEFINED 
DEFINER=CURRENT_USER SQL SECURITY DEFINER 
VIEW `v_song_detail` AS select `s`.`id` AS `id`,`s`.`name` AS `name`,`s`.`artist_names` AS `artist_names`,`s`.`artist_id` AS `artist_id`,`s`.`album_id` AS `album_id`,`a`.`name` AS `album_name`,`s`.`main_type` AS `main_type`,`s`.`language` AS `language`,`s`.`duration` AS `duration`,`s`.`cover` AS `cover`,`s`.`play_count` AS `play_count`,`s`.`favorite_count` AS `favorite_count`,`s`.`avg_rating` AS `avg_rating`,`s`.`comment_count` AS `comment_count`,(case when (`l`.`id` is not null) then 1 else 0 end) AS `has_lyric` from ((`song` `s` left join `album` `a` on((`s`.`album_id` = `a`.`id`))) left join `lyric` `l` on((`s`.`id` = `l`.`song_id`))) where (`s`.`deleted` = 0) ;
SET character_set_client      = @saved_cs_client ;
SET character_set_results     = @saved_cs_results ;
SET collation_connection      = @saved_col_connection ;
DROP VIEW IF EXISTS `v_user_listen_stats`;
SET @saved_cs_client          = @@character_set_client ;
SET @saved_cs_results         = @@character_set_results ;
SET @saved_col_connection     = @@collation_connection ;
SET character_set_client      = utf8mb4 ;
SET character_set_results     = utf8mb4 ;
SET collation_connection      = utf8mb4_general_ci ;
CREATE ALGORITHM=UNDEFINED 
DEFINER=CURRENT_USER SQL SECURITY DEFINER 
VIEW `v_user_listen_stats` AS select `lh`.`user_id` AS `user_id`,count(0) AS `total_plays`,count(distinct `lh`.`song_id`) AS `unique_songs`,sum(`s`.`duration`) AS `total_duration`,max(`lh`.`listen_time`) AS `last_listen_time` from (`listen_history` `lh` join `song` `s` on((`lh`.`song_id` = `s`.`id`))) where (`lh`.`deleted` = 0) group by `lh`.`user_id` ;
SET character_set_client      = @saved_cs_client ;
SET character_set_results     = @saved_cs_results ;
SET collation_connection      = @saved_col_connection ;
DROP VIEW IF EXISTS `v_user_music_preference`;
SET @saved_cs_client          = @@character_set_client ;
SET @saved_cs_results         = @@character_set_results ;
SET @saved_col_connection     = @@collation_connection ;
SET character_set_client      = utf8mb4 ;
SET character_set_results     = utf8mb4 ;
SET collation_connection      = utf8mb4_general_ci ;
CREATE ALGORITHM=UNDEFINED 
DEFINER=CURRENT_USER SQL SECURITY DEFINER 
VIEW `v_user_music_preference` AS select `u`.`id` AS `user_id`,`u`.`username` AS `username`,(select `s`.`main_type` from (`song` `s` join `listen_history` `lh` on((`s`.`id` = `lh`.`song_id`))) where ((`lh`.`user_id` = `u`.`id`) and (`lh`.`deleted` = 0)) group by `s`.`main_type` order by count(0) desc limit 1) AS `favorite_genre`,(select count(0) from `listen_history` where ((`listen_history`.`user_id` = `u`.`id`) and (`listen_history`.`deleted` = 0))) AS `total_plays`,(select count(distinct `listen_history`.`song_id`) from `listen_history` where ((`listen_history`.`user_id` = `u`.`id`) and (`listen_history`.`deleted` = 0))) AS `unique_songs_played`,(select count(0) from `song_like` where ((`song_like`.`user_id` = `u`.`id`) and (`song_like`.`is_favorite` = 1) and (`song_like`.`deleted` = 0))) AS `total_favorites` from `user` `u` where (`u`.`deleted` = 0) ;
SET character_set_client      = @saved_cs_client ;
SET character_set_results     = @saved_cs_results ;
SET collation_connection      = @saved_col_connection ;
SET TIME_ZONE=@OLD_TIME_ZONE ;

SET SQL_MODE=@OLD_SQL_MODE ;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS ;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS ;
SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT ;
SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS ;
SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION ;
SET SQL_NOTES=@OLD_SQL_NOTES ;

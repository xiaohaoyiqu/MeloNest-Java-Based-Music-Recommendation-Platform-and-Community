




SET NAMES utf8mb4;
SET @execute_import := 0;

SET @node3_root := '/sdb1/haoranmusicData/Datas';


CREATE TEMPORARY TABLE tmp_node3_media_import (
    row_no BIGINT NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(64) NOT NULL COMMENT 'song 或 mv',
    target_id BIGINT NOT NULL COMMENT '新库 song.id 或 mv.id，不能填写旧库 ID',
    asset_role VARCHAR(64) NOT NULL COMMENT 'song: original/standard/high/lossless/hires/master/instrumental; mv: original/360p/720p/1080p/2160p',
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) DEFAULT NULL,
    public_url VARCHAR(1000) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL COMMENT 'node3 中位于 @node3_root 下的绝对路径',
    file_hash VARCHAR(128) DEFAULT NULL COMMENT '建议填写 SHA-256',
    file_size BIGINT DEFAULT NULL,
    scan_status VARCHAR(32) NOT NULL DEFAULT 'CLEAN',
    replace_catalog_url TINYINT NOT NULL DEFAULT 0 COMMENT '1 才更新 song/MV 的播放 URL 与大小字段',
    PRIMARY KEY (row_no),
    UNIQUE KEY uk_tmp_node3_media_storage_path (storage_path),
    UNIQUE KEY uk_tmp_node3_media_target_role (target_type, target_id, asset_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;














SELECT COUNT(*) INTO @invalid_manifest_rows
FROM tmp_node3_media_import i
WHERE i.target_type NOT IN ('song', 'mv')
   OR i.target_id <= 0
   OR i.asset_role = ''
   OR (i.target_type = 'song' AND i.asset_role NOT IN ('original', 'standard', 'high', 'lossless', 'hires', 'master', 'instrumental'))
   OR (i.target_type = 'mv' AND i.asset_role NOT IN ('original', '360p', '720p', '1080p', '2160p'))
   OR i.original_name = '' OR i.original_name LIKE '%/%' OR LOCATE(CHAR(92), i.original_name) > 0
   OR i.public_url = '' OR CHAR_LENGTH(i.public_url) > 1000
   OR NOT (i.public_url LIKE '/%' OR i.public_url LIKE 'https://%')
   OR i.storage_path = '' OR CHAR_LENGTH(i.storage_path) > 1000
   OR LEFT(i.storage_path, CHAR_LENGTH(@node3_root) + 1) <> CONCAT(@node3_root, '/')
   OR i.storage_path LIKE '%..%' OR LOCATE(CHAR(92), i.storage_path) > 0
   OR i.file_size IS NULL OR i.file_size <= 0
   OR i.scan_status <> 'CLEAN'
   OR i.replace_catalog_url NOT IN (0, 1);


SELECT COUNT(*) INTO @missing_target_rows
FROM tmp_node3_media_import i
WHERE (i.target_type = 'song' AND NOT EXISTS (SELECT 1 FROM song s WHERE s.id = i.target_id))
   OR (i.target_type = 'mv' AND NOT EXISTS (SELECT 1 FROM mv m WHERE m.id = i.target_id));


SELECT COUNT(*) INTO @conflicting_asset_rows
FROM tmp_node3_media_import i
JOIN media_asset a
  ON (a.storage_node = 'node3' AND a.storage_path = i.storage_path)
  OR (a.public_url = i.public_url)
WHERE NOT (a.source_type <=> 'official_derivative')
   OR NOT (a.source_id <=> i.target_id)
   OR NOT (a.asset_role <=> i.asset_role)
   OR a.status NOT IN ('ACTIVE', 'RECLAIMING', 'RECLAIM_FAILED');

SELECT
    (SELECT COUNT(*) FROM tmp_node3_media_import) AS manifest_rows,
    @invalid_manifest_rows AS invalid_manifest_rows,
    @missing_target_rows AS missing_target_rows,
    @conflicting_asset_rows AS conflicting_asset_rows,
    @execute_import AS execute_import;


SET @can_import := IF(@execute_import = 1
                      AND @invalid_manifest_rows = 0
                      AND @missing_target_rows = 0
                      AND @conflicting_asset_rows = 0, 1, 0);

START TRANSACTION;


INSERT INTO media_asset
    (owner_id, upload_session_id, purpose, visibility, original_name, content_type, media_type,
     source_type, source_id, asset_role, public_url, storage_node, storage_path, file_hash,
     file_size, scan_status, status, grace_until, create_time, updated_at)
SELECT
    NULL, NULL, NULL, 'PUBLIC', i.original_name, i.content_type,
    CASE i.target_type WHEN 'song' THEN 'audio' ELSE 'video' END,
    'official_derivative', i.target_id, i.asset_role, i.public_url, 'node3', i.storage_path,
    NULLIF(i.file_hash, ''), i.file_size, i.scan_status, 'ACTIVE', NULL, NOW(), NOW()
FROM tmp_node3_media_import i
LEFT JOIN media_asset existing
  ON existing.storage_node = 'node3' AND existing.storage_path = i.storage_path
WHERE @can_import = 1 AND existing.id IS NULL;


INSERT INTO media_asset_reference
    (asset_id, target_type, target_id, reference_role, create_time, released_at)
SELECT a.id, i.target_type, i.target_id, 'active', NOW(), NULL
FROM tmp_node3_media_import i
JOIN media_asset a
  ON a.storage_node = 'node3'
 AND a.storage_path = i.storage_path
 AND a.source_type = 'official_derivative'
 AND a.source_id = i.target_id
 AND a.asset_role = i.asset_role
LEFT JOIN media_asset_reference r
  ON r.asset_id = a.id
 AND r.target_type = i.target_type
 AND r.target_id = i.target_id
 AND r.reference_role = 'active'
 AND r.released_at IS NULL
WHERE @can_import = 1 AND r.id IS NULL;


UPDATE song s
JOIN (
    SELECT target_id,
           MAX(CASE WHEN asset_role = 'standard' THEN public_url END) AS url_standard,
           MAX(CASE WHEN asset_role = 'high' THEN public_url END) AS url_high,
           MAX(CASE WHEN asset_role = 'lossless' THEN public_url END) AS url_lossless,
           MAX(CASE WHEN asset_role = 'hires' THEN public_url END) AS url_hires,
           MAX(CASE WHEN asset_role = 'master' THEN public_url END) AS url_master,
           MAX(CASE WHEN asset_role = 'instrumental' THEN public_url END) AS url_instrumental,
           MAX(CASE WHEN asset_role = 'standard' THEN file_size END) AS size_standard,
           MAX(CASE WHEN asset_role = 'high' THEN file_size END) AS size_high,
           MAX(CASE WHEN asset_role = 'lossless' THEN file_size END) AS size_lossless,
           MAX(CASE WHEN asset_role = 'hires' THEN file_size END) AS size_hires,
           MAX(CASE WHEN asset_role = 'master' THEN file_size END) AS size_master,
           MAX(CASE WHEN asset_role = 'instrumental' THEN file_size END) AS size_instrumental
    FROM tmp_node3_media_import
    WHERE target_type = 'song' AND replace_catalog_url = 1
    GROUP BY target_id
) i ON i.target_id = s.id
SET s.url_standard = COALESCE(i.url_standard, s.url_standard),
    s.url_high = COALESCE(i.url_high, s.url_high),
    s.url_lossless = COALESCE(i.url_lossless, s.url_lossless),
    s.url_hires = COALESCE(i.url_hires, s.url_hires),
    s.url_master = COALESCE(i.url_master, s.url_master),
    s.url_instrumental = COALESCE(i.url_instrumental, s.url_instrumental),
    s.size_standard = COALESCE(i.size_standard, s.size_standard),
    s.size_high = COALESCE(i.size_high, s.size_high),
    s.size_lossless = COALESCE(i.size_lossless, s.size_lossless),
    s.size_hires = COALESCE(i.size_hires, s.size_hires),
    s.size_master = COALESCE(i.size_master, s.size_master),
    s.size_instrumental = COALESCE(i.size_instrumental, s.size_instrumental),
    s.update_time = NOW()
WHERE @can_import = 1;

UPDATE mv m
JOIN (
    SELECT target_id,
           MAX(CASE WHEN asset_role = '360p' THEN public_url END) AS url_360p,
           MAX(CASE WHEN asset_role = '720p' THEN public_url END) AS url_720p,
           MAX(CASE WHEN asset_role = '1080p' THEN public_url END) AS url_1080p,
           MAX(CASE WHEN asset_role = '2160p' THEN public_url END) AS url_2160p,
           MAX(CASE WHEN asset_role = '360p' THEN file_size END) AS size_360p,
           MAX(CASE WHEN asset_role = '720p' THEN file_size END) AS size_720p,
           MAX(CASE WHEN asset_role = '1080p' THEN file_size END) AS size_1080p,
           MAX(CASE WHEN asset_role = '2160p' THEN file_size END) AS size_2160p
    FROM tmp_node3_media_import
    WHERE target_type = 'mv' AND replace_catalog_url = 1
    GROUP BY target_id
) i ON i.target_id = m.id
SET m.url_360p = COALESCE(i.url_360p, m.url_360p),
    m.url_720p = COALESCE(i.url_720p, m.url_720p),
    m.url_1080p = COALESCE(i.url_1080p, m.url_1080p),
    m.url_2160p = COALESCE(i.url_2160p, m.url_2160p),
    m.size_360p = COALESCE(i.size_360p, m.size_360p),
    m.size_720p = COALESCE(i.size_720p, m.size_720p),
    m.size_1080p = COALESCE(i.size_1080p, m.size_1080p),
    m.size_2160p = COALESCE(i.size_2160p, m.size_2160p),
    m.update_time = NOW()
WHERE @can_import = 1;

COMMIT;


SELECT i.row_no, i.target_type, i.target_id, i.asset_role, i.public_url, i.storage_path,
       a.id AS asset_id, a.status AS asset_status, r.id AS active_reference_id
FROM tmp_node3_media_import i
LEFT JOIN media_asset a
  ON a.storage_node = 'node3' AND a.storage_path = i.storage_path
LEFT JOIN media_asset_reference r
  ON r.asset_id = a.id AND r.target_type = i.target_type AND r.target_id = i.target_id
 AND r.reference_role = 'active' AND r.released_at IS NULL
ORDER BY i.row_no;

DROP TEMPORARY TABLE tmp_node3_media_import;

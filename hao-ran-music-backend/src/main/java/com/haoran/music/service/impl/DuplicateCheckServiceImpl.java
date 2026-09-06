package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.CreatorWork;
import com.haoran.music.entity.LyricRequest;
import com.haoran.music.entity.SongResourceRequest;
import com.haoran.music.mapper.CreatorWorkMapper;
import com.haoran.music.mapper.LyricRequestMapper;
import com.haoran.music.mapper.SongResourceRequestMapper;
import com.haoran.music.service.DuplicateCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

   
                      
                                                      
   
@Slf4j
@Service
public class DuplicateCheckServiceImpl implements DuplicateCheckService {

    @Autowired
    private CreatorWorkMapper creatorWorkMapper;

    @Autowired
    private LyricRequestMapper lyricRequestMapper;

    @Autowired
    private SongResourceRequestMapper songResourceRequestMapper;

    private static final List<Integer> ACTIVE_STATUS = Arrays.asList(0, 1);

    @Override
    public boolean isWorkNameDuplicate(Long userId, String workName) {
        return isWorkNameDuplicate(userId, workName, null);
    }

    @Override
    public boolean isWorkNameDuplicate(Long userId, String workName, Long excludeWorkId) {
        if (userId == null || workName == null || workName.trim().isEmpty()) {
            return false;
        }

        QueryWrapper<CreatorWork> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("work_name", workName.trim())
                .eq("deleted", CommonConstants.NOT_DELETED);

        if (excludeWorkId != null) {
            wrapper.ne("id", excludeWorkId);
        }

        wrapper.in("status", ACTIVE_STATUS);

        Long count = creatorWorkMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    @Override
    public String checkFileHashDuplicate(Long userId, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File cannot be empty");
        }

        String fileHash = calculateFileHash(file);

        QueryWrapper<CreatorWork> wrapper = new QueryWrapper<>();
        wrapper.eq("file_hash", fileHash)
                .eq("deleted", CommonConstants.NOT_DELETED);

        if (userId != null) {
            wrapper.eq("user_id", userId);
        }

        wrapper.in("status", ACTIVE_STATUS);

        Long count = creatorWorkMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new RuntimeException("This file has already been submitted");
        }

        return fileHash;
    }

    @Override
    public boolean isLyricRequestDuplicate(Long userId, Long songId, String correctedLyric) {
        if (userId == null || songId == null || correctedLyric == null) {
            return false;
        }

        String contentHash = calculateContentHash(correctedLyric);

        QueryWrapper<LyricRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("song_id", songId)
                .eq("status", 0)
                .eq("deleted", CommonConstants.NOT_DELETED);

        List<LyricRequest> existingRequests = lyricRequestMapper.selectList(wrapper);

        for (LyricRequest request : existingRequests) {
            String existingHash = calculateContentHash(request.getCorrectedLyric());
            if (contentHash.equals(existingHash)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isSongResourceRequestDuplicate(Long userId, Long songId, String resourceType) {
        if (userId == null || songId == null || resourceType == null) {
            return false;
        }

        QueryWrapper<SongResourceRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("song_id", songId)
                .eq("resource_type", resourceType)
                .eq("status", 0)
                .eq("deleted", CommonConstants.NOT_DELETED);

        Long count = songResourceRequestMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    @Override
    public String calculateFileHash(MultipartFile file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        return hexString.toString();
    }

    private String calculateContentHash(String content) {
        if (content == null) {
            return "";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes("UTF-8"));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to calculate content hash");
            return content;
        }
    }
}

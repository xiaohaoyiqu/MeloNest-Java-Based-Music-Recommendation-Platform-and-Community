



package com.haoran.music.service;

import com.haoran.music.vo.EmojiUploadBatchResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface EmojiPackageUploadService {
    EmojiUploadBatchResult upload(Long packageId, List<MultipartFile> files,
                                  String idempotencyKey, Long creatorId);

    int cleanupRecoverableBatches();
}

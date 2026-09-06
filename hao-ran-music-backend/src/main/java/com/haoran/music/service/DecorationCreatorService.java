



package com.haoran.music.service;

import com.haoran.music.dto.decoration.DecorationCreatorRequest;
import com.haoran.music.entity.DecorationConfig;

import java.util.List;

public interface DecorationCreatorService {
    List<DecorationConfig> getMyDecorations(Long creatorId);
    DecorationConfig getForReview(Long decorationId);
    Long createDraft(DecorationCreatorRequest request, Long creatorId);
    void updateDraft(Long decorationId, DecorationCreatorRequest request, Long creatorId);
    void submit(Long decorationId, Long creatorId);
    void review(Long decorationId, boolean approved, String reason, Long reviewerId);
}

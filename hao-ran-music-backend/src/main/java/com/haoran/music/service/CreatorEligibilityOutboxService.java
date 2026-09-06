


package com.haoran.music.service;

import com.haoran.music.entity.User;




public interface CreatorEligibilityOutboxService {




    void record(User user, String eventType, String oldStatus, Long operatorId, String reason);




    int retryDueEvents(int limit);
}

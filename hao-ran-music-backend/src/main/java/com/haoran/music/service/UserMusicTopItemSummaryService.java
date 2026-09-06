   
                      
   
package com.haoran.music.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public interface UserMusicTopItemSummaryService {

    String PERIOD_DAILY = "daily";
    String PERIOD_MONTHLY = "monthly";
    String ITEM_SONG = "song";
    String ITEM_ARTIST = "artist";

    int refreshDailyTopItems(LocalDate statDate);

    int refreshMonthlyTopItems(YearMonth statMonth);

    boolean hasCompleteCoverage(Long userId, String periodType, LocalDate startKey, LocalDate endKey, int expectedPeriods);

    List<Map<String, Object>> aggregateTopItems(Long userId, String periodType,
                                                 LocalDate startKey, LocalDate endKey,
                                                 String itemType, int limit);

    Map<String, Object> getStatus();
}

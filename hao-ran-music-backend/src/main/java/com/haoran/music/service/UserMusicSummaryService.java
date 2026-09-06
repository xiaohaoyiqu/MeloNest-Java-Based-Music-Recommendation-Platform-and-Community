   
                      
   
package com.haoran.music.service;

import com.haoran.music.entity.UserMusicDailySummary;
import com.haoran.music.entity.UserMusicMonthlySummary;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public interface UserMusicSummaryService {

    String CALCULATION_VERSION = "music-summary-v1";

    int refreshDailySummary(LocalDate statDate);

    int refreshMonthlySummary(YearMonth statMonth);

    Map<String, Object> backfillDailySummaries(LocalDate startDate, LocalDate endDate);

    Map<String, Object> backfillMonthlySummaries(YearMonth startMonth, YearMonth endMonth);

    Map<String, Object> getSummaryStatus();

    List<UserMusicDailySummary> listDailySummaries(Long userId, LocalDate startDate, LocalDate endDate);

    List<UserMusicMonthlySummary> listMonthlySummaries(Long userId, YearMonth startMonth, YearMonth endMonth);
}

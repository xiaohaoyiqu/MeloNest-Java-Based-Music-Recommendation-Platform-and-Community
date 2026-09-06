package com.haoran.music.common.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

   
                      
                      
   
@Data
public class WorkStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean isWorkTime;

    private String currentTime;

    private String nextWorkStartTime;

    private Integer remainingMinutesToday;

    private List<ModeratorVO> onlineModerators;

    private List<ModeratorVO> activeModerators;

    private Map<Long, Integer> moderatorLoads;

    private Integer onlineModeratorCount;

    private Integer activeModeratorCount;

    private Long pendingAssignmentCount;

    private Long inProgressCount;

    private Long todayCompletedCount;
}

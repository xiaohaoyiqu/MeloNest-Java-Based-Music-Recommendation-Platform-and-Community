package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

   
                      
                      
   
@Component
@ConfigurationProperties(prefix = "worktime")
@Data
public class WorkTimeConfig {

       
                 
                     
       
    private int activeTimeoutMinutes = 15;

       
                 
       
    private int offlineTimeoutMinutes = 30;

       
               
       
    private boolean overtimeEnabled = false;

       
               
       
    private List<TimeSlotConfig> regularSlots = Arrays.asList(
        new TimeSlotConfig("08:30", "12:00"),
        new TimeSlotConfig("13:00", "17:30")
    );

       
                   
       
    private List<TimeSlotConfig> overtimeSlots = Arrays.asList(
        new TimeSlotConfig("18:30", "20:30")
    );

       
             
       
    @Data
    public static class TimeSlotConfig {
        private String start;
        private String end;

        public TimeSlotConfig() {}

        public TimeSlotConfig(String start, String end) {
            this.start = start;
            this.end = end;
        }

           
                 
           
        public LocalTime getStartTime() {
            return LocalTime.parse(start);
        }

           
                 
           
        public LocalTime getEndTime() {
            return LocalTime.parse(end);
        }

           
                        
           
        public boolean contains(LocalTime time) {
            LocalTime startTime = getStartTime();
            LocalTime endTime = getEndTime();

            if (startTime.isBefore(endTime)) {
                return !time.isBefore(startTime) && !time.isAfter(endTime);
            } else {
                       
                return !time.isBefore(startTime) || !time.isAfter(endTime);
            }
        }

           
                   
           
        public int getDurationMinutes() {
            return (int) ChronoUnit.MINUTES.between(getStartTime(), getEndTime());
        }
    }

       
                   
       
    public boolean isWorkTime() {
                                                                                             
        if (!isWorkDay()) {
            return false;
        }
        return isWorkTime(LocalTime.now());
    }

       
                     
       
    public boolean isWorkTime(LocalTime time) {
                   
        for (TimeSlotConfig slot : regularSlots) {
            if (slot.contains(time)) {
                return true;
            }
        }

                       
        if (overtimeEnabled) {
            for (TimeSlotConfig slot : overtimeSlots) {
                if (slot.contains(time)) {
                    return true;
                }
            }
        }

        return false;
    }

       
               
       
    public boolean isWorkDay() {
        DayOfWeek day = java.time.LocalDate.now().getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

       
                     
       
    public LocalTime getNextWorkStartTime() {
        LocalTime now = LocalTime.now();

                       
        for (TimeSlotConfig slot : regularSlots) {
            if (now.isBefore(slot.getStartTime())) {
                return slot.getStartTime();
            }
            if (slot.contains(now)) {
                return now;
            }
        }

                 
        if (overtimeEnabled) {
            for (TimeSlotConfig slot : overtimeSlots) {
                if (now.isBefore(slot.getStartTime())) {
                    return slot.getStartTime();
                }
            }
        }

                           
        return regularSlots.get(0).getStartTime();
    }

       
                     
       
    public int getRemainingWorkMinutesToday() {
        LocalTime now = LocalTime.now();
        int remaining = 0;

        for (TimeSlotConfig slot : regularSlots) {
            if (now.isBefore(slot.getStartTime())) {
                remaining += slot.getDurationMinutes();
            } else if (slot.contains(now)) {
                remaining += (int) ChronoUnit.MINUTES.between(now, slot.getEndTime());
            }
        }

        if (overtimeEnabled) {
            for (TimeSlotConfig slot : overtimeSlots) {
                if (now.isBefore(slot.getStartTime())) {
                    remaining += slot.getDurationMinutes();
                } else if (slot.contains(now)) {
                    remaining += (int) ChronoUnit.MINUTES.between(now, slot.getEndTime());
                }
            }
        }

        return Math.max(0, remaining);
    }

       
                   
       
    public Integer getMinutesUntilOff(LocalTime now) {
        for (TimeSlotConfig slot : regularSlots) {
            if (slot.contains(now)) {
                return (int) ChronoUnit.MINUTES.between(now, slot.getEndTime());
            }
        }

        if (overtimeEnabled) {
            for (TimeSlotConfig slot : overtimeSlots) {
                if (slot.contains(now)) {
                    return (int) ChronoUnit.MINUTES.between(now, slot.getEndTime());
                }
            }
        }

        return 0;
    }
}

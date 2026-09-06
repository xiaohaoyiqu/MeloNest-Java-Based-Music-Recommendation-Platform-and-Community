




package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;





@Component
@ConfigurationProperties(prefix = "price-range")
@Data
public class PriceRangeConfig {




    private PriceRange song = new PriceRange(new BigDecimal("2"), new BigDecimal("10"));




    private PriceRange mv = new PriceRange(new BigDecimal("5"), new BigDecimal("20"));




    private PriceRange album = new PriceRange(new BigDecimal("15"), new BigDecimal("50"));




    private PlaylistPriceRange playlist = new PlaylistPriceRange(
            new BigDecimal("5"),
            new BigDecimal("30"),
            "month"
    );




    @Data
    public static class PriceRange {



        protected BigDecimal min;




        protected BigDecimal max;

        public PriceRange() {}

        public PriceRange(BigDecimal min, BigDecimal max) {
            this.min = min;
            this.max = max;
        }




        public boolean isInRange(BigDecimal price) {
            return price.compareTo(min) >= 0 && price.compareTo(max) <= 0;
        }




        public boolean isOutOfRange(BigDecimal price) {
            return !isInRange(price);
        }




        public String getRangeDescription() {
            return min + "-" + max + "元";
        }
    }




    @Data
    public static class PlaylistPriceRange extends PriceRange {



        private String period = "month";

        public PlaylistPriceRange() {}

        public PlaylistPriceRange(BigDecimal min, BigDecimal max, String period) {
            super(min, max);
            this.period = period;
        }




        @Override
        public String getRangeDescription() {
            return min + "-" + max + "元/" + period;
        }
    }







    public PriceRange getPriceRange(String resourceType) {
        switch (resourceType.toLowerCase()) {
            case "song":
                return song;
            case "mv":
            case "video":
                return mv;
            case "album":
                return album;
            case "playlist":
                return playlist;
            default:

                return song;
        }
    }








    public boolean isPriceInRange(String resourceType, BigDecimal price) {
        PriceRange range = getPriceRange(resourceType);
        return range.isInRange(price);
    }







    public String getPriceRangeDescription(String resourceType) {
        PriceRange range = getPriceRange(resourceType);
        return range.getRangeDescription();
    }
}

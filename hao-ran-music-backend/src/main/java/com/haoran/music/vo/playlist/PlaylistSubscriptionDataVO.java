package com.haoran.music.vo.playlist;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;





@Data
public class PlaylistSubscriptionDataVO implements Serializable {

    private static final long serialVersionUID = 1L;




    private Long playlistId;




    private String playlistName;




    private Integer totalSubscribers;




    private Integer activeSubscribers;




    private BigDecimal monthlyRevenue;




    private BigDecimal totalRevenue;




    private List<Integer> subscriberGrowth;
}

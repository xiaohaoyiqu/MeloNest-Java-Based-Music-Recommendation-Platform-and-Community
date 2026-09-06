package com.haoran.music.vo.favorite;

import lombok.Data;

import java.util.List;

                        
@Data
public class FavoriteGroupStateVO {
    private List<FavoriteGroupVO> groups;
    private List<FavoriteGroupItemVO> items;
}

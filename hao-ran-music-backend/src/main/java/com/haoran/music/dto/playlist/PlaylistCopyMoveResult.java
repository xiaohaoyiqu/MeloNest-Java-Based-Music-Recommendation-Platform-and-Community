package com.haoran.music.dto.playlist;

import lombok.Data;

import java.io.Serializable;





@Data
public class PlaylistCopyMoveResult implements Serializable {

    private static final long serialVersionUID = 1L;




    private Integer totalRequested;




    private Integer duplicateCount;




    private Integer actualCount;
}

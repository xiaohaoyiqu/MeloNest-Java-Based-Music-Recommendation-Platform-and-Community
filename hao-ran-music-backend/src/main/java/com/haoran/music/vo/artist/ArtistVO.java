package com.haoran.music.vo.artist;

import com.haoran.music.vo.album.AlbumVO;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

   
                      
                      
   
@Data

public class ArtistVO implements Serializable {

    private static final long serialVersionUID = 1L;


    private Long id;


    private String name;


    private String avatar;


    private String cover;


    private String description;


    private Integer type;

                                              
    private String artistKind;

    private String artistKindName;

                                                        
    private String profileSource;

    private String profileSourceName;

    private Boolean isCreator;


    private Integer fansCount;


    private Integer followingCount;


       
          
       
    private Long playCount;


       
          
       
    private Integer commentCount;


    private Integer songCount;


    private Integer albumCount;


    private String firstLetter;


    private String birthplace;


    private LocalDate birthday;

       
                       
       
    private Integer gender;


    private Boolean isFollow;


    private List<AlbumVO.SongSimpleVO> hotSongs;


    private List<AlbumVO.AlbumSimpleVO> albums;


    private LocalDateTime createTime;

       
                     
       
    @Data

    public static class SongSimpleVO implements Serializable {

        private static final long serialVersionUID = 1L;


        private Long id;


        private String name;


        private Integer duration;


        private String mainType;


        private String cover;
    }

       
                     
       
    @Data

    public static class AlbumSimpleVO implements Serializable {

        private static final long serialVersionUID = 1L;


        private Long id;


        private String name;


        private String cover;


        private LocalDate releaseDate;
    }
}

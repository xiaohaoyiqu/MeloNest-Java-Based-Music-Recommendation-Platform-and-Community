package com.haoran.music.vo.search;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

   
                      
                      
   
@Data
public class SearchSuggestVO implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    private List<SimpleSongVO> songs;

       
           
       
    private List<SimpleArtistVO> artists;

       
           
       
    private List<SimpleAlbumVO> albums;

       
           
       
    private List<SimplePlaylistVO> playlists;

       
                    
       
    private List<String> keywords;

       
             
       
    @Data
    public static class SimpleSongVO implements Serializable {
        private Long id;
        private String name;
        private String artistNames;
        private String cover;
    }

       
             
       
    @Data
    public static class SimpleArtistVO implements Serializable {
        private Long id;
        private String name;
        private String avatar;
    }

       
             
       
    @Data
    public static class SimpleAlbumVO implements Serializable {
        private Long id;
        private String name;
        private String cover;
        private String artistName;
    }

       
             
       
    @Data
    public static class SimplePlaylistVO implements Serializable {
        private Long id;
        private String name;
        private String cover;
        private Integer songCount;
    }
}

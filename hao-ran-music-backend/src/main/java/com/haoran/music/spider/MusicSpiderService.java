package com.haoran.music.spider;

import com.haoran.music.entity.Album;
import com.haoran.music.entity.Artist;
import com.haoran.music.entity.Song;

import java.util.List;

   
                      
                          
  
                                                 
                             
   
public interface MusicSpiderService {

       
             
      
                         
                          
                   
       
    List<Song> spiderSongs(String keyword, Integer limit);

       
             
      
                         
                          
                   
       
    List<Artist> spiderArtists(String keyword, Integer limit);

       
             
      
                                        
                          
                           
                   
       
    List<Album> spiderAlbums(Long artistId, String keyword, Integer limit);

       
                       
      
                           
                           
                   
       
    List<Song> spiderSongsByArtist(Long artistId, Integer limit);

       
                       
      
                          
                   
       
    List<Song> spiderSongsByAlbum(Long albumId);
}

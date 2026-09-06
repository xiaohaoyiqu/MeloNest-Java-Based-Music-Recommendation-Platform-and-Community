package com.haoran.music.service;

import com.haoran.music.entity.CreatorWork;
import com.haoran.music.entity.UserWork;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

   
                                                                                
  
                      
   
public interface SubmissionAlbumPublishService {

       
                                                  
      
                                        
                             
       
    AlbumPublishResult publishCreatorAlbum(CreatorWork work);

       
                                                      
      
                                     
                             
       
    AlbumPublishResult publishUserAlbum(UserWork work);

       
                                                                                                
      
                                    
       
    void enrichUserWorks(List<UserWork> works);

       
                                                                                                   
      
                                       
       
    void enrichCreatorWorks(List<CreatorWork> works);

       
                                                           
       
    final class AlbumPublishResult {
        private final Long albumId;
        private final List<Long> songIds;

        public AlbumPublishResult(Long albumId, List<Long> songIds) {
            this.albumId = albumId;
            if (songIds == null || songIds.isEmpty()) {
                this.songIds = Collections.emptyList();
            } else {
                this.songIds = Collections.unmodifiableList(new ArrayList<>(songIds));
            }
        }

        public static AlbumPublishResult empty() {
            return new AlbumPublishResult(null, Collections.emptyList());
        }

        public Long getAlbumId() {
            return albumId;
        }

        public List<Long> getSongIds() {
            return songIds;
        }

        public Long getFirstSongId() {
            return songIds.isEmpty() ? null : songIds.get(0);
        }

        public int getCreatedSongCount() {
            return songIds.size();
        }

        public boolean hasPublishedSongs() {
            return albumId != null && !songIds.isEmpty();
        }
    }
}

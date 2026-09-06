package com.haoran.music.dto.localMusic;

import lombok.Data;

   
                      
                                 
   
@Data
public class FileInfoDTO {

       
           
       
    private String path;

       
          
       
    private String name;

       
               
       
    private Long size;

       
           
       
    private String modified;

       
           
       
    private String created;

       
             
       
    private String mime;

       
           
       
    private String format;

       
                     
       
    private AudioMetadata audio;

       
            
       
    @lombok.Data
    public static class AudioMetadata {
           
                
           
        private Integer duration;

           
                    
           
        private Integer bitrate;

           
               
           
        private String codec;

           
                                     
           
        private String quality;

           
               
           
        private String formatName;

           
               
           
        private String formatQuality;

           
              
           
        private Integer sampleRate;

           
              
           
        private Integer bitsPerSample;

           
              
           
        private Integer numberOfChannels;

           
             
           
        private String title;

           
              
           
        private String artist;

           
             
           
        private String album;
    }
}

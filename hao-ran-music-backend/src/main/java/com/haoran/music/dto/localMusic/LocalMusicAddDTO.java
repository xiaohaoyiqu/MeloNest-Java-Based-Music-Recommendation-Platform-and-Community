package com.haoran.music.dto.localMusic;

import javax.validation.constraints.NotBlank;





public class LocalMusicAddDTO {




    @NotBlank(message = "文件路径不能为空")
    private String filePath;




    private String name;




    private String artist;




    private String album;



  private Integer quality;

  public Integer getQuality() {
    return quality;
  }

  public void setQuality(Integer quality) {
    this.quality = quality;
  }


    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }
}

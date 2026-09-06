package com.haoran.music.vo.search;

import com.haoran.music.common.util.MediaPlaybackUrlUtil;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;





@Data
public class SearchResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String keyword;
    private List<SongSimpleVO> songs;
    private List<AlbumSimpleVO> albums;
    private List<ArtistSimpleVO> artists;
    private List<PlaylistSimpleVO> playlists;
    private List<MvSimpleVO> mvs;
    private List<UserSimpleVO> users;
    private Integer page;
    private Integer size;
    private Long total;
    private Integer pages;


    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public List<SongSimpleVO> getSongs() { return songs; }
    public void setSongs(List<SongSimpleVO> songs) { this.songs = songs; }
    public List<AlbumSimpleVO> getAlbums() { return albums; }
    public void setAlbums(List<AlbumSimpleVO> albums) { this.albums = albums; }
    public List<ArtistSimpleVO> getArtists() { return artists; }
    public void setArtists(List<ArtistSimpleVO> artists) { this.artists = artists; }
    public List<PlaylistSimpleVO> getPlaylists() { return playlists; }
    public void setPlaylists(List<PlaylistSimpleVO> playlists) { this.playlists = playlists; }
    public List<MvSimpleVO> getMvs() { return mvs; }
    public void setMvs(List<MvSimpleVO> mvs) { this.mvs = mvs; }
    public List<UserSimpleVO> getUsers() { return users; }
    public void setUsers(List<UserSimpleVO> users) { this.users = users; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
    public Integer getPages() { return pages; }
    public void setPages(Integer pages) { this.pages = pages; }



    @Getter
    @Setter
    public static class SongSimpleVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String name;
        private String artistNames;
        private String albumName;
        private Integer duration;
        private String mainType;
        private String cover;
        private Boolean isFavorite;
        private String urlStandard;
        private String urlHigh;
        private String urlLossless;
        private String versionType;
        private String versionName;
        private String language;

        public String getUrlStandard() {
            return MediaPlaybackUrlUtil.songUrl(id, "standard", urlStandard);
        }

        public String getUrlHigh() {
            return MediaPlaybackUrlUtil.songUrl(id, "high", urlHigh);
        }

        public String getUrlLossless() {
            return MediaPlaybackUrlUtil.songUrl(id, "lossless", urlLossless);
        }
    }




    @Getter
    @Setter
    public static class AlbumSimpleVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String name;
        private String artistNames;
        private String releaseDate;
        private String cover;
        private String language;
    }




    @Getter
    @Setter
    public static class ArtistSimpleVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String name;
        private Integer type;
        private String avatar;
    }




    @Getter
    @Setter
    public static class PlaylistSimpleVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String name;
        private String creatorName;
        private Integer songCount;
        private String cover;
    }




    @Getter
    @Setter
    public static class MvSimpleVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String name;
        private String artistNames;
        private Integer duration;
        private Long playCount;
        private String cover;
        private String songLanguage;
    }




    @Getter
    @Setter
    public static class UserSimpleVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;
        private String nickname;
        private String avatar;
        private Integer fansCount;
        private String signature;
    }
}

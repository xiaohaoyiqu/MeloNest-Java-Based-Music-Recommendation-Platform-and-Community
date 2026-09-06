package com.haoran.music.spider.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.haoran.music.entity.Album;
import com.haoran.music.entity.Artist;
import com.haoran.music.entity.Song;
import com.haoran.music.spider.AbstractMusicSpider;
import com.haoran.music.spider.MusicSpiderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;








@Slf4j
@Service
public class GenericMusicSpiderImpl extends AbstractMusicSpider implements MusicSpiderService {




    private static final String API_BASE_URL = "https://api.example.com";

    @Override
    protected String getBaseUrl() {
        return API_BASE_URL;
    }

    @Override
    protected boolean isSuccess(JSONObject responseJson) {
        if (responseJson == null) {
            return false;
        }

        Integer code = responseJson.getInt("code");
        return code != null && code == 200;
    }

    @Override
    protected String getErrorMessage(JSONObject responseJson) {
        if (responseJson == null) {
            return "响应为空";
        }
        return responseJson.getStr("message", "未知错误");
    }

    @Override
    public List<Song> spiderSongs(String keyword, Integer limit) {
        log.info("开始爬取歌曲，关键词: {}, 限制: {}", keyword, limit);

        List<Song> result = new ArrayList<>();

        try {

            String url = getBaseUrl() + "/search/song?keyword=" + encode(keyword) + "&limit=" + limit;


            String response = doGet(url, null);
            JSONObject json = parseJson(response);

            if (json == null || !isSuccess(json)) {
                log.warn("获取歌曲数据失败: {}", getErrorMessage(json));
                return result;
            }


            JSONArray songs = json.getJSONArray("data");
            if (songs != null && !songs.isEmpty()) {
                for (int i = 0; i < songs.size() && result.size() < limit; i++) {
                    JSONObject songObj = songs.getJSONObject(i);
                    Song song = parseSong(songObj);
                    if (song != null) {
                        result.add(song);
                    }
                }
            }

            log.info("成功爬取{}首歌曲", result.size());

        } catch (Exception e) {
            log.error("爬取歌曲失败");
        }

        return result;
    }

    @Override
    public List<Artist> spiderArtists(String keyword, Integer limit) {
        log.info("开始爬取歌手，关键词: {}, 限制: {}", keyword, limit);

        List<Artist> result = new ArrayList<>();

        try {
            String url = getBaseUrl() + "/search/artist?keyword=" + encode(keyword) + "&limit=" + limit;
            String response = doGet(url, null);
            JSONObject json = parseJson(response);

            if (json == null || !isSuccess(json)) {
                log.warn("获取歌手数据失败: {}", getErrorMessage(json));
                return result;
            }

            JSONArray artists = json.getJSONArray("data");
            if (artists != null && !artists.isEmpty()) {
                for (int i = 0; i < artists.size() && result.size() < limit; i++) {
                    JSONObject artistObj = artists.getJSONObject(i);
                    Artist artist = parseArtist(artistObj);
                    if (artist != null) {
                        result.add(artist);
                    }
                }
            }

            log.info("成功爬取{}位歌手", result.size());

        } catch (Exception e) {
            log.error("爬取歌手失败");
        }

        return result;
    }

    @Override
    public List<Album> spiderAlbums(Long artistId, String keyword, Integer limit) {
        log.info("开始爬取专辑，歌手ID: {}, 关键词: {}, 限制: {}", artistId, keyword, limit);

        List<Album> result = new ArrayList<>();

        try {
            StringBuilder urlBuilder = new StringBuilder(getBaseUrl());
            urlBuilder.append("/search/album?limit=").append(limit);

            if (artistId != null) {
                urlBuilder.append("&artistId=").append(artistId);
            }
            if (StrUtil.isNotBlank(keyword)) {
                urlBuilder.append("&keyword=").append(encode(keyword));
            }

            String response = doGet(urlBuilder.toString(), null);
            JSONObject json = parseJson(response);

            if (json == null || !isSuccess(json)) {
                log.warn("获取专辑数据失败: {}", getErrorMessage(json));
                return result;
            }

            JSONArray albums = json.getJSONArray("data");
            if (albums != null && !albums.isEmpty()) {
                for (int i = 0; i < albums.size() && result.size() < limit; i++) {
                    JSONObject albumObj = albums.getJSONObject(i);
                    Album album = parseAlbum(albumObj);
                    if (album != null) {
                        result.add(album);
                    }
                }
            }

            log.info("成功爬取{}张专辑", result.size());

        } catch (Exception e) {
            log.error("爬取专辑失败");
        }

        return result;
    }

    @Override
    public List<Song> spiderSongsByArtist(Long artistId, Integer limit) {
        log.info("开始爬取歌手的歌曲，歌手ID: {}, 限制: {}", artistId, limit);

        List<Song> result = new ArrayList<>();

        try {
            String url = getBaseUrl() + "/artist/" + artistId + "/songs?limit=" + limit;
            String response = doGet(url, null);
            JSONObject json = parseJson(response);

            if (json == null || !isSuccess(json)) {
                log.warn("获取歌手歌曲失败: {}", getErrorMessage(json));
                return result;
            }

            JSONArray songs = json.getJSONArray("data");
            if (songs != null && !songs.isEmpty()) {
                for (int i = 0; i < songs.size() && result.size() < limit; i++) {
                    JSONObject songObj = songs.getJSONObject(i);
                    Song song = parseSong(songObj);
                    if (song != null) {
                        result.add(song);
                    }
                }
            }

            log.info("成功爬取{}首歌曲", result.size());

        } catch (Exception e) {
            log.error("爬取歌手歌曲失败");
        }

        return result;
    }

    @Override
    public List<Song> spiderSongsByAlbum(Long albumId) {
        log.info("开始爬取专辑的歌曲，专辑ID: {}", albumId);

        List<Song> result = new ArrayList<>();

        try {
            String url = getBaseUrl() + "/album/" + albumId + "/songs";
            String response = doGet(url, null);
            JSONObject json = parseJson(response);

            if (json == null || !isSuccess(json)) {
                log.warn("获取专辑歌曲失败: {}", getErrorMessage(json));
                return result;
            }

            JSONArray songs = json.getJSONArray("data");
            if (songs != null && !songs.isEmpty()) {
                for (int i = 0; i < songs.size(); i++) {
                    JSONObject songObj = songs.getJSONObject(i);
                    Song song = parseSong(songObj);
                    if (song != null) {
                        result.add(song);
                    }
                }
            }

            log.info("成功爬取{}首歌曲", result.size());

        } catch (Exception e) {
            log.error("爬取专辑歌曲失败");
        }

        return result;
    }







    private Song parseSong(JSONObject obj) {
        if (obj == null) {
            return null;
        }

        try {
            Song song = new Song();


            song.setId(obj.getLong("id"));
            song.setName(obj.getStr("name"));
            song.setArtistIds(obj.getStr("artistIds"));
            song.setArtistNames(obj.getStr("artistNames"));
            song.setAlbumId(obj.getLong("albumId"));
            song.setAlbumName(obj.getStr("albumName"));


            String durationStr = obj.getStr("duration");
            song.setDuration(parseDuration(durationStr));


            String dateStr = obj.getStr("publishDate");
            if (StrUtil.isNotBlank(dateStr)) {
                try {
                    song.setReleaseDate(LocalDate.parse(dateStr));
                } catch (Exception e) {

                }
            }


            song.setMainType(obj.getStr("mainType"));
            song.setSubTypes(obj.getStr("subTypes"));


            song.setUrlStandard(obj.getStr("urlStandard"));
            song.setUrlHigh(obj.getStr("urlHigh"));
            song.setUrlLossless(obj.getStr("urlLossless"));


            song.setCover(obj.getStr("cover"));

            return song;

        } catch (Exception e) {
            log.error("解析歌曲信息失败: {}", obj);
            return null;
        }
    }







    private Artist parseArtist(JSONObject obj) {
        if (obj == null) {
            return null;
        }

        try {
            Artist artist = new Artist();

            artist.setId(obj.getLong("id"));
            artist.setName(obj.getStr("name"));
            artist.setAvatar(obj.getStr("avatar"));
            artist.setCover(obj.getStr("cover"));
            artist.setDescription(obj.getStr("description"));
            artist.setType(obj.getInt("type", 0));
            artist.setFansCount(Long.valueOf(obj.getInt("fansCount", 0)));
            artist.setSongCount(Long.valueOf(obj.getInt("songCount", 0)));
            artist.setAlbumCount(Long.valueOf(obj.getInt("albumCount", 0)));


            String name = obj.getStr("name");
            if (StrUtil.isNotBlank(name)) {
                artist.setFirstLetter(getFirstLetter(name));
            }

            return artist;

        } catch (Exception e) {
            log.error("解析歌手信息失败: {}", obj);
            return null;
        }
    }







    private Album parseAlbum(JSONObject obj) {
        if (obj == null) {
            return null;
        }

        try {
            Album album = new Album();

            album.setId(obj.getLong("id"));
            album.setName(obj.getStr("name"));


            String dateStr = obj.getStr("publishDate");
            if (StrUtil.isNotBlank(dateStr)) {
                try {
                    album.setReleaseDate(LocalDate.parse(dateStr));
                } catch (Exception e) {

                }
            }

            album.setCountry(obj.getStr("country"));
            album.setProvince(obj.getStr("province"));
            album.setCompany(obj.getStr("company"));
            album.setArtistIds(obj.getStr("artistIds"));
            album.setArtistNames(obj.getStr("artistNames"));
            album.setCover(obj.getStr("cover"));
            album.setDescription(obj.getStr("description"));
            album.setSongCount(Long.valueOf(obj.getInt("songCount", 0)));

            return album;

        } catch (Exception e) {
            log.error("解析专辑信息失败: {}", obj);
            return null;
        }
    }







    private String getFirstLetter(String str) {
        if (StrUtil.isBlank(str)) {
            return "#";
        }

        char first = str.charAt(0);


        if (first >= 0x4E00 && first <= 0x9FA5) {

            return String.valueOf(Character.toUpperCase(first));
        }


        if ((first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z')) {
            return String.valueOf(Character.toUpperCase(first));
        }

        return "#";
    }







    private String encode(String str) {
        if (StrUtil.isBlank(str)) {
            return "";
        }
        try {
            return java.net.URLEncoder.encode(str, "UTF-8");
        } catch (Exception e) {
            return str;
        }
    }
}

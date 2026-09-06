package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.dto.song.SongVO;
import com.haoran.music.entity.Song;

import java.util.List;





public interface SongService extends IService<Song> {








    SongVO getSongById(Long songId, Long userId);








    IPage<SongVO> pageSongs(PageQuery pageQuery, Long userId);








    IPage<SongVO> getNewSongs(PageQuery pageQuery, Long userId);









    List<SongVO> getHotSongs(String type, Integer limit, Long userId);







    List<SongVO> getPublicSongsByIdsInOrder(List<Long> songIds);








    IPage<SongVO> getFavoriteSongDetails(Long userId, PageQuery pageQuery);









    void recordPlay(Long songId, Long userId, String quality, Long playlistId);








    String getPlayUrl(Long songId, String quality, Long userId);


    String getPreviewUrl(Long songId, Long userId);










    void streamSong(Long songId, String quality, String grant, String range, Long userId,
                    javax.servlet.http.HttpServletResponse response);







    List<SongVO> getRisingSongs(Integer limit);








    List<SongVO> getSongsByGenre(String genre, Integer limit);







    List<SongVO> getNewSongs(Integer limit);
                                                                                                                                                               List<SongVO> getSongsByLanguage(String language, Integer limit);
}

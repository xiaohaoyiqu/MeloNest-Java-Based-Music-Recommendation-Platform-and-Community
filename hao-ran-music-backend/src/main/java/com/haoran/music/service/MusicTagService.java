package com.haoran.music.service;

import com.haoran.music.common.dto.PageResult;
import com.haoran.music.dto.song.SongVO;
import com.haoran.music.vo.tag.MusicTagVO;

import java.util.List;
import java.util.Map;





public interface MusicTagService {






    Map<String, List<MusicTagVO>> getTagList();







    List<MusicTagVO> getHotTags(Integer limit);









    PageResult<SongVO> searchSongsByTags(List<Long> tagIds, Integer page, Integer size);







    List<MusicTagVO> getSongTags(Long songId);







    void addSongTag(Long songId, Long tagId);







    void removeSongTag(Long songId, Long tagId);






    List<MusicTagVO> getUserTagPreference();







    List<SongVO> recommendByTags(Integer limit);







    void updateUserTagPreference(Long userId, Long songId);
}

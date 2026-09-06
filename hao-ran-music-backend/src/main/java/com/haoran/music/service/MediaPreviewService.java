



package com.haoran.music.service;

import com.haoran.music.entity.MV;
import com.haoran.music.entity.Song;





public interface MediaPreviewService {

    String ensureSongPreview(Song song, String sourceUrl);

    String findSongPreview(Long songId);

    String ensureMvPreview(MV mv, String sourceUrl);

    String findMvPreview(Long mvId);
}

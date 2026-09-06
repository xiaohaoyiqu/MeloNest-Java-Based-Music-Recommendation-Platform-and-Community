


package com.haoran.music.service;

import com.haoran.music.entity.Album;
import com.haoran.music.entity.MV;
import com.haoran.music.entity.Playlist;
import com.haoran.music.entity.Song;




public interface ContentAccessService {

    void requireSongMetadataAccess(Song song, Long userId);

    void requireMvMetadataAccess(MV mv, Long userId);

    void requireAlbumMetadataAccess(Album album, Long userId);

    void requirePlaylistMetadataAccess(Playlist playlist, Long userId);

    void requireSongAccess(Song song, Long userId);


    void requireSongPreviewAccess(Song song, Long userId);

    void requireMvAccess(MV mv, Long userId);


    void requireMvPreviewAccess(MV mv, Long userId);

    void requireAlbumAccess(Album album, Long userId);

    void requirePlaylistAccess(Playlist playlist, Long userId);
}

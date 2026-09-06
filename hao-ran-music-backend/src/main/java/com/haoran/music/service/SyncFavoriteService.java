package com.haoran.music.service;

import com.haoran.music.common.result.Result;





public interface SyncFavoriteService {








    Result<Long> syncFavoriteData(Long userId);








    Result<Long> fixFavoritePlaylist(Long userId);






    Result<Integer> updateAllPlaylistSongCount();
}

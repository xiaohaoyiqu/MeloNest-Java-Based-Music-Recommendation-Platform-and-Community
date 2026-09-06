


package com.haoran.music.service.search;

import com.haoran.music.vo.search.SearchResultVO;





public interface SearchEngineAdapter {

    String engineName();

    SearchResultVO search(String keyword, Long userId);

    SearchResultVO searchSongs(String keyword, Long userId, Integer page, Integer size);




    default SearchResultVO searchSongs(String keyword, Long userId, Integer page, Integer size, String field) {
        return searchSongs(keyword, userId, page, size);
    }

    SearchResultVO searchAlbums(String keyword, Long userId, Integer page, Integer size);

    SearchResultVO searchArtists(String keyword, Long userId, Integer page, Integer size);

    SearchResultVO searchPlaylists(String keyword, Long userId, Integer page, Integer size);

    SearchResultVO searchMvs(String keyword, Long userId, Integer page, Integer size);

    SearchResultVO searchUsers(String keyword, Long userId, Integer page, Integer size);
}

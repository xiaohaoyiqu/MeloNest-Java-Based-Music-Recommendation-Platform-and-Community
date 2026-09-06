package com.haoran.music.service.search;

import com.haoran.music.vo.search.SearchResultVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;






@Primary
@Service
public class SearchEngineRouter implements SearchEngineAdapter {

    @Resource
    private MysqlSearchEngineAdapter mysqlAdapter;

    @Resource
    private ElasticsearchSearchEngineAdapter elasticsearchAdapter;

    @Value("${search.engine:mysql}")
    private String engine;

    @Value("${search.elasticsearch.enabled:false}")
    private boolean elasticsearchEnabled;

    @Override
    public String engineName() {
        return useElasticsearch() ? elasticsearchAdapter.engineName() : mysqlAdapter.engineName();
    }

    @Override
    public SearchResultVO search(String keyword, Long userId) {
        return delegate().search(keyword, userId);
    }

    @Override
    public SearchResultVO searchSongs(String keyword, Long userId, Integer page, Integer size) {
        return delegate().searchSongs(keyword, userId, page, size);
    }

    @Override
    public SearchResultVO searchSongs(String keyword, Long userId, Integer page, Integer size, String field) {
        return delegate().searchSongs(keyword, userId, page, size, field);
    }

    @Override
    public SearchResultVO searchAlbums(String keyword, Long userId, Integer page, Integer size) {
        return delegate().searchAlbums(keyword, userId, page, size);
    }

    @Override
    public SearchResultVO searchArtists(String keyword, Long userId, Integer page, Integer size) {
        return delegate().searchArtists(keyword, userId, page, size);
    }

    @Override
    public SearchResultVO searchPlaylists(String keyword, Long userId, Integer page, Integer size) {
        return delegate().searchPlaylists(keyword, userId, page, size);
    }

    @Override
    public SearchResultVO searchMvs(String keyword, Long userId, Integer page, Integer size) {
        return delegate().searchMvs(keyword, userId, page, size);
    }

    @Override
    public SearchResultVO searchUsers(String keyword, Long userId, Integer page, Integer size) {
        return delegate().searchUsers(keyword, userId, page, size);
    }

    private SearchEngineAdapter delegate() {
        return useElasticsearch() ? elasticsearchAdapter : mysqlAdapter;
    }

    private boolean useElasticsearch() {
        return elasticsearchEnabled && "elasticsearch".equalsIgnoreCase(engine);
    }
}

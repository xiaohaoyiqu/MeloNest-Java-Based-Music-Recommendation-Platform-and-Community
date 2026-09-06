




package com.haoran.music.service;

import java.util.Map;




public interface CacheWarmupService {






    Map<String, Object> warmUpAll();






    int warmUpHotSongs();






    int warmUpHotAlbums();






    int warmUpHotArtists();






    int warmUpRecommendations();






    int warmUpRankings();






    Map<String, Object> clearAllCache();






    Map<String, Object> getCacheStatus();
}

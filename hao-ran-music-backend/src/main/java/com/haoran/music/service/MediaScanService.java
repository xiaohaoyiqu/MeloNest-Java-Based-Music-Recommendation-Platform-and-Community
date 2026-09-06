package com.haoran.music.service;

import java.util.List;





public interface MediaScanService {






    int scanAndUpdateMVDuration(int limit);






    int scanAndUpdateSongDuration(int limit);






    int scanSpecificMVs(List<Long> mvIds);






    int scanSpecificSongs(List<Long> songIds);
}

package com.haoran.music.service;






public interface OfficialMediaDerivativeService {









    void submitSongDerivativeJob(Long songId, String sourceUrl, Long sourceSize, Integer sourceQuality);







    void submitMvDerivativeJob(Long mvId, String sourceUrl);






    int retryDueDerivativeJobs();
}

package com.haoran.music.service;

import org.springframework.web.multipart.MultipartFile;





public interface DuplicateCheckService {








    boolean isWorkNameDuplicate(Long userId, String workName);









    boolean isWorkNameDuplicate(Long userId, String workName, Long excludeWorkId);









    String checkFileHashDuplicate(Long userId, MultipartFile file) throws Exception;









    boolean isLyricRequestDuplicate(Long userId, Long songId, String correctedLyric);









    boolean isSongResourceRequestDuplicate(Long userId, Long songId, String resourceType);








    String calculateFileHash(MultipartFile file) throws Exception;
}

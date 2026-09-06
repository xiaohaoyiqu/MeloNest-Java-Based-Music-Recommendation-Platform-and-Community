package com.haoran.music.service;

import com.haoran.music.common.util.VideoCompressUtil;
import com.haoran.music.service.model.Node3DiskUsage;
import com.haoran.music.service.model.Node3MediaInventory;

import java.io.File;






public interface Node3MediaService {








    boolean uploadFile(File localFile, String remoteDir);









    boolean uploadFile(File localFile, String remoteDir, String remoteFileName);







    VideoCompressUtil.VideoInfo probeVideo(String remotePath);












    boolean generateVideoVariant(String inputPath, String outputPath, int targetHeight, int crf,
                                 int minDuration, int maxDuration);









    boolean generateAudioVariant(String inputPath, String outputPath, int bitrateKbps);




    boolean generateAudioPreview(String inputPath, String outputPath, int maxSeconds);




    boolean generateVideoPreview(String inputPath, String outputPath, int maxSeconds);









    boolean generateVideoThumbnail(String inputPath, String outputPath, int timeSeconds);







    boolean exists(String remotePath);








    boolean copyRemoteFile(String inputPath, String outputPath);







    long fileSize(String remotePath);








    Node3MediaInventory inventory(String remoteRoot, int limit);







    Node3DiskUsage diskUsage(String remoteRoot);







    String sha256(String remotePath);






    boolean deleteQuietly(String remotePath);
}

package com.haoran.music.service;

import java.io.File;
import java.io.InputStream;
import java.util.Map;





public interface VirusScanService {








    boolean scanFile(File file) throws Exception;









    boolean scanInputStream(InputStream inputStream, String filename) throws Exception;









    boolean scanBytes(byte[] data, String filename) throws Exception;






    boolean isAvailable();






    String getVersion();






    Map<String, Object> getStatus();
}

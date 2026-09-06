   
                      
                              
   

package com.haoran.music.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;

   
          
                            
   
@Slf4j
public class ImageCompressUtil {

       
                      
                                             
       
    private static final float DEFAULT_QUALITY = 0.85f;

       
                 
       
    private static final int MAX_WIDTH = 1920;

       
                 
       
    private static final int MAX_HEIGHT = 1920;

       
             
      
                              
                               
                           
                               
       
    public static long compressImage(String sourcePath, String targetPath) throws IOException {
        return compressImage(sourcePath, targetPath, DEFAULT_QUALITY);
    }

       
                   
      
                              
                               
                                      
                           
                               
       
    public static long compressImage(String sourcePath, String targetPath, float quality) throws IOException {
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists()) {
            throw new FileNotFoundException("源文件不存在: " + sourcePath);
        }

        long originalSize = sourceFile.length();
        log.debug("event=image_compress_started originalBytes={} quality={}", originalSize, quality);

               
        BufferedImage image = ImageIO.read(sourceFile);
        if (image == null) {
            throw new IOException("无法读取图片文件: " + sourcePath);
        }

                   
        BufferedImage processedImage = image;
        if (image.getWidth() > MAX_WIDTH || image.getHeight() > MAX_HEIGHT) {
            processedImage = scaleImage(image, MAX_WIDTH, MAX_HEIGHT);
            log.debug("event=image_scaled originalWidth={} originalHeight={} targetWidth={} targetHeight={}",
                    image.getWidth(), image.getHeight(),
                    processedImage.getWidth(), processedImage.getHeight());
        }

                  
        String formatName = getImageFormatName(sourcePath);

                
        File targetFile = new File(targetPath);
        targetFile.getParentFile().mkdirs();

        if ("png".equalsIgnoreCase(formatName)) {
                        
            compressPNG(processedImage, targetFile);
        } else {
                              
            compressWithQuality(processedImage, targetFile, formatName, quality);
        }

        long compressedSize = targetFile.length();
        double ratio = (1.0 - (double) compressedSize / originalSize) * 100;

        log.info("event=image_compress_succeeded originalBytes={} compressedBytes={} compressionPercent={}",
                originalSize, compressedSize, ratio);

        return compressedSize;
    }

       
                 
      
                              
                                            
                       
                               
       
    public static byte[] compressImage(byte[] imageData, String formatName) throws IOException {
        return compressImage(imageData, formatName, DEFAULT_QUALITY);
    }

       
                      
      
                              
                             
                             
                       
                               
       
    public static byte[] compressImage(byte[] imageData, String formatName, float quality) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(imageData)) {
            BufferedImage image = ImageIO.read(bis);
            if (image == null) {
                throw new IOException("无法读取图片数据");
            }

                   
            BufferedImage processedImage = image;
            if (image.getWidth() > MAX_WIDTH || image.getHeight() > MAX_HEIGHT) {
                processedImage = scaleImage(image, MAX_WIDTH, MAX_HEIGHT);
            }

                      
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                if ("png".equalsIgnoreCase(formatName)) {
                    ImageIO.write(processedImage, "png", bos);
                } else {
                    compressWithQuality(processedImage, bos, formatName, quality);
                }
                return bos.toByteArray();
            }
        }
    }

       
                  
      
                                
                                
                                
                     
       
    private static BufferedImage scaleImage(BufferedImage originalImage, int maxWidth, int maxHeight) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

                 
        double widthRatio = (double) maxWidth / originalWidth;
        double heightRatio = (double) maxHeight / originalHeight;
        double ratio = Math.min(widthRatio, heightRatio);

        int newWidth = (int) (originalWidth * ratio);
        int newHeight = (int) (originalHeight * ratio);

                   
        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = scaledImage.createGraphics();
        try {
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        } finally {
            g2d.dispose();
        }

        return scaledImage;
    }

       
              
      
                          
                             
                             
                                   
                               
       
    private static void compressWithQuality(BufferedImage image, File outputFile,
                                           String formatName, float quality) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             ImageOutputStream ios = ImageIO.createImageOutputStream(fos)) {

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);
            if (!writers.hasNext()) {
                throw new IOException("不支持的图片格式: " + formatName);
            }

            ImageWriter writer = writers.next();
            try {
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }

                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
        }
    }

       
                  
      
                          
                              
                             
                          
                               
       
    private static void compressWithQuality(BufferedImage image, OutputStream outputStream,
                                           String formatName, float quality) throws IOException {
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream)) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);
            if (!writers.hasNext()) {
                throw new IOException("不支持的图片格式: " + formatName);
            }

            ImageWriter writer = writers.next();
            try {
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }

                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
        }
    }

       
              
      
                          
                             
                               
       
    private static void compressPNG(BufferedImage image, File outputFile) throws IOException {
                           
                    
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            throw new IOException("不支持PNG格式");
        }

        try (FileOutputStream fos = new FileOutputStream(outputFile);
             ImageOutputStream ios = ImageIO.createImageOutputStream(fos)) {

            ImageWriter writer = writers.next();
            try {
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_DEFAULT);
                }

                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
        }
    }

       
               
      
                           
                       
       
    private static String getImageFormatName(String filePath) {
        String extension = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
        if (extension.equals("jpg")) {
            return "jpeg";
        }
        return extension;
    }

       
              
      
                       
                       
       
    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

       
                
                       
      
                                 
                      
       
    public static float getRecommendedQuality(long fileSize) {
        if (fileSize < 100 * 1024) {
                            
            return 0.95f;
        } else if (fileSize < 500 * 1024) {
                                 
            return 0.85f;
        } else if (fileSize < 1024 * 1024) {
                               
            return 0.75f;
        } else {
                           
            return 0.65f;
        }
    }
}

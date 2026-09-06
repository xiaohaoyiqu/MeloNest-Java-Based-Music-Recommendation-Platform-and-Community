




package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.FileUploadUtil;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;






@RestController
@RequestMapping("/file")
public class FileUploadController {

    @Resource
    private FileUploadUtil fileUploadUtil;








    @PostMapping("/upload")
    @ApiLog("上传文件")
    public Result<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        return Result.error(410, "通用上传入口已停用，请使用对应业务的受管上传接口");
    }






    @GetMapping("/config")
    @ApiLog("获取上传配置")
    public Result<Map<String, Object>> getUploadConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("maxFileSize", formatSize(fileUploadUtil.getMaxImageSize()));
        config.put("maxFileSizeBytes", fileUploadUtil.getMaxImageSize());
        config.put("allowedTypes", fileUploadUtil.getAllowedImageTypes());
        config.put("allowedExtensions", fileUploadUtil.getAllowedImageExtensions());
        config.put("compressEnabled", fileUploadUtil.isCompressEnabled());
        config.put("compressThreshold", fileUploadUtil.getCompressThreshold());
        return Result.success(config);
    }



    private String formatSize(long bytes) {
        if (bytes < 1024L * 1024L) {
            return String.format("%.2fKB", bytes / 1024.0);
        }
        return String.format("%.2fMB", bytes / (1024.0 * 1024.0));
    }

}

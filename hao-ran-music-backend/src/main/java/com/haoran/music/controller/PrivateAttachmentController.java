package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.dto.attachment.PrivateAttachmentSessionRequest;
import com.haoran.music.service.PrivateAttachmentService;
import com.haoran.music.vo.attachment.PrivateAttachmentAssetVO;
import com.haoran.music.vo.attachment.PrivateAttachmentDownload;
import com.haoran.music.vo.attachment.PrivateAttachmentGrantVO;
import com.haoran.music.vo.attachment.PrivateAttachmentSessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.nio.charset.StandardCharsets;

   
                   
  
                      
   
@RestController
@RequestMapping("/private-attachments")
@RequiredArgsConstructor
public class PrivateAttachmentController {

    private final PrivateAttachmentService privateAttachmentService;

       
              
      
                              
                            
                   
       
    @PostMapping("/sessions")
    @ApiLog("创建私有附件上传会话")
    public Result<PrivateAttachmentSessionVO> createSession(
            @Valid @RequestBody PrivateAttachmentSessionRequest requestBody,
            HttpServletRequest request) {
        return Result.success(privateAttachmentService.createSession(
                requireUserId(request), requestBody.getPurpose()));
    }

       
                  
      
                               
                       
                            
                     
       
    @PostMapping("/sessions/{sessionToken}/assets")
    @ApiLog("上传私有附件")
    public Result<PrivateAttachmentAssetVO> upload(
            @PathVariable String sessionToken,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        return Result.success(privateAttachmentService.upload(
                requireUserId(request), sessionToken, file));
    }

       
               
      
                               
                            
                  
       
    @DeleteMapping("/sessions/{sessionToken}")
    @ApiLog("取消私有附件上传会话")
    public Result<Void> cancelSession(@PathVariable String sessionToken,
                                      HttpServletRequest request) {
        privateAttachmentService.cancelSession(requireUserId(request), sessionToken);
        return Result.success();
    }

       
                        
      
                          
                            
                   
       
    @PostMapping("/assets/{assetId}/grant")
    @ApiLog("签发私有附件读取授权")
    public Result<PrivateAttachmentGrantVO> issueGrant(@PathVariable Long assetId,
                                                       HttpServletRequest request) {
        return Result.success(privateAttachmentService.issueGrant(assetId, requireUserId(request)));
    }

       
                         
      
                          
                        
                            
                   
       
    @GetMapping("/assets/{assetId}/content")
    @ApiLog("读取私有附件")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable Long assetId,
            @RequestParam(required = false) String grant,
            HttpServletRequest request) {
        Long viewerId = parseUserId(request.getAttribute("userId"));
        PrivateAttachmentDownload download = privateAttachmentService.loadForDownload(assetId, viewerId, grant);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(download.getContentType()))
                .contentLength(download.getFileSize())
                .body(download.getResource());
    }

       
                  
      
                            
                   
       
    private Long requireUserId(HttpServletRequest request) {
        Long userId = parseUserId(request.getAttribute("userId"));
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

       
                     
      
                        
                     
       
    private Long parseUserId(Object value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

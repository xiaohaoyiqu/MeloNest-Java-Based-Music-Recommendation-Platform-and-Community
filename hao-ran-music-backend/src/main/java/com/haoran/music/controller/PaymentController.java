   
                      
                              
   

package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.config.MusicUploadConfig;
import com.haoran.music.common.config.PaymentConfig;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.FileSecurityUtil;
import com.haoran.music.common.util.PaymentSshSessionFactory;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.PaymentOrderService;
import com.haoran.music.service.PaymentProofLifecycleService;
import com.haoran.music.service.VirusScanService;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

   
                 
  
                                                 
   
@Slf4j
@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentOrderService paymentOrderService;
    private final PaymentConfig paymentConfig;
    private final MusicUploadConfig musicUploadConfig;
    private final VirusScanService virusScanService;
    private final PaymentSshSessionFactory paymentSshSessionFactory;
    private final PaymentProofLifecycleService paymentProofLifecycleService;

    public PaymentController(PaymentOrderService paymentOrderService,
                             PaymentConfig paymentConfig,
                             MusicUploadConfig musicUploadConfig,
                             VirusScanService virusScanService,
                             PaymentSshSessionFactory paymentSshSessionFactory,
                             PaymentProofLifecycleService paymentProofLifecycleService) {
        this.paymentOrderService = paymentOrderService;
        this.paymentConfig = paymentConfig;
        this.musicUploadConfig = musicUploadConfig;
        this.virusScanService = virusScanService;
        this.paymentSshSessionFactory = paymentSshSessionFactory;
        this.paymentProofLifecycleService = paymentProofLifecycleService;
    }

       
             
      
                            
                               
                             
                                         
                                           
                                 
                   
  
    @ApiLog("创建支付订单")
    @PostMapping("/create")
    public Result createOrder(HttpServletRequest request,
                            @RequestParam String businessType,
                            @RequestParam Long businessId,
                            @RequestParam(required = false) BigDecimal amount,
                             @RequestParam(required = false) Long payeeId,
                             @RequestParam(required = false) String userRemark,
                             @RequestHeader("Idempotency-Key") String idempotencyKey) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(paymentOrderService.createOrder(userId, businessType,
                businessId, amount, payeeId, userRemark, idempotencyKey));
    }

       
              
      
                          
                    
  
    @ApiLog("获取付款码")
    @GetMapping("/qrcode/{orderId}")
    public Result getOrderQrCode(@PathVariable Long orderId,
                                 @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(paymentOrderService.getOrderQrCode(orderId, userId));
    }

       
             
      
                          
                            
                            
                   
  
    @ApiLog("提交付款凭证")
    @PostMapping("/submit")
    public Result submitPayment(@RequestParam Long orderId,
                              @RequestAttribute(value = "userId", required = false) Long userId,
                              @RequestParam(required = false) String proofUrl,
                              @RequestParam String verifyCode) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(paymentOrderService.submitPayment(orderId, userId, proofUrl, verifyCode));
    }

       
                
      
                                                               
       
    @ApiLog("重新提交付款凭证")
    @PostMapping("/resubmit")
    public Result resubmitPayment(@RequestParam Long orderId,
                                  @RequestAttribute(value = "userId", required = false) Long userId,
                                  @RequestParam(required = false) String proofUrl,
                                  @RequestParam String verifyCode) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(paymentOrderService.submitPayment(orderId, userId, proofUrl, verifyCode));
    }

       
              
      
                                                          
                                          
       
    @ApiLog("上传支付凭证")
    @PostMapping("/proof/upload")
    public Result<String> uploadPaymentProof(@RequestParam("file") MultipartFile file,
                                             @RequestParam Long orderId,
                                             HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        paymentOrderService.getOrderStatus(orderId, userId);
        if (file == null || file.isEmpty()) {
            return Result.error(400, "付款凭证不能为空");
        }
        if (file.getSize() > musicUploadConfig.getImageMaxFileSize()) {
            return Result.error(400, "付款凭证图片过大");
        }

        String contentType = file.getContentType();
        if (!musicUploadConfig.isAllowedImageType(contentType)) {
            return Result.error(400, "付款凭证只支持图片文件");
        }

        File tempFile = null;
        Session session = null;
        ChannelSftp channel = null;
        try {
            String extension = resolveImageExtension(file.getOriginalFilename(), contentType);
            tempFile = File.createTempFile("payment_proof_", extension);
            file.transferTo(tempFile);

            FileSecurityUtil.SecurityCheckResult securityResult = FileSecurityUtil.checkImageSecurity(
                    tempFile,
                    contentType,
                    musicUploadConfig.getImageMaxFileSize(),
                    musicUploadConfig.getAllowedImageTypes(),
                    musicUploadConfig.getImageMaxWidth(),
                    musicUploadConfig.getImageMaxHeight());
            if (!securityResult.isSafe()) {
                return Result.error(400, "付款凭证安全检测失败");
            }

                                        
            if (musicUploadConfig.isVirusScanEnabled()) {
                boolean clean = virusScanService.scanFile(tempFile);
                if (!clean) {
                    return Result.error(400, "payment proof virus scan failed");
                }
            }

            String datePath = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String filename = userId + "_" + orderId
                    + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "")
                    + extension;
            String remoteDir = normalizeDirectory(paymentConfig.getProofPath()) + datePath + "/" + userId + "/";
            String remoteFile = remoteDir + filename;

            session = createPaymentSshSession();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(paymentConfig.getSsh().getConnectTimeout());
            ensureRemoteDirectory(channel, remoteDir);
            channel.put(tempFile.getAbsolutePath(), remoteFile);

            String privateReference = "payment-proof:" + datePath + "/" + userId + "/" + filename;
            try {
                paymentOrderService.bindPaymentProof(orderId, userId, privateReference);
            } catch (RuntimeException e) {
                try {
                    channel.rm(remoteFile);
                } catch (Exception cleanupError) {
                    log.warn("event=payment_proof_compensation_cleanup_failed orderId={} errorType={}",
                            orderId, cleanupError.getClass().getSimpleName());
                }
                throw e;
            }
            log.info("event=payment_proof_upload_completed userId={} orderId={}", userId, orderId);
            return Result.successData("/api/payment/proof/" + orderId);
        } catch (com.haoran.music.common.exception.BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("event=payment_proof_upload_failed userId={} orderId={} errorType={}",
                    userId, orderId, e.getClass().getSimpleName());
            return Result.error(500, "上传付款凭证失败，请稍后重试");
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("event=payment_proof_temp_cleanup_deferred orderId={}", orderId);
            }
        }
    }

       
                                       
       
    @ApiLog("查看付款凭证")
    @GetMapping("/proof/{orderId}")
    public void getPaymentProof(@PathVariable Long orderId,
                                @RequestAttribute(value = "userId", required = false) Long userId,
                                HttpServletResponse response) throws Exception {
        if (userId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return;
        }
        String reference = paymentOrderService.getPaymentProofReference(orderId, userId);
        String relativePath = resolveProofRelativePath(reference);
        String remoteFile = normalizeDirectory(paymentConfig.getProofPath()) + relativePath;

        response.setHeader("Cache-Control", "no-store, private");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType(resolveProofContentType(relativePath));

        Session session = null;
        ChannelSftp channel = null;
        try {
            session = createPaymentSshSession();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(paymentConfig.getSsh().getConnectTimeout());
            channel.get(remoteFile, response.getOutputStream());
            response.flushBuffer();
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

       
                    
      
                          
                              
                           
                                   
                   
  
    @ApiLog("审核支付订单")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/review/{orderId}")
    public Result reviewOrder(@PathVariable Long orderId,
                            @RequestAttribute(value = "userId", required = false) Long reviewerId,
                            @RequestParam Boolean approved,
                            @RequestParam(required = false) String reviewReason) {
        if (reviewerId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(paymentOrderService.reviewOrder(orderId, reviewerId,
                approved, reviewReason));
    }

       
               
      
                            
                             
                                   
                     
                       
                   
  
    @ApiLog("获取我的订单")
    @GetMapping("/my")
    public Result getMyOrders(HttpServletRequest request,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false) String businessType,
                           @RequestParam(defaultValue = "1") Integer page,
                           @RequestParam(defaultValue = "20") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(paymentOrderService.getMyOrders(userId, status, businessType, page, size));
    }

       
                
      
                               
                     
                       
                      
  
    @ApiLog("获取待审核订单")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/pending")
    public Result getPendingOrders(@RequestParam(required = false) Long payeeId,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(paymentOrderService.getPendingOrders(payeeId, page, size));
    }

       
                         
       
    @ApiLog("获取待补发支付订单")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/completion-pending")
    public Result getPendingCompletionOrders(@RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(paymentOrderService.getPendingCompletionOrders(page, size));
    }

       
           
      
                            
                          
                   
  
    @ApiLog("取消订单")
    @PostMapping("/cancel/{orderId}")
    public Result cancelOrder(HttpServletRequest request,
                           @PathVariable Long orderId) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(paymentOrderService.cancelOrder(orderId, userId));
    }

       
                
      
                          
                            
       
    @ApiLog("获取支付订单状态")
    @GetMapping("/{orderId}/status")
    public Result getOrderStatus(@PathVariable Long orderId,
                                 @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(paymentOrderService.getOrderStatus(orderId, userId));
    }

       
                
      
                                           
       
    @ApiLog("获取支付订单统计")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/statistics")
    public Result getOrderStatistics() {
        return Result.success(paymentOrderService.getOrderStatistics());
    }

       
             
      
                          
                   
  
    @ApiLog("获取订单详情")
    @GetMapping("/{orderId}")
    public Result getOrderDetail(@PathVariable Long orderId,
                                 @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(paymentOrderService.getOrderDetail(orderId, userId));
    }

       
              
      
                         
                   
  
    @ApiLog("查询订单")
    @GetMapping("/order/{orderNo}")
    public Result getOrderByNo(@PathVariable String orderNo,
                               @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(paymentOrderService.getOrderByNo(orderNo, userId));
    }

       
                 
      
                                    
                          
       
    @ApiLog("补发支付订单权益")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/retry-completion/{orderId}")
    public Result retryOrderCompletion(@PathVariable Long orderId,
                                       @RequestAttribute(value = "userId", required = false) Long operatorId) {
        if (operatorId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(paymentOrderService.retryOrderCompletion(orderId, operatorId));
    }

       
                             
       
    @ApiLog("获取付款凭证回收状态")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/proof/cleanup/status")
    public Result getPaymentProofCleanupStatus() {
        return Result.success(paymentProofLifecycleService.getStatusSummary());
    }

       
                              
       
    @ApiLog("获取付款凭证回收失败")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/proof/cleanup/failures")
    public Result getPaymentProofCleanupFailures(
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(paymentProofLifecycleService.getRecentFailures(limit));
    }

       
                         
       
    @ApiLog("重试付款凭证回收")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/proof/cleanup/retry/{assetId}")
    public Result retryPaymentProofCleanup(@PathVariable Long assetId) {
        return Result.success(paymentProofLifecycleService.retryFailedCleanup(assetId));
    }

       
                                   
       
    private Session createPaymentSshSession() throws Exception {
        return paymentSshSessionFactory.connect();
    }

       
                                      
       
    private void ensureRemoteDirectory(ChannelSftp channel, String directory) throws Exception {
        String normalized = directory.replace("\\", "/");
        String[] parts = normalized.split("/");
        String current = normalized.startsWith("/") ? "/" : "";

        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            current = "/".equals(current) ? current + part : current + "/" + part;
            try {
                channel.cd(current);
            } catch (Exception e) {
                channel.mkdir(current);
                channel.cd(current);
            }
        }
    }

       
                             
       
    private String normalizeDirectory(String directory) {
        if (directory == null || directory.isEmpty()) {
            return "/sdb1/myprojoct/haoranmusic/song_requests/payment/proof/";
        }
        String normalized = directory.replace("\\", "/");
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

       
                                      
       
    private String resolveProofRelativePath(String reference) {
        String prefix = "payment-proof:";
        if (reference == null || !reference.startsWith(prefix)) {
            throw new IllegalArgumentException("付款凭证引用无效");
        }
        String relativePath = reference.substring(prefix.length());
        if (!relativePath.matches("\\d{8}/\\d+/[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("付款凭证引用无效");
        }
        return relativePath;
    }

       
                             
       
    private String resolveProofContentType(String relativePath) {
        String lower = relativePath.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }

       
                                   
       
    private String resolveImageExtension(String originalFilename, String contentType) {
        String lowerName = originalFilename == null ? "" : originalFilename.toLowerCase();
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return lowerName.endsWith(".jpeg") ? ".jpeg" : ".jpg";
        }
        if (lowerName.endsWith(".png")) {
            return ".png";
        }
        if (lowerName.endsWith(".gif")) {
            return ".gif";
        }
        if (lowerName.endsWith(".webp")) {
            return ".webp";
        }
        if ("image/jpeg".equals(contentType) || "image/jpg".equals(contentType)) {
            return ".jpg";
        }
        if ("image/gif".equals(contentType)) {
            return ".gif";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        return ".png";
    }
}

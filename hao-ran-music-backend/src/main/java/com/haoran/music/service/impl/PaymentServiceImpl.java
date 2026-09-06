   
                      
                      
  
          
                
                     
   

package com.haoran.music.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.haoran.music.common.config.PaymentConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.PaymentOrderStatusUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import java.math.BigDecimal;
import com.haoran.music.service.PaymentService;
import com.haoran.music.service.UserActivityPointsService;
import com.haoran.music.service.UserVipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

   
         
   
@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final VipPurchaseRecordMapper vipPurchaseRecordMapper;
    private final UserMapper userMapper;
    private final UserVipService userVipService;
    private final PaymentConfig paymentConfig;
                                        
    private final DecorationConfigMapper decorationConfigMapper;
    private final UserDecorationMapper userDecorationMapper;

    public PaymentServiceImpl(
            PaymentOrderMapper paymentOrderMapper,
            VipPurchaseRecordMapper vipPurchaseRecordMapper,
            UserMapper userMapper,
            UserVipService userVipService,
            PaymentConfig paymentConfig,
            DecorationConfigMapper decorationConfigMapper,
            UserDecorationMapper userDecorationMapper) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.vipPurchaseRecordMapper = vipPurchaseRecordMapper;
        this.userMapper = userMapper;
        this.userVipService = userVipService;
        this.paymentConfig = paymentConfig;
        this.decorationConfigMapper = decorationConfigMapper;
        this.userDecorationMapper = userDecorationMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createVipOrder(Long userId, Integer months, String paymentType) {
               
        User user = userMapper.selectById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }
        UserAccountStatusUtil.requireCanInteract(user, "创建VIP支付订单");

                 
        if (months < paymentConfig.getVip().getMinMonths() || months > paymentConfig.getVip().getMaxMonths()) {
            throw new BusinessException("\u8d2d\u4e70\u6708\u6570\u5fc5\u987b\u5728" + paymentConfig.getVip().getMinMonths() + "-" + paymentConfig.getVip().getMaxMonths() + "\u4e2a\u6708\u4e4b\u95f4");
        }

                 
        if (!"alipay".equals(paymentType) && !"wechat".equals(paymentType)) {
            throw new BusinessException("不支持的支付类型");
        }

                     
        int amount = paymentConfig.getVip().getPricePerMonth() * months;

                
        String orderNo = generateOrderNo("VIP");

                       
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(paymentConfig.getOrderExpireMinutes());

               
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setPaymentType(paymentType);
        order.setBusinessType("vip");
        order.setAmount(new BigDecimal(amount));
        order.setOrderTitle(months + "个月VIP");
        order.setOrderDesc("购买" + months + "个月VIP会员");
        order.setStatus("pending");
        order.setExpireTime(expireTime);

                 
        Map<String, Object> productInfo = new HashMap<>();
        productInfo.put("months", months);
        productInfo.put("vipDays", months * paymentConfig.getVip().getDaysPerMonth());
        order.setProductInfo(JSON.toJSONString(productInfo));

        paymentOrderMapper.insert(order);

                 
        Map<String, Object> paymentInfo = new HashMap<>();
        paymentInfo.put("orderNo", orderNo);
        paymentInfo.put("orderAmount", amount);
        paymentInfo.put("orderTitle", months + "个月VIP");
        paymentInfo.put("months", months);
        paymentInfo.put("pricePerMonth", paymentConfig.getVip().getPricePerMonth());

        if ("alipay".equals(paymentType)) {
                              
            String payUrl = buildAlipayPayUrl(orderNo, months + "个月VIP", amount);
            paymentInfo.put("payUrl", payUrl);
            paymentInfo.put("qrCodeUrl", payUrl);
        } else {
                            
            Map<String, String> wechatPayParams = buildWechatPayParams(orderNo, months + "个月VIP", amount);
            paymentInfo.put("codeUrl", wechatPayParams.get("codeUrl"));
            paymentInfo.put("prepayId", wechatPayParams.get("prepayId"));
        }

        log.info("创建VIP订单: userId={}, months={}, orderNo={}, amount={}",
                userId, months, orderNo, amount);

        return paymentInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createDecorationOrder(Long userId, String decorationId, String paymentType) {
               
        User user = userMapper.selectById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }
        UserAccountStatusUtil.requireCanInteract(user, "创建装饰支付订单");

                 
        DecorationConfig config = getDecorationConfig(decorationId);
        if (config == null) {
            throw new BusinessException("装饰不存在");
        }

                   
        if (config.getIsEnabled() == null || config.getIsEnabled() != 1) {
            throw new BusinessException("装饰暂不可用");
        }

                  
        if (hasDecoration(userId, decorationId)) {
            throw new BusinessException("已拥有该装饰");
        }

                 
        if (!"alipay".equals(paymentType) && !"wechat".equals(paymentType)) {
            throw new BusinessException("不支持的支付类型");
        }

                                    
        Integer amount = config.getCashPrice();
        if (amount == null || amount <= 0) {
            throw new BusinessException("该装饰不支持现金购买，请使用活跃值兑换");
        }

                
        String orderNo = generateOrderNo("DEC");

                       
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(paymentConfig.getOrderExpireMinutes());

               
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setPaymentType(paymentType);
        order.setBusinessType("decoration");
        order.setAmount(new BigDecimal(amount));
        order.setOrderTitle(config.getDecorationName());
        order.setOrderDesc("购买装饰：" + config.getDecorationName());
        order.setStatus("pending");
        order.setExpireTime(expireTime);

                 
        Map<String, Object> productInfo = new HashMap<>();
        productInfo.put("decorationId", decorationId);
        productInfo.put("decorationType", config.getDecorationType());
        order.setProductInfo(JSON.toJSONString(productInfo));

        paymentOrderMapper.insert(order);

                 
        Map<String, Object> paymentInfo = new HashMap<>();
        paymentInfo.put("orderNo", orderNo);
        paymentInfo.put("orderAmount", amount);
        paymentInfo.put("orderTitle", config.getDecorationName());
        paymentInfo.put("decorationId", decorationId);
        paymentInfo.put("decorationType", config.getDecorationType());

        if ("alipay".equals(paymentType)) {
            String payUrl = buildAlipayPayUrl(orderNo, config.getDecorationName(), amount);
            paymentInfo.put("payUrl", payUrl);
            paymentInfo.put("qrCodeUrl", payUrl);
        } else {
            Map<String, String> wechatPayParams = buildWechatPayParams(orderNo, config.getDecorationName(), amount);
            paymentInfo.put("codeUrl", wechatPayParams.get("codeUrl"));
            paymentInfo.put("prepayId", wechatPayParams.get("prepayId"));
        }

        log.info("创建装饰订单: userId={}, decorationId={}, orderNo={}, amount={}",
                userId, decorationId, orderNo, amount);

        return paymentInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleAlipayNotify(Map<String, String> params) {
        String orderNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        String tradeNo = params.get("trade_no");

        log.info("收到支付宝回调: orderNo={}, tradeStatus={}", orderNo, tradeStatus);

        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            return "failure";
        }

               
        try {
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    paymentConfig.getAlipay().getPublicKey(),
                    "UTF-8",
                    "RSA2"
            );
            if (!signVerified) {
                log.warn("支付宝签名验证失败: orderNo={}", orderNo);
                return "failure";
            }
        } catch (Exception e) {
            log.error("支付宝签名验证异常: orderNo={}, error={}", orderNo, e.getClass().getSimpleName());
            return "failure";
        }

               
        PaymentOrder order = getOrderByNo(orderNo);
        if (order == null) {
            log.warn("订单不存在: orderNo={}", orderNo);
            return "failure";
        }

                 
        if (isPaymentHandled(order)) {
            return "success";        
        }

        if (holdPaymentForManualRefund(order, tradeNo, JSON.toJSONString(params))) {
            return "success";
        }

               
        if (!markPaymentPaid(order, tradeNo, JSON.toJSONString(params))) {
            return "success";
        }
        deliverGoods(order);

        return "success";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleWechatNotify(String xmlData) {
        log.info("收到微信支付回调");

        try {
                    
            Map<String, String> params = parseXmlToMap(xmlData);

            String orderNo = params.get("out_trade_no");
            String returnCode = params.get("return_code");
            String resultCode = params.get("result_code");

            if (!"SUCCESS".equals(returnCode)) {
                log.warn("微信支付回调失败: returnCode={}", returnCode);
                return buildWechatXml("FAIL", "回调处理失败");
            }

            if (!"SUCCESS".equals(resultCode)) {
                log.warn("微信支付订单失败: orderNo={}, err_code={}",
                        orderNo, params.get("err_code"));
                return buildWechatXml("SUCCESS", "OK");
            }

                   
            String sign = params.get("sign");
            if (!verifyWechatSign(params, sign)) {
                log.warn("微信签名验证失败: orderNo={}", orderNo);
                return buildWechatXml("FAIL", "签名验证失败");
            }

                   
            PaymentOrder order = getOrderByNo(orderNo);
            if (order == null) {
                log.warn("订单不存在: orderNo={}", orderNo);
                return buildWechatXml("FAIL", "订单不存在");
            }

                     
            if (isPaymentHandled(order)) {
                return buildWechatXml("SUCCESS", "OK");
            }

            if (holdPaymentForManualRefund(order, params.get("transaction_id"), xmlData)) {
                return buildWechatXml("SUCCESS", "OK");
            }

                   
            if (!markPaymentPaid(order, params.get("transaction_id"), xmlData)) {
                return buildWechatXml("SUCCESS", "OK");
            }
            deliverGoods(order);

            log.info("微信支付订单处理成功: orderNo={}", orderNo);
            return buildWechatXml("SUCCESS", "OK");

        } catch (Exception e) {
            log.error("微信支付回调处理异常: {}", e.getClass().getSimpleName());
            return buildWechatXml("FAIL", "处理异常");
        }
    }

    @Override
    public Map<String, Object> queryOrderStatus(String orderNo) {
        PaymentOrder order = getOrderByNo(orderNo);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("status", order.getStatus());
        result.put("orderAmount", order.getAmount());
        result.put("orderType", order.getBusinessType());
        result.put("createTime", order.getCreateTime());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancelOrder(String orderNo) {
        PaymentOrder order = getOrderByNo(orderNo);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

                    
        if (!"pending".equals(order.getStatus())) {
            throw new BusinessException("订单状态不允许取消");
        }

        order.setStatus("cancelled");
        paymentOrderMapper.updateById(order);

        log.info("取消订单: orderNo={}", orderNo);
        return true;
    }

    @Override
    public Boolean verifySign(Map<String, String> params, String sign, String paymentType) {
        if ("alipay".equals(paymentType)) {
            return verifyAlipaySign(params, sign);
        } else if ("wechat".equals(paymentType)) {
            return verifyWechatSign(params, sign);
        }
        return false;
    }

    @Override
    public Map<Integer, VipPriceInfo> getVipPriceConfig() {
        Map<Integer, VipPriceInfo> config = new HashMap<>();
        for (int months = 1; months <= paymentConfig.getVip().getMaxMonths(); months++) {
            int price = paymentConfig.getVip().getPricePerMonth() * months;
            config.put(months, new VipPriceInfo(
                    months,
                    months + "个月VIP",
                    months * paymentConfig.getVip().getDaysPerMonth(),
                    price,             
                    price,                    
                    0                       
            ));
        }
        return config;
    }

                                                     

       
           
       
    private void deliverGoods(PaymentOrder order) {
        if (!UserAccountStatusUtil.canInteract(order.getUserId(), userMapper::selectById)) {
            log.warn("支付成功但账号状态不可用，暂不发放权益: orderId={}, userId={}, businessType={}",
                    order.getId(), order.getUserId(), order.getBusinessType());
            return;
        }

        String orderType = order.getBusinessType();
        Map<String, Object> productInfo = parseProductInfo(order.getProductInfo());

        if ("vip".equals(orderType)) {
                    
            Integer months = (Integer) productInfo.get("months");
            Integer vipDays = (Integer) productInfo.get("vipDays");

            try {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime previousExpireTime = userVipService
                        .getActiveVipExpirations(java.util.Collections.singleton(order.getUserId()))
                        .get(order.getUserId());
                LocalDateTime startTime = previousExpireTime != null && previousExpireTime.isAfter(now)
                        ? previousExpireTime : now;
                userVipService.grantVip(order.getUserId(), 1, vipDays, "payment");

                         
                VipPurchaseRecord record = new VipPurchaseRecord();
                record.setUserId(order.getUserId());
                record.setOrderId(order.getId());
                record.setVipDays(vipDays);
                LocalDateTime endTime = userVipService
                        .getActiveVipExpirations(java.util.Collections.singleton(order.getUserId()))
                        .get(order.getUserId());
                if (endTime == null) {
                    throw new BusinessException("VIP权益写入后无法读取");
                }
                record.setStartTime(startTime);
                record.setEndTime(endTime);
                vipPurchaseRecordMapper.insert(record);

                log.info("VIP发放成功: userId={}, months={}, vipDays={}",
                        order.getUserId(), months, vipDays);
            } catch (Exception e) {
                log.error("VIP发放失败: userId={}, error={}", order.getUserId(), e.getClass().getSimpleName());
                throw new BusinessException("VIP发放失败");
            }

        } else if ("decoration".equals(orderType)) {
                   
            String decorationId = (String) productInfo.get("decorationId");

            try {
                DecorationConfig config = getDecorationConfig(decorationId);
                UserDecoration userDecoration = new UserDecoration();
                userDecoration.setUserId(order.getUserId());
                userDecoration.setDecorationType(config.getDecorationType());
                userDecoration.setDecorationId(decorationId);
                userDecoration.setDecorationName(config.getDecorationName());
                userDecoration.setIsEquipped(0);
                userDecoration.setObtainTime(LocalDateTime.now());
                userDecoration.setSource("payment");
                userDecoration.setRarity(config.getRarity());

                               
                if (config.getIsPermanent() == 0 && config.getDurationDays() != null) {
                    userDecoration.setExpireTime(LocalDateTime.now().plusDays(config.getDurationDays()));
                }

                userDecorationMapper.insert(userDecoration);

                log.info("装饰发放成功: userId={}, decorationId={}",
                        order.getUserId(), decorationId);
            } catch (Exception e) {
                log.error("装饰发放失败: userId={}, decorationId={}, error={}",
                        order.getUserId(), decorationId, e.getClass().getSimpleName());
                throw new BusinessException("装饰发放失败");
            }
        }
    }

    private boolean isPaymentHandled(PaymentOrder order) {
        if (order == null) {
            return true;
        }
        return PaymentOrderStatusUtil.isPaymentHandled(order.getStatus());
    }

    private boolean holdPaymentForManualRefund(PaymentOrder order, String transactionId, String notifyContent) {
        if (order == null || UserAccountStatusUtil.canInteract(order.getUserId(), userMapper::selectById)) {
            return false;
        }

        order.setStatus("refunding");
        order.setTransactionId(transactionId);
        order.setNotifyTime(LocalDateTime.now());
        order.setNotifyContent(notifyContent);
        paymentOrderMapper.updateById(order);
        log.warn("支付成功但账号已异常，订单转入退款处理: orderId={}, userId={}",
                order.getId(), order.getUserId());
        return true;
    }

    private boolean markPaymentPaid(PaymentOrder order, String transactionId, String notifyContent) {
        LambdaUpdateWrapper<PaymentOrder> transition = new LambdaUpdateWrapper<>();
        transition.eq(PaymentOrder::getId, order.getId())
                .in(PaymentOrder::getStatus, Arrays.asList("pending", "submitted"))
                .set(PaymentOrder::getStatus, "paid")
                .set(PaymentOrder::getTransactionId, transactionId)
                .set(PaymentOrder::getNotifyTime, LocalDateTime.now())
                .set(PaymentOrder::getNotifyContent, notifyContent)
                .set(PaymentOrder::getUpdateTime, LocalDateTime.now());
        return paymentOrderMapper.update(null, transition) == 1;
    }

       
            
       
    private String generateOrderNo(String prefix) {
        return prefix + System.currentTimeMillis() + (int)(Math.random() * 10000);
    }

       
                 
       
    private String buildAlipayPayUrl(String orderNo, String subject, int amount) {
        try {
                       
            AlipayClient alipayClient = new DefaultAlipayClient(
                    paymentConfig.getAlipay().getGateway(),
                    paymentConfig.getAlipay().getAppId(),
                    paymentConfig.getAlipay().getPrivateKey(),
                    "json",
                    "UTF-8",
                    paymentConfig.getAlipay().getPublicKey(),
                    "RSA2"
            );

                     
            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            request.setBizContent("{" +
                    "\"out_trade_no\":\"" + orderNo + "\"," +
                    "\"total_amount\":\"" + (amount / 100.0) + "\"," +
                    "\"subject\":\"" + subject + "\"," +
                    "\"timeout_express\":\"30m\"," +
                    "\"store_id\":\"haoran_music\"" +
                    "}");

                   
            AlipayTradePrecreateResponse response = alipayClient.execute(request);

            if (response.isSuccess()) {
                return response.getQrCode();
            } else {
                log.error("event=alipay_payment_url_build_failed");
                return paymentConfig.getAlipay().getGateway() + "?orderNo=" + orderNo;
            }
        } catch (AlipayApiException e) {
            log.error("event=alipay_sdk_call_failed errorType={}", e.getClass().getSimpleName());
                                  
            return String.format("%s?orderNo=%s&subject=%s&amount=%d",
                    paymentConfig.getAlipay().getGateway(), orderNo, subject, amount);
        }
    }

       
               
       
    private Map<String, String> buildWechatPayParams(String orderNo, String subject, int amount) {
        Map<String, String> params = new HashMap<>();

        try {
                     
            Map<String, Object> data = new HashMap<>();
            data.put("appid", paymentConfig.getWechat().getAppId());
            data.put("mch_id", paymentConfig.getWechat().getMchId());
            data.put("nonce_str", generateNonceStr());
            data.put("body", subject);
            data.put("out_trade_no", orderNo);
            data.put("total_fee", amount);
            data.put("spbill_create_ip", paymentConfig.getWechat().getClientIp());
            data.put("notify_url", paymentConfig.getWechat().getNotifyUrl());
            data.put("trade_type", "NATIVE");

                   
            String sign = generateWechatSign(data);
            data.put("sign", sign);

                     
            String xml = mapToXml(data);

                                       
            params.put("codeUrl", "weixin://wxpay/bizpayurl?pr=pending");
            params.put("prepayId", "pending");

            log.info("微信支付参数构建完成: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("微信支付参数构建异常: {}", e.getClass().getSimpleName());
            params.put("codeUrl", "weixin://wxpay/bizpayurl?pr=error");
            params.put("prepayId", "error");
        }

        return params;
    }

       
              
       
    private boolean verifyAlipaySign(Map<String, String> params, String sign) {
        try {
            return AlipaySignature.rsaCheckV1(
                    params,
                    paymentConfig.getAlipay().getPublicKey(),
                    "UTF-8",
                    "RSA2"
            );
        } catch (Exception e) {
            log.error("支付宝签名验证异常: {}", e.getClass().getSimpleName());
            return false;
        }
    }

       
             
       
    private boolean verifyWechatSign(Map<String, String> params, String sign) {
        try {
                       
            Map<String, String> data = new HashMap<>(params);
            data.remove("sign");

                     
            List<String> keys = new ArrayList<>(data.keySet());
            Collections.sort(keys);

                    
            StringBuilder sb = new StringBuilder();
            for (String key : keys) {
                if (data.get(key) != null && !"".equals(data.get(key))) {
                    sb.append(key).append("=").append(data.get(key)).append("&");
                }
            }
            sb.append("key=").append(paymentConfig.getWechat().getApiKey());

                    
            String calculatedSign = md5(sb.toString()).toUpperCase();

            return sign.equals(calculatedSign);
        } catch (Exception e) {
            log.error("微信签名验证异常: {}", e.getClass().getSimpleName());
            return false;
        }
    }

       
             
       
    private String generateWechatSign(Map<String, Object> params) {
        try {
            List<String> keys = new ArrayList<>(params.keySet());
            Collections.sort(keys);

            StringBuilder sb = new StringBuilder();
            for (String key : keys) {
                if (params.get(key) != null && !"".equals(params.get(key))) {
                    sb.append(key).append("=").append(params.get(key)).append("&");
                }
            }
            sb.append("key=").append(paymentConfig.getWechat().getApiKey());

            return md5(sb.toString()).toUpperCase();
        } catch (Exception e) {
            log.error("生成微信签名异常: {}", e.getClass().getSimpleName());
            return "";
        }
    }

       
            
       
    private String md5(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(str.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("MD5加密异常: {}", e.getClass().getSimpleName());
            return "";
        }
    }

       
              
       
    private String generateNonceStr() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }

       
              
       
                                                                                                                             private String buildWechatXml(String returnCode, String returnMsg) {        return "<xml><return_code><![CDATA[" + returnCode + "]]></return_code>"               + "<return_msg><![CDATA[" + returnMsg + "]]></return_msg></xml>";    }
    private String mapToXml(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("<xml>");
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            sb.append("<").append(entry.getKey()).append(">")
              .append("<![CDATA[").append(entry.getValue()).append("]]>")
              .append("</").append(entry.getKey()).append(">");
        }
        sb.append("</xml>");
        return sb.toString();
    }

       
              
       
       
              
                             
       
    private Map<String, String> parseXmlToMap(String xml) {
        Map<String, String> map = new HashMap<>();
        if (StrUtil.isBlank(xml)) {
            return map;
        }

        try {
                                
                                                                   
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<([^>]+)>(.*?)</\1>");
            java.util.regex.Matcher matcher = pattern.matcher(xml);

            while (matcher.find()) {
                String key = matcher.group(1);
                String value = matcher.group(2);

                          
                if (value.contains("<![CDATA[")) {
                    value = value.replaceAll("<!\\[CDATA\\[(.*?)\\]\\]>", "$1");
                }

                             
                if (StrUtil.isNotBlank(key) && !"xml".equals(key)) {
                    map.put(key.trim(), value.trim());
                }
            }
        } catch (Exception e) {
            log.error("解析XML失败: {}", xml);
                      
            return parseXmlToMapSimple(xml);
        }

        return map;
    }

       
                     
       
    private Map<String, String> parseXmlToMapSimple(String xml) {
        Map<String, String> map = new HashMap<>();
        String[] tags = xml.split("<");
        for (String tag : tags) {
            if (tag.startsWith("xml>") || tag.endsWith("/xml>")) continue;
            int endIdx = tag.indexOf(">");
            if (endIdx > 0) {
                String key = tag.substring(0, endIdx);
                int valueStart = tag.indexOf("<![CDATA[");
                int valueEnd = tag.indexOf("]]>");
                if (valueStart > 0 && valueEnd > 0) {
                    String value = tag.substring(valueStart + 9, valueEnd);
                    if (StrUtil.isNotBlank(key)) {
                        map.put(key.trim(), value.trim());
                    }
                }
            }
        }
        return map;
    }

       
             
       
    private DecorationConfig getDecorationConfig(String decorationId) {
        LambdaQueryWrapper<DecorationConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DecorationConfig::getDecorationId, decorationId)
                .eq(DecorationConfig::getDeleted, 0);
        return decorationConfigMapper.selectOne(wrapper);
    }

       
               
                    
                               
                   
       
    private boolean hasDecoration(Long userId, String decorationId) {
        LambdaQueryWrapper<UserDecoration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDecoration::getUserId, userId)
                .eq(UserDecoration::getDecorationId, decorationId)
                .eq(UserDecoration::getDeleted, 0);
        Long count = userDecorationMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

       
           
       
    private PaymentOrder getOrderByNo(String orderNo) {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentOrder::getOrderNo, orderNo)
                .eq(PaymentOrder::getDeleted, 0);
        return paymentOrderMapper.selectOne(wrapper);
    }

       
             
       
    private Map<String, Object> parseProductInfo(String productInfoJson) {
        try {
            if (productInfoJson == null || productInfoJson.isEmpty()) {
                return new HashMap<>();
            }
            return JSON.parseObject(productInfoJson, Map.class);
        } catch (Exception e) {
            log.error("解析商品信息失败: {}", e.getClass().getSimpleName());
            return new HashMap<>();
        }
    }
}

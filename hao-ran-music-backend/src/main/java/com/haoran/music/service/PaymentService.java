








package com.haoran.music.service;

import java.util.Map;




public interface PaymentService {









    Map<String, Object> createVipOrder(Long userId, Integer months, String paymentType);









    Map<String, Object> createDecorationOrder(Long userId, String decorationId, String paymentType);







    String handleAlipayNotify(Map<String, String> params);







    String handleWechatNotify(String xmlData);







    Map<String, Object> queryOrderStatus(String orderNo);







    Boolean cancelOrder(String orderNo);









    Boolean verifySign(Map<String, String> params, String sign, String paymentType);






    Map<Integer, VipPriceInfo> getVipPriceConfig();




    class VipPriceInfo {
        private Integer level;               
        private String name;               
        private Integer days;                        
        private Integer alipayPrice;              
        private Integer wechatPrice;             
        private Integer pointsPrice;                     

        public VipPriceInfo(Integer level, String name, Integer days,
                           Integer alipayPrice, Integer wechatPrice, Integer pointsPrice) {
            this.level = level;
            this.name = name;
            this.days = days;
            this.alipayPrice = alipayPrice;
            this.wechatPrice = wechatPrice;
            this.pointsPrice = pointsPrice;
        }

        public Integer getLevel() { return level; }
        public String getName() { return name; }
        public Integer getDays() { return days; }
        public Integer getAlipayPrice() { return alipayPrice; }
        public Integer getWechatPrice() { return wechatPrice; }
        public Integer getPointsPrice() { return pointsPrice; }

        public void setLevel(Integer level) { this.level = level; }
        public void setName(String name) { this.name = name; }
        public void setDays(Integer days) { this.days = days; }
        public void setAlipayPrice(Integer alipayPrice) { this.alipayPrice = alipayPrice; }
        public void setWechatPrice(Integer wechatPrice) { this.wechatPrice = wechatPrice; }
        public void setPointsPrice(Integer pointsPrice) { this.pointsPrice = pointsPrice; }
    }
}

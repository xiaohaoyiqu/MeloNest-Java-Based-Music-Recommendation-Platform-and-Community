




package com.haoran.music.service;

import javax.servlet.http.HttpServletRequest;










public interface CrawlerDetectionService {








    boolean isCrawler(HttpServletRequest request, Long userId);









    void recordNormalBehavior(HttpServletRequest request, Long userId, String action);







    boolean isIpBanned(String ip);








    void banIp(String ip, int durationHours, String reason);







    CrawlerDetectionResult detectAndGetDetails(HttpServletRequest request, Long userId);




    class CrawlerDetectionResult {
        private boolean isCrawler;
        private String reason;
        private int riskScore;               
        private String[] riskFactors;

        public CrawlerDetectionResult(boolean isCrawler, String reason, int riskScore, String[] riskFactors) {
            this.isCrawler = isCrawler;
            this.reason = reason;
            this.riskScore = riskScore;
            this.riskFactors = riskFactors;
        }

        public boolean isCrawler() { return isCrawler; }
        public String getReason() { return reason; }
        public int getRiskScore() { return riskScore; }
        public String[] getRiskFactors() { return riskFactors; }
    }
}

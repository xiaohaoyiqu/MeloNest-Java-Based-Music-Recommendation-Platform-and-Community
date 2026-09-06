package com.haoran.music.common.util;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.text.Normalizer;
import java.util.regex.Pattern;

   
                      
                                                
   
@Slf4j
public class SecurityCheckUtil {

       
                
       
    private static final Pattern[] XSS_PATTERNS = {
        Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onload\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onerror\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onclick\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onmouseover\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onfocus\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onblur\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<iframe[^>]*>.*?</iframe>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("<object[^>]*>.*?</object>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("<embed[^>]*>.*?</embed>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("expression\\s*\\(", Pattern.CASE_INSENSITIVE)
    };

       
                
       
    private static final Pattern[] SQL_INJECTION_PATTERNS = {
        Pattern.compile("--", Pattern.CASE_INSENSITIVE),
        Pattern.compile(";--", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/\\*.*?\\*/", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("union\\s+select", Pattern.CASE_INSENSITIVE),
        Pattern.compile("select\\s+.+\\s+from", Pattern.CASE_INSENSITIVE),
        Pattern.compile("insert\\s+into", Pattern.CASE_INSENSITIVE),
        Pattern.compile("delete\\s+from", Pattern.CASE_INSENSITIVE),
        Pattern.compile("update\\s+.+\\s+set", Pattern.CASE_INSENSITIVE),
        Pattern.compile("drop\\s+table", Pattern.CASE_INSENSITIVE),
        Pattern.compile("exec\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("execute\\s*\\(", Pattern.CASE_INSENSITIVE)
    };

                                          
    private static final Pattern EXTERNAL_LINK_PATTERN = Pattern.compile(
            "(?i)(?:https?://|www\\.|(?:[a-z0-9-]+\\.)+(?:com|cn|net|org|io|cc|tv|me|vip|top|xyz)(?:/|\\b))");

                                      
    private static final Pattern CONTACT_HINT_PATTERN = Pattern.compile(
            "(?i)(?:微信|weixin|wx|v信|qq|群号|q群|telegram|tg|电报|扫码|二维码|联系方式|联系我|加我|私聊我)");

                              
    private static final Pattern CONTACT_NUMBER_PATTERN = Pattern.compile(
            "(?<!\\d)(?:1[3-9]\\d{9}|[1-9]\\d{5,11})(?!\\d)");

                                   
    private static final Pattern PROMOTION_HINT_PATTERN = Pattern.compile(
            "(?i)(?:推广|引流|代理|兼职|返利|刷单|贷款|办证|代购彩票|赚钱|福利群|优惠码|代购|加群领|招募)");

       
                 
       
    private static final Pattern[] NAME_ILLEGAL_PATTERNS = {
        Pattern.compile("<script", Pattern.CASE_INSENSITIVE),
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE)
    };

       
                             
       
    private static final Pattern NAME_VALID_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5a-zA-Z0-9\\s\\-_\\.()\\[\\]【】（）·～]+$");

       
             
       
    private static final int MIN_NAME_LENGTH = 1;

       
             
       
    private static final int MAX_NAME_LENGTH = 100;

       
           
       
    public static class CheckResult {
        private boolean safe;
        private String message;
        private String cleanedValue;

        public CheckResult(boolean safe, String message, String cleanedValue) {
            this.safe = safe;
            this.message = message;
            this.cleanedValue = cleanedValue;
        }

        public boolean isSafe() {
            return safe;
        }

        public String getMessage() {
            return message;
        }

        public String getCleanedValue() {
            return cleanedValue;
        }

        public static CheckResult success(String cleanedValue) {
            return new CheckResult(true, "检测通过", cleanedValue);
        }

        public static CheckResult fail(String message) {
            return new CheckResult(false, message, null);
        }
    }

       
                    
      
                         
                        
       
    public static boolean containsXSS(String input) {
        if (StrUtil.isBlank(input)) {
            return false;
        }

        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("event=security_input_rejected category={}", "xss");
                return true;
            }
        }

        return false;
    }

       
                    
      
                         
                        
       
    public static boolean containsSQLInjection(String input) {
        if (StrUtil.isBlank(input)) {
            return false;
        }

        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("event=security_input_rejected category={}", "sql_injection");
                return true;
            }
        }

        return false;
    }

       
              
      
                     
                                                   
                   
       
    public static CheckResult checkName(String name, String fieldDescription) {
        if (StrUtil.isBlank(name)) {
            return CheckResult.fail(fieldDescription + "不能为空");
        }

               
        int length = name.trim().length();
        if (length < MIN_NAME_LENGTH) {
            return CheckResult.fail(fieldDescription + "长度不能小于" + MIN_NAME_LENGTH + "个字符");
        }
        if (length > MAX_NAME_LENGTH) {
            return CheckResult.fail(fieldDescription + "长度不能超过" + MAX_NAME_LENGTH + "个字符");
        }

                 
        for (Pattern pattern : NAME_ILLEGAL_PATTERNS) {
            if (pattern.matcher(name).find()) {
                return CheckResult.fail(fieldDescription + "包含非法字符");
            }
        }

                    
        if (!NAME_VALID_PATTERN.matcher(name).matches()) {
            return CheckResult.fail(fieldDescription + "包含不支持的字符，仅支持中文、英文、数字及常见符号");
        }

                
        if (containsXSS(name)) {
            return CheckResult.fail(fieldDescription + "包含非法脚本代码");
        }

        return CheckResult.success(name.trim());
    }

       
               
      
                          
                   
       
    public static CheckResult checkUsername(String username) {
        return checkName(username, "用户名");
    }

       
                
      
                           
                   
       
    public static CheckResult checkSongName(String songName) {
        return checkName(songName, "歌曲名称");
    }

       
                
      
                             
                   
       
    public static CheckResult checkArtistName(String artistName) {
        return checkName(artistName, "歌手名称");
    }

       
                
      
                            
                   
       
    public static CheckResult checkAlbumName(String albumName) {
        return checkName(albumName, "专辑名称");
    }

       
              
      
                      
                   
       
    public static CheckResult checkTitle(String title) {
        return checkName(title, "标题");
    }

       
                
      
                              
                   
       
    public static CheckResult checkDescription(String description) {
        if (StrUtil.isBlank(description)) {
            return CheckResult.success("");
        }

                       
        if (description.length() > 1000) {
            return CheckResult.fail("描述内容过长，不能超过1000个字符");
        }

                
        if (containsXSS(description)) {
            return CheckResult.fail("描述内容包含非法脚本代码");
        }

        return CheckResult.success(description.trim());
    }

       
                                
                                          
       
    public static CheckResult checkCommunityText(String content, String fieldDescription) {
        CheckResult basicCheck = checkDescription(content);
        if (!basicCheck.isSafe()) {
            return basicCheck;
        }

        String normalized = Normalizer.normalize(basicCheck.getCleanedValue(), Normalizer.Form.NFKC)
                .replaceAll("[\\s\\u200B-\\u200D\\uFEFF]", "")
                .toLowerCase();
        if (normalized.isEmpty()) {
            return CheckResult.success("");
        }
        if (EXTERNAL_LINK_PATTERN.matcher(normalized).find()) {
            return CheckResult.fail(fieldDescription + "不支持站外链接，请使用平台内分享功能");
        }

        boolean containsContactHint = CONTACT_HINT_PATTERN.matcher(normalized).find();
        boolean containsContactNumber = CONTACT_NUMBER_PATTERN.matcher(normalized).find();
        boolean containsPromotionHint = PROMOTION_HINT_PATTERN.matcher(normalized).find();
        if ((containsContactHint && containsContactNumber)
                || (containsContactHint && containsPromotionHint)) {
            return CheckResult.fail(fieldDescription + "疑似包含广告导流信息，请移除站外联络方式");
        }
        return CheckResult.success(basicCheck.getCleanedValue());
    }

       
                    
      
                         
                      
       
    public static String escapeHtml(String input) {
        if (input == null) {
            return null;
        }

        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;")
                   .replace("/", "&#x2F;");
    }

       
                      
      
                         
                      
       
    public static String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }

                 
        String cleaned = input.replaceAll("[\\x00-\\x1F\\x7F]", "");

                      
        cleaned = cleaned.replace("'", "")
                       .replace("\"", "")
                       .replace("--", "")
                       .replace("/*", "")
                       .replace("*/", "")
                       .replace(";", "");

        return cleaned.trim();
    }

       
               
      
                          
                   
       
    public static CheckResult checkFileName(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return CheckResult.fail("文件名不能为空");
        }

                 
        if (fileName.contains("..") || fileName.contains("./") || fileName.contains(".\\")) {
            return CheckResult.fail("文件名包含非法路径字符");
        }

                 
        if (fileName.contains("/") && fileName.startsWith("/")) {
            return CheckResult.fail("不允许使用绝对路径");
        }

                        
        if (fileName.matches("^[A-Za-z]:.*")) {
            return CheckResult.fail("文件名包含非法路径");
        }

        return CheckResult.success(fileName);
    }

       
               
      
                       
                   
       
    public static CheckResult checkUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return CheckResult.success("");
        }

                
        if (containsXSS(url)) {
            return CheckResult.fail("URL包含非法脚本代码");
        }

                         
        if (url.toLowerCase().startsWith("javascript:")) {
            return CheckResult.fail("不允许使用javascript协议");
        }

                            
        if (url.toLowerCase().startsWith("data:")) {
            return CheckResult.fail("不允许使用data协议");
        }

                       
        if (url.toLowerCase().startsWith("vbscript:")) {
            return CheckResult.fail("不允许使用vbscript协议");
        }

        return CheckResult.success(url.trim());
    }

       
                         
      
                         
                                   
                   
       
    public static CheckResult comprehensiveCheck(String input, String fieldDescription) {
        if (StrUtil.isBlank(input)) {
            return CheckResult.success("");
        }

                
        if (containsXSS(input)) {
            return CheckResult.fail(fieldDescription + "包含非法脚本代码");
        }

                  
        if (containsSQLInjection(input)) {
            return CheckResult.fail(fieldDescription + "包含非法SQL语句");
        }

        return CheckResult.success(input.trim());
    }
}

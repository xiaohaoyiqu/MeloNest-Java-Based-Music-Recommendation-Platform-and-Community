package com.haoran.music.common.filter;

import lombok.extern.slf4j.Slf4j;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.util.HashMap;
import java.util.Map;

   
                      
                                        
   
@Slf4j
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

       
                  
       
    private static final String[][] XSS_PATTERNS = {
            {"<(?i)script[^>]*>.*?</(?i)script>", ""},                         
            {"<(?i)iframe[^>]*>.*?</(?i)iframe>", ""},                         
            {"<(?i)embed[^>]*>.*?</(?i)embed>", ""},                          
            {"<(?i)object[^>]*>.*?</(?i)object>", ""},                         
            {"<(?i)link[^>]*>", ""},                                         
            {"<(?i)meta[^>]*>", ""},                                         
            {"<(?i)style[^>]*>.*?</(?i)style>", ""},                          
            {"<(?i)img[^>]*onerror[^>]*>", ""},                                   
            {"<(?i)img[^>]*onload[^>]*>", ""},                                   
            {"javascript:", ""},                                                     
            {"vbscript:", ""},                                                    
            {"onload\\s*=", ""},                                            
            {"onerror\\s*=", ""},                                           
            {"onclick\\s*=", ""},                                           
            {"onmouseover\\s*=", ""},                                       
            {"onfocus\\s*=", ""},                                           
            {"onblur\\s*=", ""},                                            
            {"eval\\s*\\(", ""},                                             
            {"expression\\s*\\(", ""}                                                
    };

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String[] getParameterValues(String parameter) {
        String[] values = super.getParameterValues(parameter);
        if (values == null) {
            return null;
        }

        String[] encodedValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            encodedValues[i] = cleanXss(values[i]);
        }
        return encodedValues;
    }

    @Override
    public String getParameter(String parameter) {
        String value = super.getParameter(parameter);
        return cleanXss(value);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> originalMap = super.getParameterMap();
        Map<String, String[]> cleanedMap = new HashMap<>();

        for (Map.Entry<String, String[]> entry : originalMap.entrySet()) {
            String[] values = entry.getValue();
            String[] cleanedValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                cleanedValues[i] = cleanXss(values[i]);
            }
            cleanedMap.put(entry.getKey(), cleanedValues);
        }
        return cleanedMap;
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
                           
        if ("User-Agent".equalsIgnoreCase(name) || "Referer".equalsIgnoreCase(name)) {
            return cleanXss(value);
        }
        return value;
    }

       
                
      
                       
                    
       
    private String cleanXss(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String cleanValue = value;

                   
        for (String[] pattern : XSS_PATTERNS) {
            cleanValue = cleanValue.replaceAll("(?i)" + pattern[0], pattern[1]);
        }

                   
        if (!cleanValue.equals(value)) {
            log.warn("XSS过滤: 原始长度={}, 清理后长度={}, 差异={}",
                    value.length(), cleanValue.length(), value.length() - cleanValue.length());
        }

        return cleanValue;
    }

       
                       
      
                        
                                      
       
    public static boolean containsXss(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        String lowerValue = value.toLowerCase();
        String[] dangerousKeywords = {
                "<script", "</script", "javascript:", "vbscript:",
                "onerror=", "onload=", "onclick=", "onmouseover=",
                "eval(", "expression(", "<iframe", "</iframe", "<embed"
        };

        for (String keyword : dangerousKeywords) {
            if (lowerValue.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

package com.haoran.music.common.util;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;





@Slf4j
@Component
public class IpMaskUtil {




    @Value("${music.cluster.enable-ip-mask}")
    private boolean enableMask;




    private static final String IPV4_MASK = "***";




    private static final String IPV6_MASK = ":***";




    private static final String UNKNOWN_IP = "未知";








    public String mask(String ip) {

        if (!enableMask) {
            return ip;
        }


        if (StrUtil.isBlank(ip)) {
            return UNKNOWN_IP;
        }


        if (isIPv4(ip)) {
            return maskIPv4(ip);
        }


        if (isIPv6(ip)) {
            return maskIPv6(ip);
        }


        return "***.***.***";
    }







    public List<String> maskList(List<String> ips) {
        if (ips == null || ips.isEmpty()) {
            return ips;
        }
        return ips.stream()
                .map(this::mask)
                .collect(Collectors.toList());
    }







    private String maskIPv4(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {

            parts[3] = IPV4_MASK;
            return String.join(".", parts);
        }

        return "***.***.***";
    }







    private String maskIPv6(String ip) {

        int lastColon = ip.lastIndexOf(":");
        if (lastColon > 0) {
            return ip.substring(0, lastColon) + IPV6_MASK;
        }
        return ip;
    }







    private boolean isIPv4(String ip) {
        if (StrUtil.isBlank(ip)) {
            return false;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }







    private boolean isIPv6(String ip) {
        if (StrUtil.isBlank(ip)) {
            return false;
        }
        return ip.contains(":");
    }







    public boolean isInternalIp(String ip) {
        if (StrUtil.isBlank(ip)) {
            return false;
        }


        if (ip.startsWith("10.")) {
            return true;
        }


        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }


        if (ip.startsWith("192.168.")) {
            return true;
        }


        if (ip.startsWith("127.")) {
            return true;
        }

        return false;
    }







    public boolean isLoopback(String ip) {
        if (StrUtil.isBlank(ip)) {
            return false;
        }
        return "127.0.0.1".equals(ip) || "::1".equals(ip) || ip.startsWith("127.");
    }








    public String getNetworkSegment(String ip) {
        if (StrUtil.isBlank(ip)) {
            return "";
        }
        if (!isIPv4(ip)) {
            return ip;
        }
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
        }
        return "";
    }








    public boolean isSameNetwork(String ip1, String ip2) {
        String segment1 = getNetworkSegment(ip1);
        String segment2 = getNetworkSegment(ip2);
        return StrUtil.isNotBlank(segment1) && segment1.equals(segment2);
    }






    public boolean isEnableMask() {
        return enableMask;
    }






    public void setEnableMask(boolean enableMask) {
        this.enableMask = enableMask;
        log.info("event=ip_mask_setting_updated enabled={}", enableMask);
    }
}

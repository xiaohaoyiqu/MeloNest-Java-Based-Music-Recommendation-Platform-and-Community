   
                      
   
package com.haoran.music.common.util;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

   
                                                                            
   
public final class ExternalUrlGuard {

    private ExternalUrlGuard() {
    }

    public static Validation validate(String rawUrl) {
        return validate(rawUrl, new String[0]);
    }

       
                                                                                 
                                                                                
       
    public static Validation validate(String rawUrl, String... trustedPrefixes) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return Validation.reject("empty-url");
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return Validation.reject("scheme-not-allowed");
            }
            if (uri.getUserInfo() != null || uri.getHost() == null
                    || uri.getHost().trim().isEmpty()) {
                return Validation.reject("host-not-allowed");
            }

            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if ("localhost".equals(host) || host.endsWith(".localhost")
                    || "metadata.google.internal".equals(host)
                    || "instance-data".equals(host)) {
                return Validation.reject("local-host-not-allowed");
            }

            if (matchesTrustedPrefix(uri, trustedPrefixes)) {
                return Validation.allow();
            }

            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return Validation.reject("host-unresolved");
            }
            for (InetAddress address : addresses) {
                if (isBlocked(address)) {
                    return Validation.reject("private-or-local-address");
                }
            }
            return Validation.allow();
        } catch (Exception e) {
            return Validation.reject("invalid-url");
        }
    }

    private static boolean matchesTrustedPrefix(URI uri, String... trustedPrefixes) {
        if (trustedPrefixes == null) {
            return false;
        }
        String requestScheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String requestHost = uri.getHost().toLowerCase(Locale.ROOT);
        int requestPort = effectivePort(requestScheme, uri.getPort());
        String requestPath = normalizePath(uri.getRawPath());

        for (String rawPrefix : trustedPrefixes) {
            if (rawPrefix == null || rawPrefix.trim().isEmpty()) {
                continue;
            }
            try {
                URI prefix = URI.create(rawPrefix.trim());
                if (prefix.getScheme() == null || prefix.getHost() == null
                        || prefix.getUserInfo() != null
                        || (!"http".equalsIgnoreCase(prefix.getScheme())
                        && !"https".equalsIgnoreCase(prefix.getScheme()))) {
                    continue;
                }
                String prefixScheme = prefix.getScheme().toLowerCase(Locale.ROOT);
                String prefixHost = prefix.getHost().toLowerCase(Locale.ROOT);
                if (!requestScheme.equals(prefixScheme)
                        || !requestHost.equals(prefixHost)
                        || effectivePort(prefixScheme, prefix.getPort()) != requestPort) {
                    continue;
                }

                String prefixPath = normalizePath(prefix.getRawPath());
                if ("/".equals(prefixPath) || requestPath.startsWith(prefixPath)) {
                    return true;
                }
            } catch (Exception ignored) {
                                                                         
            }
        }
        return false;
    }

    private static int effectivePort(String scheme, int port) {
        if (port >= 0) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private static String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "/";
        }
        String normalized = URI.create("http://guard.invalid" + path).normalize().getPath();
        if (normalized == null || normalized.isEmpty()) {
            return "/";
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 192 && second == 0 && (bytes[2] & 0xff) == 0)
                    || (first == 198 && second == 18);
        }

        int first = bytes[0] & 0xff;
        return (first & 0xfe) == 0xfc;
    }

    public static final class Validation {
        private final boolean allowed;
        private final String reason;

        private Validation(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public static Validation allow() {
            return new Validation(true, null);
        }

        public static Validation reject(String reason) {
            return new Validation(false, reason);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getReason() {
            return reason;
        }
    }
}

package xyz.lawlietcache.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class InternetUtil {

    private InternetUtil() {
    }

    public static String escapeForURL(String url) {
        return URLEncoder.encode(url, StandardCharsets.UTF_8);
    }

    public static boolean urlContainsImage(String url) {
        return url.endsWith("jpeg") || url.endsWith("jpg") || url.endsWith("png") || url.endsWith("bmp") || url.endsWith("gif");
    }

}

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
        String ext = extractUrlExtension(url);
        return ext.equals("jpeg") || ext.equals("jpg") || ext.equals("png") || ext.equals("bmp") || ext.equals("gif") || ext.equals("webp");
    }

    public static boolean urlContainsVideo(String url) {
        String ext = extractUrlExtension(url);
        return ext.equals("mp4") || ext.equals("avi") || ext.equals("webm");
    }

    public static String extractUrlExtension(String url) {
        return url.replaceAll(".*/[^.]+\\.([^?]+)\\??.*", "$1").toLowerCase();
    }

}

package util;

import com.google.common.net.UrlEscapers;

public class InternetUtil {

    private InternetUtil() {
    }

    public static String escapeForURL(String url) {
        return UrlEscapers.urlFragmentEscaper().escape(url);
    }

    public static boolean urlContainsImage(String url) {
        return url.endsWith("jpeg") || url.endsWith("jpg") || url.endsWith("png") || url.endsWith("bmp") || url.endsWith("gif");
    }

    public static boolean urlContainsVideo(String url) {
        return url.endsWith("mp4") || url.endsWith("webm") || url.endsWith("avi");
    }

}

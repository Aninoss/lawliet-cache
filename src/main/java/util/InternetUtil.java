package util;

import com.google.common.net.UrlEscapers;

public class InternetUtil {

    private InternetUtil() {
    }

    public static String escapeForURL(String url) {
        return UrlEscapers.urlFragmentEscaper().escape(url);
    }

}

package util;

import java.util.List;
import java.util.regex.Pattern;

public final class NSFWUtil {

    private NSFWUtil() {
    }

    public static String filterPornSearchKey(String str, List<String> filters) {
        for (String filter : filters) {
            str = str.replaceAll("(?i)\\b(?<!-)" + Pattern.quote(filter) + "\\b", "");
        }
        return str;
    }

    public static boolean stringContainsBannedTags(String str, List<String> additionalFilter) {
        return !filterPornSearchKey(str, additionalFilter).equalsIgnoreCase(str);
    }

}

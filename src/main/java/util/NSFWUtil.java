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

    public static boolean tagListAllowed(List<String> tagList, List<String> filterTags) {
        for (String tag : tagList) {
            String tagLowerCase = tag.toLowerCase();
            if (filterTags.stream().anyMatch(filterTag -> tagLowerCase.contains(filterTag.toLowerCase()))) {
                return false;
            }
        }
        return true;
    }

}

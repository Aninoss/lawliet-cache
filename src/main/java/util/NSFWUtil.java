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

    public static boolean tagListAllowed(List<String> tagList, List<String> filters) {
        return tagList.stream()
                .noneMatch(tag -> filters.stream().anyMatch(tag::equalsIgnoreCase));
    }

}

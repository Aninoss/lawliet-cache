package util;

import java.util.List;

public final class NSFWUtil {

    public static boolean tagListAllowed(List<String> tagList, List<String> filterTags) {
        for (String tag : tagList) {
            if (filterTags.stream().anyMatch(filterTag -> tagMatchesFilterTag(tag.toLowerCase(), filterTag.toLowerCase()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean tagMatchesFilterTag(String tag, String filterTag) {
        return tag.equals(filterTag) ||
                tag.startsWith(filterTag + "_") ||
                tag.endsWith("_" + filterTag);
    }

}

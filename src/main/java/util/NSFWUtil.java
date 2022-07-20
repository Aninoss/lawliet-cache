package util;

import java.util.List;

public final class NSFWUtil {

    public static boolean tagListAllowed(List<String> tagList, List<String> filterTags) {
        for (String tag : tagList) {
            String tagLowerCase = tag.toLowerCase();
            if (filterTags.stream().anyMatch(filterTag -> tagLowerCase.startsWith(filterTag.toLowerCase()) || tagLowerCase.endsWith(filterTag.toLowerCase()))) {
                return false;
            }
        }
        return true;
    }

}

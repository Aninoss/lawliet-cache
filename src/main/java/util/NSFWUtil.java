package util;

import java.util.List;
import java.util.stream.Collectors;

public final class NSFWUtil {

    public static boolean tagListAllowed(List<String> tagList, List<String> filterTags) {
        List<String> processedFilterTags = filterTags.stream()
                .map(filterTag -> "_" + filterTag.toLowerCase() + "_")
                .collect(Collectors.toList());

        for (String tag : tagList) {
            String processedTag = "_" + tag.toLowerCase() + "_";
            if (processedFilterTags.stream().anyMatch(processedTag::contains)) {
                return false;
            }
        }
        return true;
    }

}

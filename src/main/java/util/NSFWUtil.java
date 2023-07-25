package util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class NSFWUtil {

    public static boolean tagListAllowed(List<String> tagList, List<String> filterTags, List<String> strictFilterTags) {
        List<String> processedFilterTags = filterTags.stream()
                .map(filterTag -> "_" + filterTag.toLowerCase() + "_")
                .collect(Collectors.toList());

        for (String tag : expandTags(tagList)) {
            String processedTag = "_" + tag.toLowerCase() + "_";
            if (processedFilterTags.stream().anyMatch(processedTag::contains) ||
                    strictFilterTags.stream().anyMatch(tag::equalsIgnoreCase)
            ) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> expandTags(List<String> tags) {
        HashSet<String> expandedTags = new HashSet<>();
        for (String tag : tags) {
            expandedTags.add(tag.toLowerCase());
            expandedTags.add(tag.toLowerCase().replaceAll("\\p{Punct}| ", "_").replace("__", "_"));
            expandedTags.add(StringUtil.camelToSnake(tag));
            expandedTags.add(StringUtil.camelToSnake(tag).replaceAll("\\p{Punct}| ", "_").replace("__", "_"));
        }
        return expandedTags;
    }

}

package xyz.lawlietcache.util;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

public final class NSFWUtil {

    public static boolean containsFilterTags(List<String> tagList, List<String> filterTags, List<String> strictFilterTags) {
        return containsFilterTags(StringUtils.join(tagList, " "), filterTags, strictFilterTags);
    }

    public static boolean containsFilterTags(String tagString, List<String> filterTags, List<String> strictFilterTags) {
        return containsNormalFilterTags(tagString, filterTags) ||
                containsStrictFilters(tagString, strictFilterTags);
    }

    private static boolean containsNormalFilterTags(String tagString, List<String> filterTags) {
        StringBuilder regexBuilder = new StringBuilder("(?i)(^|.* )(|[^-\\P{Punct}]|[^- ][^ ]*\\p{Punct})(");
        for (int i = 0; i < filterTags.size(); i++) {
            if (i > 0) {
                regexBuilder.append("|");
            }
            regexBuilder.append(Pattern.quote(filterTags.get(i)));
        }
        regexBuilder.append(")(( |\\p{Punct}).*|$)");

        return tagString.matches(regexBuilder.toString());
    }

    private static boolean containsStrictFilters(String tagString, List<String> strictFilterTags) {
        String newTagString = " " + tagString.toLowerCase() + " ";
        return strictFilterTags.stream()
                .anyMatch(t -> newTagString.contains(" " + t.toLowerCase() + " "));
    }

}

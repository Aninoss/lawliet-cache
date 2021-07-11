package util;

import java.util.ArrayList;

public class StringUtil {

    public static String[] extractGroups(String string, String start, String end) {
        ArrayList<String> groups = new ArrayList<>();
        while (string.contains(start) && string.contains(end)) {
            int startIndex = string.indexOf(start) + start.length();
            int endIndex = string.indexOf(end, startIndex);
            if (endIndex == -1) break;

            String groupStr = "";
            if (endIndex > startIndex) groupStr = string.substring(startIndex, endIndex);
            groups.add(groupStr);

            string = string.replaceFirst(start, "");
            string = string.replaceFirst(end, "");
        }
        return groups.toArray(new String[0]);
    }

}






package com.haoran.music.service;

import com.haoran.music.common.util.SearchLimitUtil;
import com.haoran.music.util.PinyinUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;




@Slf4j
@Service
public class SearchCorrectionService {




    private static final Map<String, List<String>> ARTIST_ALIAS_MAP = new HashMap<>();




    private static final Map<String, String> PINYIN_COMMON_ERROR = new HashMap<>();




    private static final List<String> COMMON_SEARCH_KEYWORDS = Arrays.asList(
        "周杰伦", "林俊杰", "邓紫棋", "陈奕迅", "薛之谦",
        "李荣浩", "毛不易", "华晨宇", "张杰", "汪苏泷",
        "许嵩", "徐佳莹", "田馥甄", "蔡依林", "孙燕姿",
        "梁静茹", "王力宏", "陶喆", "吴青峰", "苏打绿",
        "五月天", "苏醒", "花儿乐队", "飞儿乐团", "信乐团"
    );

    static {

        addAlias("周杰伦", Arrays.asList("zhoujielun", "zjl", "杰伦", "周董"));
        addAlias("林俊杰", Arrays.asList("linjunjie", "ljj", "jj", "俊杰"));
        addAlias("邓紫棋", Arrays.asList("dengziqi", "dzq", "棋哥", "gem"));
        addAlias("陈奕迅", Arrays.asList("chenyixun", "cyx", "eason", "奕迅"));
        addAlias("华晨宇", Arrays.asList("hua chenyu", "hcy", "花花"));
        addAlias("李荣浩", Arrays.asList("lironghao", "lrh", "荣浩"));
        addAlias("毛不易", Arrays.asList("maobuyi", "mby", "毛毛"));
        addAlias("张杰", Arrays.asList("zhangjie", "zj", "杰哥", "jason"));
        addAlias("汪苏泷", Arrays.asList("wang.sulong", "wsl", "苏泷"));
        addAlias("薛之谦", Arrays.asList("xue zhiqian", "xuezq", "薛薛"));


        PINYIN_COMMON_ERROR.put("zoujielun", "周杰伦");          
        PINYIN_COMMON_ERROR.put("linjunjie", "林俊杰");          
        PINYIN_COMMON_ERROR.put("dengziqi", "邓紫棋");
        PINYIN_COMMON_ERROR.put("zhoujiel", "周杰伦");        
        PINYIN_COMMON_ERROR.put("zjl", "周杰伦");
        PINYIN_COMMON_ERROR.put("ljj", "林俊杰");
        PINYIN_COMMON_ERROR.put("dzq", "邓紫棋");
        PINYIN_COMMON_ERROR.put("cyx", "陈奕迅");
        PINYIN_COMMON_ERROR.put("xue", "薛之谦");
        PINYIN_COMMON_ERROR.put("lrh", "李荣浩");
        PINYIN_COMMON_ERROR.put("mby", "毛不易");
        PINYIN_COMMON_ERROR.put("hcy", "华晨宇");
        PINYIN_COMMON_ERROR.put("zj", "张杰");
        PINYIN_COMMON_ERROR.put("wsl", "汪苏泷");
    }






    private static void addAlias(String artistName, List<String> aliases) {
        ARTIST_ALIAS_MAP.put(artistName.toLowerCase(), aliases);
        for (String alias : aliases) {
            ARTIST_ALIAS_MAP.put(alias.toLowerCase(), Arrays.asList(artistName));
        }
    }







    public String getCorrectedQuery(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return userInput;
        }

        String input = userInput.trim().toLowerCase();


        if (PINYIN_COMMON_ERROR.containsKey(input)) {
            String corrected = PINYIN_COMMON_ERROR.get(input);
            log.info("拼音纠错: {} -> {}", input, corrected);
            return corrected;
        }


        if (ARTIST_ALIAS_MAP.containsKey(input)) {
            List<String> originals = ARTIST_ALIAS_MAP.get(input);
            if (originals != null && !originals.isEmpty()) {
                String corrected = originals.get(0);                 
                log.info("别名纠错: {} -> {}", input, corrected);
                return corrected;
            }
        }


        String corrected = correctByEditDistance(input, COMMON_SEARCH_KEYWORDS);
        if (corrected != null) {
            log.info("编辑距离纠错: {} -> {}", input, corrected);
            return corrected;
        }


        return userInput;
    }








    public String correctByEditDistance(String input, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        String bestMatch = null;
        int minDistance = Integer.MAX_VALUE;

        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            int distance = levenshteinDistance(input, candidate.toLowerCase());


            if (distance == 1 || distance <= input.length() * 0.3) {
                if (distance < minDistance) {
                    minDistance = distance;
                    bestMatch = candidate;
                }
            }
        }

        return bestMatch;
    }








    private int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s1.isEmpty()) {
            return s2 == null ? 0 : s2.length();
        }
        if (s2 == null || s2.isEmpty()) {
            return s1.length();
        }

        int len1 = s1.length();
        int len2 = s2.length();


        int[][] dp = new int[len1 + 1][len2 + 1];


        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }


        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j], dp[i][j - 1]),
                        dp[i - 1][j - 1]
                    ) + 1;
                }
            }
        }

        return dp[len1][len2];
    }








    public List<String> getSearchSuggestions(String userInput, int limit) {
        int safeLimit = SearchLimitUtil.normalize(limit);
        List<String> suggestions = new ArrayList<>();

        if (userInput == null || userInput.trim().isEmpty()) {
            return COMMON_SEARCH_KEYWORDS.stream()
                    .limit(safeLimit)
                    .collect(java.util.stream.Collectors.toList());
        }

        String input = userInput.trim().toLowerCase();


        if (PinyinUtil.isPinyin(input)) {
            for (String keyword : COMMON_SEARCH_KEYWORDS) {
                String pinyin = PinyinUtil.toPinyin(keyword).replace(" ", "");
                String initial = PinyinUtil.toPinyinInitial(keyword);

                if (pinyin.contains(input) || initial.contains(input)) {
                    suggestions.add(keyword);
                    if (suggestions.size() >= safeLimit) {
                        break;
                    }
                }
            }
        }


        if (PinyinUtil.containsChinese(input)) {
            for (String keyword : COMMON_SEARCH_KEYWORDS) {
                if (keyword.contains(input) && !keyword.equals(input)) {
                    suggestions.add(keyword);
                    if (suggestions.size() >= safeLimit) {
                        break;
                    }
                }
            }
        }


        if (suggestions.size() < safeLimit) {
            for (String keyword : COMMON_SEARCH_KEYWORDS) {
                if (!suggestions.contains(keyword)) {
                    suggestions.add(keyword);
                    if (suggestions.size() >= safeLimit) {
                        break;
                    }
                }
            }
        }

        return suggestions.stream()
                .limit(safeLimit)
                .collect(java.util.stream.Collectors.toList());
    }







    public boolean isPossibleTypo(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        String trimmed = input.trim();


        if (PinyinUtil.isPinyin(trimmed) && trimmed.length() == 1) {
            return true;
        }


        if (trimmed.matches(".*[0-9].*") && PinyinUtil.containsChinese(trimmed)) {
            return true;
        }


        String corrected = correctByEditDistance(trimmed, COMMON_SEARCH_KEYWORDS);
        return corrected != null && !corrected.equals(trimmed);
    }
}

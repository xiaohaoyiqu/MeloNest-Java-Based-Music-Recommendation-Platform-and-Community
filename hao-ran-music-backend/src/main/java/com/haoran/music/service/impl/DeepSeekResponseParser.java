   
                      
   
package com.haoran.music.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DeepSeekResponseParser {

    private static final Pattern LRC_TIME_TAG =
            Pattern.compile("\\[(\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?)\\]");
    private static final Pattern LEADING_LRC_TIME_TAGS =
            Pattern.compile("^(?:\\s*\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?\\])+\\s*");
    private static final Pattern LRC_METADATA_TAG =
            Pattern.compile("\\[(?:ar|al|by|ti|length|offset|re|ve):[^\\]]*\\]",
                    Pattern.CASE_INSENSITIVE);

    private DeepSeekResponseParser() {
    }

    static String parseContent(String response) {
        JSONObject jsonResponse = JSONUtil.parseObj(response);
        JSONArray choices = jsonResponse.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new BusinessException(ResultCode.ERROR, "翻译服务响应缺少choices");
        }

        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject message = firstChoice == null ? null : firstChoice.getJSONObject("message");
        String content = message == null ? null : message.getStr("content");
        if (content == null || content.trim().isEmpty()) {
            content = firstChoice == null ? null : firstChoice.getStr("text");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ResultCode.ERROR, "翻译服务响应缺少content");
        }
        return content.trim();
    }

    static String extractLrcTranslation(String originalLrc, String translatedText) {
        String[] originalLines = originalLrc == null ? new String[0] : originalLrc.split("\n");
        String[] translatedLines = translatedText == null ? new String[0] : translatedText.split("\n");

        StringBuilder result = new StringBuilder();
        int originalIndex = 0;

        for (String translatedLineValue : translatedLines) {
            if (originalIndex >= originalLines.length) {
                break;
            }
            String translatedLine = stripLeadingTimeTags(translatedLineValue == null ? "" : translatedLineValue.trim());
            if (translatedLine.isEmpty()) {
                continue;
            }

            while (originalIndex < originalLines.length) {
                String originalLine = originalLines[originalIndex].trim();
                Matcher matcher = LRC_TIME_TAG.matcher(originalLine);

                if (matcher.find()) {
                    StringBuilder timeTags = new StringBuilder();
                    do {
                        timeTags.append(matcher.group());
                    } while (matcher.find());
                    result.append(timeTags).append(stripMetadataTags(translatedLine)).append("\n");
                    originalIndex++;
                    break;
                }
                originalIndex++;
            }
        }

        return result.toString();
    }

    private static String stripLeadingTimeTags(String line) {
        return LEADING_LRC_TIME_TAGS.matcher(line).replaceFirst("").trim();
    }

    private static String stripMetadataTags(String line) {
        return LRC_METADATA_TAG.matcher(line).replaceAll("").trim();
    }
}

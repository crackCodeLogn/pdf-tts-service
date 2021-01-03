package com.vv.personal.pdf.tts.util;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.vv.personal.pdf.tts.constants.Constants.EMPTY_STR;

/**
 * @author Vivek
 * @since 27/12/20
 */
public class StringUtil {

    public static String reduceStringToOnlyCharacters(String title) {
        List<Character> characters = new ArrayList<>();
        for (int i = 0; i < title.length(); i++) {
            char ch = title.charAt(i);
            if (Character.isAlphabetic(ch)) characters.add(ch);
        }
        return StringUtils.join(characters, EMPTY_STR);
    }
}

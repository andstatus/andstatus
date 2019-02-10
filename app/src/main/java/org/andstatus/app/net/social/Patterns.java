package org.andstatus.app.net.social;

import java.util.regex.Pattern;

public final class Patterns {

    // RegEx from http://www.mkyong.com/regular-expressions/how-to-validate-email-address-with-regular-expression/
    private static final String WEBFINGER_ID_REGEX = "^[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";
    public static final Pattern WEBFINGER_ID_REGEX_PATTERN = Pattern.compile(WEBFINGER_ID_REGEX);
    private static final String USERNAME_REGEX_SIMPLE = "^[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*$";
    public static final Pattern USERNAME_REGEX_SIMPLE_PATTERN = Pattern.compile(USERNAME_REGEX_SIMPLE);
    static final String USERNAME_CHARS = "._ABCDEFGHIGKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-+";
    static final String WEBFINGER_ID_CHARS = USERNAME_CHARS + "@";
    private static final String USERNAME_REGEX_ALL_CHARS = "^[_A-Za-z0-9-+]+([_A-Za-z0-9-+@.]+)*$";
    public static final Pattern USERNAME_REGEX_ALL_CHARS_PATTERN = Pattern.compile(USERNAME_REGEX_ALL_CHARS);

    private Patterns() {}
}

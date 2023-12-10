package org.andstatus.app.net.social

import java.util.regex.Pattern

object Patterns {
    // Modified RegEx from http://www.mkyong.com/regular-expressions/how-to-validate-email-address-with-regular-expression/
    //   on 2023-12-10 we allowed TLD (top level domain) to contain numbers.
    //   TLD cannot be purely numerical, see https://github.com/andstatus/andstatus/issues/586#issuecomment-1848276775
    private val WEBFINGER_ID_REGEX: String = "^[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*" +
        // TLD:
        "\\.(([A-Za-z][A-Za-z0-9]+)|([A-Za-z0-9]+[A-Za-z])|([A-Za-z0-9]+[A-Za-z][A-Za-z0-9]+))$"
    val WEBFINGER_ID_REGEX_PATTERN = Pattern.compile(WEBFINGER_ID_REGEX)
    private val USERNAME_REGEX_SIMPLE: String = "^[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*$"
    val USERNAME_REGEX_SIMPLE_PATTERN = Pattern.compile(USERNAME_REGEX_SIMPLE)
    val USERNAME_CHARS: String = "._ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-+"
    val WEBFINGER_ID_CHARS: String = USERNAME_CHARS + "@"
    private val USERNAME_REGEX_ALL_CHARS: String = "^[_A-Za-z0-9-+]+([_A-Za-z0-9-+@.]+)*$"
    val USERNAME_REGEX_ALL_CHARS_PATTERN = Pattern.compile(USERNAME_REGEX_ALL_CHARS)
}

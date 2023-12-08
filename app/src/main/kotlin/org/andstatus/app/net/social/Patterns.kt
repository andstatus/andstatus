package org.andstatus.app.net.social

import java.util.regex.Pattern

object Patterns {
    // RegEx from http://www.mkyong.com/regular-expressions/how-to-validate-email-address-with-regular-expression/
    private val WEBFINGER_ID_REGEX: String = "^[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z0-9]{2,})$"
    val WEBFINGER_ID_REGEX_PATTERN = Pattern.compile(WEBFINGER_ID_REGEX)
    private val USERNAME_REGEX_SIMPLE: String = "^[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*$"
    val USERNAME_REGEX_SIMPLE_PATTERN = Pattern.compile(USERNAME_REGEX_SIMPLE)
    val USERNAME_CHARS: String = "._ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-+"
    val WEBFINGER_ID_CHARS: String = USERNAME_CHARS + "@"
    private val USERNAME_REGEX_ALL_CHARS: String = "^[_A-Za-z0-9-+]+([_A-Za-z0-9-+@.]+)*$"
    val USERNAME_REGEX_ALL_CHARS_PATTERN = Pattern.compile(USERNAME_REGEX_ALL_CHARS)
}

package org.andstatus.app.origin;

import org.andstatus.app.net.Connection.ApiEnum;

class OriginTwitter extends Origin {
    protected OriginTwitter() {
        name = ORIGIN_NAME_TWITTER;
        id = ORIGIN_ID_TWITTER;
        isOAuthDefault = true;
        canChangeOAuth = false;  // Starting from 2010-09 twitter.com allows OAuth only
        shouldSetNewUsernameManuallyIfOAuth = false;
        shouldSetNewUsernameManuallyNoOAuth = false;
        usernameRegEx = "[a-zA-Z_0-9/\\.\\-\\(\\)]+";
        maxCharactersInMessage = CHARS_MAX_DEFAULT;

        connectionData.api = ApiEnum.TWITTER1P1;
        connectionData.isHttps = false;
        connectionData.host = "api.twitter.com";
        connectionData.basicPath = "1.1";
        connectionData.oauthPath = "oauth";
    }

    @Override
    public boolean isUsernameValidToStartAddingNewAccount(String username, boolean isOAuthUser) {
        if (isOAuthUser) {
            return true;  // Name doesn't matter at this step
        } else {
            return isUsernameValid(username);
        }
    }
}

package org.andstatus.app.origin;

import org.andstatus.app.net.Connection.ApiEnum;

class OriginPumpio extends Origin {
    protected OriginPumpio() {
        name = ORIGIN_NAME_PUMPIO;
        id = ORIGIN_ID_PUMPIO;
        isOAuthDefault = true;  
        canChangeOAuth = false;
        shouldSetNewUsernameManuallyIfOAuth = true;
        shouldSetNewUsernameManuallyNoOAuth = false;
        usernameRegEx = "[a-zA-Z_0-9/\\.\\-\\(\\)]+@[a-zA-Z_0-9/\\.\\-\\(\\)]+";
        maxCharactersInMessage = 5000; // This is not a hard limit, just for convenience.

        connectionData.api = ApiEnum.PUMPIO;
        connectionData.isHttps = true;
        connectionData.host = "identi.ca";  // Default host
        connectionData.basicPath = "api";
        connectionData.oauthPath = "oauth";
    }

    @Override
    public boolean isUsernameValidToStartAddingNewAccount(String username, boolean isOAuthUser) {
        return isUsernameValid(username);
    }
}

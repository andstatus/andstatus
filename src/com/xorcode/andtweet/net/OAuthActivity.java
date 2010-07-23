/*
 * Copyright (C) 2010 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2010 Brion N. Emde, "BLOA" example, http://github.com/brione/Brion-Learns-OAuth 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xorcode.andtweet.net;

import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.xorcode.andtweet.PreferencesActivity;
import com.xorcode.andtweet.TwitterUser;
import com.xorcode.andtweet.TwitterUser.CredentialsVerified;

public class OAuthActivity extends Activity {
    private static final String TAG = "OAuthActivity";

    // The URI is consistent with "scheme" and "host" in AndroidManifest
    public static final Uri CALLBACK_URI = Uri.parse("andtweet-oauth://twitt");

    private OAuthConsumer mConsumer = null;

    private OAuthProvider mProvider = null;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // We don't need to worry about any saved states: we can reconstruct the
        // state
        mConsumer = new CommonsHttpOAuthConsumer(OAuthKeys.TWITTER_CONSUMER_KEY,
                OAuthKeys.TWITTER_CONSUMER_SECRET);

        mProvider = new CommonsHttpOAuthProvider(ConnectionOAuth.TWITTER_REQUEST_TOKEN_URL,
                ConnectionOAuth.TWITTER_ACCESS_TOKEN_URL, ConnectionOAuth.TWITTER_AUTHORIZE_URL);

        // It turns out this was the missing thing to making standard Activity
        // launch mode work
        mProvider.setOAuth10a(true);

    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        boolean done = false;
        boolean authenticated = false;
        TwitterUser tu = TwitterUser.getTwitterUser(this, false);

        Uri uri = getIntent().getData();
        if (uri != null && CALLBACK_URI.getScheme().equals(uri.getScheme())) {
            String token = tu.getSharedPreferences().getString(ConnectionOAuth.REQUEST_TOKEN, null);
            String secret = tu.getSharedPreferences().getString(ConnectionOAuth.REQUEST_SECRET, null);

            tu.clearAuthInformation();
            if (!tu.isOAuth()) {
                Log.e(TAG, "Connection is not of OAuth type ???");
            } else {
                ConnectionOAuth conn = ((ConnectionOAuth) tu.getConnection());
                try {
                    // Clear the request stuff, we've used it already
                    OAuthActivity.saveRequestInformation(tu.getSharedPreferences(), null, null);

                    if (!(token == null || secret == null)) {
                        mConsumer.setTokenWithSecret(token, secret);
                    }
                    String otoken = uri.getQueryParameter(OAuth.OAUTH_TOKEN);
                    String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);

                    /*
                     * yvolk 2010-07-08: It appeared that this may be not true:
                     * Assert.assertEquals(otoken, mConsumer.getToken()); (e.g.
                     * if User denied access during OAuth...) hence this is not
                     * Assert :-)
                     */
                    if (otoken != null || mConsumer.getToken() != null) {
                        // We send out and save the request token, but the
                        // secret is not the same as the verifier
                        // Apparently, the verifier is decoded to get the
                        // secret, which is then compared - crafty
                        // This is a sanity check which should never fail -
                        // hence the assertion
                        // Assert.assertEquals(otoken,
                        // mConsumer.getToken());

                        // This is the moment of truth - we could throw here
                        mProvider.retrieveAccessToken(mConsumer, verifier);
                        // Now we can retrieve the goodies
                        token = mConsumer.getToken();
                        secret = mConsumer.getTokenSecret();
                        authenticated = true;
                    }
                } catch (OAuthMessageSignerException e) {
                    e.printStackTrace();
                } catch (OAuthNotAuthorizedException e) {
                    e.printStackTrace();
                } catch (OAuthExpectationFailedException e) {
                    e.printStackTrace();
                } catch (OAuthCommunicationException e) {
                    e.printStackTrace();
                } finally {
                    if (authenticated) {
                        conn.saveAuthInformation(token, secret);
                    }
                }
            }
            done = true;
        }
        if (done) {
            if (!authenticated) {
                tu.clearAuthInformation();
                // So we won't loop back here from the Preferences Activity
                tu.setCredentialsVerified(CredentialsVerified.FAILED);
            }
            Log.d(TAG, "onResume ended, "
                    + (authenticated ? "authenticated" : "authentication failed"));
            startActivity(new Intent(this, PreferencesActivity.class));
            finish();
        }
    }

    public static void saveRequestInformation(SharedPreferences settings, String token,
            String secret) {
        // null means to clear the old values
        SharedPreferences.Editor editor = settings.edit();
        if (token == null) {
            editor.remove(ConnectionOAuth.REQUEST_TOKEN);
            Log.d(TAG, "Clearing Request Token");
        } else {
            editor.putString(ConnectionOAuth.REQUEST_TOKEN, token);
            Log.d(TAG, "Saving Request Token: " + token);
        }
        if (secret == null) {
            editor.remove(ConnectionOAuth.REQUEST_SECRET);
            Log.d(TAG, "Clearing Request Secret");
        } else {
            editor.putString(ConnectionOAuth.REQUEST_SECRET, secret);
            Log.d(TAG, "Saving Request Secret: " + secret);
        }
        editor.commit();

    }
}

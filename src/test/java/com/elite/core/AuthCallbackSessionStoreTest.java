package com.elite.core;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AuthCallbackSessionStoreTest {
    @Test
    public void twitterStateAndRedirectAreRequired() {
        Uri auth = Uri.parse(
                "https://x.com/i/oauth2/authorize?state=tw-state"
                        + "&redirect_uri=twitterkit%3A%2F%2Fcallback");
        AuthCallbackSessionStore.begin(auth);

        assertFalse(AuthCallbackSessionStore.accept(
                Uri.parse("twitterkit://callback?state=wrong&code=bad")));

        AuthCallbackSessionStore.begin(auth);
        assertTrue(AuthCallbackSessionStore.accept(
                Uri.parse("twitterkit://callback?state=tw-state&code=good")));
        assertFalse(AuthCallbackSessionStore.accept(
                Uri.parse("twitterkit://callback?state=tw-state&code=replay")));
    }

    @Test
    public void facebookSupportsFragmentStateAndRejectsWrongRedirect() {
        Uri auth = Uri.parse(
                "https://www.facebook.com/dialog/oauth?state=fb-state"
                        + "&redirect_uri=fbconnect%3A%2F%2Fcct.app");
        AuthCallbackSessionStore.begin(auth);

        assertFalse(AuthCallbackSessionStore.accept(
                Uri.parse("fbconnect://wrong.host?code=bad#state=fb-state")));

        AuthCallbackSessionStore.begin(auth);
        assertTrue(AuthCallbackSessionStore.accept(
                Uri.parse("fbconnect://cct.app#state=fb-state&access_token=good")));
    }

    @Test
    public void legacyTwitterAuthorizePageCanReturnWithoutRedirectUri() {
        AuthCallbackSessionStore.begin(Uri.parse(
                "https://api.twitter.com/oauth/authorize?oauth_token=request-token"));

        assertTrue(AuthCallbackSessionStore.accept(Uri.parse(
                "twitterkit://callback?oauth_token=request-token&oauth_verifier=verified")));
    }
}

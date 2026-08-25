package com.elite.proxy;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.elite.EliteInstaller;
import com.elite.core.AuthCore;
import com.elite.utils.Slog;

import org.lsposed.lsparanoid.Obfuscate;

/**
 * Host-level OAuth callback relay.
 *
 * Native app (X/Twitter/Facebook) OAuth complete karke jo deep-link
 * callback bhejta hai (twitterkit:// / fb{appId}://) wo host AMS pe
 * aata hai — VM ke andar wale game tak pahunch nahi pata tha isliye
 * game ka login webview "Loading..." pe atak jata tha.
 *
 * Ye activity host manifest me merged hoti hai (AAR manifest merge) aur
 * callback ko VM ke andar game ke deep-link handler tak relay karta hai.
 *
 * SAFE MODE: koi token capture/modification nahi — sirf delivery relay.
 */
@Obfuscate
public class AuthCallbackRelayActivity extends Activity {

    private static final String TAG = "AuthRelay";
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
        finish();
    }

    private void handleIntent(Intent callback) {
        if (callback == null || callback.getData() == null) {
            Slog.d(TAG, "No data, ignoring");
            return;
        }

        Uri data = callback.getData();
        Slog.d(TAG, "Auth callback received: " + data);

        // Auth callback nahi (plain fb:// etc) -> seedha native app
        if (!AuthCore.isAuthCallbackIntent(callback)) {
            launchNativeFallback(data, callback);
            return;
        }

        // EliteInstaller init nahi hua -> native app fallback
        if (!isSdkReady()) {
            launchNativeFallback(data, callback);
            return;
        }

        // VM ke andar koi app (game) is callback ko handle karta hai?
        // Component/package clear karo taaki VM resolver decide kare
        final Intent relay = new Intent(callback);
        relay.setComponent(null);
        relay.setPackage(null);
        relay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        boolean dispatched = false;
        try {
            if (EliteInstaller.getBPackageManager()
                    .resolveActivity(relay, 0, null, 0) != null) {
                sHandler.post(() -> {
                    try {
                        EliteInstaller.getBActivityManager().startActivity(relay, 0);
                        Slog.d(TAG, "Auth callback relayed into VM: " + data);
                    } catch (Throwable t) {
                        Slog.e(TAG, "VM relay failed: " + t);
                    }
                });
                dispatched = true;
            }
        } catch (Throwable t) {
            Slog.e(TAG, "VM resolve failed: " + t);
        }

        if (!dispatched) {
            launchNativeFallback(data, callback);
        }
    }

    /**
     * VM me handler nahi mila -> seedha native app khol do
     */
    private void launchNativeFallback(Uri data, Intent original) {
        try {
            String nativePkg = AuthCore.callbackNativePackage(data);
            if (nativePkg == null) {
                Slog.w(TAG, "No native app for callback: " + data);
                return;
            }
            Intent nativeIntent = new Intent(original);
            nativeIntent.setPackage(nativePkg);
            nativeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(nativeIntent);
            Slog.d(TAG, "Auth callback -> native " + nativePkg + ": " + data);
        } catch (Throwable t) {
            Slog.e(TAG, "Native fallback failed: " + t);
        }
    }

    private static boolean isSdkReady() {
        try {
            return EliteInstaller.getContext() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}

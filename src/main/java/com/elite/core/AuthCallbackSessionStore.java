package com.elite.core;

import android.net.Uri;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Short-lived OAuth state/redirect validator shared by the web-login hook and
 * callback relay. It never stores access tokens; it only tracks the redirect
 * contract needed to prevent an unrelated callback from entering the VM.
 */
final class AuthCallbackSessionStore {
    private static final Object LOCK = new Object();
    private static final long TTL_MS = 3L * 60L * 1000L;
    private static final int MAX_SESSIONS = 8;
    private static final List<Session> SESSIONS = new ArrayList<>();

    private AuthCallbackSessionStore() {
    }

    static void begin(Uri authUri) {
        if (authUri == null) return;
        String redirect = authUri.getQueryParameter("redirect_uri");
        String state = authUri.getQueryParameter("state");
        Uri expected = null;
        if (redirect != null && !redirect.trim().isEmpty()) {
            try {
                expected = Uri.parse(redirect);
            } catch (Throwable ignored) {
                return;
            }
            if (expected.getScheme() == null || expected.getScheme().isEmpty()) return;
        } else if (!isLegacyTwitterAuthorize(authUri)) {
            return;
        }
        synchronized (LOCK) {
            purgeLocked(SystemClock.elapsedRealtime());
            // One virtual app launch owns the active callback slot. Replacing
            // it prevents an older session from accepting a later replay.
            SESSIONS.clear();
            while (SESSIONS.size() >= MAX_SESSIONS) SESSIONS.remove(0);
            SESSIONS.add(new Session(expected, state, SystemClock.elapsedRealtime()));
        }
    }

    /** Returns true only for a callback matching an active launch session. */
    static boolean accept(Uri callback) {
        if (callback == null) return false;
        synchronized (LOCK) {
            long now = SystemClock.elapsedRealtime();
            purgeLocked(now);
            if (SESSIONS.isEmpty()) return false;
            for (Session session : SESSIONS) {
                if (!sameRedirect(session.expectedRedirect, callback)) continue;
                if (session.state != null && !session.state.isEmpty()) {
                    String callbackState = callback.getQueryParameter("state");
                    if ((callbackState == null || callbackState.isEmpty())
                            && "fbconnect".equalsIgnoreCase(callback.getScheme())) {
                        callbackState = fragmentParameter(callback, "state");
                    }
                    if (!session.state.equals(callbackState)) continue;
                }
                boolean result = hasOAuthResult(callback);
                if (result) SESSIONS.remove(session);
                return result;
            }
            return false;
        }
    }

    private static boolean sameRedirect(Uri expected, Uri callback) {
        if (expected == null) return isTwitterCallback(callback);
        if (!eq(expected.getScheme(), callback.getScheme())) return false;
        if (!eq(expected.getAuthority(), callback.getAuthority())) return false;
        if (!eq(normalizePath(expected.getPath()), normalizePath(callback.getPath()))) return false;
        for (String name : expected.getQueryParameterNames()) {
            if (!expected.getQueryParameters(name).equals(callback.getQueryParameters(name))) return false;
        }
        return true;
    }

    private static boolean isLegacyTwitterAuthorize(Uri uri) {
        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null || path == null) return false;
        String h = host.toLowerCase();
        String p = path.toLowerCase();
        return ("twitter.com".equals(h) || h.endsWith(".twitter.com")
                || "x.com".equals(h) || h.endsWith(".x.com"))
                && (p.contains("authorize") || p.contains("authenticate"));
    }

    private static boolean isTwitterCallback(Uri callback) {
        String scheme = callback.getScheme();
        if (scheme == null) return false;
        String s = scheme.toLowerCase();
        return s.equals("twittersdk") || s.equals("twitterkit") || s.equals("twitter")
                || s.equals("twitterauth") || s.equals("oauth-twitter")
                || s.equals("xauth") || s.equals("x");
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path;
    }

    private static boolean hasOAuthResult(Uri callback) {
        return has(callback, "code") || has(callback, "oauth_token")
                || has(callback, "access_token") || has(callback, "oauth_verifier")
                || has(callback, "error") || has(callback, "denied");
    }

    private static boolean has(Uri uri, String name) {
        String value = uri.getQueryParameter(name);
        if (value != null && !value.isEmpty()) return true;
        value = fragmentParameter(uri, name);
        return value != null && !value.isEmpty();
    }

    private static String fragmentParameter(Uri uri, String name) {
        String fragment = uri.getEncodedFragment();
        if (fragment == null || fragment.isEmpty()) return null;
        for (String pair : fragment.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator < 0 ? pair : pair.substring(0, separator);
            if (!name.equals(Uri.decode(key))) continue;
            return separator < 0 ? "" : Uri.decode(pair.substring(separator + 1));
        }
        return null;
    }

    private static boolean eq(String left, String right) {
        return left == null ? right == null : left.equalsIgnoreCase(right);
    }

    private static void purgeLocked(long now) {
        Iterator<Session> it = SESSIONS.iterator();
        while (it.hasNext()) {
            long age = now - it.next().createdAt;
            if (age < 0L || age > TTL_MS) it.remove();
        }
    }

    private static final class Session {
        final Uri expectedRedirect;
        final String state;
        final long createdAt;

        Session(Uri expectedRedirect, String state, long createdAt) {
            this.expectedRedirect = expectedRedirect;
            this.state = state;
            this.createdAt = createdAt;
        }
    }
}

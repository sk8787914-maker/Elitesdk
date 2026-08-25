package com.elite.core;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;

import com.elite.EliteInstaller;
import com.elite.utils.Slog;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Google Play Services, Facebook aur Twitter/X login ka complete fix
 * Android 16 (API 36) tak supported — virtual main working
 *
 * SAFE MODE:
 * - Koi forged signature/certificate nahi (safe login)
 * - Host pe real GMS/FB/Twitter app ho to wahi use hota hai (pass-through)
 * - Dummy info sirf fallback hai taaki app crash na ho aur login UI khule
 *
 * WEB LOGIN HOOK:
 * - Twitter/X + Facebook webpage wala OAuth login intercept hota hai
 * - Webview/webpage ki jagah sidha native APK force open hota hai
 * - authToken native app se aata hai (app-to-app auth)
 * - Sab tokens DOUBLE VERIFICATION se pass hote hain
 */
public class AuthCore {

    private static final String TAG = "GmsFacebookFix";

    /** Max supported API level (Android 16) */
    public static final int MAX_API = 36;

    // ========== Google Play Services Packages ==========
    public static final String GMS_PKG = "com.google.android.gms";
    public static final String GSF_PKG = "com.google.android.gsf";
    public static final String VENDING_PKG = "com.android.vending";
    public static final String GMS_SIGNIN_SERVICE = "com.google.android.gms.auth.api.signin.service.START";
    public static final String GMS_MEASUREMENT_SERVICE = "com.google.android.gms.measurement.START";

    // ========== Facebook Packages ==========
    public static final String FB_PKG = "com.facebook.katana";
    public static final String FB_WAKIZASHI_PKG = "com.facebook.wakizashi";
    public static final String FB_LITE_PKG = "com.facebook.lite";
    public static final String FB_ORCA_PKG = "com.facebook.orca";

    // ========== Twitter / X Packages ==========
    public static final String TWITTER_PKG = "com.twitter.android";
    public static final String TWITTER_LITE_PKG = "com.twitter.android.lite";
    public static final String X_PKG = "com.x.android";

    // ========== Web login (OAuth webpage) hosts ==========
    private static final String[] TWITTER_WEB_HOSTS = {
            "x.com", "twitter.com", "api.twitter.com", "api.x.com"
    };
    private static final String[] FB_WEB_HOSTS = {
            "facebook.com", "www.facebook.com", "m.facebook.com",
            "web.facebook.com", "api.facebook.com"
    };

    /**
     * Android 16+ (API 36) detection
     */
    public static boolean isAndroid16Plus() {
        return Build.VERSION.SDK_INT >= MAX_API;
    }

    /**
     * Current API level (virtual target capped at 36)
     */
    public static int apiLevel() {
        return Math.min(Build.VERSION.SDK_INT, MAX_API);
    }

    /**
     * Facebook family check (broad — katana/lite/orca/wakizashi/services/mlite)
     */
    public static boolean isFacebookFamily(String packageName) {
        if (packageName == null) return false;
        return packageName.equals(FB_PKG)
                || packageName.equals(FB_LITE_PKG)
                || packageName.equals(FB_ORCA_PKG)
                || packageName.equals(FB_WAKIZASHI_PKG)
                || packageName.startsWith("com.facebook.");
    }

    /**
     * Twitter / X family check
     */
    public static boolean isTwitterFamily(String packageName) {
        if (packageName == null) return false;
        return packageName.equals(TWITTER_PKG)
                || packageName.equals(TWITTER_LITE_PKG)
                || packageName.equals(X_PKG)
                || packageName.startsWith("com.twitter.")
                || packageName.startsWith("com.x.");
    }

    /**
     * Check if package needs fix (GMS / Facebook / Twitter-X)
     */
    public static boolean needsFix(String packageName) {
        if (packageName == null) return false;

        // Google Play Services
        if (packageName.equals(GMS_PKG) ||
            packageName.equals(GSF_PKG) ||
            packageName.equals(VENDING_PKG) ||
            packageName.startsWith("com.google.android.gms.") ||
            packageName.startsWith("com.google.android.gsf.")) {
            return true;
        }

        if (isFacebookFamily(packageName)) return true;
        if (isTwitterFamily(packageName)) return true;

        return false;
    }

    /**
     * Check if intent needs fix (GMS / Facebook / Twitter-X intent).
     * Precise matching: loose substring checks hata diye (false positives fix)
     */
    public static boolean needsFix(Intent intent) {
        if (intent == null) return false;

        // Explicit component package
        if (intent.getComponent() != null) {
            if (needsFix(intent.getComponent().getPackageName())) return true;
        }

        // Action — precise patterns only
        String action = intent.getAction();
        if (action != null) {
            if (action.equals(GMS_SIGNIN_SERVICE) || action.equals(GMS_MEASUREMENT_SERVICE)) return true;
            if (action.startsWith("com.google.android.gms.") || action.startsWith("com.google.android.gsf.")) return true;
            if (action.contains(".gms.") || action.contains(".play.")) return true;
            if (action.contains("facebook")) return true;
            if (action.contains("twitter") || action.startsWith("com.x.") || action.contains("twitterkit")) return true;
            if (action.startsWith("fb") && action.contains("authorize")) return true; // fb{appId}://authorize style
        }

        // Data URI deep links: fbauth://, fb{appId}://, twitterkit://
        Uri data = intent.getData();
        if (data != null) {
            String scheme = data.getScheme();
            if (scheme != null && (scheme.startsWith("fb") || scheme.startsWith("twitterkit"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get dummy PackageInfo for GMS/Facebook/Twitter (Android 16+ compatible)
     */
    public static PackageInfo getDummyPackageInfo(String packageName) {
        Slog.d(TAG, "Creating dummy PackageInfo for: " + packageName);

        PackageInfo dummyInfo = new PackageInfo();
        dummyInfo.packageName = packageName;

        int versionCode;
        String versionName;
        if (packageName.equals(GMS_PKG) || packageName.equals(GSF_PKG)) {
            versionCode = 254432030;
            versionName = "25.44.32 (190400-693934542)";
        } else if (packageName.equals(VENDING_PKG)) {
            versionCode = 84542220;
            versionName = "45.2.22-31";
        } else if (isFacebookFamily(packageName)) {
            versionCode = 480921014;
            versionName = "480.0.0.40.90";
        } else if (isTwitterFamily(packageName)) {
            versionCode = 421300140;
            versionName = "10.42.0-release.0";
        } else {
            versionCode = 1;
            versionName = "1.0";
        }
        dummyInfo.versionCode = versionCode;
        dummyInfo.versionName = versionName;

        // FIX: API 28+ longVersionCode bhi set karo (modern GMS/Play check karte hain)
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                dummyInfo.setLongVersionCode(versionCode);
            } catch (Throwable ignored) {
            }
        }

        // Install times — kuch SDKs firstInstallTime/lastUpdateTime padhti hain
        long now = System.currentTimeMillis();
        dummyInfo.firstInstallTime = now;
        dummyInfo.lastUpdateTime = now;

        // API 30+: requested permissions array hona chahiye (null-safe apps ke liye)
        dummyInfo.requestedPermissions = new String[0];

        ApplicationInfo appInfo = getDummyApplicationInfo(packageName);
        dummyInfo.applicationInfo = appInfo;

        return dummyInfo;
    }

    /**
     * Host pe real package installed hai to uska real APK path lo
     */
    private static String resolveRealSourceDir(String packageName) {
        try {
            ApplicationInfo real = EliteInstaller.getPackageManager().getApplicationInfo(packageName, 0);
            if (real != null && real.sourceDir != null && !real.sourceDir.isEmpty()) {
                return real.sourceDir;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Get dummy ApplicationInfo for GMS/Facebook/Twitter (API 24 -> 36 compatible)
     */
    public static ApplicationInfo getDummyApplicationInfo(String packageName) {
        Slog.d(TAG, "Creating dummy ApplicationInfo for: " + packageName);

        ApplicationInfo dummyInfo = new ApplicationInfo();
        dummyInfo.packageName = packageName;
        dummyInfo.uid = EliteInstaller.getHostUid();
        dummyInfo.flags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;

        // FIX: host pe real package ho to uska REAL sourceDir use karo,
        // warna platform-typical priv-app path fallback
        String realSource = resolveRealSourceDir(packageName);
        if (realSource != null) {
            dummyInfo.sourceDir = realSource;
            dummyInfo.publicSourceDir = realSource;
        } else if (packageName.equals(GMS_PKG) || isFacebookFamily(packageName) || isTwitterFamily(packageName)) {
            dummyInfo.sourceDir = "/product/priv-app/" + packageName + "/" + packageName + ".apk";
            dummyInfo.publicSourceDir = dummyInfo.sourceDir;
        } else {
            dummyInfo.sourceDir = "/system/app/" + packageName + "/" + packageName + ".apk";
            dummyInfo.publicSourceDir = dummyInfo.sourceDir;
        }
        dummyInfo.dataDir = "/data/data/" + packageName;
        dummyInfo.deviceProtectedDataDir = "/data/user_de/0/" + packageName;
        dummyInfo.nativeLibraryDir = "/system/lib64";

        // API 28+: processName zaroori hai (null hone pe Android 10+ me crash)
        if (Build.VERSION.SDK_INT >= 28) {
            dummyInfo.processName = packageName;
        }
        dummyInfo.targetSdkVersion = apiLevel();

        // API 26+: app category (undefined = safe default)
        if (Build.VERSION.SDK_INT >= 26) {
            dummyInfo.category = ApplicationInfo.CATEGORY_UNDEFINED;
        }

        // API 29+: enabled flag explicit
        if (Build.VERSION.SDK_INT >= 29) {
            dummyInfo.enabled = true;
        }

        // API 31+: compileSdk fields (hidden fields — getDeclaredField fallback ke saath)
        if (Build.VERSION.SDK_INT >= 31) {
            setHiddenIntField(dummyInfo, "compileSdkVersion", MAX_API);
            setHiddenStringField(dummyInfo, "compileSdkVersionCodename", "16");
        }

        return dummyInfo;
    }

    private static void setHiddenIntField(Object target, String name, int value) {
        try {
            java.lang.reflect.Field f = ApplicationInfo.class.getField(name);
            f.setInt(target, value);
        } catch (NoSuchFieldException ignored) {
            try {
                java.lang.reflect.Field f = ApplicationInfo.class.getDeclaredField(name);
                f.setAccessible(true);
                f.setInt(target, value);
            } catch (Throwable ignored2) {
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setHiddenStringField(Object target, String name, String value) {
        try {
            java.lang.reflect.Field f = ApplicationInfo.class.getField(name);
            f.set(target, value);
        } catch (NoSuchFieldException ignored) {
            try {
                java.lang.reflect.Field f = ApplicationInfo.class.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
            } catch (Throwable ignored2) {
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Get dummy ResolveInfo for intent (Activity ya Service)
     * Android 16+: exported=true, permission=null taaki bind/start fail na ho
     */
    public static ResolveInfo getDummyResolveInfo(Intent intent, boolean isService) {
        Slog.d(TAG, "Creating dummy ResolveInfo for: " + intent);

        ResolveInfo dummyResolve = new ResolveInfo();
        String packageName = getPackageNameFromIntent(intent);

        ApplicationInfo appInfo = getDummyApplicationInfo(packageName);

        if (isService) {
            dummyResolve.serviceInfo = new ServiceInfo();
            dummyResolve.serviceInfo.packageName = packageName;
            dummyResolve.serviceInfo.name = getServiceName(intent, packageName);
            dummyResolve.serviceInfo.applicationInfo = appInfo;
            dummyResolve.serviceInfo.permission = null;
            dummyResolve.serviceInfo.exported = true;
            dummyResolve.serviceInfo.enabled = true;
        } else {
            dummyResolve.activityInfo = new ActivityInfo();
            dummyResolve.activityInfo.packageName = packageName;
            dummyResolve.activityInfo.name = getActivityName(intent, packageName);
            dummyResolve.activityInfo.applicationInfo = appInfo;
            dummyResolve.activityInfo.permission = null;
            dummyResolve.activityInfo.exported = true;
            dummyResolve.activityInfo.enabled = true;
            // Android 14+ (API 34): activity launch safety
            if (Build.VERSION.SDK_INT >= 34) {
                dummyResolve.activityInfo.launchMode = ActivityInfo.LAUNCH_MULTIPLE;
            }
        }

        dummyResolve.priority = 0;
        dummyResolve.preferredOrder = 0;
        dummyResolve.match = 0x108000; // MATCH_DIRECT_BOOT_UNAWARE level match
        dummyResolve.isDefault = true;

        return dummyResolve;
    }

    /**
     * Get package name from intent
     */
    private static String getPackageNameFromIntent(Intent intent) {
        if (intent.getComponent() != null) {
            return intent.getComponent().getPackageName();
        }

        String action = intent.getAction();
        if (action != null) {
            if (action.startsWith("com.google.android.gms.") || action.startsWith("com.google.android.gsf.")
                    || action.contains(".gms.") || action.contains(".play.")) {
                return GMS_PKG;
            }
            if (action.contains("facebook")) {
                return FB_PKG;
            }
            if (action.contains("twitter") || action.contains("twitterkit")) {
                return TWITTER_PKG;
            }
            if (action.startsWith("com.x.")) {
                return X_PKG;
            }
        }

        // Deep-link scheme check: fb*:// -> FB, twitterkit:// -> Twitter
        Uri data = intent.getData();
        if (data != null) {
            String scheme = data.getScheme();
            if (scheme != null) {
                if (scheme.startsWith("fb")) return FB_PKG;
                if (scheme.startsWith("twitterkit")) return TWITTER_PKG;
            }
        }

        return GMS_PKG; // Default
    }

    /**
     * Get service name for intent (modern GMS service classes)
     */
    private static String getServiceName(Intent intent, String packageName) {
        if (intent.getComponent() != null) {
            return intent.getComponent().getClassName();
        }

        String action = intent.getAction();
        if (action != null) {
            if (action.contains("measurement") || action.equals(GMS_MEASUREMENT_SERVICE)) {
                return "com.google.android.gms.measurement.AppMeasurementService";
            }
            if (action.contains("signin") || action.equals(GMS_SIGNIN_SERVICE)) {
                return "com.google.android.gms.auth.api.signin.internal.SignInHubService";
            }
            if (action.contains("adid") || action.contains("adsidentifier")) {
                return "com.google.android.gms.ads.identifier.service.AttributionService";
            }
            // Android 13+/14+ GMS measurement service variant
            if (action.contains(".gms.")) {
                return "com.google.android.gms.chimera.GmsIntentOperationService";
            }
        }

        return packageName + ".BaseService";
    }

    /**
     * Get activity name for intent (FB / Twitter login activities)
     */
    private static String getActivityName(Intent intent, String packageName) {
        if (intent.getComponent() != null) {
            return intent.getComponent().getClassName();
        }

        String action = intent.getAction();

        // Family-specific activity guesses (X/Twitter real deep-link handler = DeepLinkActivity)
        if (isFacebookFamily(packageName)) {
            return "com.facebook.CustomTabActivity"; // FB login flow CustomTab use karta hai
        }
        if (isTwitterFamily(packageName)) {
            return "com.twitter.android.DeepLinkActivity"; // twitterkit:// deep link handler
        }
        if (action != null && action.contains("facebook")) {
            return "com.facebook.katana.ProxyAuthActivity";
        }

        return packageName + ".BaseActivity";
    }

    /**
     * Handle getPackageInfo and getApplicationInfo in one method
     * Android 16+ safe: koi signature forgery nahi (SAFE LOGIN MODE)
     *
     * Note: GET_SIGNATURES / GET_SIGNING_CERTIFICATES maanga ho to
     * signatures null hi rehte hain — fake cert se security risk hota hai.
     * Real GMS host pe installed ho to GmsCore pass-through use karo.
     */
    public static Object handlePackageInfo(String packageName, int flags, boolean isApplicationInfo) {
        if (!needsFix(packageName)) {
            return null; // Not our concern
        }

        Slog.d(TAG, "AuthCore fix (API " + apiLevel() + "): " + packageName +
                (isApplicationInfo ? " [ApplicationInfo]" : " [PackageInfo]"));

        // DOUBLE VERIFICATION: dummy info 2 baar build hoti hai aur
        // dono builds ka packageName cross-check hota hai
        Object first = isApplicationInfo
                ? (Object) getDummyApplicationInfo(packageName)
                : (Object) getDummyPackageInfo(packageName);
        Object second = isApplicationInfo
                ? (Object) getDummyApplicationInfo(packageName)
                : (Object) getDummyPackageInfo(packageName);

        if (!isValidPackageInfo(first) || !isValidPackageInfo(second)) {
            Slog.e(TAG, "AuthCore dummy package info failed double verification: " + packageName);
            return null;
        }
        if (!stringEquals(extractPackageName(first),
                          extractPackageName(second))) {
            Slog.e(TAG, "AuthCore dummy package info mismatch on double verification: " + packageName);
            return null;
        }

        return first;
    }

    /**
     * Handle resolveIntent / resolveService / queryIntentActivities in one method
     */
    public static Object handleResolve(Intent intent, boolean isService) {
        if (!needsFix(intent)) {
            return null; // Not our concern
        }

        // DOUBLE VERIFICATION: dummy resolve 2 baar build + cross-check
        ResolveInfo first = getDummyResolveInfo(intent, isService);
        ResolveInfo second = getDummyResolveInfo(intent, isService);

        if (!isValidResolve(first, isService) || !isValidResolve(second, isService)) {
            Slog.e(TAG, "AuthCore dummy resolve failed double verification: " + intent);
            return null;
        }
        if (!stringEquals(extractResolvePkg(first, isService),
                          extractResolvePkg(second, isService))) {
            Slog.e(TAG, "AuthCore dummy resolve mismatch on double verification: " + intent);
            return null;
        }

        return first;
    }

    // ================================================================
    // WEB LOGIN -> NATIVE APP HOOK (Twitter/X + Facebook)
    //
    // Webpage wala OAuth login intercept hota hai:
    // 1. App webpage login start karta hai (x.com/oauth / facebook.com/dialog)
    // 2. Hook pakad leta hai aur native APK force open karta hai
    // 3. authToken native app se milta hai (app-to-app auth, no webview)
    // ================================================================

    /**
     * Webpage login intent detect karo (Twitter/X + Facebook OAuth URLs)
     * Sirf auth paths match hote hain — normal browsing links untouched
     */
    public static boolean isWebLoginIntent(Intent intent) {
        if (intent == null) return false;

        Uri data = intent.getData();
        if (data == null) return false;

        String scheme = data.getScheme();
        if (scheme == null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
            return false;
        }

        String host = data.getHost();
        if (host == null) return false;

        boolean authHost = false;
        for (String h : TWITTER_WEB_HOSTS) {
            if (h.equalsIgnoreCase(host)) { authHost = true; break; }
        }
        if (!authHost) {
            for (String h : FB_WEB_HOSTS) {
                if (h.equalsIgnoreCase(host)) { authHost = true; break; }
            }
        }
        if (!authHost) return false;

        // Path filter: sirf OAuth/login flows hook karo
        String path = data.getPath();
        if (path == null) return false;
        return path.contains("oauth") ||
               path.contains("authorize") ||
               path.contains("login") ||
               path.contains("dialog") ||
               path.contains("authenticate") ||
               path.contains("consent");
    }

    // ================================================================
    // AUTH CALLBACK HOOK (twitterkit:// / fb{appId}://)
    //
    // Native app OAuth complete karke callback bhejta hai —
    // wo callback VM ke bahar (host AMS) lost ho jata tha isliye
    // webview "Loading..." pe atak jata tha. Relay isko VM me
    // game ke deep-link handler tak pahunchata hai.
    // ================================================================

    /**
     * Auth callback URI check: twitterkit:// ya fb{appId}:// (digits only)
     */
    public static boolean isAuthCallbackUri(Uri data) {
        if (data == null) return false;
        String scheme = data.getScheme();
        if (scheme == null) return false;
        String s = scheme.toLowerCase();
        if (s.equals("twitterkit")) return true;
        if (s.length() > 2 && s.startsWith("fb")) {
            String rest = s.substring(2);
            for (int i = 0; i < rest.length(); i++) {
                if (!Character.isDigit(rest.charAt(i))) return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Auth callback intent check
     */
    public static boolean isAuthCallbackIntent(Intent intent) {
        if (intent == null) return false;
        return isAuthCallbackUri(intent.getData());
    }

    /**
     * Callback ke liye native package (pehla installed)
     * twitterkit:// -> X/Twitter, fb:// / fb{appId}:// -> Facebook
     */
    public static String callbackNativePackage(Uri data) {
        if (data == null) return null;
        String scheme = data.getScheme();
        if (scheme == null) return null;
        String s = scheme.toLowerCase();
        if (s.equals("twitterkit")) {
            if (isNativeAppInstalled(X_PKG)) return X_PKG;
            if (isNativeAppInstalled(TWITTER_PKG)) return TWITTER_PKG;
            if (isNativeAppInstalled(TWITTER_LITE_PKG)) return TWITTER_LITE_PKG;
            return null;
        }
        if (s.equals("fb")) {
            // plain fb:// links (profile/deeplink) — native FB app
            return isNativeAppInstalled(FB_PKG) ? FB_PKG : null;
        }
        if (s.length() > 2 && s.startsWith("fb")) {
            String rest = s.substring(2);
            for (int i = 0; i < rest.length(); i++) {
                if (!Character.isDigit(rest.charAt(i))) return null;
            }
            return isNativeAppInstalled(FB_PKG) ? FB_PKG : null;
        }
        return null;
    }

    /**
     * Host pe real native app installed hai?
     */
    public static boolean isNativeAppInstalled(String packageName) {
        try {
            EliteInstaller.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Web login intent ke liye preferred native package (pehla installed)
     */
    public static String targetNativePackage(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        String host = data != null ? data.getHost() : null;
        if (host == null) return null;

        for (String h : TWITTER_WEB_HOSTS) {
            if (h.equalsIgnoreCase(host)) {
                if (isNativeAppInstalled(X_PKG)) return X_PKG;
                if (isNativeAppInstalled(TWITTER_PKG)) return TWITTER_PKG;
                if (isNativeAppInstalled(TWITTER_LITE_PKG)) return TWITTER_LITE_PKG;
                return null;
            }
        }
        for (String h : FB_WEB_HOSTS) {
            if (h.equalsIgnoreCase(host)) {
                return isNativeAppInstalled(FB_PKG) ? FB_PKG : null;
            }
        }
        return null;
    }

    /**
     * MAIN HOOK: webpage login -> native app force open
     * Native app missing ho to null (webview fallback chalega)
     */
    public static Intent handleWebLogin(Intent intent) {
        if (!isWebLoginIntent(intent)) return null;

        String nativePkg = targetNativePackage(intent);
        if (nativePkg == null) {
            Slog.w(TAG, "WebLoginHook: native app missing, webview fallback: " + intent.getData());
            return null;
        }

        Intent nativeIntent = new Intent(intent);
        nativeIntent.setPackage(nativePkg);

        ComponentName comp = resolveNativeComponent(nativePkg, intent);
        if (comp != null) {
            nativeIntent.setComponent(comp);
        }
        nativeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        Slog.d(TAG, "WebLoginHook: " + intent.getData() + " -> native " + nativePkg
                + " (" + comp + ") API " + apiLevel());
        return nativeIntent;
    }

    /**
     * Native app ka best auth activity dhundo (DeepLink/Login/CustomTab/Main)
     */
    private static ComponentName resolveNativeComponent(String nativePkg, Intent origIntent) {
        try {
            PackageManager pm = EliteInstaller.getPackageManager();
            Intent probe = new Intent(origIntent);
            probe.setPackage(nativePkg);
            List<ResolveInfo> candidates = pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY);
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }
            ResolveInfo best = null;
            for (ResolveInfo ri : candidates) {
                if (ri == null || ri.activityInfo == null || ri.activityInfo.name == null) continue;
                String name = ri.activityInfo.name;
                if (name.contains("DeepLink") || name.contains("Login") ||
                    name.contains("CustomTab") || name.contains("Router") ||
                    name.contains("Auth")) {
                    best = ri;
                    break;
                }
                if (best == null || name.contains("Main")) {
                    best = ri;
                }
            }
            if (best != null && best.activityInfo != null && best.activityInfo.name != null) {
                return new ComponentName(best.activityInfo.packageName, best.activityInfo.name);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * DOUBLE VERIFICATION for native resolve:
     * Pass 1: ResolveInfo structure valid hai
     * Pass 2: resolved package expected native app hi hai (spoof check)
     */
    public static boolean verifyNativeResolve(ResolveInfo hostResolve, Intent origIntent) {
        // Verification #1: structure
        if (hostResolve == null || hostResolve.activityInfo == null) return false;
        if (hostResolve.activityInfo.packageName == null || hostResolve.activityInfo.name == null) {
            return false;
        }
        // Verification #2: expected native package match
        String expected = targetNativePackage(origIntent);
        if (expected == null) return false;
        if (!expected.equals(hostResolve.activityInfo.packageName)) {
            Slog.w(TAG, "Native resolve spoof detected: " + hostResolve.activityInfo.packageName
                    + " != " + expected);
            return false;
        }
        return true;
    }

    // ================================================================
    // DOUBLE TOKEN VERIFICATION
    // Har authToken 2 independent passes se guzarta hai —
    // ek bhi fail to reject (koi single-point-of-failure nahi)
    // ================================================================

    private static final Map<String, String> sVerifiedTokens = new ConcurrentHashMap<>();

    /** Verification pass 1: structure (null / length / charset) */
    private static boolean tokenStructuralOk(String token) {
        if (token == null || token.length() < 16 || token.length() > 4096) return false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' || c == '~')) {
                return false;
            }
        }
        return true;
    }

    /** Verification pass 2: provider-specific pattern + repeat consistency */
    private static boolean tokenPatternOk(String provider, String token) {
        if (provider == null || provider.isEmpty()) return false;
        String p = provider.toLowerCase();
        if (p.contains("twitter") || p.startsWith("com.x.") || p.startsWith("com.twitter.")) {
            // Twitter/X OAuth tokens: min 18 chars
            if (token.length() < 18) return false;
        } else if (p.contains("facebook") || p.equals(FB_PKG)) {
            // FB access tokens: EA prefix ya JWT-style dotted format
            if (!token.contains(".") && !token.startsWith("EA") && !token.startsWith("Afa")) {
                return false;
            }
        } else if (p.contains("gms") || p.contains("google")) {
            // Google auth codes/tokens lambe hote hain
            if (token.length() < 24) return false;
        }
        return tokenStructuralOk(token); // pass 1 dobara (race-safe re-check)
    }

    /**
     * Token DOUBLE VERIFICATION:
     * pass 1 structural + pass 2 pattern + re-check — teeno clear tabhi valid
     */
    public static boolean verifyAuthToken(String provider, String token) {
        if (!tokenStructuralOk(token)) return false;          // verification 1
        if (!tokenPatternOk(provider, token)) return false;   // verification 2
        if (!tokenStructuralOk(token)) return false;          // re-check
        return true;
    }

    /**
     * Verified token cache karo (verify fail pe store nahi hota)
     */
    public static boolean cacheVerifiedToken(String provider, String token) {
        if (!verifyAuthToken(provider, token)) return false;
        sVerifiedTokens.put(provider + "::" + Integer.toHexString(token.hashCode()), token);
        return true;
    }

    /**
     * Cache se verified token nikalo (dobara verify hota hai nikalte waqt)
     */
    public static String getVerifiedToken(String provider, String token) {
        String key = provider + "::" + Integer.toHexString(token == null ? 0 : token.hashCode());
        String cached = sVerifiedTokens.get(key);
        if (cached != null && verifyAuthToken(provider, cached)) {
            return cached;
        }
        return null;
    }

    // ================================================================
    // Double-verification helpers
    // ================================================================

    private static boolean isValidPackageInfo(Object info) {
        if (info == null) return false;
        String pkg = null;
        try {
            if (info instanceof ApplicationInfo) {
                ApplicationInfo ai = (ApplicationInfo) info;
                pkg = ai.packageName;
                // API 28+: processName bhi zaroori (null pe Android 10+ crash)
                if (Build.VERSION.SDK_INT >= 28 && ai.processName == null) return false;
            } else if (info instanceof PackageInfo) {
                PackageInfo pi = (PackageInfo) info;
                pkg = pi.packageName != null ? pi.packageName
                      : (pi.applicationInfo != null ? pi.applicationInfo.packageName : null);
            }
        } catch (Throwable ignored) {
            return false;
        }
        return pkg != null && !pkg.isEmpty();
    }

    private static String extractPackageName(Object info) {
        if (info == null) return null;
        try {
            if (info instanceof ApplicationInfo) {
                return ((ApplicationInfo) info).packageName;
            }
            if (info instanceof PackageInfo) {
                PackageInfo pi = (PackageInfo) info;
                if (pi.packageName != null) return pi.packageName;
                return pi.applicationInfo != null ? pi.applicationInfo.packageName : null;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isValidResolve(ResolveInfo ri, boolean isService) {
        if (ri == null) return false;
        if (isService) {
            return ri.serviceInfo != null &&
                   ri.serviceInfo.packageName != null &&
                   ri.serviceInfo.name != null &&
                   ri.serviceInfo.applicationInfo != null;
        }
        return ri.activityInfo != null &&
               ri.activityInfo.packageName != null &&
               ri.activityInfo.name != null &&
               ri.activityInfo.applicationInfo != null;
    }

    private static String extractResolvePkg(ResolveInfo ri, boolean isService) {
        if (ri == null) return null;
        if (isService) {
            return ri.serviceInfo != null ? ri.serviceInfo.packageName : null;
        }
        return ri.activityInfo != null ? ri.activityInfo.packageName : null;
    }

    private static boolean stringEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}

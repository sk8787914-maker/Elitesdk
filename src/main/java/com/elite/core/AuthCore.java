package com.elite.core;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;

import com.elite.EliteInstaller;
import com.elite.utils.Slog;

/**
 * Google Play Services aur Facebook login ka complete fix
 * Android 16 (API 36) tak supported — virtual main working
 *
 * SAFE MODE:
 * - Koi forged signature/certificate nahi (safe login)
 * - Host pe real GMS ho to wahi use hota hai (GmsCore pass-through)
 * - Dummy info sirf fallback hai taaki app crash na ho aur login UI khule
 */
public class AuthCore {

    private static final String TAG = "GmsFacebookFix";

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

    /**
     * Android 16+ (API 36) detection
     */
    public static boolean isAndroid16Plus() {
        return Build.VERSION.SDK_INT >= 36;
    }

    /**
     * Current API level (virtual target capped at 36)
     */
    public static int apiLevel() {
        int sdk = Build.VERSION.SDK_INT;
        return Math.min(sdk, 36);
    }

    /**
     * Check if package needs fix (GMS ya Facebook)
     */
    public static boolean needsFix(String packageName) {
        if (packageName == null) return false;

        // Google Play Services check
        if (packageName.equals(GMS_PKG) ||
            packageName.equals(GSF_PKG) ||
            packageName.equals(VENDING_PKG) ||
            packageName.startsWith("com.google.android.gms.") ||
            packageName.startsWith("com.google.android.gsf.")) {
            return true;
        }

        // Facebook check
        if (packageName.equals(FB_PKG) ||
            packageName.equals(FB_WAKIZASHI_PKG) ||
            packageName.equals(FB_LITE_PKG) ||
            packageName.equals(FB_ORCA_PKG) ||
            packageName.contains("facebook") ||
            packageName.startsWith("com.facebook.")) {
            return true;
        }

        return false;
    }

    /**
     * Check if intent needs fix (GMS ya Facebook intent)
     */
    public static boolean needsFix(Intent intent) {
        if (intent == null) return false;

        // Check component package
        if (intent.getComponent() != null) {
            String pkg = intent.getComponent().getPackageName();
            if (needsFix(pkg)) return true;
        }

        // Check action
        String action = intent.getAction();
        if (action != null) {
            if (action.contains("gms") ||
                action.contains("measurement") ||
                action.contains("signin") ||
                action.contains("adid") ||
                action.contains("fonts.update") ||
                action.contains("facebook") ||
                action.startsWith("fb") && action.contains("authorize") ||
                action.equals(GMS_SIGNIN_SERVICE) ||
                action.equals(GMS_MEASUREMENT_SERVICE)) {
                return true;
            }
        }

        // Check data URI (Facebook custom tab: fbauth:// / fb{appId}://authorize)
        android.net.Uri data = intent.getData();
        if (data != null) {
            String scheme = data.getScheme();
            if (scheme != null && scheme.startsWith("fb")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get dummy PackageInfo for GMS/Facebook (Android 16+ compatible)
     */
    public static PackageInfo getDummyPackageInfo(String packageName) {
        Slog.d(TAG, "Creating dummy PackageInfo for: " + packageName);

        PackageInfo dummyInfo = new PackageInfo();
        dummyInfo.packageName = packageName;

        // Modern GMS version (long versionCode — API 28+ major/minor split)
        if (packageName.equals(GMS_PKG) || packageName.equals(GSF_PKG)) {
            dummyInfo.versionCode = 254432030;
            dummyInfo.versionName = "25.44.32 (190400-693934542)";
            if (Build.VERSION.SDK_INT >= 28) {
                dummyInfo.versionCodeMajor = 0;
            }
        } else if (packageName.equals(VENDING_PKG)) {
            dummyInfo.versionCode = 84542220;
            dummyInfo.versionName = "45.2.22-31";
        } else if (isFacebook(packageName)) {
            dummyInfo.versionCode = 480921014;
            dummyInfo.versionName = "480.0.0.40.90";
        } else {
            dummyInfo.versionCode = 1;
            dummyInfo.versionName = "1.0";
        }

        // API 30+: requested permissions array hona chahiye (null-safe apps ke liye)
        dummyInfo.requestedPermissions = new String[0];

        ApplicationInfo appInfo = getDummyApplicationInfo(packageName);
        dummyInfo.applicationInfo = appInfo;

        return dummyInfo;
    }

    /**
     * Get dummy ApplicationInfo for GMS/Facebook (API 24 -> 36 compatible)
     */
    public static ApplicationInfo getDummyApplicationInfo(String packageName) {
        Slog.d(TAG, "Creating dummy ApplicationInfo for: " + packageName);

        ApplicationInfo dummyInfo = new ApplicationInfo();
        dummyInfo.packageName = packageName;
        dummyInfo.uid = EliteInstaller.getHostUid();
        dummyInfo.flags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        dummyInfo.sourceDir = "/system/app/" + packageName + "/" + packageName + ".apk";
        dummyInfo.publicSourceDir = dummyInfo.sourceDir;
        dummyInfo.dataDir = "/data/data/" + packageName;
        dummyInfo.deviceProtectedDataDir = "/data/user_de/0/" + packageName;
        dummyInfo.nativeLibraryDir = "/system/lib64";

        // API 28+: processName zaroori hai (null hone pe Android 10+ me crash)
        if (Build.VERSION.SDK_INT >= 28) {
            dummyInfo.processName = packageName;
        }
        dummyInfo.targetSdkVersion = apiLevel();

        // API 26+: app category (GMS = undefined, safe default)
        if (Build.VERSION.SDK_INT >= 26) {
            dummyInfo.category = ApplicationInfo.CATEGORY_UNDEFINED;
        }

        // API 29+: apn-less storage, seedling flags safe rakho
        if (Build.VERSION.SDK_INT >= 29) {
            dummyInfo.enabled = true;
        }

        // API 31+: compileSdk fields (Android 12+ pe apps check karte hain)
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                java.lang.reflect.Field f1 = ApplicationInfo.class.getField("compileSdkVersion");
                f1.setInt(dummyInfo, 36);
                java.lang.reflect.Field f2 = ApplicationInfo.class.getField("compileSdkVersionCodename");
                f2.set(dummyInfo, "16");
            } catch (Throwable ignored) {
            }
        }

        return dummyInfo;
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
     * Check if package is Facebook family
     */
    private static boolean isFacebook(String packageName) {
        return packageName.equals(FB_PKG) ||
               packageName.equals(FB_LITE_PKG) ||
               packageName.equals(FB_ORCA_PKG) ||
               packageName.equals(FB_WAKIZASHI_PKG);
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
            if (action.contains("gms") || action.contains("measurement") ||
                action.contains("signin") || action.contains("adid")) {
                return GMS_PKG;
            }
            if (action.contains("facebook")) {
                return FB_PKG;
            }
        }

        // Facebook custom tab scheme check
        android.net.Uri data = intent.getData();
        if (data != null && data.getScheme() != null && data.getScheme().startsWith("fb")) {
            return FB_PKG;
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
            if (action.contains("adid")) {
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
     * Get activity name for intent (FB login activities)
     */
    private static String getActivityName(Intent intent, String packageName) {
        if (intent.getComponent() != null) {
            return intent.getComponent().getClassName();
        }

        String action = intent.getAction();
        if (action != null) {
            if (action.contains("facebook")) {
                return "com.facebook.katana.ProxyAuthActivity";
            }
        }

        // Facebook custom tab (login flow uses this)
        android.net.Uri data = intent.getData();
        if (data != null && data.getScheme() != null && data.getScheme().startsWith("fb")) {
            return "com.facebook.CustomTabActivity";
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

        if (isApplicationInfo) {
            return getDummyApplicationInfo(packageName);
        } else {
            return getDummyPackageInfo(packageName);
        }
    }

    /**
     * Handle resolveIntent and resolveService in one method
     */
    public static Object handleResolve(Intent intent, boolean isService) {
        if (!needsFix(intent)) {
            return null; // Not our concern
        }

        return getDummyResolveInfo(intent, isService);
    }
}

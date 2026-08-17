// Ye alag file mein rahega - simple version
package com.elite.core.system.api;

import android.MetaCore.RemoteManager;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class MetaActivationManager {
    
    /* ================= ACTIVATE SDK ================= */
    public static void activateSdk(final String userkey) {
        try {
            RemoteManager.getInstance().activateSdk(userkey);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /* ================= GET SERVER MESSAGE ================= */
    public static String getServerMessage() {
        try {
            return RemoteManager.getInstance().getServerMessage();
        } catch (Throwable e) {
            e.printStackTrace();
            return "ERROR: FAILED TO GET SERVER MESSAGE";
        }
    }

    /* ================= GET PANEL URL ================= */
    public static String getPanelUrl() {
        try {
            return RemoteManager.getInstance().getPanelUrl();
        } catch (Throwable e) {
            e.printStackTrace();
            return "";
        }
    }

    /* ================= CHECK SDK STATUS ================= */
    public static boolean getActivatedStatus() {
        try {
            return RemoteManager.getInstance().getActivatedSdk();
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }
}
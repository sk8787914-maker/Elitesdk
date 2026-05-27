package com.elite.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;

import com.elite.EliteInstaller;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Best-effort crash guard for SDK-side crashes.
 */
public final class CrashFixHelper {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private CrashFixHelper() {
    }

    public static void install(Context context) {
        if (context == null || !INSTALLED.compareAndSet(false, true)) {
            return;
        }

        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                Slog.e("CrashFixHelper", "Caught uncaught exception from thread: " + thread.getName(), throwable);
            } catch (Throwable ignored) {
            }

            final boolean mainThreadCrash = thread == Looper.getMainLooper().getThread();
            if (mainThreadCrash) {
                safeScheduleRestart(context);
                // Do not call previous for main thread crash to reduce forced process termination.
                return;
            }

            if (previous != null) {
                try {
                    previous.uncaughtException(thread, throwable);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static void safeScheduleRestart(Context context) {
        try {
            String hostPkg = EliteInstaller.getHostPkg();
            if (hostPkg == null) {
                return;
            }
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(hostPkg);
            if (launchIntent == null) {
                return;
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            int flags = PendingIntent.FLAG_CANCEL_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pi = PendingIntent.getActivity(context, 7357, launchIntent, flags);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.setExact(AlarmManager.ELAPSED_REALTIME,
                        SystemClock.elapsedRealtime() + 600L,
                        pi);
            }
        } catch (Throwable ignored) {
        }
    }
}

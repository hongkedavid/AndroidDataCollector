package umich.edu.datacollector;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.util.Log;


/**
 * Created by kehong on 2/7/17.
 * This class is responsible for triggering APK update.
 */
public class UpdateReceiver extends BroadcastReceiver {
    private final String TAG = "UpdateReceiver";

    private final String ACTION = "UPDATE_ALARM";

    public void checkForUpdate(Context context) {
        // For testing purpose, always update without checking server-side version
        /*
        int serverCode = 1000;
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (serverCode > pInfo.versionCode) {
                Installer mInstaller = new Installer(context);
                Thread t = new Thread(mInstaller);
                t.start();
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        */
        // TODO: maintain a persistent Installer object with usage states
        // and a listener to instantiate an Installer thread for each update checking in UpdateService
        Installer mInstaller = new Installer(context);
        Thread t = new Thread(mInstaller);
        t.start();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // To wake up for checking update even when screen is off
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();
        String action = intent.getAction();
        if (!action.equals(ACTION))
            return;
        Log.d(TAG, "Wakeup to check update " + intent.getAction());
        checkForUpdate(context);
        wl.release();
    }

    public void setAlarm(Context context) {
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), AlarmManager.INTERVAL_HALF_HOUR, pi); // Millisec * Second * Minute
        Log.d(TAG, "setAlarm");
    }

    public void cancelAlarm(Context context) {
        Intent i = new Intent(ACTION);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, i, 0);
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(sender);
        Log.d(TAG, "cancelAlarm");
    }
}

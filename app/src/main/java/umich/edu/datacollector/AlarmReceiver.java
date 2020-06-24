package umich.edu.datacollector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.PowerManager;
import android.app.AlarmManager;
import android.app.PendingIntent;

/**
 * Created by kehong on 2/4/17.
 */
public class AlarmReceiver extends BroadcastReceiver {
    private final String TAG = "AlarmReceiver";
    private final String ACTION = "WAKEUP_ALARM";

    @Override
    public void onReceive(Context context, Intent intent) {
        // To wake up for checking upload even when screen is off
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();
        String action = intent.getAction();
        if (!action.equals(ACTION))
            return;
        Log.d(TAG, "Wakeup after 15min " + intent.getAction());
        Intent i = new Intent(SendFiles.UPLOAD_ALARM);
        context.sendBroadcast(i);
        wl.release();
    }

    public void setAlarm(Context context) {
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ACTION); //new Intent(context, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi); // Millisec * Second * Minute
        Log.d(TAG, "setAlarm");
    }

    public void cancelAlarm(Context context) {
        Intent i = new Intent(ACTION); //new Intent(context, AlarmReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, i, 0);
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(sender);
        Log.d(TAG, "cancelAlarm");
    }

}
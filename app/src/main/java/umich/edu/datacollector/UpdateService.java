package umich.edu.datacollector;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by kehong on 2/6/17.
 * A background service to manage the APK update functionality for data collector.
 */
public class UpdateService extends Service {
    private final String TAG = "UpdateService";

    private UpdateReceiver updateAlarm;

    @Override
    public void onCreate() {
        Log.d(TAG, "service created");
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "service started");
        updateAlarm = new UpdateReceiver();
        updateAlarm.setAlarm(this);
        int START_STICKY = 1;
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "service on destroy");
    }

    public class LocalBinder extends Binder {
        UpdateService getService() {
            return UpdateService.this;
        }
    }

}

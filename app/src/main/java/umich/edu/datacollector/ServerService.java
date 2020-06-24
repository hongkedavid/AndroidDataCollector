package umich.edu.datacollector;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by kehong on 2/3/17.
 * A background service to manage the trace upload functionality for data collector.
 */
public class ServerService extends Service {
    private final String TAG = "ServerService";

//    private ConfigChange mConfigChange;

    public final static String UPLOAD_ON = "ON";
    public final static String UPLOAD_OFF = "OFF";

    private boolean networkListenerRegistered;
    private AlarmReceiver alarm;

    private boolean alarmListenerRegistered;

    private SendFiles mSender;

    @Override
    public void onCreate() {
        Log.d(TAG, "service created");
        networkListenerRegistered = false;
        alarm = null;
        alarmListenerRegistered = false;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "service started");
//        mConfigChange = new ConfigChange();
//        mConfigChange.start();

        IntentFilter filter_network = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        if (!networkListenerRegistered) {
            registerReceiver(networkbroadcastReceiver, filter_network);
            networkListenerRegistered = true;
        }

        mSender = new SendFiles(this.getApplicationContext());
        IntentFilter filter_alarm = new IntentFilter();
        filter_alarm.addAction(Installer.INSTALLING_MSG);
        filter_alarm.addAction(Installer.INSTALLED_MSG);
        filter_alarm.addAction(SendFiles.UPLOAD_ALARM);
        if (!alarmListenerRegistered) {
            registerReceiver(alarmbroadcastReceiver, filter_alarm);
            alarmListenerRegistered = true;
        }
        alarm = new AlarmReceiver();
        alarm.setAlarm(this);

        storeServiceState(UPLOAD_ON);
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
//        Log.d(TAG,"mConfigChange is " + mConfigChange);
//        if(mConfigChange != null)
//            mConfigChange.terminate();

        storeServiceState(UPLOAD_OFF);
        if (networkListenerRegistered)
            unregisterReceiver(networkbroadcastReceiver);
        if (alarmListenerRegistered)
            unregisterReceiver(alarmbroadcastReceiver);
        if (alarm != null) {
            alarm.cancelAlarm(this);
            alarm = null;
        }
        mSender.unregister(this.getApplicationContext());
        mSender = null;
    }

    public void storeServiceState(String service_state) {
        try {
            FileInputStream in = new FileInputStream(SendFiles.app_path+"defaultProperties");
            Properties defaultProps = new Properties();
            defaultProps.load(in);
            in.close();
            FileOutputStream out = new FileOutputStream(SendFiles.app_path+"defaultProperties");
            defaultProps.setProperty("UPLOAD_STATE", service_state);
            defaultProps.store(out, null);
            Log.d(TAG, "config: UPLOAD_STATE " + service_state);
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    BroadcastReceiver networkbroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION))
                return;
            Log.d(TAG, "Change in network connectivity");
        }
    };

    BroadcastReceiver alarmbroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!action.equals(Installer.INSTALLING_MSG) && !action.equals(Installer.INSTALLED_MSG) && !action.equals(SendFiles.UPLOAD_ALARM))
                return;
            if (action.equals(SendFiles.UPLOAD_ALARM)) {
                Log.d(TAG, "Checking upload conditions");
                if (mSender.shouldUpload(context)) {
                    Thread t = new Thread(mSender);
                    t.start();
                }
            }
            else if (alarm != null) {
                if (action.equals(Installer.INSTALLING_MSG)) {
                    //alarm.cancelAlarm(context);
                    Log.d(TAG, "Pause alarm for app updating");
                }
                else if (action.equals(Installer.INSTALLED_MSG)) {
                    //alarm.setAlarm(context);
                    Log.d(TAG, "Resume alarm for app updating");
                }
            }
        }
    };

    public class LocalBinder extends Binder {
        ServerService getService() {
            return ServerService.this;
        }
    }

}

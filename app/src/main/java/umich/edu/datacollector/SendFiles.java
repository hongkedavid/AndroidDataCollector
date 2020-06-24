package umich.edu.datacollector;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.util.Properties;
import java.util.Calendar;
import java.util.List;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;

/**
 * Created by kehong on 2/3/17.
 * This class is responsible for all of the policy decisions on when to actually send logs to a remote server.
 * It determine what files to upload from local storage and invokes LogUploader to send files.
 */
public class SendFiles implements Runnable {
    private final String TAG = "SendFiles";

    public final static int USER_MODE = 0;

    public final static String UPLOAD_ALARM = "UPLOAD_ALARM";

    private static final int START_HOUR = 0;
    private static final int END_HOUR = 5;

    private static final int MIN_IDLE_TIME = 1000 * 60 * 30; // 30min

    public static final String app_path = "/sdcard/datacollector/";
    public static final int SFTP_PORT = 22;

    public static final int CONNECTION_NONE = 0;
    public static final int CONNECTION_WIFI = 1;
    public static final int CONNECTION_3G = 2;

    private ConnectivityManager connectivityManager;
    private TelephonyManager telephonyManager;

    private String data_path;

    private LogUploader mUploader;
    private String serverIP;
    private int serverPort;

    private Context mContext;

    public SendFiles(Context context) {
        mContext = context;

        telephonyManager = (TelephonyManager)context.getSystemService(
                Context.TELEPHONY_SERVICE);
        connectivityManager = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);

        IntentFilter sfilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        sfilter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(screenbroadcastReceiver, sfilter);

        getConfig();
        if (data_path == null || serverIP == null || serverPort < 0)
            Log.d(TAG, "Missing config");
    }

    public void unregister(Context context) {
        context.unregisterReceiver(screenbroadcastReceiver);
    }

    public boolean launchUploader() {
        // Read config again in case config changes after initialization
        getConfig();
        if (data_path != null && serverIP != null && serverPort >= 0) {
            mUploader = new LogUploader(mContext, serverIP, serverPort);
            return true;
        }
        else {
            Log.d(TAG, "Missing config");
            return false;
        }
    }

    public void getConfig() {
        data_path = null;
        serverIP = null;
        serverPort = -1;
        try {
            Properties defaultProps = new Properties();
            FileInputStream in = new FileInputStream(app_path+"defaultProperties");
            defaultProps.load(in);
            if (defaultProps.containsKey("DATA_DIR"))
                data_path = defaultProps.getProperty("DATA_DIR");
            if (defaultProps.containsKey("SERVER_IP"))
                serverIP = defaultProps.getProperty("SERVER_IP");
            if (defaultProps.containsKey("SERVER_PORT"))
                serverPort = Integer.parseInt(defaultProps.getProperty("SERVER_PORT"));
            Log.d(TAG, "config: data " + data_path + " server " + serverIP + ":" + serverPort);
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateLastUploadTime(String utime) {
        try {
            FileInputStream in = new FileInputStream(app_path+"defaultProperties");
            Properties defaultProps = new Properties();
            defaultProps.load(in);
            in.close();
            FileOutputStream out = new FileOutputStream(app_path+"defaultProperties");
            defaultProps.setProperty("LAST_UPLOAD", utime);
            defaultProps.store(out, null);
            Log.d(TAG, "config: data " + data_path + " last upload " + utime);
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int connectionAvailable() {
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if(info == null) {
            return CONNECTION_NONE;
        }
        int netType = info.getType();
        int netSubtype = info.getSubtype();
        if (netType == ConnectivityManager.TYPE_WIFI) {
            return info.isConnected() ? CONNECTION_WIFI : CONNECTION_NONE;
        } else if (netType == ConnectivityManager.TYPE_MOBILE
                && (netSubtype == TelephonyManager.NETWORK_TYPE_UMTS || netSubtype == TelephonyManager.NETWORK_TYPE_EDGE)
                && !telephonyManager.isNetworkRoaming()) {
            return info.isConnected() ? CONNECTION_3G : CONNECTION_NONE;
        }
        return CONNECTION_NONE;
    }

    private boolean screenOff = false;
    private long screenOff_time;

    private long get_sleep_time() {
        if (screenOff)
            return (System.currentTimeMillis() - screenOff_time);
        else
            return 0;
    }

    BroadcastReceiver screenbroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF))    {
                screenOff = true;
                screenOff_time = System.currentTimeMillis();
                Log.d(TAG, "Screen off " + screenOff_time);
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                screenOff = false;
                long screenOn_time = System.currentTimeMillis();
                long sleep_time = screenOn_time - screenOff_time;
                Log.d(TAG, "Screen on " + screenOn_time + " sleep for " + sleep_time / 1000 + " sec");
            }
        }

    };

    @Override
    public void run() {
        if (launchUploader())
            ReadAndSend(data_path, USER_MODE);
    }

    private void ReadAndSend(String file_path, int mode) {
        File[] files = new File(file_path).listFiles();
        boolean uploaded = false;
        if (serverPort == SFTP_PORT) {
            uploaded = mUploader.sendSFTP(files);
        }
        else {
            for (File file : files) {
                //if (connectionAvailable() != CONNECTION_WIFI) // The connectivity can change, so check again
                //      break;
                Log.d("LogUploader", "filename  is " + file.getName() + " length " + file.length());
                byte[] file_buffer = new byte[(int)file.length()];
                try {
                    BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
                    int sz = in.read(file_buffer, 0, (int)file.length());
                    if (sz == -1) continue;
                    boolean success = mUploader.send(file.getName(), file_buffer, (int)file.length(), mode);
                    in.close();
                    if (success) {
                        uploaded = true;
                        // Delete uploaded files at once
                        file.delete();
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Record last upload time
        if (uploaded)
            updateLastUploadTime(Long.toString(System.currentTimeMillis()));
    }

   // TODO: how to detect no foreground activities
   private void getForegroundActivity(Context context) {
       ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
       List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
       Log.d(TAG, "Foregreound activity :" + taskInfo.get(0).topActivity.getPackageName()
               + " " + taskInfo.get(0).topActivity.getClassName());
   }

    private boolean isAppOnForeground(Context context) {
        ActivityManager activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null)
            return false;
       if (appProcesses.get(0).importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
           Log.d(TAG, "Foreground app " + appProcesses.get(0).processName);
       /*for (RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    //&& !appProcess.processName.equals("com.sec.android.app.launcher")) {
                Log.d(TAG, "Foreground app " + appProcess.processName);
                //return true;
            }
        }*/
        return (!appProcesses.get(0).processName.equals("com.sec.android.app.launcher"));
    }

    /* Should upload when both file exists and specified criteria satisfied */
    public boolean shouldUpload(Context ctx){
        File [] user_files = new File(data_path).listFiles();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        // Check if connected to WiFi
        boolean cond0 = (connectionAvailable() == CONNECTION_WIFI);
        // Check if it is midnight
        boolean cond1 = (hour >= START_HOUR && hour <= END_HOUR);
        // Check if there is no foreground activity
        boolean cond2 = (!isAppOnForeground(ctx));
        // Check sleep/screenoff time
        long sleep_time = get_sleep_time();
        boolean cond3 = (sleep_time >= MIN_IDLE_TIME);
        getForegroundActivity(ctx);
        Log.d(TAG, "current hour " + hour + " foregroundApp " + cond2 + " user idle time " + sleep_time / 60000 + " min " + cond3);
        return ((user_files.length != 0) && (cond1 || (cond2 && cond3)));
    }

}

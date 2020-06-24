package umich.edu.datacollector;

import android.location.LocationManager;
import android.location.LocationListener;
import android.location.Location;
import android.content.Context;
import android.net.TrafficStats;
import android.util.Log;
import android.os.Bundle;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.os.StatFs;
import android.os.Environment;
import android.content.BroadcastReceiver;
import android.os.BatteryManager;
import android.content.Intent;
import android.content.IntentFilter;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Calendar;
import java.text.SimpleDateFormat;


/**
 * Created by kehong on 2/5/17.
 * This class is responsible for polling client device states to be shown on UI.
 */
public class ClientInfo {
    private final String TAG = "ClientInfo";

    private GPSTracker gpsStat;
    private boolean gpsStatRunning;

    private TrafficStats netStat;

    private Intent batteryStatus;
    private boolean batteryStatusRunning;

    public ClientInfo(Context context) {
        gpsStat = new GPSTracker(context);
        gpsStatRunning = gpsStat.startTracker();
        netStat = new TrafficStats();
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = context.registerReceiver(batterybroadcastIntentReceiver, ifilter);
        batteryStatusRunning = true;
    }

    public void unregister(Context context) {
        gpsStat.stopTracker();
        gpsStatRunning = false;
        context.unregisterReceiver(batterybroadcastIntentReceiver);
        batteryStatusRunning = false;
    }

    public double getBatteryLevel() {
        if (!batteryStatusRunning)
            return -1.0;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return level / (double)scale;
    }

    public String getGPSInfo() {
        String gpsInfo = "";
        if (gpsStatRunning)
            gpsInfo = gpsStat.getLatitude() + ", " + gpsStat.getLongitude();
        return gpsInfo;
    }

    public String getAndroidVersion() {
        String release = Build.VERSION.RELEASE;
        //int sdkVersion = Build.VERSION.SDK_INT;
        return ("Android " + release);
    }

    public String getDeviceModel() {
        return Build.MODEL;
    }

    public double getFreeStorageRatio() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        if (Build.VERSION.SDK_INT >= 18)
            return (double)stat.getAvailableBlocksLong() / (double)stat.getBlockCountLong();
        else
            return (double)stat.getAvailableBlocks() / (double)stat.getBlockCount();
    }

    public long getFreeStorage() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        if (Build.VERSION.SDK_INT >= 18)
            return stat.getAvailableBytes();
        else
            return ((long)stat.getBlockSize() * (long)stat.getAvailableBlocks());
    }

    public String getAppVersion(Context context) {
        PackageInfo pInfo = null;
        try {
            pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return pInfo.versionName;
    }

    public long getTotalMobileData() {
        return (netStat.getMobileTxBytes() + netStat.getMobileRxBytes());
    }

    public long getTotalData() {
        return (netStat.getTotalRxBytes() + netStat.getTotalTxBytes());
    }

    public String getLastUploadTime() {
        String utime = "";
        try {
            Properties defaultProps = new Properties();
            FileInputStream in = new FileInputStream(SendFiles.app_path+"defaultProperties");
            defaultProps.load(in);
            if (defaultProps.containsKey("LAST_UPLOAD")) {
                utime = defaultProps.getProperty("LAST_UPLOAD");
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(Long.parseLong(utime));
                String dateFormat = "dd-MM-yyyy hh:mm:ss z";
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat);
                utime = simpleDateFormat.format(calendar.getTime());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return utime;
    }

    BroadcastReceiver batterybroadcastIntentReceiver = new BroadcastReceiver() {
        private boolean isCharging;
        private boolean isDischarging;

        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING) || (status == BatteryManager.BATTERY_STATUS_FULL);
            isDischarging = (status == BatteryManager.BATTERY_STATUS_DISCHARGING);
        }

        public boolean getChargingStatus() {
            return isCharging;
        }
    };

    private class GPSTracker implements LocationListener {

        // The minimum distance to change Updates in meters
        private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10 meters

        // The minimum time between updates in milliseconds
        private static final long MIN_TIME_BW_UPDATES = 1000 * 60 * 1; // 1 minute

        private final Context mContext;

        // Flag for GPS status
        boolean isGPSEnabled = false;

        // Flag for network status
        boolean isNetworkEnabled = false;

        // Flag for GPS status
        boolean canGetLocation = false;

        Location location; // Location
        double latitude; // Latitude
        double longitude; // Longitude

        protected LocationManager locationManager;

        private static final int requestCode = 4444;

        public GPSTracker(Context context) {
            this.mContext = context;
        }

        public boolean startTracker() {
            try {
                locationManager = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
                // Getting GPS status
                isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                // Getting network status
                isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

                if (ContextCompat.checkSelfPermission(mContext, android.Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED)
                    return this.canGetLocation; //ActivityCompat.requestPermissions(,
                    //        new String[] {android.Manifest.permission.ACCESS_COARSE_LOCATION}, requestCode);

                if (isGPSEnabled || isNetworkEnabled) {
                    this.canGetLocation = true;
                    if (isNetworkEnabled) {
                        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                                MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                        Log.d(TAG, "Network location enabled");
                        if (locationManager != null) {
                            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                            if (location != null) {
                                latitude = location.getLatitude();
                                longitude = location.getLongitude();
                            }
                        }
                    }
                    // If GPS enabled, get latitude/longitude using GPS Services
                    if (isGPSEnabled) {
                        if (location == null) {
                            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                                    MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                            Log.d(TAG, "GPS location enabled");
                            if (locationManager != null) {
                                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                                if (location != null) {
                                    latitude = location.getLatitude();
                                    longitude = location.getLongitude();
                                }
                            }
                        }
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            return this.canGetLocation;
        }

        public void stopTracker(){
            if (ContextCompat.checkSelfPermission(mContext, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)
                return;
            if (locationManager != null) {
                locationManager.removeUpdates(GPSTracker.this);
            }
        }

        public double getLatitude(){
            if(location != null){
                latitude = location.getLatitude();
            }
            return latitude;
        }

        public double getLongitude(){
            if(location != null){
                longitude = location.getLongitude();
            }
            return longitude;
        }

        @Override
        public void onLocationChanged(Location loc) {
            longitude = loc.getLongitude();
            latitude = loc.getLatitude();
        }

        @Override
        public void onProviderEnabled(String s) {}

        @Override
        public void onProviderDisabled(String s) {}

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

    }

}

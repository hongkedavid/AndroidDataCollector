package umich.edu.datacollector;

import android.os.Bundle;
import android.os.IBinder;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Properties;

/**
 * Created by kehong on 2/3/17.
 * MainActivity for DataCollector app.
 */
public class DataCollectorActivity extends AppCompatActivity {

    private String TAG = "DataCollectorActivity";

    private ServerService mServerService;
    private Button mServiceStartButton;
    private Intent mServerIntent;
    private boolean mServerIsRunning;
    private boolean mServerIsBound;

    private Button mRefreshButton;
    private ClientInfo clientDevice;

    private UpdateService mUpdateService;
    private Intent mUpdateIntent;
    private boolean mUpdateIsRunning;
    private boolean mUpdateIsBound;

    // Poll device states and post them on screen
    private void updateUsage() {
        TextView statTextView = (TextView)findViewById(R.id.statText);
        String utime = clientDevice.getLastUploadTime();
        String statInfo = "GPS: " + clientDevice.getGPSInfo() + "\n"
                + "OS: " + clientDevice.getAndroidVersion() + "\n"
                + "Model: " + clientDevice.getDeviceModel() + "\n"
                + "App version: " + clientDevice.getAppVersion(this.getApplicationContext()) + "\n"
                + "Free storage: " + new BigDecimal(clientDevice.getFreeStorage() / (1024*1024)).setScale(2, BigDecimal.ROUND_HALF_EVEN)
                + " MB (" + new BigDecimal(clientDevice.getFreeStorageRatio()*100).setScale(0, BigDecimal.ROUND_HALF_EVEN) + "%)\n"
                + "Network usage: " + new BigDecimal(clientDevice.getTotalData() / (1024*1024)).setScale(2, BigDecimal.ROUND_HALF_EVEN) + " MB" + "\n"
                + "Battery level: " + (clientDevice.getBatteryLevel() * 100) + "%" + "\n"
                + "Last upload: " + utime;
        statTextView.setText(statInfo);
    }

    private boolean shouldServerServiceRun() {
        String curr_state = ServerService.UPLOAD_OFF;
        try {
            Properties defaultProps = new Properties();
            FileInputStream in = new FileInputStream(SendFiles.app_path+"defaultProperties");
            defaultProps.load(in);
            if (defaultProps.containsKey("UPLOAD_STATE"))
                curr_state = defaultProps.getProperty("UPLOAD_STATE");
            Log.d(TAG, "config: UPLOAD_STATE " + curr_state);
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return (curr_state.equals(ServerService.UPLOAD_ON));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_collector);

        mServerIntent = new Intent(this, ServerService.class);
        mServiceStartButton = (Button)findViewById(R.id.serviceStartButton);
        mServiceStartButton.setOnClickListener(serviceStartButtonListener);
        mServerIsRunning = isServerServiceRunning();
        if (shouldServerServiceRun() && !mServerIsRunning) {
            startService(mServerIntent);
            mServerIsRunning = isServerServiceRunning();
            Log.d(TAG, "start upload service");
        }
        if (mServerIsRunning)
            doBindServerService();

        mRefreshButton = (Button)findViewById(R.id.refreshButton);
        mRefreshButton.setOnClickListener(refreshButtonListener);

        clientDevice = new ClientInfo(this.getApplicationContext());
        updateUsage();

        mUpdateIntent = new Intent(this, UpdateService.class);
        mUpdateIsRunning = isUpdateServiceRunning();
        if (!mUpdateIsRunning) {
            //Log.d(TAG, "update service not started");
            startService(mUpdateIntent);
            mUpdateIsRunning = isUpdateServiceRunning();
            Log.d(TAG, "start update service");
        }
        //else {
        doBindUpdateService();
        //}

        if (!mServerIsRunning) {
            mServiceStartButton.setBackgroundColor(Color.GREEN);
            mServiceStartButton.setText("Start service!");
        }
        else {
            mServiceStartButton.setBackgroundColor(Color.RED);
            mServiceStartButton.setText("Stop service!");
        }
        Log.d(TAG, "++ ON CREATE ++");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "++ ON START ++");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "+ ON RESUME +");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "- ON DESTROY -");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_data_collector, menu);
        return true;
    }

    private boolean isServerServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (RunningServiceInfo service: manager.getRunningServices(Integer.MAX_VALUE)) {
            if (ServerService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isUpdateServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (RunningServiceInfo service: manager.getRunningServices(Integer.MAX_VALUE)) {
            if (UpdateService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private Button.OnClickListener refreshButtonListener =
            new Button.OnClickListener() {
                public void onClick(View v) {
                    Log.d(TAG, "Button refresh has been clicked");
                    mRefreshButton.setEnabled(false);
                    updateUsage();
                    mRefreshButton.setEnabled(true);
                }
            };

    private Button.OnClickListener serviceStartButtonListener =
            new Button.OnClickListener() {
                public void onClick(View v) {
                    Log.d(TAG, "Button service has been clicked "+ mServerIsRunning);
                    mServiceStartButton.setEnabled(false);
                    if (mServerIsRunning) {
                        doUnbindServerService();
                        stopService(mServerIntent);
                        //if (clientDevice != null)
                        //    clientDevice.unregister(this.getApplicationContext());
                        mServiceStartButton.setBackgroundColor(Color.GREEN);
                        mServiceStartButton.setText("Start service!");
                        mServerIsRunning = false;
                    }
                    else {
                        startService(mServerIntent);
                        mServiceStartButton.setBackgroundColor(Color.RED);
                        mServiceStartButton.setText("Stop service!");
                        mServerIsRunning = true;
                        doBindServerService();
                    }
                    mServiceStartButton.setEnabled(true);
                }
            };

    private ServiceConnection mServerConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mServerService = ((ServerService.LocalBinder)service).getService();
        }

        public void onServiceDisconnected(ComponentName className) {
            mServerService = null;
        }
    };

    void doBindServerService() {
        bindService(mServerIntent, mServerConnection, Context.BIND_AUTO_CREATE);
        mServerIsBound = true;
    }

    void doUnbindServerService() {
        if (mServerIsBound) {
            unbindService(mServerConnection);
            mServerIsBound = false;
        }
    }

    private ServiceConnection mUpdateConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mUpdateService = ((UpdateService.LocalBinder)service).getService();
        }

        public void onServiceDisconnected(ComponentName className) {
            mUpdateService = null;
        }
    };

    void doBindUpdateService() {
        bindService(mUpdateIntent, mUpdateConnection, Context.BIND_AUTO_CREATE);
        mUpdateIsBound = true;
    }

    void doUnbindUpdateService() {
        if (mUpdateIsBound) {
            unbindService(mUpdateConnection);
            mUpdateIsBound = false;
        }
    }
}

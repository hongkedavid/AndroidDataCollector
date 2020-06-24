package umich.edu.datacollector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by kehong on 2/3/17.
 * This class is responsible for auto-starting ServerService and UpdateService after bootup or update.
 */
public class StartupReceiver extends BroadcastReceiver {
    private static final String TAG = "StartupReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, ServerService.class);
        if (shouldServerServiceRun()) {
            context.startService(serviceIntent);
            Log.d(TAG, "start server service");
        }
        Intent updateIntent = new Intent(context, UpdateService.class);
        context.startService(updateIntent);
        Log.d(TAG, "start update service");
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

}

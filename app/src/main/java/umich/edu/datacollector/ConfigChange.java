package umich.edu.datacollector;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;
import java.util.ArrayDeque;
import java.lang.Process;

import android.util.Log;

/**
 * Created by kehong on 2/3/17.
 */
public class ConfigChange extends Thread {
    private final String TAG = "ConfigChange";
    private final int CHECKING_INTERVAL = 10* 60 * 1000; // 10 minutes

    private static final int NUM_SAMPLE = 10;


    private ArrayDeque<Integer> configQueue;
    private int [] count;

    private Random random;

    private boolean active;

    private long [] statsBuf;
    private long mTotal;
    private long mUser;
    private long mSystem;

    public ConfigChange() {
        int i =0;
        count = new int[4];
        for(i=0; i<4; i++){
            count[i]=0;
        }
        random = new Random();
        configQueue = new ArrayDeque();
        active = true;

        statsBuf = new long[8];
        mTotal = 0;
        mUser = 0;
        mSystem = 0;
    }

    @Override
    public void run() {
        while((active) && (!interrupted()))
        {
            if(ShouldChangeConfig())
            {
                Log.i(TAG,"Should change configuration");
                //ChangeConfig();
            }
            try {
                sleep(CHECKING_INTERVAL);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    public void terminate(){
        active = false;
        interrupt();
    }

    /*
     * Monitor the average utilization for NUM_SAMPLE seconds and then determine
     */
    public boolean ShouldChangeConfig(){
        //fastReadStats();
        double avgUsage = 0;
        for(int i=0; i< NUM_SAMPLE; i++)
        {
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                // The thread got terminated
                return false;
            }
        }
        //avgUsage = fastReadStats();
        if(avgUsage < 10.0)
            return true;
        else
            return false;
    }
/*
    public double fastReadStats(){
        SystemInfo sysInfo = SystemInfo.getInstance();

        if(!sysInfo.getUsrSysTotalTime(statsBuf)){
            Log.d(TAG,"Failed to read cpu times");
            return 0.0;
        }
        long usrTime = statsBuf[SystemInfo.INDEX_USER_TIME];
        long sysTime = statsBuf[SystemInfo.INDEX_SYS_TIME];
        long totalTime = statsBuf[SystemInfo.INDEX_TOTAL_TIME];

        double usr_sys_perc = updateStats(usrTime, sysTime, totalTime);
        Log.d(TAG,"User stats: "+ usrTime + " "+ sysTime + " "+ totalTime+ " "+ usr_sys_perc);
        return usr_sys_perc;
    }

    private double updateStats(long user, long system, long total)
    {
        double user_sys_perc = 0.0;
        double user_perc = 0.0;
        double sys_perc = 0.0;
        if (mTotal != 0 || total >= mTotal) {
            long duser = user - mUser;
            long dsystem = system - mSystem;
            long dtotal = total - mTotal;
            user_sys_perc = (double)(duser+dsystem)*100.0/dtotal;
            user_perc = (double)(duser)*100.0/dtotal;
            sys_perc = (double)(dsystem)*100.0/dtotal;
        }
        mUser = user;
        mSystem = system;
        mTotal = total;
        return user_sys_perc;
    }
*/
}

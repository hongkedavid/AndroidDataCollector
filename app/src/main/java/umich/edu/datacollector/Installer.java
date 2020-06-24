package umich.edu.datacollector;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.File;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Created by kehong on 2/7/17.
 * This class is responsible for downloading and installing a newer version of APK (root access required on a device).
 */
public class Installer implements Runnable {
    private final String TAG = "Installer";

    public static final String INSTALLING_MSG = "INSTALLING_APK";
    public static final String INSTALLED_MSG = "INSTALLED_APK";

    private Context mContext;

    // Server config
    private String serverIP;
    private int serverPort;
    private String userName;

    // Current app verson code
    private int curr_vcode;

    public Installer(Context context) {
        Log.d(TAG, "start self-updating...");
        mContext = context;
        getConfig();
    }

    public void getConfig() {
        serverIP = null;
        serverPort = -1;
        try {
            Properties defaultProps = new Properties();
            FileInputStream in = new FileInputStream(SendFiles.app_path+"defaultProperties");
            defaultProps.load(in);
            if (defaultProps.containsKey("SERVER_IP"))
                serverIP = defaultProps.getProperty("SERVER_IP");
            if (defaultProps.containsKey("SERVER_PORT"))
                serverPort = Integer.parseInt(defaultProps.getProperty("SERVER_PORT"));
            Log.d(TAG, "config: server " + serverIP + ":" + serverPort);
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Get version code
        curr_vcode = 0;
        try {
            PackageInfo pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            curr_vcode = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String getKeyFile() {
        String keyFile_name = null;
        userName = null;
        try {
            Properties defaultProps = new Properties();
            FileInputStream in = new FileInputStream(SendFiles.app_path+"defaultProperties");
            defaultProps.load(in);
            if (defaultProps.containsKey("USER"))
                userName = defaultProps.getProperty("USER");
            if (defaultProps.containsKey("KEY_FILE"))
                keyFile_name = defaultProps.getProperty("KEY_FILE");
            Log.d(TAG, "config: user " + userName + " keyFile " + keyFile_name);
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (keyFile_name != null && userName != null) {
            File keyFile = new File(keyFile_name);
            if (keyFile.exists() && keyFile.canRead())
                return (keyFile + "");
        }
        return null;
    }

    private String getApkFile() {
        String apkFile = "";
        try {
            Properties defaultProps = new Properties();
            FileInputStream in = new FileInputStream(SendFiles.app_path+"defaultProperties");
            defaultProps.load(in);
            if (defaultProps.containsKey("APK_FILE"))
                apkFile = defaultProps.getProperty("APK_FILE");
            Log.d(TAG, "config: apkFile " + apkFile);
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return apkFile;
    }

    private String getApkConf() {
        String apkConf = "";
        try {
            Properties defaultProps = new Properties();
            FileInputStream in = new FileInputStream(SendFiles.app_path+"defaultProperties");
            defaultProps.load(in);
            if (defaultProps.containsKey("APK_CONF"))
                apkConf = defaultProps.getProperty("APK_CONF");
            Log.d(TAG, "config: apkConf " + apkConf);
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return apkConf;
    }

    public String downloadApk() {
        Log.d(TAG, "downloading new APK file");
        boolean conStatus = false;
        Session session = null;
        Channel channel = null;
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        Log.d(TAG, "SFTP session " + conStatus);

        try {
            JSch ssh = new JSch();
            String path = getKeyFile();
            if (path != null) {
                ssh.addIdentity(path);
                session = ssh.getSession(userName, serverIP, serverPort);
                session.setConfig(config);
                session.connect();
                conStatus = session.isConnected();
                Log.d(TAG, "SFTP session " + conStatus);
                channel = session.openChannel("sftp");
                channel.connect();
                ChannelSftp sftp = (ChannelSftp) channel;
                String apkConf = getApkConf();
                BufferedInputStream bis = new BufferedInputStream(sftp.get(apkConf));
                byte[] buffer = new byte[LogUploader.SOCK_BUFFER];
                int readCount;
                String new_vcode = "";
                while((readCount = bis.read(buffer)) != -1) {
                    new_vcode += new String(buffer, 0, readCount);
                }
                Log.d(TAG, "curr vcode " + curr_vcode + " new vcode " + new_vcode);
                // Determine if a newer version of APK exists
                if (curr_vcode < Integer.parseInt(new_vcode.trim())) {
                    // Notify ServerService ongoing APK update
                    Intent i = new Intent(INSTALLING_MSG);
                    mContext.sendBroadcast(i);
                    String apkFile = getApkFile();
                    bis = new BufferedInputStream(sftp.get(apkFile));
                    String local_apk_file = SendFiles.app_path + "app-debug.apk";
                    File newFile = new File(local_apk_file);
                    OutputStream os = new FileOutputStream(newFile);
                    BufferedOutputStream bos = new BufferedOutputStream(os);
                    buffer = new byte[LogUploader.SOCK_BUFFER];
                    while ((readCount = bis.read(buffer)) > 0) {
                        bos.write(buffer, 0, readCount);
                    }
                    bos.close();
                    return local_apk_file;
                }
            }
        } catch (JSchException e) {
            e.printStackTrace();
        } catch (SftpException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean checkValidApk(String filePath) {
        File file = new File(filePath);
        return file.exists();
    }

    public boolean installApk(String filePath) {
        Log.d(TAG, "installing new APK file");
        try {
            // For testing purpose, intentionally making update time much longer than actual
            /*try {
                Thread.currentThread().sleep(5 * 60 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }*/
            Runtime.getRuntime().exec(new String[] {"su", "-c", "pm install -r /sdcard/datacollector/app-debug.apk"});
            return true;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void run() {
        String file_path = downloadApk();
        if (file_path != null) {
            if (checkValidApk(file_path)) {
                if (installApk(file_path)) {
                    // Notify ServerService the completion of APK update
                    Intent i = new Intent(INSTALLED_MSG);
                    mContext.sendBroadcast(i);
                    File file = new File(file_path);
                    file.delete();
                    /*try {
                        Runtime.getRuntime().exec(new String[] {"su", "-c", "am startservice -n umich.edu.datacollector/.UpdateService"});
                        Log.d(TAG, "restart update service");
                        Runtime.getRuntime().exec(new String[] {"su", "-c", "am startservice -n umich.edu.datacollector/.ServerService"});
                        Log.d(TAG, "restart upload service");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }*/
                    Log.d(TAG, "successful app update at " + System.currentTimeMillis());
                }
                else {
                    Log.d(TAG, "fail to update app");
                }
            }
        }
    }

}

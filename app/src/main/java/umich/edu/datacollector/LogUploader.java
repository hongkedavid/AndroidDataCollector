package umich.edu.datacollector;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.jcraft.jsch.*;

/**
 * Created by kehong on 2/3/17.
 * This class is responsible for actually sending the data should it decide that it is appropriate.
 */
public class LogUploader {
    private final String TAG = "LogUploader";

    public final static int SOCK_BUFFER = 1024;
    public final static int USER_MODE = 0;

    // Maximum bytes per upload: 1M
    public static final int UPLOAD_THRESHOLD = 1*1024*1024;

    // Server config
    private String serverIP;
    private int serverPort;
    private String userName;

    private TelephonyManager telephonyManager;

    public LogUploader(Context context, String sIP, int sPort) {
        telephonyManager = (TelephonyManager)context.getSystemService(
                Context.TELEPHONY_SERVICE);
        serverIP = sIP;
        serverPort = sPort;
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

    private String getRemoteDir() {
        String remoteDir = "";
        try {
            Properties defaultProps = new Properties();
            FileInputStream in = new FileInputStream(SendFiles.app_path+"defaultProperties");
            defaultProps.load(in);
            if (defaultProps.containsKey("REMOTE_DIR"))
                remoteDir = defaultProps.getProperty("REMOTE_DIR");
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "config: remoteDir " + remoteDir + getDeviceID() + "/");
        return (remoteDir + getDeviceID() + "/");
    }

    public boolean sendSFTP(File[] files) {
        boolean conStatus = false;
        Session session = null;
        Channel channel = null;
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        Log.d(TAG, "SFTP session " + conStatus);

        boolean uploaded = false;
        JSch ssh = new JSch();
        String path = getKeyFile();

        if (path != null) {
            try {
                ssh.addIdentity(path);
                session = ssh.getSession(userName, serverIP, serverPort);
                session.setConfig(config);
                session.connect();
                conStatus = session.isConnected();
                Log.d(TAG, "SFTP session " + conStatus);
                channel = session.openChannel("sftp");
                channel.connect();
            } catch (JSchException e) {
                e.printStackTrace();
            }
            ChannelSftp sftp = (ChannelSftp) channel;
            String deviceDir = getRemoteDir();
            SftpATTRS attrs = null;
            try {
                attrs = sftp.stat(deviceDir);
            } catch (SftpException e) {
                try {
                    if (attrs == null) {
                        sftp.mkdir(deviceDir);
                        Log.d(TAG, "mkdir " + deviceDir);
                    }
                } catch (SftpException ee) {
                    ee.printStackTrace();
                }
            }
            try {
                for (File file : files) {
                    sftp.put(file.getAbsolutePath(), deviceDir);
                    if (sftp.ls(deviceDir + file.getName()).size() > 0) {
                        Log.d(TAG, "filename " + file.getName() + " length "
                                + file.length() + " uploaded to " + deviceDir);
                        uploaded = true;
                        // Delete uploaded files at once
                        file.delete();
                    }
                }
            } catch (SftpException e) {
                e.printStackTrace();
            }
        }

        return uploaded;
    }

    public boolean send(String runID, byte[] source, int len, int mode) {
        // Only send when connected to WiFi
        //if (connectionAvailable() != CONNECTION_WIFI)
        //    return false;
        Log.i(TAG, "Sending log data " + runID + " " + len + " " + mode);
        Socket s = new Socket();
        try {
            s.setSoTimeout(4000);
            s.connect(new InetSocketAddress(serverIP, serverPort), 15000);
        } catch(IOException e) {
              /* Failed to connect to server.  Try again later.
               */
            return false;
        }
        try {
            BufferedOutputStream sockOut = new BufferedOutputStream(
                    s.getOutputStream(), SOCK_BUFFER);
              /* Write the prefix string to the server. */
            sockOut.write(getPrefix(runID, len, mode));
            sockOut.write(0);
              /* Write the array to the server. */
            int offset = 0;
            while (true) {
                int sz = (len - offset) > SOCK_BUFFER? SOCK_BUFFER: (len-offset);
                if (sz <= 0) break;
                sockOut.write(source, offset, sz);
                offset += sz;
            }
            sockOut.flush();
            int response = s.getInputStream().read();
            s.close();

            if(response != 0) {
                Log.w(TAG, "Log data not accepted by server");
                //return false;
            }
        } catch(SocketTimeoutException e) {
              /* Connection trouble with server.  Try again later.
               */
            return false;
        } catch(IOException e) {
            Log.w(TAG, "Unexpected exception sending log.  Dropping log data");
            e.printStackTrace();
            return false;
        }
        Log.d(TAG, "Sending log data " + runID + " " + len + " "+ mode + " success");
        return true;
    }

    private byte[] getPrefix(String runID, long payloadLength, int mode) {
        String deviceID = telephonyManager.getDeviceId();
        //return (getMD5(deviceID) + "|" + mode + "|" + runID + "|" + payloadLength).getBytes();
        return (getMD5(deviceID) + "|" + runID + "|" + payloadLength).getBytes();
    }

    private String getDeviceID() {
        String deviceID = telephonyManager.getDeviceId();
        return getMD5(deviceID);
    }

    private String getMD5(String s){
        MessageDigest m = null;
        try {
            m = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        m.update(s.getBytes(), 0, s.length());
        return new BigInteger(1, m.digest()).toString(16);
    }

}

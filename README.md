The data collector is an Android app that serves the purpose of sending the collected data (stored in a folder) regularly to a server.

To run the app, you need to create a new directory /sdcard/datacollector on your device and push config/defaultProperties to /sdcard/datacollector using adb. 

The parameters in the config file (i.e., defaultProperties) should be customized accordingly as follows. 
We asssume that traces are uploaded to and new APK are downloaded from a same server.

DATA_DIR denotes the on-device directory in which trace files are stored

SERVER_IP denotes the IP address of the server for trace uploading

USER denotes the user name for access to the server over SFTP

KEY_FILE denotes the location of user private key file on the device

REMOTE_DIR denotes the target direction on the server for trace uploading

APK_FILE denotes the location of new APK on the server

APK_CONF denotes the location of the config file storing the app version code for the new APK (e.g., config/verson.conf) on the server

UPLOAD_STATE denotes the ON/OFF state of ServerService (for trace uploading)

To build a newer version of APK, configure the version code for the app in app/build.gradle

package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 11/9/2015.
 */
public class Constants {


    public interface ACTION {
        public static String MAIN_ACTION = "com.marothiatechs.foregroundservice.action.main";
        public static String INIT_ACTION = "com.marothiatechs.foregroundservice.action.init";
        public static String STARTFOREGROUND_ACTION = "com.marothiatechs.foregroundservice.action.startforeground";
        public static String STOPFOREGROUND_ACTION = "com.marothiatechs.foregroundservice.action.stopforeground";
    }

    public interface NOTIFICATION_ID {
        public static int FOREGROUND_SERVICE = 101;
        public static String BROADCAST_TAG = "com.marothiatechs.foregroundservice.action.broadcast";
        public static String LABEL_TAG = "com.marothiatechs.foregroundservice.action.broadcast.label";
    }

    public interface SENSORS {
        public static String SENSOR1 = "F8:C3:26:64:08:AA";//""D2:02:B3:1C:D2:C3";
        public static String SENSOR2 = "FB:98:22:86:BC:26";//"EB:0B:E2:6E:8C:52";
        public static String SENSOR3 = "EE:F2:E9:A5:78:6D";//"F7:FC:FF:D2:F1:66";
        public static String SENSOR4 = "DA:87:99:11:4B:EF";//"EE:9F:61:85:DA:6C";
        public static String SENSOR5 = "E5:B8:F6:89:06:95";//"E8:BD:10:7D:58:B4";
    }
}
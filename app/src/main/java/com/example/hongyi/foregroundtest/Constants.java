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
        public static String SENSOR1 = "D2:02:B3:1C:D2:C3";
        public static String SENSOR2 = "EB:0B:E2:6E:8C:52";
        public static String SENSOR3 = "F7:FC:FF:D2:F1:66";
        public static String SENSOR4 = "EE:9F:61:85:DA:6C";
        public static String SENSOR5 = "E8:BD:10:7D:58:B4";
    }
}
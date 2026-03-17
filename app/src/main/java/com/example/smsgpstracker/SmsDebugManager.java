package com.example.smsgpstracker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmsDebugManager {

    // LISTA LOG
    private static final List<String> smsLog = new ArrayList<>();

    // registra SMS TX
    public static void logTx(String message) {

        String time = new SimpleDateFormat(
                "HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());

        smsLog.add("TX | " + time + " | " + message);
    }

    // restituisce log
    public static List<String> getLogs() {
        return smsLog;
    }

    // pulisce log
    public static void clear() {
        smsLog.clear();
    }
}
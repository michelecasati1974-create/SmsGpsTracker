package com.example.smsgpstracker;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.*;
import android.telephony.SmsManager;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.*;

public class TxForegroundService extends Service {

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_UPDATE =
            "com.example.smsgpstracker.TX_UPDATE";

    private static final String CHANNEL_ID = "TxServiceChannel";

    private FusedLocationProviderClient fusedClient;

    private boolean isRunning = false;
    private int smsSent = 0;
    private int maxSms = 0;
    private int intervalMinutes = 1;
    private String phoneNumber = "";

    private long cycleStartTime = 0;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    private Runnable uiRunnable;

    public enum TxStatus {
        IDLE,
        WAITING,
        TRACKING
    }

    @Override
    public void onCreate() {
        super.onCreate();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {

            String action = intent.getAction();

            if (ACTION_START.equals(action)) {
                phoneNumber = intent.getStringExtra("phone");
                maxSms = intent.getIntExtra("maxSms", 10);
                intervalMinutes = intent.getIntExtra("interval", 1);
                startTracking();
            }

            if (ACTION_STOP.equals(action)) {
                stopTracking(true);
            }
        }

        return START_STICKY;
    }

    private void startTracking() {

        if (isRunning) return;

        isRunning = true;
        smsSent = 0;
        cycleStartTime = System.currentTimeMillis();

        startForeground(1, buildNotification());

        sendControlSms("CTRL:START");

        startUiTimer();
        startTimer();

        sendUpdate(TxStatus.WAITING, 0, 0);
    }

    private void startUiTimer() {

        uiRunnable = new Runnable() {
            @Override
            public void run() {

                if (!isRunning) return;

                int seconds =
                        (int) ((System.currentTimeMillis() - cycleStartTime) / 1000);

                sendUpdate(TxStatus.TRACKING, seconds, smsSent);

                uiHandler.postDelayed(this, 1000);
            }
        };

        uiHandler.post(uiRunnable);
    }

    private void startTimer() {

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (!isRunning) return;

                requestLocation();

                handler.postDelayed(this, intervalMinutes * 60 * 1000L);
            }
        }, 1000);
    }

    private void requestLocation() {

        if (smsSent >= maxSms) {
            sendControlSms("CTRL:END");
            stopTracking(false);
            return;
        }

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest request =
                LocationRequest.create()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setInterval(2000)
                        .setNumUpdates(1);

        fusedClient.requestLocationUpdates(
                request,
                new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult result) {

                        if (result == null) return;

                        Location location = result.getLastLocation();

                        if (location != null) {
                            sendSms(location);
                        }

                        fusedClient.removeLocationUpdates(this);
                    }
                },
                Looper.getMainLooper()
        );
    }

    private void sendSms(Location location) {

        String message = "GPS:"
                + location.getLatitude()
                + ","
                + location.getLongitude();

        SmsManager.getDefault().sendTextMessage(
                phoneNumber,
                null,
                message,
                null,
                null
        );

        smsSent++;
    }

    private void stopTracking(boolean manualStop) {

        if (!isRunning) return;

        if (manualStop) {
            sendControlSms("CTRL:STOP");
        }

        isRunning = false;

        handler.removeCallbacksAndMessages(null);
        uiHandler.removeCallbacksAndMessages(null);

        stopForeground(true);
        stopSelf();

        sendUpdate(TxStatus.IDLE, 0, 0);
    }

    private void sendUpdate(TxStatus status, int timer, int smsCount) {

        Intent intent = new Intent(ACTION_UPDATE);

        intent.setPackage(getPackageName()); // 🔥 FONDAMENTALE

        intent.putExtra("status", status.name());
        intent.putExtra("timer", timer);
        intent.putExtra("smsCount", smsCount);

        sendBroadcast(intent);
    }

    private Notification buildNotification() {

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SmsGpsTracker attivo")
                .setContentText("Invio GPS in corso")
                .setSmallIcon(R.drawable.led_green)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Tx Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);
        }
    }

    private void sendControlSms(String text) {

        if (phoneNumber == null || phoneNumber.isEmpty()) return;

        SmsManager smsManager;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            smsManager = getSystemService(SmsManager.class);
        } else {
            smsManager = SmsManager.getDefault();
        }

        smsManager.sendTextMessage(phoneNumber, null, text, null, null);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
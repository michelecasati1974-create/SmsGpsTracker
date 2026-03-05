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
import android.util.Log;
import android.content.SharedPreferences;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import java.util.List;
import android.telephony.CellSignalStrength;



public class TxForegroundService extends Service {

    public static final String ACTION_START = "ACTION_START";

    public static final String ACTION_FORCE_POSITION = "com.example.smsgpstracker.FORCE_POSITION";

    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_UPDATE =
            "com.example.smsgpstracker.TX_UPDATE";
    public static final String ACTION_ABORT = "ACTION_ABORT";

    public static final String ACTION_SET_MONITOR_INTERVAL =
            "com.example.smsgpstracker.SET_MONITOR_INTERVAL";



    private TelephonyManager telephonyManager;
    private PhoneStateListener signalListener;

    private int lastSignalDbm = Integer.MIN_VALUE;

    private static final String CHANNEL_ID = "TxServiceChannel";

    private FusedLocationProviderClient fusedClient;

    private boolean isRunning = false;
    private int smsSent = 0;
    private int maxSms = 0;
    private int intervalMinutes = 1;
    private String phoneNumber = "";

    private long cycleStartTime = 0;

    private long nextTickTime = 0;

    private boolean noSignalAlertEnabled = false;
    private long noSignalStartTime = 0;
    private boolean vibrationTriggered = false;


    private Handler handler = new Handler(Looper.getMainLooper());
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    private Runnable uiRunnable;

    private Location lastLocation = null;
    private LocationCallback continuousLocationCallback;

    private double lastLatitude = 0;
    private double lastLongitude = 0;

    private long monitorIntervalMs = 5000;

    private Handler signalHandler = new Handler(Looper.getMainLooper());
    private Runnable signalPollRunnable;



    private boolean gpsFixValid = false;

    private boolean continuousMode = false;


    private long rxTimeoutMs;
    private Handler rxMonitorHandler = new Handler(Looper.getMainLooper());

    public enum TxStatus {
        IDLE,
        WAITING,
        TRACKING
    }

    private void startContinuousGps() {

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                monitorIntervalMs
        )
                .setMinUpdateIntervalMillis(monitorIntervalMs / 2)
                .build();

        continuousLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {

                if (result == null) return;

                Location loc = result.getLastLocation();
                if (loc == null) return;

                lastLocation = loc;

                gpsFixValid = loc.hasAccuracy() && loc.getAccuracy() <= 100;

                sendGpsRealtimeUpdate(
                        loc.getLatitude(),
                        loc.getLongitude(),
                        loc.getAccuracy()
                );
            }
        };

        fusedClient.requestLocationUpdates(
                request,
                continuousLocationCallback,
                Looper.getMainLooper()
        );
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) return START_STICKY;

        String action = intent.getAction();

        Log.d("TX_DEBUG", "ACTION RICEVUTA: " + action);

        if (ACTION_START.equals(action)) {

            phoneNumber = intent.getStringExtra("phone");
            maxSms = intent.getIntExtra("maxSms", 10);
            intervalMinutes = intent.getIntExtra("interval", 1);
            continuousMode = intent.getBooleanExtra("continuousMode", false);
            noSignalAlertEnabled =
                    intent.getBooleanExtra("noSignalAlert", false);

            startTracking();

            monitorIntervalMs = 5000;

            restartContinuousGps();


            return START_STICKY;
        }

        if (ACTION_STOP.equals(action)) {

            // Invia sempre STOP se il numero è valido
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                sendControlSms("CTRL:STOP");
            }

            stopTrackingInternal();
            return START_STICKY;
        }

        if ("ACTION_ABORT".equals(action)) {
            stopTrackingInternal();
            return START_STICKY;
        }

        if (ACTION_FORCE_POSITION.equals(action)) {
            Log.d("TX_DEBUG", "ENTRATO IN FORCE POSITION");
            sendSinglePositionSms();
            return START_STICKY;
        }

        if (ACTION_SET_MONITOR_INTERVAL.equals(action)) {

            monitorIntervalMs = intent.getLongExtra("intervalMs", 5000);

            restartContinuousGps();
            restartSignalPolling();

            return START_STICKY;
        }

        return START_STICKY;
    }

    private void startSignalPolling() {

        signalPollRunnable = new Runnable() {
            @Override
            public void run() {

                // manda solo se abbiamo un valore reale
                if (lastSignalDbm < 0 && lastSignalDbm > -140) {
                    sendSignalUpdate(lastSignalDbm);
                }

                signalHandler.postDelayed(this, monitorIntervalMs);

                if (isRunning && noSignalAlertEnabled) {

                    boolean noSignal =
                            (lastSignalDbm >= 0 || lastSignalDbm < -140);

                    long now = System.currentTimeMillis();

                    if (noSignal) {

                        if (noSignalStartTime == 0) {
                            noSignalStartTime = now;
                        }

                        int Tc = getSharedPreferences("SmsGpsTrackerPrefs", MODE_PRIVATE)
                                .getInt("noSignalTc", 10);

                        long TcMs = Tc * 1000L;

                        if (!vibrationTriggered &&
                                now - noSignalStartTime >= TcMs) {

                            triggerVibration();
                            vibrationTriggered = true;
                        }

                    } else {

                        noSignalStartTime = 0;
                        vibrationTriggered = false;
                    }
                }
            }
        };

        signalHandler.post(signalPollRunnable);
    }

    private void triggerVibration() {

        int Ts = getSharedPreferences("SmsGpsTrackerPrefs",
                MODE_PRIVATE)
                .getInt("vibrationTs", 3);

        long duration = Ts * 1000L;

        Vibrator vibrator =
                (Vibrator) getSystemService(VIBRATOR_SERVICE);

        if (vibrator != null) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                vibrator.vibrate(
                        VibrationEffect.createOneShot(
                                duration,
                                VibrationEffect.DEFAULT_AMPLITUDE)
                );

            } else {
                vibrator.vibrate(duration);
            }
        }
    }

    private void restartSignalPolling() {

        if (signalPollRunnable != null) {
            signalHandler.removeCallbacks(signalPollRunnable);
        }

        startSignalPolling();
    }






    @Override
    public void onCreate() {
        super.onCreate();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
        startSignalMonitor();     // listener sempre attivo
        startSignalPolling();     // polling base 5 sec
    }

    private void requestSingleImmediateLocation() {

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
        ).addOnSuccessListener(location -> {

            if (location != null) {
                gpsFixValid = location.hasAccuracy()
                        && location.getAccuracy() <= 100;

                sendSms(location);

                smsSent++;

                sendUpdate(TxStatus.TRACKING, 0, smsSent);
            }
        });
    }

    private void sendSinglePositionSms() {

        Log.d("TX_DEBUG", "isRunning=" + isRunning);
        Log.d("TX_DEBUG", "lastLocation=" + lastLocation);
        Log.d("TX_DEBUG", "phoneNumber=" + phoneNumber);

        if (!isRunning) return;
        if (lastLocation == null) return;
        if (phoneNumber == null || phoneNumber.isEmpty()) return;

        try {

            String message = "🌟 POS MANUALE:\n"
                    + lastLocation.getLatitude()
                    + ","
                    + lastLocation.getLongitude();

            SmsManager smsManager;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            smsManager.sendTextMessage(phoneNumber, null, message, null, null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startTracking() {

        if (isRunning) return;

        isRunning = true;
        smsSent = 0;
        cycleStartTime = System.currentTimeMillis();
        gpsFixValid = false;   // reset LED GPS all'avvio ciclo

        startForeground(1, buildNotification());
        startContinuousGps();
        requestSingleImmediateLocation();


        sendControlSms("CTRL:START");


        startUiTimer();
        startTimer();

        sendUpdate(TxStatus.WAITING, 0, 0);
        rxTimeoutMs = intervalMinutes * 150000L;
        // 10 min × 2.5 = 25 min
        startRxMonitor();
        SharedPreferences prefs =
                getSharedPreferences("HEARTBEAT", MODE_PRIVATE);

        prefs.edit()
                .putLong("lastAlive", System.currentTimeMillis())
                .apply();

        pingSent = false;
        sendRxStatus(true);
    }

    private boolean pingSent = false;

    private void startRxMonitor() {

        rxMonitorHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (!isRunning) return;

                SharedPreferences prefs =
                        getSharedPreferences("HEARTBEAT", MODE_PRIVATE);

                long lastAlive = prefs.getLong("lastAlive", 0);

                long diff = System.currentTimeMillis() - lastAlive;

                if (diff > rxTimeoutMs && !pingSent) {

                    sendControlSms("CTRL:PING");
                    pingSent = true;
                }

                if (diff <= rxTimeoutMs) {
                    pingSent = false;
                    sendRxStatus(true);
                }

                if (diff > rxTimeoutMs * 1.5) {
                    sendRxStatus(false);
                }

                rxMonitorHandler.postDelayed(this, 10_000); // controllo ogni 10 sec
            }
        }, 10_000);
    }


    private void startUiTimer() {

        uiRunnable = new Runnable() {
            @Override
            public void run() {

                if (!isRunning) return;

                long remainingMs = nextTickTime - System.currentTimeMillis();
                if (remainingMs < 0) remainingMs = 0;

                int remainingSec = (int)(remainingMs / 1000);

                TxStatus currentStatus =
                        (smsSent == 0) ? TxStatus.WAITING : TxStatus.TRACKING;

                sendUpdate(currentStatus, remainingSec, smsSent);

                uiHandler.postDelayed(this, 1000);
            }
        };

        uiHandler.post(uiRunnable);
    }
    private void startTimer() {

        nextTickTime = System.currentTimeMillis()
                + intervalMinutes * 60 * 1000L;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (!isRunning) return;

                if (lastLocation != null) {
                    sendSms(lastLocation);
                }

                if (!continuousMode && smsSent >= maxSms) {
                    sendControlSms("CTRL:END");
                    stopTrackingInternal();
                    return;
                }

                nextTickTime = System.currentTimeMillis()
                        + intervalMinutes * 60 * 1000L;

                handler.postDelayed(this, intervalMinutes * 60 * 1000L);
            }

        }, intervalMinutes * 60 * 1000L);
    }

    private void sendGpsRealtimeUpdate(double lat, double lon, float accuracy) {

        // 🔥 SALVA COORDINATE GLOBALI
        lastLatitude = lat;
        lastLongitude = lon;

        Intent intent = new Intent("com.example.smsgpstracker.TX_GPS_UPDATE");

        intent.putExtra("lat", lat);
        intent.putExtra("lon", lon);
        intent.putExtra("accuracy", accuracy);

        intent.setPackage(getPackageName());

        sendBroadcast(intent);
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
        Log.d("GPS_DEBUG",
                "Lat: " + location.getLatitude()
                        + " Lon: " + location.getLongitude()
                        + " Accuracy: " + location.getAccuracy()
        );
    }

    private void stopTrackingInternal() {

        if (!isRunning) return;

        isRunning = false;

        handler.removeCallbacksAndMessages(null);
        uiHandler.removeCallbacksAndMessages(null);
        rxMonitorHandler.removeCallbacksAndMessages(null);

        if (continuousLocationCallback != null) {
            fusedClient.removeLocationUpdates(continuousLocationCallback);
        }

        stopForeground(true);

        sendUpdate(TxStatus.IDLE, 0, smsSent);
    }

    private void sendRxStatus(boolean alive) {

        Intent intent = new Intent(ACTION_UPDATE);
        intent.setPackage(getPackageName());

        intent.putExtra("rxAlive", alive);
        intent.putExtra("gpsFix", gpsFixValid);
        intent.putExtra("status", TxStatus.TRACKING.name());
        intent.putExtra("timer", 0);
        intent.putExtra("smsCount", smsSent);

        sendBroadcast(intent);
    }

    private void sendUpdate(TxStatus status, int timer, int smsCount) {

        Intent intent = new Intent(ACTION_UPDATE);

        intent.setPackage(getPackageName());

        intent.putExtra("status", status.name());
        intent.putExtra("timer", timer);
        intent.putExtra("smsCount", smsCount);

        // 🔥 NUOVO EXTRA PER LED GPS
        intent.putExtra("gpsFix", gpsFixValid);

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

    private void startSignalMonitor() {

        telephonyManager =
                (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        signalListener = new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                super.onSignalStrengthsChanged(signalStrength);

                try {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                        List<CellSignalStrength> strengths =
                                signalStrength.getCellSignalStrengths();

                        if (strengths != null && !strengths.isEmpty()) {

                            int bestDbm = Integer.MIN_VALUE;

                            for (CellSignalStrength css : strengths) {

                                int dbm = css.getDbm();

                                // valore realistico rete mobile
                                if (dbm < 0 && dbm > -140) {

                                    // prendi il migliore (meno negativo)
                                    if (dbm > bestDbm) {
                                        bestDbm = dbm;
                                    }
                                }
                            }

                            if (bestDbm != Integer.MIN_VALUE) {
                                lastSignalDbm = bestDbm;
                            }
                        }

                    } else {

                        int gsm = signalStrength.getGsmSignalStrength();

                        if (gsm != 99) {
                            lastSignalDbm = -113 + 2 * gsm;
                        }
                    }

                    sendSignalUpdate(lastSignalDbm);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        telephonyManager.listen(
                signalListener,
                PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
        );
    }

    private void sendSignalUpdate(int dbm) {

        Intent intent = new Intent(ACTION_UPDATE);
        intent.setPackage(getPackageName());

        intent.putExtra("signalDbm", dbm);

        sendBroadcast(intent);
    }





    private void restartContinuousGps() {

        if (continuousLocationCallback != null) {
            fusedClient.removeLocationUpdates(continuousLocationCallback);
        }

        startContinuousGps();
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
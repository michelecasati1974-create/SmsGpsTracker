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
import java.util.ArrayList;
import com.google.android.gms.maps.model.LatLng;
import kotlin.Pair;
import com.example.smsgpstracker.tx.PolylineCodec;
import android.content.Context;
import com.example.smsgpstracker.tx.TrackSimplifier;


public class TxForegroundService extends Service {


    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_FORCE_POSITION = "com.example.smsgpstracker.FORCE_POSITION";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_UPDATE =
            "com.example.smsgpstracker.TX_UPDATE";
    public static final String ACTION_ABORT = "ACTION_ABORT";
    public static final String ACTION_SET_MONITOR_INTERVAL =
            "com.example.smsgpstracker.SET_MONITOR_INTERVAL";
    private GpsTrackBuffer gpsTrackBuffer;
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
    private static final int NOTIFICATION_ID = 1;
    private Handler handler = new Handler(Looper.getMainLooper(), null);
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private HighFrequencyTracker highFrequencyTracker = new HighFrequencyTracker();
    private SequenceManager sequenceManager = new SequenceManager();
    private Runnable uiRunnable;
    private Location lastLocation = null;
    private LocationCallback continuousLocationCallback;
    private double lastLatitude = 0;
    private double lastLongitude = 0;
    private long monitorIntervalMs = 5000;
    private Handler signalHandler = new Handler(Looper.getMainLooper());
    private Runnable signalPollRunnable;
    private long lastMovementTime = 0;
    private boolean stopMode = false;
    private boolean gpsFixValid = false;
    private boolean continuousMode = false;
    private static final float MOVEMENT_DISTANCE_METERS = 5f;
    private static final long STOP_TIMEOUT_MS = 120000; // 2 minuti
    private long rxTimeoutMs;
    private Handler rxMonitorHandler = new Handler(Looper.getMainLooper());
    private long lastSmsTime = 0;
    private int smsIntervalMinutes = 10;
    private SharedPreferences seqPrefs;
    private int sequenceNumber = 0;
    private static final String SEQ_PREFS = "tx_sequence_prefs";
    private static final String KEY_SEQ = "tx_sequence";
    private static final String STATE_PREFS = "tx_state_prefs";
    private static final String KEY_TX_STATE = "tx_state";



    ///////Nuovi parametri configurabili (Settings)/////
    private long trackSmsIntervalMs = 15 * 60 * 1000; // 15 minuti default
    private int trackSmsMaxLen = 140;                 // sicurezza SMS
    private long lastTrackSmsTime = 0;
    ////////////////////////////////////////////////////

    private SharedPreferences statePrefs;





    private float distanceMeters(Location a, Location b) {

        float[] result = new float[1];

        Location.distanceBetween(
                a.getLatitude(),
                a.getLongitude(),
                b.getLatitude(),
                b.getLongitude(),
                result
        );

        return result[0];
    }

    private boolean shouldRecordPoint(Location loc) {

        if (lastLocation == null) {

            lastLocation = loc;
            lastMovementTime = System.currentTimeMillis();

            return true;
        }

        float dist = distanceMeters(lastLocation, loc);

        long now = System.currentTimeMillis();

        if (dist >= MOVEMENT_DISTANCE_METERS) {

            lastLocation = loc;
            lastMovementTime = now;

            if (stopMode) {
                Log.d("SMART_STOP", "movement detected → exit STOP mode");
            }

            stopMode = false;

            return true;
        }

        long stoppedTime = now - lastMovementTime;

        if (stoppedTime > STOP_TIMEOUT_MS) {

            if (!stopMode) {
                Log.d("SMART_STOP", "enter STOP mode");
            }

            stopMode = true;

            return false;
        }

        return true;
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {

        double R = 6371000; // raggio terra metri

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(dLat/2) * Math.sin(dLat/2) +
                        Math.cos(Math.toRadians(lat1)) *
                                Math.cos(Math.toRadians(lat2)) *
                                Math.sin(dLon/2) *
                                Math.sin(dLon/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return R * c;
    }





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

                // SMART STOP DETECTION
                if (!shouldRecordPoint(loc)) {
                    Log.d("SMART_STOP", "device stationary -> skip point");
                    return;
                }

                // filtro alta frequenza
                if (!highFrequencyTracker.shouldAccept(loc)) {
                    return;
                }

                lastLocation = loc;

                gpsFixValid = loc.hasAccuracy() && loc.getAccuracy() <= 100;

                double lat = loc.getLatitude();
                double lon = loc.getLongitude();
                float acc = loc.getAccuracy();
                long ts = loc.getTime();

                Log.d("TX_GPS",
                        "lat=" + lat +
                                " lon=" + lon +
                                " acc=" + acc +
                                " ts=" + ts);

                Intent intent = new Intent("TX_DEBUG_UPDATE");

                intent.putExtra("accuracy", acc);
                intent.putExtra("buffer", gpsTrackBuffer.getPoints().size());
                intent.putExtra("seq", sequenceManager.peekNext());

                sendBroadcast(intent);

                GpsPoint p = new GpsPoint(ts, lat, lon, acc);

                gpsTrackBuffer.addPoint(p);

                List<GpsPoint> rawPoints = gpsTrackBuffer.getPoints();

                int size = rawPoints.size();

                Log.d("BUFFER_TEST", "size=" + size);

                if (size >= 5) {

                    List<LatLng> points = new ArrayList<>();
                    LatLng lastKept = null;

                    for (GpsPoint gp : rawPoints) {

                        LatLng current = new LatLng(gp.getLat(), gp.getLon());

                        if (lastKept == null) {
                            points.add(current);
                            lastKept = current;
                            continue;
                        }

                        double dist = distanceMeters(
                                lastKept.latitude,
                                lastKept.longitude,
                                current.latitude,
                                current.longitude
                        );

                        // filtro distanza minima
                        if (dist >= 5) {
                            points.add(current);
                            lastKept = current;
                        }
                    }

                    // se dopo il filtro rimangono pochi punti non ha senso comprimere
                    if (points.size() < 3) {
                        return;
                    }

                    // ================================
                    // TRACK SIMPLIFICATION
                    // ================================

                    List<LatLng> simplified =
                            TrackSimplifier.simplify(points, 0.00001);

                    if (simplified.size() < 2) {
                        return;
                    }

                    // conversione polyline
                    List<Pair<Double, Double>> polyPoints = new ArrayList<>();

                    for (LatLng pt : simplified) {
                        polyPoints.add(new Pair<>(pt.latitude, pt.longitude));
                    }

                    // compressione polyline
                    String encoded = PolylineCodec.INSTANCE.encode(polyPoints);

                    int pointsCount = simplified.size();

                    int seqPreview = sequenceManager.peekNext();

                    String previewHeader = "T#" + seqPreview + "/" + pointsCount + "|";

                    String previewSms = previewHeader + encoded;

                    Log.d("TRACK_STATS",
                            "raw=" + rawPoints.size()
                                    + " filtered=" + points.size()
                                    + " simplified=" + pointsCount
                                    + " encodedLen=" + encoded.length()
                                    + " smsLen=" + previewSms.length());

                    long now = System.currentTimeMillis();

                    boolean timeReached =
                            now - lastTrackSmsTime >= trackSmsIntervalMs;

                    boolean smsFull =
                            previewSms.length() >= trackSmsMaxLen;

                    // sicurezza: evita buffer troppo grande
                    boolean bufferLarge =
                            pointsCount >= 120;

                    if (timeReached || smsFull || bufferLarge) {

                        Log.d("TRACK_SMS_TRIGGER",
                                "time=" + timeReached +
                                        " full=" + smsFull +
                                        " buffer=" + bufferLarge);

                        int seq = sequenceManager.next();

                        String header = "T#" + seq + "/" + pointsCount + "|";

                        String payload = header + encoded;

                        String crc = SmsCrc.INSTANCE.crc8(payload);

                        String sms = payload + "|" + crc;

                        Log.d("SMS_TRACK", "seq=" + seq);
                        Log.d("SMS_TRACK", "points=" + pointsCount);
                        Log.d("SMS_TRACK", "smsLen=" + sms.length());
                        Log.d("SMS_TRACK", "reason=" +
                                (smsFull ? "FULL " : "") +
                                (timeReached ? "TIME " : "") +
                                (bufferLarge ? "BUFFER " : ""));

                        sendTrackSms(sms);


                        lastTrackSmsTime = now;

                        // ================================
                        // ROLLING BUFFER
                        // ================================

                        List<GpsPoint> remaining = new ArrayList<>();

                        if (rawPoints.size() >= 2) {
                            remaining.add(rawPoints.get(rawPoints.size() - 2));
                            remaining.add(rawPoints.get(rawPoints.size() - 1));
                        }

                        gpsTrackBuffer.clear();

                        for (GpsPoint rp : remaining) {
                            gpsTrackBuffer.addPoint(rp);
                        }

                        Log.d("SMS_TRACK",
                                "rollingBuffer=" + remaining.size());
                    }
                }

                sendGpsRealtimeUpdate(
                        lat,
                        lon,
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
        Log.d("TX_SERVICE", "Service started");

        startForeground(NOTIFICATION_ID, createNotification());

        if (intent == null) return START_STICKY;

        String action = intent.getAction();

        Log.d("TX_DEBUG", "ACTION RICEVUTA: " + action);

        String mode = intent.getStringExtra("MODE");

        if ("MULTI_GPS_SMS".equals(mode)) {

            Log.d("TX_SERVICE", "MULTI GPS MODE ATTIVO");

            monitorIntervalMs = 5000; // 5 secondi // GPS veloce per traccia

            restartContinuousGps();

            return START_STICKY;
        }

        if (ACTION_START.equals(action)) {

            phoneNumber = intent.getStringExtra("phone");
            maxSms = intent.getIntExtra("maxSms", 10);
            intervalMinutes = intent.getIntExtra("interval", 1);
            continuousMode = intent.getBooleanExtra("continuousMode", false);
            noSignalAlertEnabled =
                    intent.getBooleanExtra("noSignalAlert", false);

            saveTxState("TRACKING");
            startTracking();


            monitorIntervalMs = 5000;

            restartContinuousGps();

            return START_STICKY;
        }

        if (ACTION_STOP.equals(action)) {

            Log.d("TX_SERVICE", "STOP ACTION RECEIVED");

            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                sendControlSms("CTRL:STOP");
            }

            saveTxState("IDLE");

            // ferma GPS e tracking
            stopTrackingInternal();

            // rimuove la notifica foreground
            stopForeground(true);

            // distrugge il service
            stopSelf();

            return START_NOT_STICKY;
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

    private Notification createNotification() {

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("SMS GPS Tracker")
                        .setContentText("Tracking attivo")
                        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW);

        return builder.build();
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
    private int getNextSequence() {

        sequenceNumber++;

        seqPrefs.edit()
                .putInt(KEY_SEQ, sequenceNumber)
                .apply();

        return sequenceNumber;
    }

    private void saveTxState(String state) {

        statePrefs.edit()
                .putString(KEY_TX_STATE, state)
                .apply();

        Log.d("TX_STATE", "Saved state=" + state);
    }

    @Override
    public void onCreate() {
        statePrefs = getSharedPreferences(STATE_PREFS, MODE_PRIVATE);
        super.onCreate();
        Log.d("TX_SERVICE", "Service created");
        seqPrefs = getSharedPreferences(SEQ_PREFS, MODE_PRIVATE);
        sequenceNumber = seqPrefs.getInt(KEY_SEQ, 0);
        createNotificationChannel();
        gpsTrackBuffer = new GpsTrackBuffer(this);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
        startSignalMonitor();     // listener sempre attivo
        startSignalPolling();     // polling base 5 sec
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d("TX_SERVICE", "Service destroyed");

        // fermiamo GPS updates
        if (fusedClient != null && continuousLocationCallback != null) {
            fusedClient.removeLocationUpdates(continuousLocationCallback);
        }
        stopForeground(true);

        // eventuali cleanup
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

                if (!isRunning) {
                    sendUpdate(TxStatus.IDLE, 0, smsSent);
                    return;
                }

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

    private void sendTrackSms(String text) {

        // controllo numero destinatario
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {

            Log.e("SMS_TRACK", "Numero RX non valido o non impostato");
            return;
        }

        SmsManager smsManager;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            smsManager = getSystemService(SmsManager.class);
        } else {
            smsManager = SmsManager.getDefault();
        }

        try {

            Log.d("SMS_TRACK", "Invio SMS a: " + phoneNumber);
            Log.d("SMS_TRACK", "Payload: " + text);

            smsManager.sendTextMessage(phoneNumber, null, text, null, null);

            smsSent++;

            Log.d("SMS_TRACK", "SMS inviato");

        } catch (Exception e) {

            Log.e("SMS_TRACK", "Errore invio SMS: " + e.getMessage());
        }
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
        rxMonitorHandler.removeCallbacksAndMessages(null);

        // 👇 STOP SOLO IL TIMER UI SPECIFICO
        if (uiRunnable != null) {
            uiHandler.removeCallbacks(uiRunnable);
        }

        if (continuousLocationCallback != null) {
            fusedClient.removeLocationUpdates(continuousLocationCallback);
        }

        sendUpdate(TxStatus.IDLE, 0, smsSent);
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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SMS GPS Tracker",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription("Tracking GPS in background");

            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
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
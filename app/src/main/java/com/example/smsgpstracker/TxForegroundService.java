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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import android.os.Environment;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;




public class TxForegroundService extends Service {

    public static boolean smsDebugMode = false;
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

    private boolean debugTrackEnabled;


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
    private boolean multiGpsMode = false;
    private boolean autoModeEnabled = true;

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

    private Location lastTrackPoint = null;
    private Handler smsHandler = new Handler();
    private Location lastAcceptedLocation = null;

    private boolean firstPositionSent = false;
    private boolean firstGpsFixSent = false;
    private boolean ignoreFirstGpsFix = true;
    private long sessionStartTime = 0;
    private long sessionEndTime = 0;
    private boolean isProcessing = false;
    private double totalDistanceMeters = 0;

    private float accuracySum = 0;
    private int accuracyCount = 0;

    private Location lastDistanceLocation = null;
    private final Object bufferLock = new Object();
    private long multiSendIntervalMs;





    ///////Nuovi parametri configurabili (Settings)/////

    // sicurezza SMS
    private int trackSmsMaxLen = 140;                 // sicurezza SMS
    private long lastTrackSmsTime = 0;

    // GPS sampling
    private long gpsSampleIntervalMs = 3000;

    // invio SMS
    private long trackSmsIntervalMs = 120000;
    // limite sessione
    private int maxSmsPerSession = 20;

    // compressione percorso
    private float trackSimplifyDistance = 20f;

    // contatori debug
    private int gpsPointsCollected = 0;
    private int gpsPointsSent = 0;

    private long adaptiveGpsInterval = 3000;

    // ===== MULTI GPS ADVANCED SETTINGS =====
    private float trackSimplifyTolerance = 0.00005f;
    private float trackAngleThreshold = 10f;
    private int maxPointsPerSms = 5;
    private long multiGpsSendIntervalMs = 15000;
    private int keepPoints = 3;
    ////////////////////////////////////////////////////

    private SharedPreferences statePrefs;
    private AdaptiveConfig adaptiveConfig;
    private AdaptiveConfig lastAdaptiveConfig = null;
    private AdaptiveConfig currentConfig;

    private int estimateSmsLength(List<LatLng> points) {

        if (points == null || points.isEmpty()) return 0;

        List<Pair<Double, Double>> polyPoints = new ArrayList<>();

        for (LatLng pt : points) {
            polyPoints.add(new Pair<>(pt.latitude, pt.longitude));
        }

        String encoded = PolylineCodec.INSTANCE.encode(polyPoints);

        return encoded.length() + 5; // margine sicurezza
    }





    private void startMultiGpsTracking() {

        Log.d("TX_SERVICE", "startMultiGpsTracking()");

        startTracking();
        AdaptiveConfig config = new AdaptiveConfig(
                trackSimplifyDistance,
                trackAngleThreshold,
                trackSimplifyTolerance,
                multiSendIntervalMs
        );
    }

    private void startIntervalTracking() {

        Log.d("TX_SERVICE", "startIntervalTracking()");

        startTracking();
    }

    private void processGpsPoint(Location location) {

        gpsPointsCollected++;

        if (lastTrackPoint == null) {
            lastTrackPoint = location;
            return;
        }

        float dist = location.distanceTo(lastTrackPoint);

        if (dist >= trackSimplifyDistance) {

            lastTrackPoint = location;
            gpsPointsSent++;

            sendSms(location);
        }
    }

    private final Runnable smsRunnable = new Runnable() {
        @Override
        public void run() {

            if (smsSent >= maxSmsPerSession) {

                Log.d("TX_SMS","Max SMS reached");
                stopTrackingInternal();
                return;
            }

            if (lastLocation != null) {
                sendSms(lastLocation);
            }

            smsHandler.postDelayed(this, multiGpsSendIntervalMs);
        }
    };



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

    private void updateAdaptiveGpsInterval(Location loc) {

        float speed = loc.getSpeed(); // m/s

        long newInterval;

        if (speed < 1) {
            newInterval = 10000;   // fermo
        }
        else if (speed < 5) {
            newInterval = 5000;    // camminata
        }
        else {
            newInterval = 2000;    // veicolo
        }

        if (newInterval != adaptiveGpsInterval) {

            adaptiveGpsInterval = newInterval;

            Log.d("ADAPTIVE_GPS",
                    "speed=" + speed +
                            " interval=" + adaptiveGpsInterval);

            restartGpsUpdates();
        }
    }

    private void restartGpsUpdates() {

        if (continuousLocationCallback != null) {
            fusedClient.removeLocationUpdates(continuousLocationCallback);
        }

        startContinuousGps();
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

    private double angleBetween(
            LatLng a,
            LatLng b,
            LatLng c) {

        double abx = b.longitude - a.longitude;
        double aby = b.latitude - a.latitude;

        double bcx = c.longitude - b.longitude;
        double bcy = c.latitude - b.latitude;

        double dot = abx * bcx + aby * bcy;

        double mag1 = Math.sqrt(abx * abx + aby * aby);
        double mag2 = Math.sqrt(bcx * bcx + bcy * bcy);

        if (mag1 == 0 || mag2 == 0) return 0;

        double cos = dot / (mag1 * mag2);

        cos = Math.max(-1, Math.min(1, cos));

        return Math.toDegrees(Math.acos(cos));
    }


    private void startContinuousGps() {

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                adaptiveGpsInterval
        )
                .setMinUpdateIntervalMillis(adaptiveGpsInterval / 2)
                .build();

        continuousLocationCallback = new LocationCallback() {

            @Override
            public void onLocationResult(LocationResult result) {

                if (result == null) return;

                // IGNORA PRIMO FIX (spesso cached)
                if (ignoreFirstGpsFix) {

                    ignoreFirstGpsFix = false;
                    Log.d("GPS_FILTER", "Ignoring first cached fix");

                    return;
                }

                Location loc = result.getLastLocation();
                if (loc == null) return;

                updateAdaptiveGpsInterval(loc);

                // FIX doppio GPS allo START
                if (!firstGpsFixSent) {
                    firstGpsFixSent = true;
                    lastLocation = loc;
                    return;
                }

                // =====================================
                // MICRO FILTER (mantienilo, è leggero)
                // =====================================

                if (lastAcceptedLocation != null) {

                    float dist = loc.distanceTo(lastAcceptedLocation);

                    if (dist < 3) {
                        Log.d("GPS_FILTER", "skip <3m");
                        return;
                    }
                }

                lastAcceptedLocation = loc;

                // aggiorna posizione usata dagli SMS realtime
                lastLocation = loc;

                // conta ogni fix GPS ricevuto
                gpsPointsCollected++;

                // =====================================
                // SESSION STATS
                // =====================================

                if (loc.hasAccuracy()) {
                    accuracySum += loc.getAccuracy();
                    accuracyCount++;
                }

                if (lastDistanceLocation != null) {

                    float dist = loc.distanceTo(lastDistanceLocation);

                    if (dist > 1) {
                        totalDistanceMeters += dist;
                    }
                }

                lastDistanceLocation = loc;

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

                // =====================================
                // DEBUG UI (OK mantenerlo)
                // =====================================

                Intent intent = new Intent("TX_DEBUG_UPDATE");

                intent.putExtra("accuracy", acc);

                synchronized (bufferLock) {
                    intent.putExtra("buffer", gpsTrackBuffer.getPointsCopy().size());
                }

                intent.putExtra("seq", sequenceManager.peekNext());

                sendBroadcast(intent);

                // =====================================
                // CREAZIONE PUNTO GPS
                // =====================================

                GpsPoint p = new GpsPoint(ts, lat, lon, acc);

                // =====================================
                // REALTIME MODE (no buffer)
                // =====================================

                if (!multiGpsMode) {

                    sendGpsRealtimeUpdate(
                            lat,
                            lon,
                            loc.getAccuracy()
                    );

                    return;
                }

                // =====================================
                // BUFFER ONLY (CORE CHANGE)
                // =====================================

                synchronized (bufferLock) {
                    gpsTrackBuffer.addPoint(p);
                }

                // 👇 SOLO LOG DI DEBUG (opzionale)
                int size;

                synchronized (bufferLock) {
                    size = gpsTrackBuffer.getPointsCopy().size();
                }

                Log.d("BUFFER_TEST", "size=" + size);


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

    private Handler trackHandler = new Handler(Looper.getMainLooper());

    private final Runnable trackProcessorRunnable = new Runnable() {
        @Override
        public void run() {

            if (!isRunning || !multiGpsMode) return;

            processTrackBuffer();

            trackHandler.postDelayed(this, multiGpsSendIntervalMs);
        }
    };

    private void flushTrackBuffer() {

        // sicurezza: flush solo in modalità MULTI_GPS
        if (!multiGpsMode) {
            return;
        }

        List<GpsPoint> rawPoints;

        // ✅ COPIA SICURA DEL BUFFER
        synchronized (bufferLock) {

            List<GpsPoint> current = gpsTrackBuffer.getPointsCopy();

            if (current == null || current.size() < 3) {
                gpsTrackBuffer.clear();
                return;
            }

            rawPoints = new ArrayList<>(current);
        }

        // ================================
        // FILTRO DISTANZA
        // ================================

        List<LatLng> filtered = new ArrayList<>();
        LatLng lastKept = null;

        for (GpsPoint gp : rawPoints) {

            LatLng current = new LatLng(gp.getLat(), gp.getLon());

            if (lastKept == null) {
                filtered.add(current);
                lastKept = current;
                continue;
            }

            double dist = distanceMeters(
                    lastKept.latitude,
                    lastKept.longitude,
                    current.latitude,
                    current.longitude
            );

            if (dist >= trackSimplifyDistance) {
                filtered.add(current);
                lastKept = current;
            }
        }

        if (filtered.size() < 2) {
            synchronized (bufferLock) {
                gpsTrackBuffer.clear();
            }
            return;
        }

        // ================================
        // TRACK SIMPLIFICATION
        // ================================

        List<LatLng> simplified =
                TrackSimplifier.simplify(filtered, 0.0001);

        if (simplified.size() < 2) {
            synchronized (bufferLock) {
                gpsTrackBuffer.clear();
            }
            return;
        }

        // ================================
        // POLYLINE ENCODE
        // ================================


        List<Pair<Double, Double>> polyPoints = new ArrayList<>();

        for (LatLng pt : simplified) {
            polyPoints.add(new Pair<>(pt.latitude, pt.longitude));
        }

        String encoded = PolylineCodec.INSTANCE.encode(polyPoints);

        // ❗ FIX IMPORTANTE: NON troncare brutalmente
        while (encoded.length() > trackSmsMaxLen && simplified.size() > 2) {

            simplified.remove(simplified.size() - 1);

            polyPoints.clear();

            for (LatLng pt : simplified) {
                polyPoints.add(new Pair<>(pt.latitude, pt.longitude));
            }

            encoded = PolylineCodec.INSTANCE.encode(polyPoints);
        }

        int estimatedLen = encoded.length() + 5;

        long now = System.currentTimeMillis();
        boolean isNearLimit = estimatedLen >= (trackSmsMaxLen - 10);
        boolean timeoutReached = (now - lastTrackSmsTime) >= multiSendIntervalMs;

        // 🔴 BLOCCA INVIO SE NON SERVE
        if (!isNearLimit && !timeoutReached) {

            Log.d("TRACK", "WAIT → fill SMS (" + estimatedLen + " chars)");

            isProcessing = false;
            return;
        }

        String payload = "T|" + encoded;

        String crc = SmsCrc.INSTANCE.crc8(payload);

        String sms = payload + "|" + crc;

        sendTrackSms(sms);

        // ================================
        // ROLLING BUFFER (mantieni ultimi punti)
        // ================================

        synchronized (bufferLock) {

            List<GpsPoint> current = gpsTrackBuffer.getPointsCopy();

            int keep = Math.min(keepPoints, current.size());

            List<GpsPoint> tail = new ArrayList<>();

            for (int i = current.size() - keep; i < current.size(); i++) {
                tail.add(current.get(i));
            }

            gpsTrackBuffer.clear();

            for (GpsPoint gp : tail) {
                gpsTrackBuffer.addPoint(gp);
            }
        }
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d("TX_SERVICE", "Service started");

        startForeground(NOTIFICATION_ID, createNotification());

        if (intent == null) return START_STICKY;

        String action = intent.getAction();

        if (ACTION_START.equals(action)) {

            String mode = intent.getStringExtra("MODE");
            if (mode == null) mode = "STANDARD";
            if ("MULTI_GPS_SMS".equals(mode)) {
                startMultiGpsMode(intent);
                return START_STICKY;
            }

            phoneNumber = intent.getStringExtra("phone");
            if (phoneNumber != null) {
                phoneNumber = phoneNumber.replaceAll("\\s+", "");
            }


            Log.d("TX_SERVICE",
                    "START mode=" + mode +
                            " phone=" + phoneNumber);

            saveTxState("TRACKING");
            SmsDebugManager.clear();

            // parametri comuni
            maxSms = intent.getIntExtra("maxSms", 10);
            intervalMinutes = intent.getIntExtra("interval", 1);

            //--------------------------------
            // MULTI GPS MODE
            //--------------------------------
            if ("MULTI_GPS_SMS".equals(mode)) {

                Log.d("TX_SERVICE", "MULTI GPS MODE ATTIVO");

                multiGpsMode = true;
                continuousMode = false;

                monitorIntervalMs = 5000;

                startMultiGpsTracking();

                return START_STICKY;
            }

            //--------------------------------
            // CONTINUOUS MODE
            //--------------------------------
            if ("CONTINUOUS".equals(mode)) {

                Log.d("TX_SERVICE", "CONTINUOUS MODE");

                multiGpsMode = false;
                continuousMode = true;

                if (phoneNumber == null || phoneNumber.isEmpty()) {
                    Log.e("TX_SERVICE","Phone number missing");
                    stopSelf();
                    return START_NOT_STICKY;
                }

                startIntervalTracking(); // stesso scheduler ma senza limite

                return START_STICKY;
            }

            //--------------------------------
            // STANDARD INTERVAL MODE
            //--------------------------------
            Log.d("TX_SERVICE", "STANDARD MODE");

            multiGpsMode = false;
            continuousMode = false;

            if (phoneNumber == null || phoneNumber.isEmpty()) {
                Log.e("TX_SERVICE","Phone number missing");
                stopSelf();
                return START_NOT_STICKY;
            }

            startIntervalTracking();

            return START_STICKY;
        }

        //--------------------------------
        // STOP
        //--------------------------------
        //--------------------------------
        // STOP
        //--------------------------------
        if (ACTION_STOP.equals(action)) {

            Log.d("TX_SERVICE", "STOP ACTION RECEIVED");

            //logSessionSummary();   // <-- aggiungere

            if (multiGpsMode && gpsTrackBuffer.count() >= 5) {
                processTrackBuffer();
            }

            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                sendControlSms("CTRL:STOP");
            }

            sessionEndTime = System.currentTimeMillis();



            saveSmsLog();
            if (autoModeEnabled) {

                // 🔒 sicurezza base (evita divisioni per zero)
                if (gpsPointsCollected == 0 || smsSent == 0) {
                    Log.d("ADAPTIVE", "Skip (no data)");
                } else {

                    AdaptiveSession session = new AdaptiveSession();

                    session.gpsPoints = gpsPointsCollected;
                    session.smsSent = smsSent;
                    session.distanceKm = totalDistanceMeters / 1000.0;
                    session.avgAccuracy = accuracyCount > 0 ? accuracySum / accuracyCount : 0;
                    session.durationSec = (sessionEndTime - sessionStartTime) / 1000;

                    session.distanceParam = trackSimplifyDistance;
                    session.angleParam = trackAngleThreshold;
                    session.epsilonParam = trackSimplifyTolerance;
                    session.intervalParam = multiSendIntervalMs;

                    session.compressionRatio =
                            (double) smsSent / (double) gpsPointsCollected;

                    AdaptiveConfig current = currentConfig;

                    AdaptiveConfig newConfig = null;

                    try {
                        newConfig = AdaptiveEngine.adjust(currentConfig, session);
                    } catch (Exception e) {
                        Log.e("ADAPTIVE", "Adjust error", e);
                    }

                    // 🔥 APPLICA SOLO SE VALIDA
                    if (newConfig != null) {

                        applyAdaptiveConfig(newConfig);

                        // 🔥 salva anche per SESSION REPORT
                        lastAdaptiveConfig = newConfig;

                        saveAdaptiveReport(session, newConfig);

                    } else {
                        Log.d("ADAPTIVE", "No new config applied");
                    }
                }
            }
            saveTxState("IDLE");

            stopTrackingInternal();

            stopForeground(true);

            stopSelf();

            return START_NOT_STICKY;
        }

        //--------------------------------
        // ABORT
        //--------------------------------
        if ("ACTION_ABORT".equals(action)) {
            stopTrackingInternal();
            return START_STICKY;
        }

        //--------------------------------
        // FORCE POSITION
        //--------------------------------
        if (ACTION_FORCE_POSITION.equals(action)) {
            Log.d("TX_DEBUG", "ENTRATO IN FORCE POSITION");
            sendSinglePositionSms();
            return START_STICKY;
        }

        //--------------------------------
        // FAST MONITOR
        //--------------------------------
        if (ACTION_SET_MONITOR_INTERVAL.equals(action)) {

            monitorIntervalMs = intent.getLongExtra("intervalMs", 5000);

            restartContinuousGps();
            restartSignalPolling();

            return START_STICKY;
        }

        return START_STICKY;
    }

    private void startMultiGpsMode(Intent intent) {

        String phone = intent.getStringExtra("phone");

        SharedPreferences prefs =
                getSharedPreferences("SmsGpsTrackerPrefs", MODE_PRIVATE);

        // ================================
// 🔥 PARAMETRI MULTI GPS
// ================================

        float simplifyTolerance =
                prefs.getFloat("multi_simplify_tolerance", 0.00005f);

        float minDistanceMeters =
                prefs.getFloat("multi_min_distance", 10f);

        float angleThreshold =
                prefs.getFloat("multi_angle_threshold", 10f);

        int maxPointsPerSms =
                prefs.getInt("multi_max_points_sms", 5);

        long sendIntervalMs =
                prefs.getLong("multi_send_interval", 15000);

        multiSendIntervalMs = sendIntervalMs;

        autoModeEnabled =
                prefs.getBoolean("auto_mode_enabled", true);

        int keep =
                prefs.getInt("multi_keep_points", 3);


        // ================================
        // 🔥 INIT ADAPTIVE CONFIG
        // ================================

        currentConfig = new AdaptiveConfig(
                minDistanceMeters,
                angleThreshold,
                simplifyTolerance,
                multiSendIntervalMs
        );
        Log.d("ADAPTIVE", "AUTO MODE = " + autoModeEnabled);

        // ================================
        // LOG DEBUG
        // ================================

        Log.d("MULTI_GPS_CONFIG",
                "tol=" + simplifyTolerance +
                        " dist=" + minDistanceMeters +
                        " angle=" + angleThreshold +
                        " maxPts=" + maxPointsPerSms +
                        " interval=" + sendIntervalMs);

        // ================================
        // APPLICA AI CAMPI GLOBALI
        // ================================

        this.trackSimplifyTolerance = simplifyTolerance;
        this.trackSimplifyDistance = minDistanceMeters;
        this.trackAngleThreshold = angleThreshold;
        this.maxPointsPerSms = maxPointsPerSms;
        this.multiGpsSendIntervalMs = sendIntervalMs;

        int keepPointsPref = prefs.getInt("multi_keep_points", 3);
        this.keepPoints = keepPointsPref;
        // ✅ QUI VA IL LOG (POSIZIONE GIUSTA)
        Log.d("MULTI_GPS_CONFIG",
                "interval=" + multiGpsSendIntervalMs +
                        " dist=" + trackSimplifyDistance +
                        " angle=" + trackAngleThreshold +
                        " tol=" + trackSimplifyTolerance +
                        " maxPts=" + maxPointsPerSms +
                        " keep=" + keepPoints);

        // ================================
        // AVVIO TRACKING
        // ================================

        phoneNumber = phone;
        multiGpsMode = true;
        continuousMode = false;
        startMultiGpsTracking();

        if (debugTrackEnabled && !DebugTrackActivity.isOpen) {

            Intent i = new Intent(this, DebugTrackActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
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

    private void generateSmsLogFile() {

        try {

            File file =
                    new File(getExternalFilesDir(null),"logsms.txt");

            FileWriter writer = new FileWriter(file);

            for(String s : SmsDebugManager.getLogs()) {

                writer.write(s + "\n");

            }

            writer.close();

        } catch(Exception e){

            Log.e("SMS_DEBUG","Errore file log");
        }
    }

    private void startSignalPolling() {

        signalPollRunnable = new Runnable() {
            @Override
            public void run() {

                // manda solo se abbiamo un valore reale
                if (lastSignalDbm <= -50 && lastSignalDbm >= -130) {
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
        SharedPreferences prefs =
                getSharedPreferences("SmsGpsTrackerPrefs", MODE_PRIVATE);
        debugTrackEnabled =
                prefs.getBoolean("debug_track", false);
        statePrefs = getSharedPreferences("tx_state_prefs", MODE_PRIVATE);
        super.onCreate();
        Log.d("TX_SERVICE", "Service created");
        seqPrefs = getSharedPreferences(SEQ_PREFS, MODE_PRIVATE);
        sequenceNumber = seqPrefs.getInt(KEY_SEQ, 0);
        createNotificationChannel();
        gpsTrackBuffer = new GpsTrackBuffer(this);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        startSignalMonitor();     // listener sempre attivo
        startSignalPolling();     // polling base 5 sec
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d("TX_SERVICE", "Service destroyed");

        // aggiorna stato per la UI
        saveTxState("IDLE");

        // fermiamo GPS updates
        if (fusedClient != null && continuousLocationCallback != null) {
            fusedClient.removeLocationUpdates(continuousLocationCallback);
        }

        stopForeground(true);
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

        if (lastLocation == null) {
            Log.d("TX_DEBUG", "NO GPS FIX AVAILABLE");
            return;
        }

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Log.d("TX_DEBUG", "PHONE NUMBER MISSING");
            return;
        }

        try {

            String message =
                    "🌟 POS MANUALE:\n"
                            + lastLocation.getLatitude()
                            + ","
                            + lastLocation.getLongitude();

            // log debug (solo una riga)
            SmsDebugManager.logTx(message);

            // aggiorna contatore SMS
            smsSent++;
            updateUiSmsCounter();

            // invio reale solo se debug OFF
            if (!smsDebugMode) {

                SmsManager.getDefault().sendTextMessage(
                        phoneNumber,
                        null,
                        message,
                        null,
                        null
                );
            }

            Log.d("TX_DEBUG", "FORCED POSITION SENT");

        } catch (Exception e) {

            Log.e("TX_DEBUG", "FORCE POSITION ERROR", e);

        }
    }

    private void startTracking() {

        sessionStartTime = System.currentTimeMillis();
        totalDistanceMeters = 0;
        accuracySum = 0;
        accuracyCount = 0;
        lastDistanceLocation = null;
        ignoreFirstGpsFix = true;
        smsSent = 0;
        gpsPointsCollected = 0;
        lastSmsTime = 0;
        firstPositionSent = false;

        SmsDebugManager.clear();
        synchronized (bufferLock) {
            gpsTrackBuffer.clear();
        }

        if (isRunning) return;

        isRunning = true;

        cycleStartTime = System.currentTimeMillis();
        gpsFixValid = false;

        startContinuousGps();

        startUiTimer();

        sendUpdate(TxStatus.WAITING, 0, 0);

        // ===== MODALITA MULTI GPS =====

        if (multiGpsMode) {

            trackHandler.postDelayed(trackProcessorRunnable, multiGpsSendIntervalMs);

            return;
        }

        // ===== MODALITA STANDARD / CONTINUOUS =====

        requestSingleImmediateLocation();

        sendControlSms("CTRL:START");

        startTimer();

        rxTimeoutMs = intervalMinutes * 150000L;

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

        long intervalMs = intervalMinutes * 60 * 1000L;

        nextTickTime = System.currentTimeMillis() + intervalMs;

        handler.postDelayed(new Runnable() {

            @Override
            public void run() {

                if (!isRunning) return;

                if (lastLocation != null) {
                    sendSms(lastLocation);
                }

                nextTickTime = System.currentTimeMillis() + intervalMs;

                handler.postDelayed(this, intervalMs);
            }

        }, intervalMs);
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

        // log sempre
        SmsDebugManager.logTx(text);
        smsSent++;
        updateUiSmsCounter();

        // blocco totale SMS se debug attivo
        if (smsDebugMode) {
            Log.d("SMS_DEBUG", "SMS BLOCCATO (debug mode)");
            return;
        }

        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            Log.e("SMS_TRACK", "Numero RX non valido");
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



            Log.d("SMS_TRACK", "SMS inviato");

        } catch (Exception e) {

            Log.e("SMS_TRACK", "Errore invio SMS: " + e.getMessage());
        }
    }




    private void sendSms(Location location) {

        if (multiGpsMode) {
            return; // in modalità MULTI_GPS non usare SMS singolo
        }

        long now = System.currentTimeMillis();

        // blocca invii troppo ravvicinati
        if (now - lastSmsTime < 2000) {
            return;
        }

        lastSmsTime = now;

        if (!firstPositionSent) {
            firstPositionSent = true;
        } else {
            if (System.currentTimeMillis() - lastTrackSmsTime < 2000) {
                return;
            }
        }

        String message = "GPS:"
                + location.getLatitude()
                + ","
                + location.getLongitude();

        // log sempre
        SmsDebugManager.logTx(message);

        // incrementa SEMPRE contatore
        smsSent++;

        // aggiorna UI sempre
        updateUiSmsCounter();

        // invio reale SMS solo se debug OFF
        if (!smsDebugMode) {

            SmsManager.getDefault().sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    null,
                    null
            );
        }

        // controllo fine sessione DOPO incremento
        if (!continuousMode && smsSent >= maxSms) {

            sendControlSms("CTRL:END");

            sessionEndTime = System.currentTimeMillis();

            //logSessionSummary();   // <-- aggiungere

            saveSmsLog();

            saveTxState("IDLE");

            stopTrackingInternal();

            return;
        }
    }

    private void logSessionSummary() {

        long durationMs = sessionEndTime - sessionStartTime;

        long durationSec = durationMs / 1000;

        double distanceKm = totalDistanceMeters / 1000.0;

        float avgAccuracy = 0;

        if (accuracyCount > 0) {
            avgAccuracy = accuracySum / accuracyCount;
        }

        String mode;

        if (multiGpsMode)
            mode = "MULTI_GPS";
        else if (continuousMode)
            mode = "CONTINUOUS";
        else
            mode = "STANDARD";

        SmsDebugManager.logTx("SESSION END");

        SmsDebugManager.logTx("mode=" + mode);

        SmsDebugManager.logTx("gps=" + gpsPointsCollected);

        SmsDebugManager.logTx("sms=" + smsSent);

        SmsDebugManager.logTx(
                String.format("dist=%.2fkm", distanceKm)
        );

        SmsDebugManager.logTx(
                String.format("acc=%.1fm", avgAccuracy)
        );
    }

    private void updateUiSmsCounter() {

        Intent intent = new Intent("TX_DEBUG_UPDATE");

        intent.putExtra("sms", smsSent);

        sendBroadcast(intent);
    }

    private void saveSmsLog() {

        try {

            File folder = new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOCUMENTS),
                    "SmsGpsTracker"
            );

            if (!folder.exists()) {
                folder.mkdirs();
            }

            String timestamp = new SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm",
                    Locale.getDefault()
            ).format(new Date());

            File file = new File(folder, "logsms_" + timestamp + ".txt");

            FileWriter writer = new FileWriter(file);

            // =========================
            // CALCOLI STATISTICHE
            // =========================

            long durationMs = sessionEndTime - sessionStartTime;

            long durationSec = durationMs / 1000;

            long minutes = durationSec / 60;
            long seconds = durationSec % 60;

            double distanceKm = totalDistanceMeters / 1000.0;

            float avgAccuracy = 0;

            if (accuracyCount > 0) {
                avgAccuracy = accuracySum / accuracyCount;
            }

            int logEntries = SmsDebugManager.getLogs().size();

            float compression = 0;

            if (gpsPointsCollected > 0) {
                compression = (float) smsSent / (float) gpsPointsCollected;
            }

            String mode;

            if (multiGpsMode)
                mode = "MULTI_GPS";
            else if (continuousMode)
                mode = "CONTINUOUS";
            else
                mode = "STANDARD";

            // =========================
            // SESSION STATS
            // =========================

            writer.write("===== SESSION STATS =====\n\n");

            writer.write("Session mode: " + mode + "\n");

            writer.write(String.format(
                    "Session duration: %02d:%02d\n",
                    minutes,
                    seconds
            ));

            writer.write("\n");

            writer.write("SMS sent: " + smsSent + "\n");

            writer.write("Log entries: " + logEntries + "\n");

            writer.write("\n");

            writer.write("GPS points: " + gpsPointsCollected + "\n");

            writer.write(String.format(
                    "Distance travelled: %.2f km\n",
                    distanceKm
            ));

            writer.write(String.format(
                    "Avg GPS accuracy: %.1f m\n",
                    avgAccuracy
            ));

            writer.write(String.format(
                    "Compression ratio: %.4f\n",
                    compression
            ));

            writer.write("\n--- MULTI GPS PARAMS ---\n");

            writer.write("Send interval (ms): " + multiSendIntervalMs + "\n");
            writer.write("Min distance (m): " + trackSimplifyDistance + "\n");
            writer.write("Angle threshold (deg): " + trackAngleThreshold + "\n");
            writer.write("Simplify tolerance: " + trackSimplifyTolerance + "\n");
            writer.write("Max points per SMS: " + maxPointsPerSms + "\n");
            writer.write("Keep points: " + keepPoints + "\n");
            writer.write("\n--- AUTO MODE ---\n");

            if (debugTrackEnabled) {

                writer.write("\n--- DEBUG TRACK ---\n");

                writer.write("RAW points: " + DebugTrackStore.rawCount + "\n");
                writer.write("FILTERED points: " + DebugTrackStore.filteredCount + "\n");
                writer.write("SIMPLIFIED points: " + DebugTrackStore.simplifiedCount + "\n");

                writer.write("Last SMS length: " + DebugTrackStore.smsLength + "\n");
            }

            writer.write("\n--- AUTO MODE ---\n");

            writer.write("Enabled: " + autoModeEnabled + "\n");

            if (autoModeEnabled && lastAdaptiveConfig != null) {

                writer.write("New interval (ms): " + lastAdaptiveConfig.intervalMs + "\n");
                writer.write("New distance (m): " + lastAdaptiveConfig.distance + "\n");
                writer.write("New angle (deg): " + lastAdaptiveConfig.angle + "\n");
                writer.write("New epsilon: " + lastAdaptiveConfig.epsilon + "\n");
                writer.write("New config: " + lastAdaptiveConfig + "\n");
            }

            writer.write("\n=========================\n\n");

            // =========================
            // LOG SMS
            // =========================

            for (String s : SmsDebugManager.getLogs()) {

                writer.write(s + "\n");

            }

            writer.close();

        } catch (IOException e) {

            Log.e("SMS_DEBUG", "Errore salvataggio file", e);

        }
        if (autoModeEnabled) {

            AdaptiveSession session = new AdaptiveSession();

            session.gpsPoints = gpsPointsCollected;
            session.smsSent = smsSent;
            session.distanceKm = totalDistanceMeters / 1000.0;
            session.avgAccuracy = accuracyCount > 0 ? accuracySum / accuracyCount : 0;
            session.durationSec = (sessionEndTime - sessionStartTime) / 1000;

            session.distanceParam = trackSimplifyDistance;
            session.angleParam = trackAngleThreshold;
            session.epsilonParam = trackSimplifyTolerance;
            session.intervalParam = multiSendIntervalMs;

            session.compressionRatio =
                    (double) smsSent / (double) gpsPointsCollected;

            AdaptiveStore.saveSession(this, session);

            if (autoModeEnabled) {

                if (currentConfig != null && session != null) {

                    AdaptiveConfig newConfig =
                            AdaptiveEngine.adjust(currentConfig, session);

                    if (newConfig != null) {
                        applyAdaptiveConfig(newConfig);
                    }
                }

            } else {
                Log.d("ADAPTIVE", "AUTO MODE OFF → config invariata");
            }
        }
    }

    private void applyAdaptiveConfig(AdaptiveConfig c) {

        if (c == null) return;

        trackSimplifyDistance = c.distance;
        trackAngleThreshold = c.angle;
        trackSimplifyTolerance = c.epsilon;
        multiSendIntervalMs = c.intervalMs;

        Log.d("ADAPTIVE", "Applied new config");
    }

    private void processTrackBuffer() {

        List<GpsPoint> rawPoints;

        synchronized (bufferLock) {

            List<GpsPoint> current = gpsTrackBuffer.getPointsCopy();

            if (current == null || current.size() < 5) {
                return;
            }

            rawPoints = new ArrayList<>(current);

        }

        if (!isRunning || !multiGpsMode || isProcessing) return;

        isProcessing = true;
        long now = System.currentTimeMillis();

        // 📦 sicurezza minima dati
        boolean enoughPoints =
                rawPoints.size() >= 10;

        if (rawPoints.size() < 10) {
            Log.d("TRACK", "Skip send (not enough points)");
            isProcessing = false;
            return;
        }


        // ================================
        // FILTRO + SEMPLIFICAZIONE
        // ================================

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

            if (dist < trackSimplifyDistance) continue;

            if (points.size() >= 2) {

                LatLng prev = points.get(points.size() - 2);
                LatLng last = points.get(points.size() - 1);

                double angle = angleBetween(prev, last, current);

                if (Math.abs(angle) < trackAngleThreshold) {
                    points.set(points.size() - 1, current);
                    lastKept = current;
                    continue;
                }
            }

            points.add(current);
            lastKept = current;
        }

        if (points.size() < 3) {
            isProcessing = false;
            return;
        }
        List<LatLng> simplified =
                TrackSimplifier.simplify(points, trackSimplifyTolerance);

        if (simplified.size() < 2) {
            isProcessing = false;
            return;
        }
        // ================================
        // ENCODING
        // ================================

        List<Pair<Double, Double>> polyPoints = new ArrayList<>();

        for (LatLng pt : simplified) {
            polyPoints.add(new Pair<>(pt.latitude, pt.longitude));
        }

        String encoded = PolylineCodec.INSTANCE.encode(polyPoints);

        while (encoded.length() > trackSmsMaxLen && simplified.size() > 2) {

            simplified.remove(simplified.size() - 1);

            polyPoints.clear();

            for (LatLng pt : simplified) {
                polyPoints.add(new Pair<>(pt.latitude, pt.longitude));
            }

            encoded = PolylineCodec.INSTANCE.encode(polyPoints);
        }

        // 🔴 LIMIT SMS
        if (smsSent >= maxSmsPerSession) {

            Log.d("TRACK", "Max SMS reached → STOP");

            stopTrackingInternal();
            isProcessing = false;
            return;
        }

        String payload = "T|" + encoded;
        String crc = SmsCrc.INSTANCE.crc8(payload);
        String sms = payload + "|" + crc;

        sendTrackSms(sms);
        lastTrackSmsTime = now;

        // ================================
        // ROLLING BUFFER
        // ================================

        synchronized (bufferLock) {

            List<GpsPoint> current = gpsTrackBuffer.getPointsCopy();

            int keep = Math.min(keepPoints, current.size());

            List<GpsPoint> tail = new ArrayList<>();

            for (int i = current.size() - keep; i < current.size(); i++) {
                tail.add(current.get(i));
            }

            gpsTrackBuffer.clear();

            for (GpsPoint gp : tail) {
                gpsTrackBuffer.addPoint(gp);
            }
        }
        isProcessing = false;
        if (debugTrackEnabled && DebugTrackActivity.isOpen) {

            DebugTrackStore.raw = convertGpsPointsToLatLng(rawPoints);
            DebugTrackStore.filtered = new ArrayList<>(points);
            DebugTrackStore.simplified = new ArrayList<>(simplified);

            DebugTrackStore.rawCount = rawPoints.size();
            DebugTrackStore.filteredCount = points.size();
            DebugTrackStore.simplifiedCount = simplified.size();
            DebugTrackStore.smsLength = sms.length();
            DebugTrackStore.lastSms = sms;
        }
    }

    private List<LatLng> convertGpsPointsToLatLng(List<GpsPoint> list) {

        List<LatLng> out = new ArrayList<>();

        for (GpsPoint p : list) {
            out.add(new LatLng(p.getLat(), p.getLon()));
        }

        return out;
    }

    private void stopTrackingInternal() {

        trackHandler.removeCallbacks(trackProcessorRunnable);
        synchronized (bufferLock) {
            gpsTrackBuffer.clear();
        }
        lastTrackSmsTime = 0;

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
            continuousLocationCallback = null;
        }
        smsHandler.removeCallbacks(smsRunnable);
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

        SmsDebugManager.logTx(text);

        if (smsDebugMode) {
            Log.d("SMS_DEBUG", "CTRL SMS BLOCCATO");
            return;
        }

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
    private void saveAdaptiveReport(AdaptiveSession session, AdaptiveConfig newConfig) {

        try {

            File folder = new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOCUMENTS),
                    "SmsGpsTracker"
            );

            if (!folder.exists()) {
                folder.mkdirs();
            }

            String timestamp = new SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss",
                    Locale.getDefault()
            ).format(new Date());

            File file = new File(folder, "adaptive_" + timestamp + ".txt");

            FileWriter writer = new FileWriter(file);

            // =========================
            // HEADER
            // =========================

            writer.write("===== ADAPTIVE REPORT =====\n\n");

            // =========================
            // SESSION INFO
            // =========================

            writer.write("GPS points: " + session.gpsPoints + "\n");
            writer.write("SMS sent: " + session.smsSent + "\n");
            writer.write(String.format("Distance: %.2f km\n", session.distanceKm));
            writer.write(String.format("Avg accuracy: %.1f m\n", session.avgAccuracy));
            writer.write("Duration: " + session.durationSec + " sec\n");

            writer.write("\n");

            writer.write(String.format("Compression ratio: %.5f\n",
                    session.compressionRatio));

            writer.write("\n=========================\n\n");

            // =========================
            // CONFIG USATA
            // =========================

            writer.write("=== CONFIG USED ===\n");

            writer.write("Distance: " + session.distanceParam + "\n");
            writer.write("Angle: " + session.angleParam + "\n");
            writer.write("Epsilon: " + session.epsilonParam + "\n");
            writer.write("Interval: " + session.intervalParam + "\n");

            writer.write("\n");

            // =========================
            // CONFIG NUOVA
            // =========================

            writer.write("=== NEW CONFIG ===\n");

            writer.write("Distance: " + newConfig.distance + "\n");
            writer.write("Angle: " + newConfig.angle + "\n");
            writer.write("Epsilon: " + newConfig.epsilon + "\n");
            writer.write("Interval: " + newConfig.intervalMs + "\n");

            writer.write("\n=========================\n");

            writer.close();

            Log.d("ADAPTIVE_FILE", "Report salvato: " + file.getAbsolutePath());

        } catch (Exception e) {
            Log.e("ADAPTIVE_FILE", "Errore salvataggio report", e);
        }
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
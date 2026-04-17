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
import java.util.LinkedList;
import java.util.Deque;
import java.util.ArrayDeque;
import android.telephony.ServiceState;
import java.util.Queue;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Build;
import androidx.core.content.ContextCompat;






public class TxForegroundService extends Service {

    public enum SignalLevel {
        EXCELLENT,
        GOOD,
        WEAK,
        CRITICAL,
        NO_SIGNAL
    }
    public enum NetworkState {
        ONLINE,
        WEAK_SIGNAL,
        NO_SIGNAL,
        RECOVERY
    }
    private final AtomicBoolean finalFlushStarted = new AtomicBoolean(false);
    private SignalLevel currentSignalLevel = SignalLevel.NO_SIGNAL;
    private NetworkState currentNetworkState = NetworkState.NO_SIGNAL;
    private boolean networkAvailable = false;
    private boolean finalSmsSent = false;
    private boolean isStopping = false;


    private long helpMeThresholdMs = 0;

    private float movementThreshold = 35f; // default

    private LatLng lastReferencePoint = null;


    private boolean noSignalAlertEnabled = false;

    private long noSignalStartTime = 0;
    private long lastVibrationTime = 0;

    private int noSignalTc = 10; // sec
    private int vibrationTs = 3; // sec
    private long lastMovementTime = 0;
    private Location lastMovementLocation = null;
    private long helpMeTimeMs = 120000;





    // buffer per media mobile
    private final LinkedList<Integer> signalHistory = new LinkedList<>();
    private static final int SIGNAL_WINDOW = 10;

    private final Deque<Integer> signalWindow = new ArrayDeque<>();
    private static final int WINDOW_SIZE = 10;

    private final Queue<String> smsQueue = new LinkedList<>();
    private final Object processingLock = new Object();




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
    private HandlerThread txThread = new HandlerThread("TX_SMS");
    private Handler txHandler;


    private boolean vibrationTriggered = false;
    private static final int NOTIFICATION_ID = 1;
    private Handler handler = new Handler(Looper.getMainLooper(), null);
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    private boolean debugTrackEnabled;
    private boolean isFinalFlush = false;


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

    private String encodeBase64(String input) {
        return Base64.encodeToString(
                input.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP
        );
    }


    // =======================
    // SMS PROTOCOLLO
    // =======================
    private String currentSessionId = "0000";
    private int currentSeq = 0;
    private int totalSms = 0;
    private String generateSessionId() {
        return Integer.toHexString((int)(System.currentTimeMillis() & 0xFFFF)).toUpperCase();
    }

    private SignalLevel classifySignal(int dbm) {

        if (dbm == 0 || dbm < -140) {
            return SignalLevel.NO_SIGNAL;
        }

        if (dbm > -70) return SignalLevel.EXCELLENT;
        if (dbm > -85) return SignalLevel.GOOD;
        if (dbm > -100) return SignalLevel.WEAK;

        return SignalLevel.CRITICAL;
    }

    private int computeAverageSignal(int newDbm) {

        signalHistory.add(newDbm);

        if (signalHistory.size() > SIGNAL_WINDOW) {
            signalHistory.removeFirst();
        }

        int sum = 0;

        for (int v : signalHistory) {
            sum += v;
        }

        return sum / signalHistory.size();
    }

    private int getSmoothedDbm(int newDbm) {

        signalWindow.addLast(newDbm);

        if (signalWindow.size() > WINDOW_SIZE) {
            signalWindow.removeFirst();
        }

        int sum = 0;
        for (int v : signalWindow) sum += v;

        return sum / signalWindow.size();
    }



    private String getTrend() {

        if (signalWindow.size() < 3) return "STABLE";

        int first = signalWindow.peekFirst();
        int last = signalWindow.peekLast();

        if (last - first > 5) return "IMPROVING";
        if (first - last > 5) return "DEGRADING";

        return "STABLE";
    }

    private void updateNetworkState(int dbm) {

        handleNoSignalAlert(currentNetworkState);

        NetworkState previous = currentNetworkState;

        if (!networkAvailable) {
            currentNetworkState = NetworkState.NO_SIGNAL;

        } else if (dbm < -100) {
            currentNetworkState = NetworkState.WEAK_SIGNAL;

        } else {
            currentNetworkState = NetworkState.ONLINE;
        }

        // 🔥 RILEVA RECOVERY
        if (previous == NetworkState.NO_SIGNAL &&
                currentNetworkState != NetworkState.NO_SIGNAL) {

            currentNetworkState = NetworkState.RECOVERY;

            Log.d("SMS_QUEUE", "RECOVERY DETECTED → FLUSH");

            flushQueue(); // 🔥 QUI
        }

        // 🔥 EXTRA SICUREZZA (consigliato)
        if (currentNetworkState == NetworkState.ONLINE &&
                !smsQueue.isEmpty()) {

            Log.d("SMS_QUEUE", "ONLINE + QUEUE → FLUSH");

            flushQueue(); // 🔥 QUI
        }
    }

    private void handleNoSignalAlert(NetworkState state) {

        if (!noSignalAlertEnabled) return;

        long now = System.currentTimeMillis();

        if (state == NetworkState.NO_SIGNAL) {

            if (noSignalStartTime == 0) {
                noSignalStartTime = now;
            }

            long elapsed = (now - noSignalStartTime) / 1000;

            if (elapsed >= noSignalTc &&
                    (now - lastVibrationTime > vibrationTs * 2000)) {

                triggerVibration();
                lastVibrationTime = now;
            }

        } else {
            noSignalStartTime = 0;
        }
    }

    private void queueSms(String sms) {

        if (!smsQueue.contains(sms)) {
            smsQueue.add(sms);
            Log.d("SMS_QUEUE", "Queued size=" + smsQueue.size());
        } else {
            Log.w("SMS_QUEUE", "DUPLICATO IGNORATO");
        }
    }

    private void flushQueue() {

        if (smsQueue.isEmpty()) {
            Log.d("SMS_QUEUE", "Queue vuota");
            return;
        }

        Log.d("SMS_QUEUE", "FLUSH START size=" + smsQueue.size());

        List<String> copy = new ArrayList<>(smsQueue);
        smsQueue.clear();

        for (int i = 0; i < copy.size(); i++) {

            String sms = copy.get(i);

            int delay = i * 500;

            handler.postDelayed(() -> {
                sendNow(sms);
            }, delay);
        }
    }

    private void sendNow(String text) {

        SmsManager smsManager = SmsManager.getDefault();

        Intent sentIntent = new Intent("SMS_SENT_ACTION");
        sentIntent.putExtra("text", text);
        sentIntent.putExtra("retry", 0);

        PendingIntent sentPI = PendingIntent.getBroadcast(
                this,
                (int) System.currentTimeMillis(),
                sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        smsManager.sendTextMessage(
                phoneNumber,
                null,
                text,
                sentPI,
                null
        );
    }

    private final BroadcastReceiver smsSentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String text = intent.getStringExtra("text");
            int retry = intent.getIntExtra("retry", 0);

            switch (getResultCode()) {

                case Activity.RESULT_OK:
                    Log.d("SMS_RETRY", "SMS OK");
                    break;

                default:
                    Log.w("SMS_RETRY", "SMS FAILED → retry=" + retry);

                    if (retry < 3) {

                        int delay = (int) Math.pow(2, retry) * 2000;

                        handler.postDelayed(() -> {
                            retrySend(text, retry + 1);
                        }, delay);

                    } else {

                        Log.e("SMS_RETRY", "RETRY FALLITI → METTO IN CODA");

                        // 🔥 NON PERDERE SMS
                        queueSms(text);
                    }
                    break;
            }
        }
    };

    private void retrySend(String text, int retry) {

        Log.d("SMS_RETRY", "RETRY #" + retry);

        SmsManager smsManager = SmsManager.getDefault();

        Intent sentIntent = new Intent("SMS_SENT_ACTION");
        sentIntent.putExtra("text", text);
        sentIntent.putExtra("retry", retry);

        PendingIntent sentPI = PendingIntent.getBroadcast(
                this,
                (int) System.currentTimeMillis(),
                sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        smsManager.sendTextMessage(
                phoneNumber,
                null,
                text,
                sentPI,
                null
        );
    }

    private String getNetworkType() {

        try {

            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {

                return "NO PERM";
            }

            int type = telephonyManager.getDataNetworkType();

            switch (type) {
                case TelephonyManager.NETWORK_TYPE_LTE: return "LTE";
                case TelephonyManager.NETWORK_TYPE_NR: return "5G";
                case TelephonyManager.NETWORK_TYPE_HSPA: return "HSPA";
                case TelephonyManager.NETWORK_TYPE_EDGE: return "EDGE";
                default: return "UNKNOWN";
            }

        } catch (Exception e) {
            return "ERR";
        }
    }

    private List<LatLng> convertRawToLatLng(List<GpsPoint> rawPoints) {
        List<LatLng> result = new ArrayList<>();
        for (GpsPoint gp : rawPoints) {
            result.add(new LatLng(gp.getLat(), gp.getLon()));
        }
        return result;
    }

    private List<String> splitEncoded(String encoded, int maxLen) {

        List<String> parts = new ArrayList<>();

        int start = 0;

        while (start < encoded.length()) {

            int end = Math.min(start + maxLen, encoded.length());

            parts.add(encoded.substring(start, end));

            start = end;
        }

        return parts;
    }

    private int computeChunkSize(int totalParts) {

        // es: "T10/10|" = ~7 char
        int headerSize = 7;

        int crcSize = 3; // |XX

        return 150 - headerSize - crcSize;
    }





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
    private float trackSimplifyDistance = 2.0f;

    // contatori debug
    private int gpsPointsCollected = 0;
    private int gpsPointsSent = 0;

    private long adaptiveGpsInterval = 3000;

    // ===== MULTI GPS ADVANCED SETTINGS =====
    private float trackSimplifyTolerance = 0.00002f;
    private float trackAngleThreshold = 0.8f;
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
                Log.e("STOP_DEBUG", "STOP → motivo XYZ");
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
                Log.d("SMART_STOP", "movement detected ? exit STOP mode");
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

    private void checkNoMovement(LatLng current) {

        if (lastReferencePoint == null) {
            lastReferencePoint = current;
            lastMovementTime = System.currentTimeMillis();
            return;
        }

        float[] result = new float[1];
        Location.distanceBetween(
                lastReferencePoint.latitude,
                lastReferencePoint.longitude,
                current.latitude,
                current.longitude,
                result
        );

        float distance = result[0];
        long now = System.currentTimeMillis();

        if (distance < 3) {
            long elapsed = now - lastMovementTime;

            if (elapsed >= helpMeThresholdMs) {

                Log.d("EMERGENCY", "NO MOVEMENT DETECTED");

                Location tmp = new Location("emergency");
                tmp.setLatitude(current.latitude);
                tmp.setLongitude(current.longitude);

                sendEmergencySms(tmp);

                // reset per evitare spam
                lastMovementTime = now;
            }

        } else {
            // movimento rilevato → reset
            lastReferencePoint = current;
            lastMovementTime = now;
        }
    }


    private void sendEmergencySms(Location loc) {

        if (loc == null) return;

        String payload = "CTRL|EMERGENCY|" +
                loc.getLatitude() + "," +
                loc.getLongitude();

        String sms = payload + "|" + SmsCrc.INSTANCE.crc8(payload);


        Log.d("SMS_PROTO", sms);

        sendTrackSms(sms);
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

                // =====================================
                // 🔴 NO MOVEMENT CHECK (ROBUSTO)
                // =====================================

                if (loc.hasAccuracy() && loc.getAccuracy() <= 30) {

                    final float MOVEMENT_THRESHOLD = 35f; // 🔥 35 metri

                    long now = System.currentTimeMillis();

                    if (lastMovementLocation != null) {

                        float dist = loc.distanceTo(lastMovementLocation);

                        if (dist > MOVEMENT_THRESHOLD) {

                            Log.d("HELP_ME", "REAL MOVE: " + dist + " m");

                            lastMovementTime = now;
                            lastMovementLocation = loc;

                        } else {

                            Log.d("HELP_ME", "JITTER: " + dist + " m");

                        }

                    } else {

                        lastMovementLocation = loc;
                        lastMovementTime = now;
                    }

                    // ⏱ controllo tempo immobilità
                    long elapsed = now - lastMovementTime;

                    Log.d("HELP_ME_DEBUG",
                            "elapsed=" + elapsed +
                                    " threshold=" + helpMeTimeMs +
                                    " acc=" + loc.getAccuracy()
                    );

                    if (elapsed > helpMeTimeMs) {

                        Log.d("HELP_ME", "🚨 NO MOVEMENT DETECTED");

                        sendEmergencySms(loc);

                        lastMovementTime = now; // 🔥 anti-spam
                    }

                } else {
                    Log.d("HELP_ME", "SKIP LOW ACCURACY");
                }

                // =====================================
                // 🔧 RESTO DEL TUO CODICE (UGUALE)
                // =====================================

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

                // ?? SOLO LOG DI DEBUG (opzionale)
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
            Log.e("STOP_DEBUG", "FINAL FLUSH TRIGGERED");

            trackHandler.postDelayed(this, multiGpsSendIntervalMs);
        }
    };

    private void flushTrackBuffer() {

        if (!multiGpsMode || isProcessing) return;

        isProcessing = true;
        long now = System.currentTimeMillis();

        List<GpsPoint> rawPoints;

        synchronized (bufferLock) {
            rawPoints = gpsTrackBuffer.getPointsCopy();
            if (rawPoints == null || rawPoints.size() < 5) {
                isProcessing = false;
                return;
            }
        }

        // ========================
        // FILTRO + SEMPLIFICAZIONE
        // ========================
        List<LatLng> points = convertRawToLatLng(rawPoints);
        LatLng lastKept = null;

        if (points.size() < 3) {
            isProcessing = false;
            return;
        }

        List<LatLng> simplified = TrackSimplifier.simplify(points, trackSimplifyTolerance);

        if (simplified.size() < 2) {
            isProcessing = false;
            return;
        }





        // ========================
        // DEBUG TRACK
        // ========================
        if (debugTrackEnabled && DebugTrackActivity.isOpen) {

            DebugTrackStore.raw = convertGpsPointsToLatLng(rawPoints);
            DebugTrackStore.filtered = new ArrayList<>(points);
            DebugTrackStore.simplified = new ArrayList<>(simplified);

            DebugTrackStore.rawCount = rawPoints.size();
            DebugTrackStore.filteredCount = points.size();
            DebugTrackStore.simplifiedCount = simplified.size();


        }

        // ========================
        // ROLLING BUFFER
        // ========================
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
    }
    private synchronized int nextSeq() {
        return currentSeq++;
    }



    // Helper per convertire LatLng in Pair<Double,Double>
    private List<Pair<Double, Double>> convertToPairList(List<LatLng> list) {
        List<Pair<Double, Double>> result = new ArrayList<>();
        for (LatLng l : list) {
            result.add(new Pair<>(l.latitude, l.longitude));
        }
        return result;
    }





    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {



        noSignalAlertEnabled = intent.getBooleanExtra("noSignalAlert", false);

        // 🔥 leggi anche da SharedPreferences
        SharedPreferences prefs = getSharedPreferences("SmsGpsTrackerPrefs", MODE_PRIVATE);

        noSignalTc = prefs.getInt("noSignalTc", 10);
        vibrationTs = prefs.getInt("vibrationTs", 3);
        int helpSec = prefs.getInt("help_me_time", 120); // default 2 min
        helpMeThresholdMs = helpSec * 1000L;
        helpMeTimeMs = prefs.getInt("helpMeTime", 120) * 1000L;
        movementThreshold = prefs.getFloat("movementThreshold", 35f);

        Log.d("TX_SERVICE", "Service started");

        startForeground(NOTIFICATION_ID, createNotification());

        if (intent == null) return START_STICKY;

        String action = intent.getAction();


        Log.e("STOP_DEBUG", "onStartCommand action=" + action);

        if (ACTION_START.equals(action)) {

            currentSessionId = generateSessionId();
            currentSeq = 0;

            // 🔥 RESET CRITICO (QUI!)
            finalFlushStarted.set(false);
            finalSmsSent = false;
            isFinalFlush = false;

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

            maxSms = intent.getIntExtra("maxSms", 10);
            intervalMinutes = intent.getIntExtra("interval", 1);

            //--------------------------------
            // MULTI GPS MODE (secondo ramo)
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

            Log.e("STOP_DEBUG", "ACTION_STOP RECEIVED");

            Log.d("TX_SERVICE", "STOP ACTION RECEIVED");

            sessionEndTime = System.currentTimeMillis();

            // ================================
            // 🔥 MULTIGPS → FINAL FLUSH
            // ================================
            if (multiGpsMode) {

                if (!finalFlushStarted.compareAndSet(false, true)) {
                    Log.w("TRACK", "STOP IGNORATO → final flush già avviato");
                    return START_NOT_STICKY;
                }

                Log.d("TRACK", "STOP → FINAL FLUSH START");

                isFinalFlush = true;
                Log.e("STOP_DEBUG", "isFinalFlush = TRUE", new Exception());

                processTrackBuffer(); // 🔥 unico punto generazione F
                Log.e("STOP_DEBUG", "FINAL FLUSH TRIGGERED");
            } else {

                // ================================
                // ALTRE MODALITÀ
                // ================================
                if (phoneNumber != null && !phoneNumber.isEmpty()) {
                    sendControlSms("CTRL:STOP");
                }
            }

            // ================================
            // SALVATAGGIO LOG
            // ================================
            saveSmsLog();

            // ================================
            // AUTO MODE
            // ================================
            if (autoModeEnabled) {

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

                    AdaptiveConfig newConfig = null;

                    try {
                        newConfig = AdaptiveEngine.adjust(currentConfig, session);
                    } catch (Exception e) {
                        Log.e("ADAPTIVE", "Adjust error", e);
                    }

                    if (newConfig != null) {

                        applyAdaptiveConfig(newConfig);
                        lastAdaptiveConfig = newConfig;

                        saveAdaptiveReport(session, newConfig);

                    } else {
                        Log.d("ADAPTIVE", "No new config applied");
                    }
                }
            }

            // ================================
            // STOP REALE SERVIZIO
            // ================================
            Log.e("STOP_DEBUG", "STOP → motivo XYZ");
            stopTrackingInternal();

            saveTxState("IDLE");

            stopForeground(true);
            stopSelf();

            return START_NOT_STICKY;
        }

        //--------------------------------
        // ABORT
        //--------------------------------
        if ("ACTION_ABORT".equals(action)) {
            Log.e("STOP_DEBUG", "STOP → motivo XYZ");
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
        // ?? PARAMETRI MULTI GPS
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
        // ?? INIT ADAPTIVE CONFIG
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
        // ? QUI VA IL LOG (POSIZIONE GIUSTA)
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

                if (!isRunning) return;

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

        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

            if (vibrator != null && vibrator.hasVibrator()) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                    vibrationTs * 1000L,
                                    VibrationEffect.DEFAULT_AMPLITUDE
                            )
                    );
                } else {
                    vibrator.vibrate(vibrationTs * 1000L);
                }
            }

        } catch (Exception e) {
            Log.e("VIBRATION", "Errore vibrazione", e);
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

        ContextCompat.registerReceiver(
                this,
                smsSentReceiver,
                new IntentFilter("SMS_SENT_ACTION"),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        txThread = new HandlerThread("TX_SMS_THREAD");
        txThread.start();

        txHandler = new Handler(txThread.getLooper());
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
        unregisterReceiver(smsSentReceiver);
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
                    "?? POS MANUALE:\n"
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
        if (debugTrackEnabled) {
            DebugTrackStore.reset();
        }
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

        // ?? SALVA COORDINATE GLOBALI
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

        SmsDebugManager.logTx(text);

        smsSent++;
        updateUiSmsCounter();

        if (smsDebugMode) return;

        // 🔥 PRIORITÀ MASSIMA PER SMS FINALE
        if (text.contains("|F|")) {

            Log.d("SMS_PROTO", "FORCE SEND FINAL SMS");

            flushQueue();     // invia eventuali SMS in coda
            sendNow(text);    // invia subito

            return;
        }

        switch (currentNetworkState) {

            case ONLINE:
                sendNow(text);
                break;

            case WEAK_SIGNAL:
                handler.postDelayed(() -> sendNow(text), 5000);
                break;

            case NO_SIGNAL:
                Log.w("SMS", "NO SIGNAL → ACCODO");
                queueSms(text);
                break;

            case RECOVERY:
                Log.d("SMS", "RECOVERY → FLUSH + SEND");
                flushQueue();
                sendNow(text);
                break;
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
            Log.e("STOP_DEBUG", "STOP → motivo XYZ");
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

            // =========================
            // ?? AUTO MODE ? CALCOLO PRIMA
            // =========================

            AdaptiveSession session = null;

            if (autoModeEnabled && gpsPointsCollected > 0) {

                session = new AdaptiveSession();

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

                if (currentConfig != null) {

                    AdaptiveConfig newConfig =
                            AdaptiveEngine.adjust(currentConfig, session);

                    if (newConfig != null) {

                        // ?? SALVA PER REPORT
                        lastAdaptiveConfig = newConfig;

                        // ?? APPLICA NUOVA CONFIG
                        applyAdaptiveConfig(newConfig);

                        Log.d("ADAPTIVE", "NEW CONFIG: " + newConfig);
                    }
                }
            }

            // =========================
            // ?? CREAZIONE FILE
            // =========================

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
            // ?? STATISTICHE
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
            // ?? SESSION STATS
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

            // =========================
            // ?? PARAMETRI MULTI GPS
            // =========================

            writer.write("\n--- MULTI GPS PARAMS ---\n");

            // ?? conversione ms ? minuti
            long intervalMinutes = multiSendIntervalMs / 60000;

            writer.write("Send interval (min): " + intervalMinutes + "\n");
            writer.write("Min distance (m): " + trackSimplifyDistance + "\n");
            writer.write("Angle threshold (deg): " + trackAngleThreshold + "\n");
            writer.write("Simplify tolerance: " + trackSimplifyTolerance + "\n");
            writer.write("Max points per SMS: " + maxPointsPerSms + "\n");
            writer.write("Keep points: " + keepPoints + "\n");

            // =========================
            // ?? AUTO MODE
            // =========================

            writer.write("\n--- AUTO MODE ---\n");
            writer.write("Enabled: " + autoModeEnabled + "\n");

            if (autoModeEnabled) {

                if (lastAdaptiveConfig != null) {

                    long intervalMin = lastAdaptiveConfig.intervalMs / 60000;

                    writer.write("\n--- NEW CONFIG ---\n");
                    writer.write("Interval: " + intervalMin + " min\n");
                    writer.write("Distance: " + lastAdaptiveConfig.distance + " m\n");
                    writer.write("Angle: " + lastAdaptiveConfig.angle + " deg\n");
                    writer.write("Epsilon: " + lastAdaptiveConfig.epsilon + "\n");

                } else {

                    writer.write("No adaptive config generated\n");
                }
            }

            // =========================
            // ?? DEBUG TRACK
            // =========================

            if (debugTrackEnabled) {

                writer.write("\n--- DEBUG TRACK ---\n");
                writer.write("Final SMS length: " + DebugTrackStore.smsLength + "\n");
                writer.write("Used epsilon: " + trackSimplifyTolerance + "\n");
                writer.write("Used distance: " + trackSimplifyDistance + "\n");
                writer.write("SMS count: " + smsSent + "\n");
                writer.write("RAW points: " + DebugTrackStore.rawCount + "\n");
                writer.write("FILTERED points: " + DebugTrackStore.filteredCount + "\n");
                writer.write("SIMPLIFIED points: " + DebugTrackStore.simplifiedCount + "\n");
                writer.write("Last SMS length: " + DebugTrackStore.smsLength + "\n");
            }
            writer.write("\n--- ADAPTIVE SMS ---\n");
            writer.write("Final length: " + AdaptiveSmsCompressor.lastEncodedLength + "\n");
            writer.write("Points used: " + AdaptiveSmsCompressor.lastPoints + "\n");
            writer.write("Iterations: " + AdaptiveSmsCompressor.lastIterations + "\n");

            writer.write("\n=========================\n\n");

            // =========================
            // ?? LOG SMS
            // =========================

            for (String s : SmsDebugManager.getLogs()) {
                writer.write(s + "\n");
            }

            writer.close();

        } catch (IOException e) {

            Log.e("SMS_DEBUG", "Errore salvataggio file", e);
        }
    }

    private void sendSmsPartsSequentially(List<String> parts, boolean isFinalFlush) {

        final String sessionSnapshot = currentSessionId;
        final int totalParts = parts.size();

        for (int i = 0; i < totalParts; i++) {

            final int index = i;

            long delay = i * 300L; // 🔥 sequenza reale

            txHandler.postDelayed(() -> {

                // 🔥 BLOCCO 1 — sessione cambiata
                if (!sessionSnapshot.equals(currentSessionId)) {
                    Log.w("SEQ", "SMS DROPPATO → session changed");
                    return;
                }

                // 🔥 BLOCCO 2 — F già inviato
                if (finalSmsSent) {
                    Log.e("STOP_DEBUG", "PROCESS BLOCCATO → finalSmsSent=true");
                    return;
                }

                int seq = nextSeq();

                boolean isFinal = isFinalFlush && index == totalParts - 1;

                // 🔥 BLOCCO 3 — evita D durante flush finale
                if (isFinalFlush && !isFinal) {
                    Log.w("SEQ", "SMS DROPPATO → flush finale in corso");
                    return;
                }

                String type = isFinal ? "F" : "D";

                if (isFinal) {

                    if (finalSmsSent) {
                        Log.e("STOP_DEBUG", "PROCESS BLOCCATO → finalSmsSent=true");
                        return;
                    }

                    finalSmsSent = true;

                    Log.d("TRACK", "FINAL SMS SENT → LOCK ACTIVATED");
                }

                Log.d("SEQ", "SEND seq=" + seq + " type=" + type);

                String header = "TX|" +
                        currentSessionId + "|" +
                        seq + "|" +
                        type + "|";

                // 🔥 payload originale (solo dati)
                String payloadRaw = parts.get(index);

                // 🔥 encode Base64
                String payloadEncoded = encodeBase64(payloadRaw);

                // 🔥 costruzione messaggio con payload ENCODATO
                String payload = header + payloadEncoded;

                // 🔥 CRC sul payload codificato
                String sms = payload + "|" + SmsCrc.INSTANCE.crc8(payload);

                sendTrackSms(sms);

            }, delay);
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

    private String buildSafeSms(String encoded, int seq) {

        String header = "TX|" + currentSessionId + "|" + seq + "|D|";

        String payload = header + encoded;

        String full = payload + "|" + SmsCrc.INSTANCE.crc8(payload);

        // 🔥 HARD CUT ASSOLUTO
        if (full.length() > 140) {

            int maxPayloadLen = 140
                    - header.length()
                    - 3; // CRC

            if (encoded.length() > maxPayloadLen) {
                encoded = encoded.substring(0, maxPayloadLen);
            }

            payload = header + encoded;
            full = payload + "|" + SmsCrc.INSTANCE.crc8(payload);
        }

        return full;
    }

    private void processTrackBuffer() {


        Log.e("STOP_DEBUG",
                "processTrackBuffer CALLED | final=" + finalSmsSent +
                        " flush=" + isFinalFlush,
                new Exception());

                // 🔥 BLOCCO TOTALE DOPO F (sempre)
                if (finalSmsSent) {
                    Log.e("STOP_DEBUG", "PROCESS BLOCCATO → finalSmsSent=true");
                    return;
                }

                // ================================
                // 🔒 LOCK PROCESSING (ATOMICO)
                // ================================
                synchronized (processingLock) {
                    if (!isRunning || !multiGpsMode || isProcessing) {
                        return;
                    }
                    isProcessing = true;
                }

                try {

                    List<GpsPoint> rawPoints;

                    // ================================
                    // 📦 BUFFER READ (THREAD SAFE)
                    // ================================
                    synchronized (bufferLock) {

                        List<GpsPoint> current = gpsTrackBuffer.getPointsCopy();

                        if (current == null || current.isEmpty()) {

                            // 🔥 CASO CRITICO → BUFFER VUOTO + FLUSH
                            if (current == null || current.isEmpty()) {

                                if (isFinalFlush && !finalSmsSent) {

                                    List<String> parts = new ArrayList<>();
                                    parts.add("END");

                                    sendSmsPartsSequentially(parts, true);

                                    return;
                                }

                                return;
                            }

                            return;
                        }

                        // 🔥 NO INVIO PARZIALE SE NON FLUSH
                        if (!isFinalFlush && current.size() < 10) {
                            return;
                        }

                        rawPoints = new ArrayList<>(current);
                    }

                    long now = System.currentTimeMillis();

                    // ================================
                    // 📍 CONVERSIONE
                    // ================================
                    List<LatLng> latLngPoints = convertGpsPointsToLatLng(rawPoints);

                    // ================================
                    // 🔴 SMART POINT REDUCTION (NO LOSS TRACK)
                    // ================================
                    if (latLngPoints.size() > 300) {

                        Log.w("ADAPT", "SMART REDUCTION → original: " + latLngPoints.size());

                        // 🔥 mantieni inizio + fine + campionamento centrale
                        List<LatLng> reduced = new ArrayList<>();

                        int keepHead = 50;
                        int keepTail = 50;

                        // START
                        reduced.addAll(latLngPoints.subList(0, keepHead));

                        // MIDDLE (sampling)
                        int step = latLngPoints.size() / 200;
                        for (int i = keepHead; i < latLngPoints.size() - keepTail; i += step) {
                            reduced.add(latLngPoints.get(i));
                        }

                        // END
                        reduced.addAll(latLngPoints.subList(
                                latLngPoints.size() - keepTail,
                                latLngPoints.size()
                        ));

                        latLngPoints = reduced;

                        Log.w("ADAPT", "AFTER REDUCTION → " + latLngPoints.size());
                    }
                    // ================================
                    // 🎯 PARAMETRI SMS (UNA SOLA VOLTA)
                    // ================================
                    int maxSmsLen = 140;

                    String testHeader = "TX|" + currentSessionId + "|" + (currentSeq + 1) + "|D|";
                    int headerLen = testHeader.length();

                    int crcLen = 3;
                    int safetyMargin = 10;

                    // spazio reale payload
                    int targetPayloadLen = maxSmsLen - headerLen - crcLen - safetyMargin;



                    Log.d("ADAPT", "Target payload len=" + targetPayloadLen);

                    // ================================
                    // 🧠 COMPRESSIONE ADATTIVA (SMS SAFE)
                    // ================================
                    CompressionResult bestResult = null;
                    int bestSmsLen = Integer.MAX_VALUE;

                    float tolerance = trackSimplifyTolerance * 0.7f;

                    String payload;
                    int smsLen;

                    for (int i = 0; i < 10; i++) {

                        CompressionResult temp = AdaptiveSmsCompressor.compressToSms(
                                latLngPoints,
                                tolerance,
                                trackSimplifyDistance,
                                trackAngleThreshold,
                                200
                        );

                        payload = "TX|" + currentSessionId + "|" + (currentSeq + 1) + "|D|" + temp.encoded;
                        smsLen = (payload + "|" + SmsCrc.INSTANCE.crc8(payload)).length();

                        Log.d("ADAPT", "Iter " + i + " smsLen=" + smsLen + " tol=" + tolerance);

                        // 🔥 best candidate
                        int targetSmsLen = 120;

                        if (bestResult == null ||
                                Math.abs(smsLen - targetSmsLen) < Math.abs(bestSmsLen - targetSmsLen)) {

                            bestResult = temp;
                            bestSmsLen = smsLen;
                        }

                        // 🎯 target OK
                        if (smsLen >= 110 && smsLen <= 140) {
                            break;
                        }

                        // 🧠 auto adapt
                        if (smsLen > 140) {
                            tolerance *= 1.6f;   // 🔥 più aggressivo
                        } else if (smsLen < 100) {
                            tolerance *= 0.6f;
                        } else {
                            tolerance *= 0.8f;
                        }
                    }

                    // ✅ risultato iniziale
                    CompressionResult res = bestResult;

                    // ================================
                    // 🚨 HARD LIMIT SMS (OBBLIGATORIO)
                    // ================================

                    payload = "TX|" + currentSessionId + "|" + (currentSeq + 1) + "|D|" + res.encoded;
                    smsLen = (payload + "|" + SmsCrc.INSTANCE.crc8(payload)).length();

                    // 🔥 sicurezza assoluta
                    int safety = 0;

                    while (smsLen > 140 && safety < 10) {

                        Log.w("ADAPT", "FORCE COMPRESSION → smsLen=" + smsLen);

                        tolerance *= 1.25f;

                        res = AdaptiveSmsCompressor.compressToSms(
                                latLngPoints,
                                tolerance,
                                trackSimplifyDistance,
                                trackAngleThreshold,
                                200
                        );

                        // 🔴 RICALCOLO SUBITO
                        payload = "TX|" + currentSessionId + "|" + (currentSeq + 1) + "|D|" + res.encoded;
                        smsLen = (payload + "|" + SmsCrc.INSTANCE.crc8(payload)).length();

                        // 🚨 HARD FAILSAFE (OBBLIGATORIO)
                        if (smsLen > 140) {

                            Log.e("ADAPT", "HARD TRIM ACTIVATED → smsLen=" + smsLen);

                            int maxPayloadLen = 140
                                    - ("TX|" + currentSessionId + "|" + (currentSeq + 1) + "|D|").length()
                                    - 3 // CRC
                                    - 2; // margine sicurezza

                            if (res.encoded.length() > maxPayloadLen) {
                                res.encoded = res.encoded.substring(0, maxPayloadLen);
                            }

                            // ricalcolo finale
                            payload = "TX|" + currentSessionId + "|" + (currentSeq + 1) + "|D|" + res.encoded;
                            smsLen = (payload + "|" + SmsCrc.INSTANCE.crc8(payload)).length();

                            Log.e("ADAPT", "AFTER TRIM → smsLen=" + smsLen);
                        }



                        payload = "TX|" + currentSessionId + "|" + (currentSeq + 1) + "|D|" + res.encoded;
                        smsLen = (payload + "|" + SmsCrc.INSTANCE.crc8(payload)).length();

                        safety++;
                    }

                    // ================================
                    // 📊 DEBUG STORE
                    // ================================

                    if (debugTrackEnabled) {
                        DebugTrackStore.smsHistory.add(smsLen);
                    }

                    Log.d("ADAPT", "FINAL smsLen BEST");

                    if (debugTrackEnabled && res != null) {

                        // =========================
                        // 🔴 RAW (ACCUMULATO CORRETTO)
                        // =========================
                        DebugTrackStore.raw.addAll(latLngPoints);
                        DebugTrackStore.rawCount = DebugTrackStore.raw.size();

                        // =========================
                        // 🟡 FILTERED
                        // =========================
                        List<LatLng> filteredPoints =
                                BasicFilter.apply(latLngPoints,
                                        (float) trackSimplifyDistance,
                                        (float) trackAngleThreshold);

                        DebugTrackStore.filtered.addAll(filteredPoints);
                        DebugTrackStore.filteredCount = DebugTrackStore.filtered.size();

                        // =========================
                        // 🟢 SIMPLIFIED
                        // =========================
                        List<LatLng> simplifiedPoints =
                                TrackSimplifier.simplify(filteredPoints, res.usedEpsilon);

                        DebugTrackStore.simplified.addAll(simplifiedPoints);
                        DebugTrackStore.simplifiedCount = DebugTrackStore.simplified.size();

                        // =========================
                        // 📊 DEBUG INFO
                        // =========================
                        DebugTrackStore.lastSms = res.encoded;
                        DebugTrackStore.smsLength = res.encoded.length();

                        // =========================
                        // 📈 HISTORY
                        // =========================
                        DebugTrackStore.rawHistory.add(latLngPoints.size());
                        DebugTrackStore.filteredHistory.add(filteredPoints.size());
                        DebugTrackStore.simplifiedHistory.add(simplifiedPoints.size());
                        DebugTrackStore.timeHistory.add(now);
                    }



            Log.d("ADAPT", "FINAL len=" + res.encoded.length());

            // ================================
            // 🚫 LIMITE SESSIONE
            // ================================
                    if (smsSent >= maxSmsPerSession) {
                        Log.e("STOP_DEBUG", "STOP → MAX SMS REACHED: " + smsSent);
                        stopTrackingInternal();
                        return;
                    }

            // ================================
            // ✂️ SPLIT SMS (RIUSA VARIABILI)
            // ================================
            String encoded = res.encoded;

            // 🔥 chunk size calcolato UNA VOLTA
            int chunkSize = maxSmsLen - headerLen - crcLen - safetyMargin;

                    List<String> parts = new ArrayList<>();

                    int seqBase = currentSeq + 1;

                    List<String> rawParts = splitEncoded(encoded, chunkSize);

                    for (int i = 0; i < rawParts.size(); i++) {

                        String safeSms = buildSafeSms(rawParts.get(i), seqBase + i);

                        // 🔥 GARANZIA ASSOLUTA
                        if (safeSms.length() > 140) {
                            Log.e("SMS", "OVERFLOW BLOCCATO → " + safeSms.length());
                            safeSms = safeSms.substring(0, 140);
                        }

                        parts.add(safeSms);
                    }
            int totalParts = parts.size();

            // 🔥 RICALCOLO SICURO
            chunkSize = computeChunkSizeSafe(totalParts, headerLen);
            parts = splitEncoded(encoded, chunkSize);

            // 🔥 RICALCOLO SICURO
            chunkSize = computeChunkSizeSafe(totalParts, headerLen);
            parts = splitEncoded(encoded, chunkSize);

            // ================================
            // 🚀 INVIO SEQUENZIALE
            // ================================
            sendSmsPartsSequentially(parts, isFinalFlush);

            // ================================
            // 🔥 GESTIONE FLUSH
            // ================================
            boolean wasFinalFlush = isFinalFlush;
            isFinalFlush = false;

            lastTrackSmsTime = now;

            // ================================
            // ♻️ ROLLING BUFFER (solo NON finale)
            // ================================
            if (!wasFinalFlush) {

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

            // ================================
            // 🛑 FINALE
            // ================================
            if (wasFinalFlush) {
                Log.d("TRACK", "FINAL FLUSH COMPLETED");
            }

        } finally {

            synchronized (processingLock) {
                isProcessing = false;
            }
        }
    }
    private int computeChunkSizeSafe(int totalParts, int headerLen) {

        int maxSmsLen = 135;
        int crcLen = 3;
        int safetyMargin = 10;

        int chunk = maxSmsLen - headerLen - crcLen - safetyMargin;

        // 🔥 protezione extra (mai troppo grande)
        if (chunk > 120) chunk = 120;

        // 🔥 protezione minima
        if (chunk < 50) chunk = 50;

        Log.d("SMS_DEBUG", "Chunk finale safe=" + chunk + " parts=" + totalParts);

        return chunk;
    }

    private List<LatLng> convertGpsPointsToLatLng(List<GpsPoint> list) {

        List<LatLng> out = new ArrayList<>();

        for (GpsPoint p : list) {
            out.add(new LatLng(p.getLat(), p.getLon()));
        }

        return out;
    }

    private void stopTrackingInternal() {

        Log.e("STOP_DEBUG", "stopTrackingInternal CALLED", new Exception());

        // 👇 AGGIUNGI QUESTO SUBITO DOPO
        Log.e("STOP_DEBUG", "isRunning=" + isRunning +
                " finalSmsSent=" + finalSmsSent +
                " isFinalFlush=" + isFinalFlush);

        isFinalFlush = true;
        Log.e("STOP_DEBUG", "FINAL FLUSH TRIGGERED");
        processTrackBuffer();


        trackHandler.removeCallbacks(trackProcessorRunnable);
        synchronized (bufferLock) {
            gpsTrackBuffer.clear();
        }
        lastTrackSmsTime = 0;

        if (!isRunning) return;

        isRunning = false;
        Log.e("STOP_DEBUG", "isRunning → FALSE");

        handler.removeCallbacksAndMessages(null);

        rxMonitorHandler.removeCallbacksAndMessages(null);

        // ?? STOP SOLO IL TIMER UI SPECIFICO
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

        // ?? NUOVO EXTRA PER LED GPS
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

        if (multiGpsMode && text.equals("CTRL:STOP")) {
            Log.d("CTRL", "STOP SMS evitato in MULTIGPS");
            return;
        }

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

        // ? Inizializza TelephonyManager prima di usarlo
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager == null) {
            Log.e("TX_SERVICE", "TelephonyManager is null! Signal monitoring disabled.");
            return;
        }

        // ? Crea PhoneStateListener
        signalListener = new PhoneStateListener() {


            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                super.onSignalStrengthsChanged(signalStrength);

                try {

                    int lastDbm = lastSignalDbm; // fallback

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                        List<CellSignalStrength> strengths =
                                signalStrength.getCellSignalStrengths();

                        if (strengths != null && !strengths.isEmpty()) {

                            int bestDbm = Integer.MIN_VALUE;

                            for (CellSignalStrength css : strengths) {

                                int dbm = css.getDbm();

                                if (dbm < 0 && dbm > -140) {

                                    if (dbm > bestDbm) {
                                        bestDbm = dbm;
                                    }
                                }
                            }

                            if (bestDbm != Integer.MIN_VALUE) {
                                lastDbm = bestDbm;
                            }
                        }

                    } else {

                        int gsm = signalStrength.getGsmSignalStrength();

                        if (gsm != 99) {
                            lastDbm = -113 + 2 * gsm;
                        }
                    }

                    // ============================
                    // ?? AGGIORNAMENTO GLOBALE
                    // ============================
                    lastSignalDbm = lastDbm;

                    // ============================
                    // ?? STEP 1 — STATO RETE
                    // ============================
                    updateNetworkState(lastSignalDbm);

                    // ============================
                    // ?? STEP 3 — VECCHIO SISTEMA (mantieni)
                    // ============================
                    sendSignalUpdate(lastSignalDbm);

                    // ============================
                    // ?? DEBUG
                    // ============================
                    Log.d("NETWORK",
                            "dbm=" + lastSignalDbm +
                                    " state=" + currentNetworkState +
                                    " type=" + getNetworkType());

                } catch (Exception e) {
                    Log.e("TX_SERVICE", "Error reading signal", e);
                }
            }


            @Override
            public void onServiceStateChanged(ServiceState serviceState) {

                int state = serviceState.getState();

                switch (state) {
                    case ServiceState.STATE_IN_SERVICE:
                        networkAvailable = true;
                        break;

                    default:
                        networkAvailable = false;
                        break;
                }
            }
        };

        // ? Ora registra il listener
        telephonyManager.listen(
                signalListener,
                PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
                        PhoneStateListener.LISTEN_SERVICE_STATE
        );

        Log.d("TX_SERVICE", "Signal monitor started");
    }


    private void sendSignalUpdate(int dbm) {

        String networkType = getNetworkType();

        NetworkState state;

        if (!networkAvailable) {
            state = NetworkState.NO_SIGNAL;
        } else if (dbm < -100) {
            state = NetworkState.WEAK_SIGNAL;
        } else {
            state = NetworkState.ONLINE;
        }

        currentNetworkState = state;

        Intent intent = new Intent("NETWORK_UPDATE");

        intent.setPackage(getPackageName()); // ? FONDAMENTALE

        intent.putExtra("dbm", dbm);
        intent.putExtra("type", networkType);
        intent.putExtra("state", state.name());

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
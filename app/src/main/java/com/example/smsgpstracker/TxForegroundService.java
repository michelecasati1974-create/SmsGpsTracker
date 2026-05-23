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
import java.util.concurrent.atomic.AtomicLong;
import java.util.Queue;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Build;
import androidx.core.content.ContextCompat;
import android.app.Activity;
import java.util.concurrent.ConcurrentHashMap;

public class TxForegroundService extends Service {

    public enum NetworkState {
        ONLINE,
        WEAK_SIGNAL,
        NO_SIGNAL,
        RECOVERY
    }
    private final AtomicBoolean finalFlushStarted = new AtomicBoolean(false);
    private NetworkState currentNetworkState = NetworkState.NO_SIGNAL;
    private boolean networkAvailable = false;
    private boolean finalSmsSent = false;
    private boolean trackRunnableScheduled = false;
    // ================================
    // 📦 QUEUE + STATO
    // ================================
    private final Queue<SmsBatch> smsQueue =
            new LinkedList<>();

    private static final int MAX_SMS_RETRY = 3;

    private final ConcurrentHashMap<String, Integer> retryMap =
            new ConcurrentHashMap<>();

    private static final int MAX_QUEUE_SIZE = 100;

    private static final int MAX_PENDING_SEGMENTS = 100;

    private static final long TX_QUEUE_TIMEOUT_MS = 120000;

    private final AtomicLong nextPointId =
            new AtomicLong(1);

    private final AtomicLong nextSegmentId =
            new AtomicLong(1);

    private boolean smsReceiverRegistered = false;
    private boolean isSending = false;
    private String sessionMode = "STANDARD";
    private List<String> currentBatch = null;
    private int currentIndex = 0;
    private int currentRetry = 0;
    private final Object smsLock = new Object();
    private long helpMeThresholdMs = 0;
    private float movementThreshold = 35f; // default
    private boolean noSignalAlertEnabled = false;
    private long noSignalStartTime = 0;
    private long lastVibrationTime = 0;
    private int noSignalTc = 10; // sec
    private int vibrationTs = 3; // sec
    private long lastMovementTime = 0;
    private Location lastMovementLocation = null;
    private long helpMeTimeMs = 120000;
    private final Object processingLock = new Object();
    private int currentChunkId = 0;
    public static boolean smsDebugMode = false;
    private boolean currentBatchFinal = false;
    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_FORCE_POSITION = "com.example.smsgpstracker.FORCE_POSITION";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_UPDATE =
            "com.example.smsgpstracker.TX_UPDATE";
    public static final String ACTION_ABORT = "ACTION_ABORT";
    public static final String ACTION_SET_MONITOR_INTERVAL =
            "com.example.smsgpstracker.SET_MONITOR_INTERVAL";
    private GpsTrackBuffer gpsTrackBuffer;
    private String sessionType = "STANDARD";
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
    private boolean firstBlockSent = false;
    private boolean vibrationTriggered = false;
    private static final int NOTIFICATION_ID = 1;
    private Handler handler = new Handler(Looper.getMainLooper(), null);
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean debugTrackEnabled;
    private boolean isFinalFlush = false;
    private SequenceManager sequenceManager = new SequenceManager();
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
    private boolean multiGpsMode = false;
    private boolean autoModeEnabled = true;
    private final List<TrackPointEx> fullTrackHistory =
            new ArrayList<>();

    private final ConcurrentHashMap<Long, TrackSegment>
            pendingSegments = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_POINTS = 5000;
    private long rxTimeoutMs;
    private Handler rxMonitorHandler = new Handler(Looper.getMainLooper());
    private long lastSmsTime = 0;
    private SharedPreferences seqPrefs;
    private int sequenceNumber = 0;
    private static final String SEQ_PREFS = "tx_sequence_prefs";
    private static final String KEY_SEQ = "tx_sequence";
    private static final String KEY_TX_STATE = "tx_state";
    private Handler smsHandler = new Handler();
    private Location lastAcceptedLocation = null;
    private volatile boolean stopPending = false;
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
    private boolean sessionWasMultiGps = false;
    private final Object txQueueLock = new Object();

    private final AtomicBoolean serviceStopping =
            new AtomicBoolean(false);

    private final AtomicBoolean destroyCalled =
            new AtomicBoolean(false);

    private final AtomicBoolean finalFlushExecuting =
            new AtomicBoolean(false);

    private final AtomicBoolean queueProcessing =
            new AtomicBoolean(false);

    private final AtomicBoolean trackLoopActive =
            new AtomicBoolean(false);

    private static class SmsBatch {

        List<String> parts;
        boolean finalBatch;
        String sessionId;
        long createdAt;

        SmsBatch(
                List<String> parts,
                boolean finalBatch,
                String sessionId
        ) {
            this.parts = parts;
            this.finalBatch = finalBatch;
            this.sessionId = sessionId;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private static class TrackSegment {

        long segmentId;

        long startPointId;

        long endPointId;

        long createdAt;

        boolean acknowledged;

        int retryCount;

        String encodedPayload;

        List<TrackPointEx> points;
    }

    // =======================
    // SMS PROTOCOLLO
    // =======================
    private String currentSessionId = "0000";
    private int currentSeq = 0;
    private int globalSequence = 0;
    private boolean smsLogSaved = false;
    private String generateSessionId() {
        return Integer.toHexString((int)(System.currentTimeMillis() & 0xFFFF)).toUpperCase();
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
            Log.d("SMS_QUEUE", "RECOVERY DETECTED → PROCESS QUEUE");
            processNextBatch(); // ✅ NUOVO
        }
        // 🔥 EXTRA SICUREZZA
        if (currentNetworkState == NetworkState.ONLINE &&
                !smsQueue.isEmpty()) {
            Log.d("SMS_QUEUE", "ONLINE + QUEUE → PROCESS");
            processNextBatch(); // ✅ NUOVO
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
    private final BroadcastReceiver smsSentReceiver =
            new BroadcastReceiver() {

                @Override
                public void onReceive(
                        Context context,
                        Intent intent
                ) {

                    try {

                        String retryKey =
                                intent.getStringExtra(
                                        "retryKey"
                                );

                        int result =
                                getResultCode();

                        if (result == Activity.RESULT_OK) {

                            Log.d("SMS_SENT",
                                    "SUCCESS " + retryKey);

                        } else {

                            Log.e("SMS_SENT",
                                    "FAILED code=" + result +
                                            " key=" + retryKey);
                        }

                    } catch (Exception e) {

                        Log.e("SMS_SENT",
                                "receiver error",
                                e);
                    }
                }
            };
    private void processNextBatch() {

        Log.e(
                "BATCH_TRACE",
                "ENTER processNextBatch " +
                        "queueSize=" + smsQueue.size() +
                        " isSending=" + isSending +
                        " stopPending=" + stopPending +
                        " finalFlush=" + isFinalFlush
        );

        synchronized (txQueueLock) {

            if (queueProcessing.get()) {
                return;
            }

            SmsBatch batch = smsQueue.poll();

            if (batch == null) {
                return;
            }

            queueProcessing.set(true);

            Log.d("SMS_QUEUE",
                    "PROCESS batch final=" + batch.finalBatch);

            txHandler.post(() -> {

                try {

                    sendNextPart(
                            batch.parts,
                            0,
                            batch.sessionId,
                            batch.finalBatch
                    );

                } catch (Exception e) {

                    Log.e("SMS_QUEUE",
                            "PROCESS ERROR",
                            e);

                }
            });
        }

        Log.e(
                "STOP_TRACE",
                "processNextBatch EXIT " +
                        " isProcessing=" + isProcessing +
                        " stopPending=" + stopPending +
                        " isFinalFlush=" + isFinalFlush +
                        " finalSmsSent=" + finalSmsSent
        );
    }

// ================================
// 🔥 TX CLEANUP
// ================================

    private void clearTxQueues() {

        synchronized (txQueueLock) {

            smsQueue.clear();
        }

        retryMap.clear();

        pendingSegments.clear();
    }

// ================================
// 🔥 SESSION RESET
// ================================

    private void resetSessionRuntime() {

        finalSmsSent = false;

        isFinalFlush = false;

        finalFlushStarted.set(false);

        finalFlushExecuting.set(false);

        queueProcessing.set(false);

        serviceStopping.set(false);

        destroyCalled.set(false);

        firstBlockSent = false;

        lastTrackSmsTime = 0;

        smsLogSaved = false;
    }
    private void enqueueSmsBatch(
            List<String> parts,
            boolean isFinal
    ) {

        if (parts == null || parts.isEmpty()) {

            Log.e("SMS_QUEUE",
                    "enqueue aborted → empty batch");

            return;
        }

        synchronized (txQueueLock) {

            if (smsQueue.size() > MAX_QUEUE_SIZE) {

                Log.e("SMS_QUEUE",
                        "QUEUE OVERFLOW → clear");

                smsQueue.clear();
            }

            SmsBatch batch = new SmsBatch(
                    new ArrayList<>(parts),
                    isFinal,
                    currentSessionId
            );

            smsQueue.add(batch);

            Log.d("SMS_QUEUE",
                    "ENQUEUE batch size=" + parts.size() +
                            " final=" + isFinal +
                            " queue=" + smsQueue.size());

            if (!queueProcessing.get()) {

                processNextBatch();
            }
        }
    }

    private void onSingleSmsSentSuccess() {
        boolean batchCompleted = false;
        synchronized (smsLock) {
            currentIndex++;
            currentRetry = 0;
            if (currentBatch == null || currentIndex >= currentBatch.size()) {
                batchCompleted = true;
                isSending = false;
                currentBatch = null;
            }
        }
        if (!batchCompleted) {
            // 🔁 prossimo SMS
            handler.post(this::sendCurrentSms);
            return;
        }
        // =========================
        // 🟢 BATCH COMPLETATO
        // =========================
        Log.d("SMS_QUEUE", "BATCH COMPLETATO");
        // 🔥 STOP POST-FLUSH (PUNTO CORRETTO)
        if (stopPending) {
            Log.e("STOP_FLOW", "FINAL FLUSH COMPLETED → STOP SERVICE");
            stopPending = false;
            stopTrackingInternal();
            stopForeground(true);
            stopSelf();
            return;
        }
        // 🔁 eventuali altri batch in coda
        processNextBatch();
    }
    private void sendCurrentSms() {

        String sms;

        // =====================================
        // 🔥 INFO BATCH
        // =====================================
        boolean isLastSms;
        boolean isFinalSms;

        synchronized (smsLock) {

            if (currentBatch == null || currentIndex >= currentBatch.size()) {

                Log.e("SMS_SEND", "NO SMS TO SEND");
                return;
            }

            sms = currentBatch.get(currentIndex);

            // 🔥 ultimo sms del batch
            isLastSms = (currentIndex == currentBatch.size() - 1);

            // 🔥 ultimo sms batch finale
            isFinalSms = currentBatchFinal && isLastSms;

            // =====================================
            // DEBUG + STATS
            // =====================================
            SmsDebugManager.logTx(sms);

            smsSent++;

            updateUiSmsCounter();

            Log.e("SMS_SEND",
                    "SENDING " +
                            currentIndex + "/" + currentBatch.size() +
                            " final=" + isFinalSms);
        }

        try {

            SmsManager smsManager = SmsManager.getDefault();

            String action = getPackageName() + ".SMS_SENT";

            Intent sentIntent = new Intent(action);

            sentIntent.setPackage(getPackageName());

            sentIntent.putExtra("retry", currentRetry);

            // 🔥 PASSA FLAG FINALE
            sentIntent.putExtra("is_final_sms", isFinalSms);

            PendingIntent sentPI = PendingIntent.getBroadcast(
                    this,
                    currentIndex,
                    sentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // =====================================
            // 🔥 REGISTER RECEIVER
            // =====================================
            if (!smsReceiverRegistered) {

                IntentFilter filter = new IntentFilter(action);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                    registerReceiver(
                            smsSentReceiver,
                            filter,
                            Context.RECEIVER_NOT_EXPORTED
                    );

                } else {

                    registerReceiver(smsSentReceiver, filter);
                }

                smsReceiverRegistered = true;
            }

            // =====================================
            // 🚀 INVIO SMS
            // =====================================
            smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    sms,
                    sentPI,
                    null
            );

            // =====================================
            // 🔥 CHIUSURA SESSIONE
            // =====================================
            if (isFinalSms) {

                Log.e("FINAL_FLOW",
                        "ULTIMO SMS INVIATO → CHIUSURA SESSIONE");

                sessionEndTime = System.currentTimeMillis();

                saveSmsLog();

                saveTxState("IDLE");

                stopTrackingInternal();
            }

        } catch (Exception e) {

            Log.e("SMS_SEND", "EXCEPTION", e);

            handleRetry();
        }
    }
    private void handleRetry() {
        if (currentRetry < 3) {
            int delay = (int) Math.pow(2, currentRetry) * 2000;
            Log.w("SMS_RETRY", "Retry tra " + delay + " ms");
            currentRetry++;
            handler.postDelayed(this::sendCurrentSms, delay);
        } else {
            Log.e("SMS_RETRY", "FALLITO → SKIP SMS");
            // ⚠️ NON bloccare batch
            onSingleSmsSentSuccess();
        }
    }
    private String getNetworkType() {

        try {

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {

                return "--";
            }

            int type = telephonyManager.getDataNetworkType();

            // fallback VOCE
            if (type == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                type = telephonyManager.getVoiceNetworkType();
            }

            switch (type) {

                case TelephonyManager.NETWORK_TYPE_GPRS:
                    return "GPRS";

                case TelephonyManager.NETWORK_TYPE_EDGE:
                    return "EDGE";

                case TelephonyManager.NETWORK_TYPE_UMTS:
                    return "UMTS";

                case TelephonyManager.NETWORK_TYPE_HSPA:
                    return "HSPA";

                case TelephonyManager.NETWORK_TYPE_LTE:
                    return "LTE";

                case TelephonyManager.NETWORK_TYPE_NR:
                    return "5G";

                case TelephonyManager.NETWORK_TYPE_GSM:
                    return "GSM";

                default:
                    return "Rete registrata";
            }

        } catch (Exception e) {

            return "--";
        }
    }
    ///////Nuovi parametri configurabili (Settings)/////
    private long lastTrackSmsTime = 0;
    // limite sessione
    // compressione percorso
    private float trackSimplifyDistance = 2.0f;
    // contatori debug
    private int gpsPointsCollected = 0;
    private long adaptiveGpsInterval = 3000;
    // ===== MULTI GPS ADVANCED SETTINGS =====
    private float trackSimplifyTolerance = 0.00002f;
    private float trackAngleThreshold = 0.8f;
    private int maxPointsPerSms = 5;
    private long multiGpsSendIntervalMs = 15000;
    private int keepPoints = 3;
    ////////////////////////////////////////////////////
    private SharedPreferences statePrefs;
    private AdaptiveConfig lastAdaptiveConfig = null;
    private AdaptiveConfig currentConfig;
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
    public enum TxStatus {
        IDLE,
        WAITING,
        TRACKING
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
    private final Runnable trackProcessorRunnable =
            new Runnable() {

                @Override
                public void run() {

                    try {

                        if (!isRunning) {
                            trackRunnableScheduled = false;
                            return;
                        }

                        if (!multiGpsMode) {
                            trackRunnableScheduled = false;
                            return;
                        }

                        if (serviceStopping.get()) {
                            trackRunnableScheduled = false;
                            return;
                        }

                        if (trackLoopActive.get()) {

                            Log.w("TRACK_LOOP",
                                    "SKIP → previous processing still active");

                            trackHandler.postDelayed(
                                    this,
                                    multiGpsSendIntervalMs
                            );

                            return;
                        }

                        trackLoopActive.set(true);

                        processTrackBuffer();
                        Log.e(
                                "STOP_TRACE",
                                "processTrackBuffer DONE"
                        );

                    } catch (Exception e) {

                        Log.e("TRACK_LOOP",
                                "Runnable exception",
                                e);

                    } finally {

                        trackLoopActive.set(false);

                        if (isRunning &&
                                multiGpsMode &&
                                !serviceStopping.get()) {

                            trackHandler.postDelayed(
                                    this,
                                    multiGpsSendIntervalMs
                            );

                        } else {

                            trackRunnableScheduled = false;
                        }
                    }
                }
            };
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.e("SERVICE_LIFECYCLE",
                "onStartCommand PID=" + android.os.Process.myPid() +
                        " intent=" + intent +
                        " action=" + (intent != null ? intent.getAction() : "NULL"),
                new Exception());

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

// 🔴 CASO 1: restart totale
        if (intent == null) {

            Log.e("SERVICE", "⚠️ RESTART DA SISTEMA (intent=NULL)");

            restoreStateOrStop();

            return START_NOT_STICKY;
        }

        String action = intent.getAction();

// 🔴 CASO 2: restart sporco
        if (action == null) {

            Log.e("SERVICE", "⚠️ RESTART CON ACTION NULL");

            restoreStateOrStop();

            return START_NOT_STICKY;
        }


        Log.e("STOP_DEBUG", "onStartCommand action=" + action);

        if (ACTION_START.equals(action)) {

            Log.e("STOP_FLOW", "ENTER ACTION_START");

            currentSessionId = generateSessionId();
            currentSeq = 0;
            currentChunkId = 0;
            smsLogSaved = false;

            gpsTrackBuffer.clear();
            fullTrackHistory.clear();


            finalSmsSent = false;
            finalFlushStarted.set(false);
            isFinalFlush = false;

            // 🔥 FIX 2 — RESET PRIMO BLOCCO
            firstBlockSent = false;


            // ================================
            // 🔥 RESET TOTALE SESSIONE
            // ================================
            finalFlushStarted.set(false);
            finalSmsSent = false;
            sessionWasMultiGps = false;
            setFinalFlush(false, "ACTION_START RESET");

            // 🔴 FIX CRITICO
            trackRunnableScheduled = false;
            trackHandler.removeCallbacks(trackProcessorRunnable);

            isProcessing = false;

            // opzionale ma utile debug
            Log.d("TRACK", "RESET COMPLETO SESSIONE");

            // ================================
            String mode = intent.getStringExtra("MODE");
            if (mode == null) mode = "STANDARD";

            if ("MULTI_GPS_SMS".equals(mode)) {

                sessionWasMultiGps = true;
                sessionType = "MULTIGPS";

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

            if (multiGpsMode) {
                sessionMode = "MULTI_GPS";
            } else if (continuousMode) {
                sessionMode = "CONTINUOUS";
            } else {
                sessionMode = "STANDARD";
            }
            //--------------------------------
            // CONTINUOUS MODE
            //--------------------------------
            if ("CONTINUOUS".equals(mode)) {

                Log.d("TX_SERVICE", "CONTINUOUS MODE");

                multiGpsMode = false;
                continuousMode = true;
                sessionWasMultiGps = false;
                sessionType = "CONTINUOUS";

                if (phoneNumber == null || phoneNumber.isEmpty()) {
                    Log.e("TX_SERVICE","Phone number missing");
                    stopSelf();
                    return START_NOT_STICKY;
                }

                startIntervalTracking();
                return START_STICKY;
            }

            //--------------------------------
            // STANDARD MODE
            //--------------------------------
            Log.d("TX_SERVICE", "STANDARD MODE");

            multiGpsMode = false;
            continuousMode = false;
            sessionWasMultiGps = false;
            sessionType = "STANDARD";

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
        if (ACTION_STOP.equals(action)) {

            Log.e(
                    "STOP_TRACE",
                    "ACTION_STOP ENTER " +
                            "multiGpsMode=" + multiGpsMode +
                            " sessionWasMultiGps=" + sessionWasMultiGps +
                            " stopPending=" + stopPending +
                            " finalFlushStarted=" + finalFlushStarted.get() +
                            " isFinalFlush=" + isFinalFlush
            );

            sessionEndTime = System.currentTimeMillis();

            // ================================
            // 🔥 MULTIGPS → FINAL FLUSH
            // ================================

            if (multiGpsMode || sessionWasMultiGps) {

                if (!finalFlushStarted.compareAndSet(false, true)) {

                    Log.w(
                            "TRACK",
                            "STOP IGNORATO → final flush già avviato"
                    );

                    return START_NOT_STICKY;
                }

                Log.d(
                        "TRACK",
                        "STOP → FINAL FLUSH START"
                );

                stopPending = true;

                setFinalFlush(
                        true,
                        "ACTION_STOP"
                );
                Log.e(
                        "STOP_TRACE",
                        "FINAL FLUSH FLAG SET"
                );

                processTrackBuffer();
                Log.e(
                        "STOP_TRACE",
                        "processTrackBuffer DONE"
                );

                // 🔥 FORZA PARTENZA INVIO
                processNextBatch();

                return START_STICKY;

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
    private void restoreStateOrStop() {

        SharedPreferences prefs =
                getSharedPreferences(
                        "TX_STATE",
                        MODE_PRIVATE
                );

        boolean wasRunning =
                prefs.getBoolean(
                        "wasRunning",
                        false
                );

        if (!wasRunning) {

            Log.e(
                    "SERVICE",
                    "No active session → stopSelf()"
            );

            stopSelf();

            return;
        }

        Log.e(
                "SERVICE",
                "🔥 RIPRISTINO SESSIONE"
        );

        // =====================================
        // RIPRISTINO SESSIONE
        // =====================================

        currentSessionId =
                prefs.getString(
                        "sessionId",
                        generateSessionId()
                );

        // =====================================
        // RIPRISTINO MODALITÀ
        // =====================================

        multiGpsMode =
                prefs.getBoolean(
                        "multiGpsMode",
                        false
                );

        continuousMode =
                prefs.getBoolean(
                        "continuousMode",
                        false
                );

        sessionWasMultiGps =
                prefs.getBoolean(
                        "sessionWasMultiGps",
                        false
                );

        sessionType =
                prefs.getString(
                        "sessionType",
                        "UNKNOWN"
                );

        multiGpsSendIntervalMs =
                prefs.getLong(
                        "multiGpsSendIntervalMs",
                        15000
                );

        Log.e(
                "SERVICE_RESTORE",
                "session=" + currentSessionId +
                        " multi=" + multiGpsMode +
                        " cont=" + continuousMode +
                        " wasMulti=" + sessionWasMultiGps +
                        " type=" + sessionType
        );

        // =====================================
        // RESET RUNTIME
        // =====================================

        resetSessionRuntime();

        isRunning = true;

        // =====================================
        // STOP CALLBACK VECCHI
        // =====================================

        trackHandler.removeCallbacksAndMessages(null);

        trackRunnableScheduled = false;

        // =====================================
        // RIAVVIO LOOP MULTIGPS
        // =====================================

        if (multiGpsMode || sessionWasMultiGps) {

            Log.e(
                    "SERVICE_RESTORE",
                    "Riavvio trackProcessorRunnable"
            );

            if (!trackRunnableScheduled) {

                trackRunnableScheduled = true;

                trackHandler.postDelayed(
                        trackProcessorRunnable,
                        multiGpsSendIntervalMs
                );
            }
        }
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
        sessionWasMultiGps = true;
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
    private void saveTxState(String state) {

        SharedPreferences prefs =
                getSharedPreferences("TX_STATE", MODE_PRIVATE);

        prefs.edit()

                // =========================
                // STATO GENERALE
                // =========================
                .putString(KEY_TX_STATE, state)

                .putBoolean("wasRunning", isRunning)

                // =========================
                // SESSIONE
                // =========================
                .putString("sessionId", currentSessionId)

                // =========================
                // MODALITÀ
                // =========================
                .putBoolean("multiGpsMode", multiGpsMode)

                .putBoolean("continuousMode", continuousMode)

                .putBoolean(
                        "sessionWasMultiGps",
                        sessionWasMultiGps
                )

                .putString(
                        "sessionType",
                        sessionType
                )

                // =========================
                // CONFIG LOOP
                // =========================
                .putLong(
                        "multiGpsSendIntervalMs",
                        multiGpsSendIntervalMs
                )

                .apply();

        Log.d(
                "TX_STATE",
                "Saved state=" + state +
                        " multi=" + multiGpsMode +
                        " cont=" + continuousMode +
                        " session=" + currentSessionId
        );
    }
    @Override
    public void onCreate() {

        debugTrackEnabled = false;
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
        Log.e("SERVICE_LIFECYCLE",
                "SERVICE onCreate() PID=" + android.os.Process.myPid(),
                new Exception());
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

        if (smsReceiverRegistered) {
            try {
                unregisterReceiver(smsSentReceiver);
            } catch (Exception ignored) {}
            smsReceiverRegistered = false;
        }

        super.onDestroy();

        Log.e("SERVICE_LIFECYCLE", "onDestroy()", new Exception());

        saveTxState("IDLE");

        if (fusedClient != null && continuousLocationCallback != null) {
            fusedClient.removeLocationUpdates(continuousLocationCallback);
        }

        stopForeground(true);
    }
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.e("SERVICE_LIFECYCLE", "onTaskRemoved()");
        super.onTaskRemoved(rootIntent);
    }
    @Override
    public void onTrimMemory(int level) {
        Log.e("MEMORY", "onTrimMemory level=" + level);
        super.onTrimMemory(level);
    }
    @Override
    public void onLowMemory() {
        Log.e("MEMORY", "onLowMemory()");
        super.onLowMemory();
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

        if (isRunning) {

            Log.w("TRACK",
                    "already running");

            return;
        }

        resetSessionRuntime();

        sessionStartTime =
                System.currentTimeMillis();

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

        isRunning = true;

        cycleStartTime =
                System.currentTimeMillis();

        gpsFixValid = false;

        // 🔥 salva stato runtime
        SharedPreferences prefs =
                getSharedPreferences(
                        "TX_STATE",
                        MODE_PRIVATE
                );

        prefs.edit()
                .putBoolean("wasRunning", true)
                .putString(
                        "sessionId",
                        currentSessionId
                )
                .apply();

        startContinuousGps();

        startUiTimer();

        sendUpdate(
                TxStatus.WAITING,
                0,
                0
        );

        // =================================================
        // 🔥 MULTI GPS MODE
        // =================================================

        if (multiGpsMode) {

            trackHandler.removeCallbacksAndMessages(null);

            if (!trackRunnableScheduled) {

                trackRunnableScheduled = true;

                trackHandler.postDelayed(
                        trackProcessorRunnable,
                        multiGpsSendIntervalMs
                );
            }

            return;
        }

        // =================================================
        // 🔥 STANDARD MODE
        // =================================================

        requestSingleImmediateLocation();

        sendControlSms("CTRL:START");

        startTimer();

        rxTimeoutMs =
                intervalMinutes * 150000L;

        startRxMonitor();

        SharedPreferences hbPrefs =
                getSharedPreferences(
                        "HEARTBEAT",
                        MODE_PRIVATE
                );

        hbPrefs.edit()
                .putLong(
                        "lastAlive",
                        System.currentTimeMillis()
                )
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

        if (text == null || text.isEmpty()) {
            return;
        }

        List<String> single =
                new ArrayList<>();

        single.add(text);

        enqueueSmsBatch(
                single,
                text.contains("|F|")
        );
    }

    private boolean sendTrackSmsInternal(
            String text,
            String retryKey
    ) {

        if (text == null || text.isEmpty()) {

            Log.e("SMS_TX",
                    "EMPTY SMS");

            return false;
        }

        if (!isRunning && !isFinalFlush) {

            Log.e("SMS_TX",
                    "NOT RUNNING");

            return false;
        }

        if (phoneNumber == null ||
                phoneNumber.isEmpty()) {

            Log.e("SMS_TX",
                    "PHONE NULL");

            return false;
        }

        try {

            // =================================================
            // 🔥 DEBUG LOG
            // =================================================

            SmsDebugManager.logTx(text);

            // =================================================
            // 🔥 COUNTER
            // =================================================

            smsSent++;

            updateUiSmsCounter();

            // =================================================
            // 🔥 DEBUG MODE
            // =================================================

            if (smsDebugMode) {

                Log.d("SMS_DEBUG",
                        "SMS BLOCKED DEBUG MODE");

                return true;
            }

            SmsManager smsManager;

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S) {

                smsManager =
                        getSystemService(
                                SmsManager.class
                        );

            } else {

                smsManager =
                        SmsManager.getDefault();
            }

            if (smsManager == null) {

                Log.e("SMS_TX",
                        "SmsManager NULL");

                return false;
            }

            // =================================================
            // 🔥 SENT INTENT
            // =================================================

            Intent sentIntent =
                    new Intent(
                            "SMS_SENT_ACTION"
                    );

            sentIntent.putExtra(
                    "retryKey",
                    retryKey
            );

            PendingIntent sentPI =
                    PendingIntent.getBroadcast(
                            this,
                            retryKey.hashCode(),
                            sentIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT |
                                    PendingIntent.FLAG_IMMUTABLE
                    );

            smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    text,
                    sentPI,
                    null
            );

            Log.d("SMS_TX",
                    "SMS SENT len=" + text.length());

            return true;

        } catch (Exception e) {

            Log.e("SMS_TX",
                    "SEND ERROR",
                    e);

            return false;
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
    private void updateUiSmsCounter() {

        Intent intent = new Intent("TX_DEBUG_UPDATE");

        intent.putExtra("sms", smsSent);

        sendBroadcast(intent);
    }
    private void saveSmsLog() {

        if (smsLogSaved) {

            Log.e("SMS_LOG",
                    "SAVE BLOCCATO → già salvato");

            return;
        }

        if (sessionStartTime <= 0 ||
                sessionEndTime <= 0 ||
                sessionEndTime < sessionStartTime) {

            Log.e("SMS_LOG",
                    "SESSION TIME INVALID");

            return;
        }

        smsLogSaved = true;

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
            String mode = sessionType;
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
    private void sendBatchSequentially(List<String> parts, int index) {
        if (index >= parts.size()) {
            Log.e("SMS_QUEUE", "BATCH COMPLETED");
            // passa al batch successivo
            processNextBatch();
            return;
        }
        String sms = parts.get(index);
        Log.e("SMS_SEND", "SENDING " + index + "/" + parts.size());
        try {
            SmsManager smsManager = SmsManager.getDefault();
            PendingIntent sentPI = PendingIntent.getBroadcast(
                    this,
                    index,
                    new Intent("SMS_SENT_" + index),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    unregisterReceiver(this);
                    if (getResultCode() == Activity.RESULT_OK) {
                        Log.e("SMS_SEND", "OK " + index);
                        // prossimo SMS SOLO DOPO conferma
                        new Handler(Looper.getMainLooper()).postDelayed(() ->
                                sendBatchSequentially(parts, index + 1), 300);
                    } else {
                        Log.e("SMS_SEND", "FAILED " + index);
                        // retry semplice
                        new Handler(Looper.getMainLooper()).postDelayed(() ->
                                sendBatchSequentially(parts, index), 1000);
                    }
                }
            }, new IntentFilter("SMS_SENT_" + index));
            smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    sms,
                    sentPI,
                    null
            );
        } catch (Exception e) {
            Log.e("SMS_SEND", "EXCEPTION " + index, e);
            // retry
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    sendBatchSequentially(parts, index), 1000);
        }
    }
    private void sendNextPart(
            List<String> parts,
            int index,
            String sessionSnapshot,
            boolean finalBatch
    ){

        if (parts == null || parts.isEmpty()) {

            Log.e("SEQ",
                    "empty parts");

            return;
        }

        // =====================================================
        // 🔥 END OF BATCH
        // =====================================================

        if (index >= parts.size()) {

            Log.e("SEQ_END",
                    "ALL PARTS SENT");

            queueProcessing.set(false);

            processNextBatch();

            if (stopPending) {

                Log.e("STOP_FLOW",
                        "FINAL BATCH COMPLETE → STOP SERVICE");

                stopPending = false;

                sessionEndTime = System.currentTimeMillis();

                try {
                    saveSmsLog();
                } catch (Exception e) {
                    Log.e("STOP_FLOW", "saveSmsLog error", e);
                }

                try {
                    saveTxState("IDLE");
                } catch (Exception e) {
                    Log.e("STOP_FLOW", "saveTxState error", e);
                }

                stopTrackingInternal();
            }

            return;
        }

        // =====================================================
        // 🔥 SESSION CHANGED
        // =====================================================

        if (!sessionSnapshot.equals(currentSessionId)) {

            Log.w("SEQ",
                    "DROP → session changed");

            return;
        }

        // =====================================================
        // 🔥 STOPPING
        // =====================================================

        if (serviceStopping.get()) {

            Log.e("SEQ",
                    "STOPPING ACTIVE");

            return;
        }

        // =====================================================
        // 🔥 FINAL ALREADY SENT
        // =====================================================

        if (finalSmsSent) {

            Log.e("SEQ",
                    "BLOCKED → finalSmsSent=true");

            return;
        }

        final int currentIndex = index;

        String sms = parts.get(currentIndex);

        // =====================================================
        // 🔥 FINAL DETECTION
        // =====================================================

        boolean isFinal =
                finalBatch &&
                        currentIndex == parts.size() - 1;

        Log.e("SMS_SEND_REAL",
                "SENDING [" +
                        currentIndex +
                        "/" +
                        parts.size() +
                        "] → " +
                        sms);

        // =====================================================
        // 🔥 RETRY KEY
        // =====================================================

        String retryKey =
                currentSessionId +
                        "_" +
                        currentIndex;

        int retry =
                retryMap.getOrDefault(
                        retryKey,
                        0
                );

        try {

            boolean success =
                    sendTrackSmsInternal(
                            sms,
                            retryKey
                    );

            if (!success) {

                retry++;

                retryMap.put(
                        retryKey,
                        retry
                );

                if (retry >= MAX_SMS_RETRY) {

                    Log.e("SMS_SEND",
                            "MAX RETRY REACHED index=" +
                                    currentIndex);

                    return;
                }

                int finalRetry = retry;

                txHandler.postDelayed(() -> {

                    Log.e("SMS_RETRY",
                            "retry=" + finalRetry);

                    sendNextPart(
                            parts,
                            currentIndex + 1,
                            sessionSnapshot,
                            finalBatch
                    );

                }, 1500L);

                return;
            }

            // =================================================
            // 🔥 SUCCESS
            // =================================================

            retryMap.remove(retryKey);

        } catch (Exception e) {

            Log.e("SEQ_ERROR",
                    "SEND FAILED index=" + currentIndex,
                    e);

            return;
        }

        // =====================================================
        // 🔥 FINAL SENT
        // =====================================================

        if (isFinal) {

            finalSmsSent = true;

            Log.e("TRACK",
                    "FINAL SMS MARKED SENT");

            //completeFinalFlush();
        }

        // =====================================================
        // 🔥 NEXT PART
        // =====================================================

        txHandler.postDelayed(() -> {

            sendNextPart(
                    parts,
                    currentIndex + 1,
                    sessionSnapshot,
                    finalBatch
            );

        }, 450L);
    }


    private void applyAdaptiveConfig(AdaptiveConfig c) {
        if (c == null) return;
        trackSimplifyDistance = c.distance;
        trackAngleThreshold = c.angle;
        trackSimplifyTolerance = c.epsilon;
        multiSendIntervalMs = c.intervalMs;
        Log.d("ADAPTIVE", "Applied new config");
    }
    private double calculateAngle(LatLng a, LatLng b, LatLng c) {
        double abX = b.longitude - a.longitude;
        double abY = b.latitude - a.latitude;
        double bcX = c.longitude - b.longitude;
        double bcY = c.latitude - b.latitude;
        double dot = abX * bcX + abY * bcY;
        double magAB = Math.sqrt(abX * abX + abY * abY);
        double magBC = Math.sqrt(bcX * bcX + bcY * bcY);
        if (magAB == 0 || magBC == 0) return 180;
        double cosAngle = dot / (magAB * magBC);
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));
        return Math.toDegrees(Math.acos(cosAngle));
    }

    private List<String> splitString(String text, int chunkSize) {

        List<String> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return chunks;
        }

        for (int start = 0; start < text.length(); start += chunkSize) {

            int end = Math.min(
                    text.length(),
                    start + chunkSize
            );

            chunks.add(text.substring(start, end));
        }

        return chunks;
    }

    private static class TrackPointEx {
        long pointId;
        Location location;
        long timestamp;
    }



    private void processTrackBuffer() {

        Log.e("FLUSH_STATE",
                "ENTER processTrackBuffer | isFinalFlush=" +
                        isFinalFlush +
                        " | finalStarted=" +
                        finalFlushStarted.get() +
                        " | finalSent=" +
                        finalSmsSent +
                        " | isRunning=" +
                        isRunning,
                new Exception());

        // =====================================================
        // 🔥 GHOST FLUSH PROTECTION
        // =====================================================

        if (isFinalFlush &&
                !finalFlushStarted.get()) {

            Log.e("FLUSH_GHOST",
                    "👻 GHOST FLUSH DETECTED",
                    new Exception());

            return;
        }

        // =====================================================
        // 🔥 FINAL BLOCK
        // =====================================================

        if (finalSmsSent) {

            Log.e("STOP_DEBUG",
                    "PROCESS BLOCKED → finalSmsSent=true");

            return;
        }

        // =====================================================
        // 🔥 STOPPING
        // =====================================================

        if (serviceStopping.get()) {

            Log.e("PROCESS",
                    "SERVICE STOPPING");

            return;
        }

        // =====================================================
        // 🔥 LOCK
        // =====================================================

        synchronized (processingLock) {

            if (!isRunning ||
                    !multiGpsMode ||
                    isProcessing) {

                return;
            }

            isProcessing = true;
        }

        try {

            List<GpsPoint> rawPoints =
                    new ArrayList<>();

            TrackSegment segment = null;

            // =================================================
            // 🔥 BUFFER READ
            // =================================================

            synchronized (bufferLock) {

                List<GpsPoint> current =
                        gpsTrackBuffer.getPointsCopy();

                if (current == null ||
                        current.isEmpty()) {

                    // =========================================
// 🔥 FINAL EMPTY FLUSH
// =========================================

                    if (isFinalFlush &&
                            !finalSmsSent) {

                        Log.e("STOP_DEBUG",
                                "FINAL EMPTY FLUSH");

                        String header =
                                "TX|" +
                                        currentSessionId +
                                        "|0|0/1|F|";

                        String crc =
                                SmsCrc.INSTANCE.crc8(
                                        header
                                );

                        String sms =
                                header + "|" + crc;

                        List<String> parts =
                                new ArrayList<>();

                        parts.add(sms);

                        // 🔥 QUEUE FINAL BATCH
                        enqueueSmsBatch(
                                parts,
                                true
                        );

                        Log.e("FINAL_FLUSH",
                                "FINAL EMPTY BATCH ENQUEUED");
                    }

                    return;
                }

                // =============================================
                // 🔥 FIRST BLOCK RULES
                // =============================================

                int MIN_FIRST_BLOCK = 25;

                int MIN_NORMAL_BLOCK = 10;

                if (!isFinalFlush) {

                    if (!firstBlockSent) {

                        if (current.size() <
                                MIN_FIRST_BLOCK) {

                            return;
                        }

                    } else {

                        if (current.size() <
                                MIN_NORMAL_BLOCK) {

                            return;
                        }
                    }
                }

                // =============================================
                // 🔥 HISTORY BUILD
                // =============================================

                for (GpsPoint gps : current) {

                    TrackPointEx newPoint =
                            new TrackPointEx();

                    newPoint.pointId =
                            nextPointId.getAndIncrement();

                    Location loc =
                            new Location("gps");

                    loc.setLatitude(
                            gps.getLat()
                    );

                    loc.setLongitude(
                            gps.getLon()
                    );


                    newPoint.location = loc;

                    newPoint.timestamp =
                            gps.getTimestamp();

                    // =========================================
                    // 🔥 FIRST POINT
                    // =========================================

                    if (fullTrackHistory.isEmpty()) {

                        fullTrackHistory.add(
                                newPoint
                        );

                        continue;
                    }

                    TrackPointEx last =
                            fullTrackHistory.get(
                                    fullTrackHistory.size() - 1
                            );

                    Location lastLoc =
                            last.location;

                    // =========================================
                    // 🔥 DUPLICATE FILTER
                    // =========================================

                    double latDiff =
                            Math.abs(
                                    lastLoc.getLatitude() -
                                            loc.getLatitude()
                            );

                    double lonDiff =
                            Math.abs(
                                    lastLoc.getLongitude() -
                                            loc.getLongitude()
                            );

                    if (latDiff > 0.000001 ||
                            lonDiff > 0.000001) {

                        fullTrackHistory.add(
                                newPoint
                        );
                    }
                }

                // =============================================
                // 🔥 MEMORY LIMIT
                // =============================================

                if (fullTrackHistory.size() >
                        MAX_HISTORY_POINTS) {

                    Log.w("MEMORY",
                            "HISTORY TRIM");

                    int trimCount =
                            fullTrackHistory.size() - 4000;

                    if (trimCount > 0) {

                        fullTrackHistory.subList(
                                0,
                                trimCount
                        ).clear();
                    }
                }

                // =============================================
                // 🔥 BUILD TEMP SEGMENT
                // =============================================

                int startIndex =
                        Math.max(
                                0,
                                fullTrackHistory.size() - 300
                        );

                List<TrackPointEx> segmentPoints =
                        new ArrayList<>(
                                fullTrackHistory.subList(
                                        startIndex,
                                        fullTrackHistory.size()
                                )
                        );

                if (segmentPoints.isEmpty()) {

                    Log.d("TX_FLOW",
                            "NO SEGMENT POINTS");

                    return;
                }

                segment = new TrackSegment();

                segment.segmentId =
                        nextSegmentId.getAndIncrement();

                segment.createdAt =
                        System.currentTimeMillis();

                segment.points =
                        new ArrayList<>(
                                segmentPoints
                        );

                segment.startPointId =
                        segmentPoints.get(0).pointId;

                segment.endPointId =
                        segmentPoints.get(
                                segmentPoints.size() - 1
                        ).pointId;

                segment.acknowledged = false;

                segment.retryCount = 0;

                // =============================================
                // 🔥 PENDING LIMIT
                // =============================================

                if (pendingSegments.size() >
                        MAX_PENDING_SEGMENTS) {

                    Log.w("SEGMENT",
                            "pending overflow → clear");

                    pendingSegments.clear();
                }

                pendingSegments.put(
                        segment.segmentId,
                        segment
                );

                // =============================================
                // 🔥 CONVERT TO RAW POINTS
                // =============================================

                for (TrackPointEx tp : segmentPoints) {

                    Location loc =
                            tp.location;

                    rawPoints.add(
                            new GpsPoint(
                                    tp.timestamp,
                                    loc.getLatitude(),
                                    loc.getLongitude(),
                                    loc.hasAccuracy()
                                            ? loc.getAccuracy()
                                            : 0f
                            )
                    );
                }

                Log.d("TX_FLOW",
                        "RAW POINTS=" +
                                rawPoints.size());
            }

            // =================================================
            // 🔥 EMPTY CHECK
            // =================================================

            if (rawPoints.isEmpty()) {

                Log.w("TX_FLOW",
                        "rawPoints empty");

                return;
            }

            long now =
                    System.currentTimeMillis();

            // =================================================
            // 🔥 CONVERSION
            // =================================================

            List<LatLng> latLngPoints =
                    convertGpsPointsToLatLng(
                            rawPoints
                    );

            List<LatLng> workingPoints =
                    new ArrayList<>(
                            latLngPoints
                    );

            // =================================================
            // 🔥 HARD REDUCTION
            // =================================================

            if (workingPoints.size() > 400) {

                List<LatLng> reduced =
                        new ArrayList<>();

                int step =
                        Math.max(
                                1,
                                workingPoints.size() / 300
                        );

                for (int i = 0;
                     i < workingPoints.size();
                     i += step) {

                    reduced.add(
                            workingPoints.get(i)
                    );
                }

                workingPoints = reduced;

                Log.w("ADAPT",
                        "Compression reduction: " +
                                workingPoints.size());
            }

            // =================================================
            // 🔥 SMART REDUCTION
            // =================================================

            if (workingPoints.size() > 300) {

                List<LatLng> reduced =
                        new ArrayList<>();

                int keepHead =
                        Math.min(
                                50,
                                workingPoints.size() / 2
                        );

                int keepTail = keepHead;

                reduced.addAll(
                        workingPoints.subList(
                                0,
                                keepHead
                        )
                );

                int step =
                        Math.max(
                                1,
                                workingPoints.size() / 200
                        );

                for (int i = keepHead;
                     i < workingPoints.size() - keepTail;
                     i += step) {

                    reduced.add(
                            workingPoints.get(i)
                    );
                }

                reduced.addAll(
                        workingPoints.subList(
                                workingPoints.size() - keepTail,
                                workingPoints.size()
                        )
                );

                workingPoints = reduced;

                Log.w("ADAPT",
                        "AFTER REDUCTION → " +
                                workingPoints.size());
            }

            // =================================================
            // 🔥 CURVE PROTECTION
            // =================================================

            if (workingPoints.size() > 3) {

                List<LatLng> protectedPoints =
                        new ArrayList<>();

                int step =
                        Math.max(
                                1,
                                workingPoints.size() / 150
                        );

                for (int i = 0;
                     i < workingPoints.size();
                     i++) {

                    if (i % step == 0) {

                        protectedPoints.add(
                                workingPoints.get(i)
                        );

                        continue;
                    }

                    if (i > 0 &&
                            i < workingPoints.size() - 1) {

                        LatLng prev =
                                workingPoints.get(i - 1);

                        LatLng curr =
                                workingPoints.get(i);

                        LatLng next =
                                workingPoints.get(i + 1);

                        double angle =
                                calculateAngle(
                                        prev,
                                        curr,
                                        next
                                );

                        if (angle < 150) {

                            protectedPoints.add(curr);
                        }
                    }
                }

                workingPoints = protectedPoints;

                Log.d("CURVE",
                        "Protected points=" +
                                protectedPoints.size());
            }
            // =================================================
            // 🔥 SMS TARGET
            // =================================================

            int maxSmsLen = 140;

            String testHeader =
                    "TX|" +
                            currentSessionId +
                            "|999999|" +
                            "999/999|" +
                            "F|";

            int headerLen =
                    testHeader.length();

            int crcLen = 3;

            int safetyMargin = 10;

            int targetPayloadLen =
                    maxSmsLen
                            - headerLen
                            - crcLen
                            - safetyMargin;

            Log.d("ADAPT",
                    "Target payload len=" +
                            targetPayloadLen);

            // =================================================
            // 🔥 ADAPTIVE COMPRESSION
            // =================================================

            CompressionResult bestResult = null;

            int bestSmsLen =
                    Integer.MAX_VALUE;

            float tolerance =
                    trackSimplifyTolerance * 0.7f;

            String payload;

            int smsLen;

            for (int i = 0; i < 3; i++) {

                CompressionResult temp =
                        AdaptiveSmsCompressor.compressToSms(
                                workingPoints,
                                tolerance,
                                trackSimplifyDistance,
                                trackAngleThreshold,
                                200
                        );

                if (temp == null ||
                        temp.encoded == null) {

                    continue;
                }

                payload =
                        "TX|" +
                                currentSessionId +
                                "|0|0/0|D|" +
                                temp.encoded;

                smsLen =
                        (
                                payload +
                                        "|" +
                                        SmsCrc.INSTANCE.crc8(payload)
                        ).length();

                int targetSmsLen = 120;

                if (bestResult == null ||
                        Math.abs(smsLen - targetSmsLen)
                                <
                                Math.abs(bestSmsLen - targetSmsLen)) {

                    bestResult = temp;

                    bestSmsLen = smsLen;
                }

                if (smsLen >= 110 &&
                        smsLen <= 140) {

                    break;
                }

                if (smsLen > 140) {

                    tolerance *= 1.6f;

                } else if (smsLen < 100) {

                    tolerance *= 0.6f;

                } else {

                    tolerance *= 0.8f;
                }
            }

            // =================================================
            // 🔥 VALIDATION
            // =================================================

            if (bestResult == null ||
                    bestResult.encoded == null ||
                    bestResult.encoded.isEmpty()) {

                Log.e("ADAPT",
                        "Compression FAILED");

                return;
            }

            CompressionResult res =
                    bestResult;

            // =================================================
            // 🔥 HARD LIMIT
            // =================================================

            payload =
                    "TX|" +
                            currentSessionId +
                            "|0|0/0|D|" +
                            res.encoded;

            smsLen =
                    (
                            payload +
                                    "|" +
                                    SmsCrc.INSTANCE.crc8(payload)
                    ).length();

            int safety = 0;

            while (smsLen > 140 &&
                    safety < 5) {

                tolerance *= 1.25f;

                res =
                        AdaptiveSmsCompressor.compressToSms(
                                workingPoints,
                                tolerance,
                                trackSimplifyDistance,
                                trackAngleThreshold,
                                200
                        );

                if (res == null ||
                        res.encoded == null) {

                    break;
                }

                payload =
                        "TX|" +
                                currentSessionId +
                                "|0|0/0|D|" +
                                res.encoded;

                smsLen =
                        (
                                payload +
                                        "|" +
                                        SmsCrc.INSTANCE.crc8(payload)
                        ).length();

                safety++;
            }

            if (smsLen > 140) {

                Log.e("SMS_FATAL",
                        "UNRECOVERABLE OVERFLOW");

                return;
            }

            // =================================================
            // 🔥 DEBUG STORE SAFE
            // =================================================

            if (debugTrackEnabled &&
                    res != null) {

                final int MAX_DEBUG_POINTS = 2000;

                final int MAX_HISTORY = 200;

                if (DebugTrackStore.raw.size()
                        > MAX_DEBUG_POINTS) {

                    DebugTrackStore.raw.clear();

                    DebugTrackStore.filtered.clear();

                    DebugTrackStore.simplified.clear();

                    DebugTrackStore.rawHistory.clear();

                    DebugTrackStore.filteredHistory.clear();

                    DebugTrackStore.simplifiedHistory.clear();

                    DebugTrackStore.timeHistory.clear();
                }

                List<LatLng> filteredPoints =
                        BasicFilter.apply(
                                workingPoints,
                                (float) trackSimplifyDistance,
                                (float) trackAngleThreshold
                        );

                List<LatLng> simplifiedPoints =
                        TrackSimplifier.simplify(
                                filteredPoints,
                                res.usedEpsilon
                        );

                DebugTrackStore.raw.addAll(
                        workingPoints.subList(
                                0,
                                Math.min(
                                        20,
                                        workingPoints.size()
                                )
                        )
                );

                DebugTrackStore.filtered.addAll(
                        filteredPoints
                );

                DebugTrackStore.simplified.addAll(
                        simplifiedPoints
                );

                while (DebugTrackStore.raw.size()
                        > MAX_DEBUG_POINTS) {

                    DebugTrackStore.raw.remove(0);
                }

                while (DebugTrackStore.filtered.size()
                        > MAX_DEBUG_POINTS) {

                    DebugTrackStore.filtered.remove(0);
                }

                while (DebugTrackStore.simplified.size()
                        > MAX_DEBUG_POINTS) {

                    DebugTrackStore.simplified.remove(0);
                }

                DebugTrackStore.rawCount =
                        DebugTrackStore.raw.size();

                DebugTrackStore.filteredCount =
                        DebugTrackStore.filtered.size();

                DebugTrackStore.simplifiedCount =
                        DebugTrackStore.simplified.size();

                DebugTrackStore.lastSms =
                        res.encoded;

                DebugTrackStore.smsLength =
                        res.encoded.length();

                DebugTrackStore.smsHistory.add(
                        smsLen
                );

                DebugTrackStore.rawHistory.add(
                        workingPoints.size()
                );

                DebugTrackStore.filteredHistory.add(
                        filteredPoints.size()
                );

                DebugTrackStore.simplifiedHistory.add(
                        simplifiedPoints.size()
                );

                DebugTrackStore.timeHistory.add(now);

                while (DebugTrackStore.rawHistory.size()
                        > MAX_HISTORY) {

                    DebugTrackStore.rawHistory.remove(0);

                    DebugTrackStore.filteredHistory.remove(0);

                    DebugTrackStore.simplifiedHistory.remove(0);

                    DebugTrackStore.timeHistory.remove(0);
                }
            }

            // =================================================
            // 🔥 FINAL FLUSH STATE
            // =================================================

            boolean isRealFinalFlush =
                    isFinalFlush &&
                            finalFlushStarted.get();

            Log.e("FLUSH_REAL",
                    "isFinalFlush=" +
                            isFinalFlush +
                            " finalStarted=" +
                            finalFlushStarted.get() +
                            " => REAL=" +
                            isRealFinalFlush);

            // =================================================
            // 🔥 BASE64 PAYLOAD
            // =================================================

            List<String> parts =
                    new ArrayList<>();

            String fullEncoded =
                    Base64.encodeToString(
                            res.encoded.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            Base64.URL_SAFE |
                                    Base64.NO_WRAP
                    );

            if (segment == null) {

                Log.e("TX_FATAL",
                        "segment NULL");

                return;
            }

            segment.encodedPayload =
                    fullEncoded;

            int maxPayloadLen =
                    140
                            - testHeader.length()
                            - 4
                            - 2;

            List<String> payloadChunks =
                    splitString(
                            fullEncoded,
                            maxPayloadLen
                    );

            Log.d("TX_SPLIT",
                    "chunks=" +
                            payloadChunks.size() +
                            " totalLen=" +
                            fullEncoded.length());

            // =================================================
            // 🔥 SMS BUILD
            // =================================================

            for (int i = 0;
                 i < payloadChunks.size();
                 i++) {

                String payloadChunk =
                        payloadChunks.get(i);

                boolean isFinal =
                        isRealFinalFlush &&
                                i == payloadChunks.size() - 1;

                String type =
                        isFinal ? "F" : "D";

                String header =
                        "TX|" +
                                currentSessionId +
                                "|" +
                                segment.segmentId +
                                "|" +
                                i +
                                "/" +
                                payloadChunks.size() +
                                "|" +
                                type +
                                "|";

                String full =
                        header +
                                payloadChunk;

                String crc =
                        SmsCrc.INSTANCE.crc8(full);

                String sms =
                        full +
                                "|" +
                                crc;

                if (sms.length() > 140) {

                    Log.e("SMS_FATAL",
                            "OVERFLOW seq=" +
                                    i +
                                    " len=" +
                                    sms.length());

                    continue;
                }

                parts.add(sms);

                Log.d("TX_SMS",
                        "SEQ=" +
                                i +
                                " TYPE=" +
                                type +
                                " LEN=" +
                                sms.length());
            }

            // =================================================
            // 🔥 VALID SMS CHECK
            // =================================================

            if (parts.isEmpty()) {

                Log.e("SMS_FATAL",
                        "NO VALID SMS");

                return;
            }

            // =================================================
// 🚀 INVIO SEQUENZIALE SICURO
// =================================================

            String sessionSnapshot =
                    currentSessionId;

            Log.e("SEQ_START",
                    "START SEND parts=" + parts.size() +
                            " final=" + isRealFinalFlush +
                            " session=" + sessionSnapshot);

            txHandler.post(() ->
                    sendNextPart(
                            parts,
                            0,
                            sessionSnapshot,
                            isRealFinalFlush
                    )
            );

            firstBlockSent = true;

            lastTrackSmsTime = now;

            firstBlockSent = true;

            lastTrackSmsTime = now;

            // =================================================
// 🔥 RESET FLUSH SOLO REALE
// =================================================

// ❌ NON RESETTARE QUI
// il flush finale termina solo
// quando tutta la queue TX è realmente vuota

/*
if (isRealFinalFlush) {

    setFinalFlush(
            false,
            "processTrackBuffer RESET"
    );
}
*/

            // =================================================
            // ♻️ BUFFER ROLLING SICURO
            // =================================================

            if (!isRealFinalFlush) {

                synchronized (bufferLock) {

                    List<GpsPoint> current =
                            gpsTrackBuffer.getPointsCopy();

                    if (!current.isEmpty()) {

                        int keep =
                                Math.min(
                                        keepPoints,
                                        current.size()
                                );

                        List<GpsPoint> tail =
                                new ArrayList<>(
                                        current.subList(
                                                current.size() - keep,
                                                current.size()
                                        )
                                );

                        gpsTrackBuffer.clear();

                        for (GpsPoint p : tail) {

                            gpsTrackBuffer.addPoint(p);
                        }

                        Log.d("TX_BUFFER",
                                "KEEP LAST " +
                                        keep +
                                        " POINTS");
                    }
                }
            }

            // =================================================
            // 🔥 FINAL COMPLETE
            // =================================================

            if (isRealFinalFlush) {

                Log.e("FINAL_FLUSH",
                        "FINAL BATCH ENQUEUED");

                // NON completato qui
            }

        } catch (Exception e) {

            Log.e("TRACK_FATAL",
                    "processTrackBuffer exception",
                    e);

        } finally {

            synchronized (processingLock) {

                isProcessing = false;
            }
        }
    }

    private void setFinalFlush(boolean value, String from) {
        // 🚨 LOG SOLO QUANDO VIENE ATTIVATO
        if (value) {
            Log.e("FLUSH_ORIGIN",
                    "🔥 setFinalFlush(TRUE) FROM=" + from +
                            " | isRunning=" + isRunning +
                            " | multiGps=" + multiGpsMode +
                            " | finalStarted=" + finalFlushStarted.get() +
                            " | thread=" + Thread.currentThread().getName(),
                    new Exception());
        }
        if (value && !finalFlushStarted.get()) {
            Log.e("STOP_BLOCK",
                    "❌ FLUSH NON AUTORIZZATO FROM=" + from,
                    new Exception());
            return;
        }
        isFinalFlush = value;
        Log.e("STOP_TRACE",
                "setFinalFlush(" + value + ") FROM=" + from,
                new Exception());
    }
    private List<LatLng> convertGpsPointsToLatLng(List<GpsPoint> list) {
        List<LatLng> out = new ArrayList<>();
        for (GpsPoint p : list) {
            out.add(new LatLng(p.getLat(), p.getLon()));
        }
        return out;
    }
    private void stopTrackingInternal() {

        Log.e(
                "STOP_TRACE",
                "stopTrackingInternal ENTER"
        );

        // 🔥 anti doppia esecuzione
        if (serviceStopping.get()) {

            Log.e("STOP_DEBUG",
                    "already stopping");

            return;
        }

        serviceStopping.set(true);

        isRunning = false;

        // =================================================
        // 🔥 reset restore automatico
        // =================================================

        SharedPreferences prefs =
                getSharedPreferences(
                        "TX_STATE",
                        MODE_PRIVATE
                );

        prefs.edit()
                .putBoolean("wasRunning", false)
                .apply();

        // =================================================
        // 🔥 stop handlers
        // =================================================

        try {

            trackHandler.removeCallbacksAndMessages(null);

        } catch (Exception ignored) {}

        try {

            handler.removeCallbacksAndMessages(null);

        } catch (Exception ignored) {}

        try {

            rxMonitorHandler.removeCallbacksAndMessages(null);

        } catch (Exception ignored) {}

        try {

            smsHandler.removeCallbacksAndMessages(null);

        } catch (Exception ignored) {}

        try {

            signalHandler.removeCallbacksAndMessages(null);

        } catch (Exception ignored) {}

        try {

            uiHandler.removeCallbacksAndMessages(null);

        } catch (Exception ignored) {}

        trackRunnableScheduled = false;

        // =================================================
        // 🔥 clear tx queues
        // =================================================

        clearTxQueues();

        // =================================================
        // 🔥 clear buffers
        // =================================================

        synchronized (bufferLock) {

            gpsTrackBuffer.clear();
        }

        synchronized (processingLock) {

            isProcessing = false;
        }

        // =================================================
        // 🔥 gps stop
        // =================================================

        try {

            if (continuousLocationCallback != null) {

                fusedClient.removeLocationUpdates(
                        continuousLocationCallback
                );

                continuousLocationCallback = null;
            }

        } catch (Exception e) {

            Log.e("GPS",
                    "removeLocationUpdates error",
                    e);
        }

        // =================================================
        // 🔥 phone listener stop
        // =================================================

        try {

            if (telephonyManager != null &&
                    signalListener != null) {

                telephonyManager.listen(
                        signalListener,
                        PhoneStateListener.LISTEN_NONE
                );
            }

        } catch (Exception e) {

            Log.e("SIGNAL",
                    "listener stop error",
                    e);
        }

        // =================================================
        // 🔥 update UI
        // =================================================

        sendUpdate(
                TxStatus.IDLE,
                0,
                smsSent
        );

        // =================================================
        // 🔥 foreground stop
        // =================================================

        try {

            stopForeground(true);

        } catch (Exception ignored) {}

        // =================================================
        // 🔥 final cleanup
        // =================================================

        //completeFinalFlush();

        trackLoopActive.set(false);

        queueProcessing.set(false);

        // =================================================
        // 🔥 stop service
        // =================================================

        try {

            stopSelf();

        } catch (Exception ignored) {}

        Log.e("STOP_DEBUG",
                "SERVICE STOPPED");
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

        boolean validSignal =
                (dbm <= -50 && dbm >= -130);

        if (!validSignal) {

            state = NetworkState.NO_SIGNAL;

        } else if (dbm < -100) {

            state = NetworkState.WEAK_SIGNAL;

        } else {

            state = NetworkState.ONLINE;
        }

        currentNetworkState = state;

        Intent intent = new Intent("NETWORK_UPDATE");

        intent.setPackage(getPackageName());

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
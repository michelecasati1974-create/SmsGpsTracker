package com.example.smsgpstracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.graphics.Color;

import java.util.List;

public class DebugGraphView extends View {

    private Paint paint = new Paint();
    private Paint textPaint = new Paint();
    private Paint gridPaint = new Paint();

    public DebugGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DebugGraphView(Context context) {
        super(context);
        init();
    }

    private void init() {

        paint.setStrokeWidth(3f);
        paint.setAntiAlias(true);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30f);
        textPaint.setAntiAlias(true);

        gridPaint.setColor(Color.GRAY);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setAlpha(100);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();

        List<Integer> raw = DebugTrackStore.rawHistory;
        List<Integer> sms = DebugTrackStore.smsHistory;
        List<Integer> filtered = DebugTrackStore.filteredHistory;
        List<Integer> simplified = DebugTrackStore.simplifiedHistory;

        int size = Math.max(
                Math.max(raw.size(), filtered.size()),
                Math.max(simplified.size(), sms.size())
        );

        if (size < 2) return;


        // =========================
        // 🔥 AUTOSCALE Y
        // =========================
        int maxY = 1;

        for (int v : raw) maxY = Math.max(maxY, v);
        for (int v : filtered) maxY = Math.max(maxY, v);
        for (int v : simplified) maxY = Math.max(maxY, v);
        for (int v : sms) maxY = Math.max(maxY, v);

        int visualMax = Math.max(maxY, 140); // 🔥 include limite SMS
        float scaleY = (float) h / visualMax;
        float dx = (float) w / size;

        // =========================
        // 🔲 GRID
        // =========================
        int gridLines = 5;

        for (int i = 0; i <= gridLines; i++) {

            float y = h - (i * h / gridLines);

            canvas.drawLine(0, y, w, y, gridPaint);

            int value = (maxY * i) / gridLines;
            canvas.drawText(String.valueOf(value), 5, y - 5, textPaint);
        }

        // =========================
        // 🔵 LINEE DATI
        // =========================
        drawLine(canvas, raw, dx, h, scaleY, Color.RED);
        drawLine(canvas, filtered, dx, h, scaleY, Color.YELLOW);
        drawLine(canvas, simplified, dx, h, scaleY, Color.GREEN);
        drawLine(canvas, sms, dx, h, scaleY, Color.CYAN);

        paint.setColor(Color.RED);
        paint.setStrokeWidth(2f);

        float yLimit = h - (140 * scaleY);
        canvas.drawLine(0, yLimit, w, yLimit, paint);

        canvas.drawText("140", w - 80, yLimit - 10, textPaint);

        // =========================
        // 🏷️ ETICHETTE ASSI
        // =========================
        canvas.drawText("Tempo (SMS)", w / 2f - 100, h - 10, textPaint);
        canvas.drawText("Punti", 10, 30, textPaint);

        // =========================
        // 🧾 LEGENDA
        // =========================
        int legendY = 40;

        drawLegend(canvas, "RAW", Color.RED, 10, 40);
        drawLegend(canvas, "FILTERED", Color.YELLOW, 150, 40);
        drawLegend(canvas, "SIMPLIFIED", Color.GREEN, 350, 40);
        drawLegend(canvas, "SMS LEN", Color.CYAN, 600, 40);
    }

    private void drawLine(Canvas canvas,
                          List<Integer> data,
                          float dx,
                          int height,
                          float scaleY,
                          int color) {

        if (data == null || data.size() < 2) return;

        paint.setColor(color);

        int size = data.size();

        for (int i = 1; i < size; i++) {

            float x1 = (i - 1) * dx;
            float x2 = i * dx;

            float y1 = height - (data.get(i - 1) * scaleY);
            float y2 = height - (data.get(i) * scaleY);

            canvas.drawLine(x1, y1, x2, y2, paint);
        }
    }

    private void drawLegend(Canvas canvas, String text, int color, int x, int y) {

        paint.setColor(color);
        canvas.drawLine(x, y, x + 40, y, paint);

        canvas.drawText(text, x + 50, y + 10, textPaint);
    }
}

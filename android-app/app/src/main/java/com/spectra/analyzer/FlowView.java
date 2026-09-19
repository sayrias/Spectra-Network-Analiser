package com.spectra.analyzer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public final class FlowView extends View {
    private static final int POINTS = 60;
    private final float[] management = new float[POINTS];
    private final float[] data = new float[POINTS];
    private final float[] control = new float[POINTS];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public FlowView(Context context) { this(context, null); }
    public FlowView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void push(float mgmt, float dataPackets, float ctrl) {
        shift(management, mgmt);
        shift(data, dataPackets);
        shift(control, ctrl);
        postInvalidateOnAnimation();
    }

    private void shift(float[] values, float value) {
        System.arraycopy(values, 1, values, 0, POINTS - 1);
        values[POINTS - 1] = value;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth(), height = getHeight();
        canvas.drawColor(Color.rgb(17, 16, 15));
        paint.setStrokeWidth(dp(.7f));
        paint.setColor(Color.argb(32, 160, 195, 225));
        for (int i = 1; i < 4; i++) canvas.drawLine(0, height * i / 4f, width, height * i / 4f, paint);
        float maximum = 20;
        for (int i = 0; i < POINTS; i++) maximum = Math.max(maximum, management[i] + data[i] + control[i]);
        drawSeries(canvas, management, maximum, Color.rgb(255, 131, 49));
        drawSeries(canvas, data, maximum, Color.rgb(215, 180, 134));
        drawSeries(canvas, control, maximum, Color.rgb(255, 205, 121));
    }

    private void drawSeries(Canvas canvas, float[] values, float maximum, int color) {
        path.reset();
        for (int i = 0; i < POINTS; i++) {
            float x = i * getWidth() / (float) (POINTS - 1);
            float y = getHeight() - dp(4) - values[i] / maximum * (getHeight() - dp(8));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.4f));
        paint.setColor(color);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}

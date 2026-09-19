package com.spectra.analyzer;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

final class AlertHaptics {
    private AlertHaptics() {}
    static boolean play(Context context) {
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = context.getSystemService(VibratorManager.class);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else vibrator = context.getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) return false;
        VibrationEffect effect = VibrationEffect.createWaveform(new long[]{0, 180, 120, 260}, -1);
        // Alarm usage also permits background alert vibration. Respect Android/DND policy.
        try {
            if (Build.VERSION.SDK_INT >= 33)
                vibrator.vibrate(effect, new VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build());
            else vibrator.vibrate(effect, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            return true;
        } catch (SecurityException exception) { return false; }
    }
}

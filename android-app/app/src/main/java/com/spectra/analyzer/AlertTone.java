package com.spectra.analyzer;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AlertTone {
    private static final int SAMPLE_RATE = 22050;
    private static final ExecutorService AUDIO_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static MediaPlayer player;
    private static volatile int generation;

    private AlertTone() {}

    public static void play(Context context, String preset, int volumePercent) {
        stop();
        int request = generation;
        if (preset != null && preset.startsWith("content:")) {
            MAIN.post(() -> {
                if (request != generation) return;
                try {
                    MediaPlayer next = new MediaPlayer();
                    player = next;
                    next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
                    next.setDataSource(context, Uri.parse(preset));
                    float gain = Math.max(0, Math.min(100, volumePercent)) / 100f;
                    next.setVolume(gain, gain);
                    next.setOnPreparedListener(p -> { if (request == generation) p.start(); });
                    next.setOnCompletionListener(p -> stop());
                    next.setOnErrorListener((p, what, extra) -> { stop(); play(context, "soft", volumePercent); return true; });
                    next.prepareAsync();
                    MAIN.postDelayed(() -> { if (request == generation) stop(); }, 30000);
                } catch (Exception exception) {
                    stop();
                    AUDIO_EXECUTOR.execute(() -> synthesizeAndPlay("soft", volumePercent, generation));
                }
            });
        } else AUDIO_EXECUTOR.execute(() -> { if(request==generation) synthesizeAndPlay(preset==null?"soft":preset,volumePercent,request); });
    }

    public static void stop() {
        generation++;
        MAIN.post(() -> { if(player!=null){try{player.release();}catch(Exception ignored){}player=null;} });
    }

    private static void synthesizeAndPlay(String preset, int volumePercent, int request) {
        float duration = preset.equals("soft") ? 1.3f : preset.equals("sonar") ? 1.7f : 1.45f;
        int samples = Math.round(duration * SAMPLE_RATE);
        short[] pcm = new short[samples];
        double phase = 0;
        float gain = Math.max(0, Math.min(100, volumePercent)) / 100f * .72f;

        for (int i = 0; i < samples; i++) {
            float t = i / (float) SAMPLE_RATE;
            float frequency;
            float envelope;
            boolean square = false;

            switch (preset) {
                case "pulse": {
                    float local = t % .22f;
                    envelope = local < .13f ? fade(local, .13f) : 0;
                    frequency = 970;
                    square = true;
                    break;
                }
                case "sonar": {
                    float local = t % .55f;
                    envelope = local < .36f ? (1f - local / .36f) : 0;
                    frequency = 610 + 320 * (local / .36f);
                    break;
                }
                case "soft": {
                    float local = t % .65f;
                    envelope = local < .5f ? (float) Math.sin(Math.PI * local / .5f) : 0;
                    frequency = t < .65f ? 520 : 680;
                    break;
                }
                default: {
                    float block = t % .72f;
                    float local = block % .20f;
                    envelope = block < .6f && local < .14f ? fade(local, .14f) : 0;
                    frequency = block < .6f ? 860 : 650;
                    square = true;
                }
            }

            phase += 2.0 * Math.PI * frequency / SAMPLE_RATE;
            double wave = square ? (Math.sin(phase) >= 0 ? 1 : -1) : Math.sin(phase);
            pcm[i] = (short) (wave * envelope * gain * Short.MAX_VALUE);
        }

        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(pcm.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        track.write(pcm, 0, pcm.length);
        track.play();
        long end = android.os.SystemClock.elapsedRealtime() + (long)(duration * 1000 + 80);
        while(request==generation && android.os.SystemClock.elapsedRealtime()<end) {
            try { Thread.sleep(30); } catch (InterruptedException ignored) { break; }
        }
        track.stop();
        track.release();
    }

    private static float fade(float local, float length) {
        float edge = .018f;
        if (local < edge) return local / edge;
        if (local > length - edge) return Math.max(0, (length - local) / edge);
        return 1;
    }
}

package uy.com.mapatrayectos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Feedback de confirmación para cambios reales de estado.
 *
 * El slider ya da una respuesta táctil leve durante el gesto. Este receptor escucha
 * los estados confirmados por TrackingService y añade una confirmación más marcada
 * únicamente cuando la acción fue aceptada: iniciar/cerrar jornada o iniciar/finalizar viaje.
 */
public final class FeedbackReceiver extends BroadcastReceiver {
    private static final String PREFS = "mapa_feedback_state";
    private static final String KEY_INITIALIZED = "initialized";
    private static final String KEY_SHIFT = "shift";
    private static final String KEY_TRIP = "trip";

    private static final int EVENT_SHIFT_START = 1;
    private static final int EVENT_TRIP_START = 2;
    private static final int EVENT_TRIP_STOP = 3;
    private static final int EVENT_SHIFT_STOP = 4;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !TrackingService.ACTION_STATE.equals(intent.getAction())) return;

        boolean shift = intent.getBooleanExtra("shift_active", false);
        boolean trip = intent.getBooleanExtra("trip_active", false);
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        if (!sp.getBoolean(KEY_INITIALIZED, false)) {
            sp.edit().putBoolean(KEY_INITIALIZED, true).putBoolean(KEY_SHIFT, shift).putBoolean(KEY_TRIP, trip).apply();
            return;
        }

        boolean oldShift = sp.getBoolean(KEY_SHIFT, false);
        boolean oldTrip = sp.getBoolean(KEY_TRIP, false);
        sp.edit().putBoolean(KEY_SHIFT, shift).putBoolean(KEY_TRIP, trip).apply();

        int event = 0;
        if (!oldShift && shift && !trip) event = EVENT_SHIFT_START;
        else if (oldShift && !oldTrip && shift && trip) event = EVENT_TRIP_START;
        else if (oldShift && oldTrip && shift && !trip) event = EVENT_TRIP_STOP;
        else if (oldShift && !oldTrip && !shift && !trip) event = EVENT_SHIFT_STOP;

        if (event != 0) playConfirmation(context, event);
    }

    private void playConfirmation(Context context, int event) {
        vibrateStrong(context, event);

        // 85/100 es la ganancia del efecto respecto del volumen multimedia elegido
        // por el usuario. No modificamos el volumen general del teléfono.
        final PendingResult pending = goAsync();
        try {
            final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
            final int toneType = (event == EVENT_TRIP_STOP || event == EVENT_SHIFT_STOP)
                    ? ToneGenerator.TONE_PROP_BEEP2
                    : ToneGenerator.TONE_PROP_ACK;
            final int duration = (event == EVENT_SHIFT_STOP) ? 240 : (event == EVENT_TRIP_STOP ? 210 : 180);
            tone.startTone(toneType, duration);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { tone.release(); } catch (Exception ignored) {}
                pending.finish();
            }, duration + 140L);
        } catch (Exception ignored) {
            pending.finish();
        }
    }

    private void vibrateStrong(Context context, int event) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;

            long[] timing;
            int[] amplitude;
            if (event == EVENT_TRIP_STOP || event == EVENT_SHIFT_STOP) {
                // Doble pulso firme para acciones de cierre.
                timing = new long[]{0, 90, 45, 125};
                amplitude = new int[]{0, 255, 0, 235};
            } else {
                // Pulso único firme para acciones de inicio.
                timing = new long[]{0, 105};
                amplitude = new int[]{0, 255};
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timing, amplitude, -1));
            } else {
                vibrator.vibrate(timing, -1);
            }
        } catch (Exception ignored) {
        }
    }
}

package uy.com.mapatrayectos.conductorv0;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;

/**
 * DEBUG SOURCE SET ONLY. Simulated driver bookings, never production.
 * Aceptar/Rechazar only modifies an in-memory sample.
 */
public final class ConductorV0PreviewActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        ConductorV0View panel=new ConductorV0View(this);
        ConductorV0MockBinding.attach(panel);
        setContentView(panel);
    }
}

package uy.com.mapatrayectos.conductorv0;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

/**
 * DEBUG-SOURCE-SET ONLY. Emulator visual contract test, never part of release.
 * Cannot reach production databases, location services or navigation.
 */
public final class ConductorV0PreviewActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        ConductorV0View panel=new ConductorV0View(this);
        panel.setPreviewMode(true);
        panel.setShiftActive(true);
        panel.setCounters(2,1,0);
        panel.setPendingRequest(new ConductorV0View.Request(
            "solo-visual-no-real",
            "18 de Julio, Montevideo",
            "Aeropuerto de Carrasco",
            "21 km","30 min","$840"
        ));
        // No Listener. Buttons are visual ONLY and cannot submit a request.
        setContentView(panel);
    }
}

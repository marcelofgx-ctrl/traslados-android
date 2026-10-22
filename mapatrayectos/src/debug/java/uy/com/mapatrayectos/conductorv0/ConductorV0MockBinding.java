package uy.com.mapatrayectos.conductorv0;

import android.widget.Toast;

/**
 * Debug-only connector of approved Conductor V0 UI to in-memory fake data.
 * No networking, authenticated credentials or production reservation state.
 */
public final class ConductorV0MockBinding {
    private ConductorV0MockBinding() {}

    public static void attach(ConductorV0View view) {
        final ImmediateTripSimulation simulated = ImmediateTripSimulation.sample();
        view.setPreviewMode(true);
        view.setShiftActive(true);
        view.setListener(new ConductorV0View.Listener() {
            @Override public void onAccept(String id) {
                act(id, ImmediateTripRequest.ACCEPTED);
            }
            @Override public void onReject(String id) {
                act(id, ImmediateTripRequest.REJECTED);
            }
            private void act(String id, String status) {
                ImmediateTripSimulation.Result result = simulated.decide(id, status);
                // Display no success unless the local fake acknowledges the action.
                Toast.makeText(view.getContext(),
                        result.ok ? "Ejemplo visual: acción simulada" :
                                "Ejemplo visual: no se modificó la solicitud",
                        Toast.LENGTH_SHORT).show();
                render(view, simulated);
            }
        });
        render(view, simulated);
    }

    private static void render(ConductorV0View view, ImmediateTripSimulation data) {
        view.setCounters(data.countNew(), data.countAccepted(), data.countInJourney());
        ImmediateTripRequest next = data.firstPending();
        if (next == null) {
            view.setPendingRequest(null);
        } else {
            view.setPendingRequest(new ConductorV0View.Request(next.id,
                    next.origin, next.destination, next.distanceText(),
                    next.durationText(), next.amountText()));
        }
    }
}

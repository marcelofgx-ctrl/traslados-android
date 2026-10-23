"""Read-only-from-user perspective: exercises ONLY disposable qa_gps fake PostgreSQL.
Verifies reservation-specific privacy, consent, GPS age and uniqueness."""
import json
from test_postgres_capacity import execute, scalar, ident

A=ident(101); B=ident(102); DEVICE=ident(901); OTHER=ident(902)
TOKEN='FAKE_LOCAL_PASSENGER_SESSION_TOKEN_'+'A'*60
SECRET='LOCAL_FIXTURE_ONLY_DO_NOT_USE_IN_PRODUCTION'

def rpc(statement):
    return json.loads(scalar("SELECT "+statement+"::text;"))

def passenger(booking=A, token=TOKEN):
    return rpc(f"qa_gps.customer_position_fake('{booking}','{token}')")

def publish(accuracy=8,device=DEVICE,secret=SECRET,age="now()"):
    return rpc(f"qa_gps.publish_fake('{device}','{secret}',-34.8665,-56.0175,{accuracy},{age})")

def change(sql):
    execute(sql)

def verify_reason(obj,why):
    assert obj["available"] is False and obj["reason"]==why,(why,obj)
    assert "driverLat" not in obj and "driverLng" not in obj,"No leaked coordinates permitted"

def run():
    assert scalar("SELECT current_database()")=="qt_capacity_test"
    change("TRUNCATE qa_gps.position; TRUNCATE qa_gps.reservations; TRUNCATE qa_gps.presence;")
    change(f"""INSERT INTO qa_gps.reservations(id,public_token,status,pickup_phase)
        VALUES('{A}','{TOKEN}','ACEPTADA','EN_CAMINO'),
              ('{B}','{'B'*64}','PENDIENTE',NULL);
        INSERT INTO qa_gps.presence(singleton_id,enabled,shift_active,shift_paused,device_id,device_secret)
        VALUES(1,true,true,false,'{DEVICE}','{SECRET}');""")

    verify_reason(passenger(A,"wrong-passenger-token"*4),"unauthorized")
    verify_reason(passenger(B,TOKEN),"unauthorized")
    verify_reason(passenger(A,"short"),"unauthorized")
    verify_reason(passenger(),"position_unavailable")
    assert publish()["ok"] is True,"Valid authorized driver may publish"
    good=passenger()
    assert good["available"] is True and good["phase"]=="EN_CAMINO",good
    assert -35.3<=good["driverLat"]<=-30 and good["accuracyM"]==8
    assert 0<=good["gpsAgeSec"]<=10
    print("PASS GPS: private authorized pickup coordinates, wrong token has no location")

    change("UPDATE qa_gps.position SET gps_recorded_at=now()-interval '76 seconds';")
    verify_reason(passenger(),"position_unavailable")
    change("UPDATE qa_gps.position SET gps_recorded_at=now()+interval '11 seconds';")
    verify_reason(passenger(),"position_unavailable")
    change("UPDATE qa_gps.position SET gps_recorded_at=now(),accuracy_m=46;")
    verify_reason(passenger(),"position_unavailable")
    change("UPDATE qa_gps.position SET accuracy_m=8,received_at=now()-interval '76 seconds';")
    verify_reason(passenger(),"position_unavailable")
    change("UPDATE qa_gps.position SET received_at=now();")
    assert passenger()["available"] is True
    print("PASS GPS: >75s expired, future timestamp, >45m accuracy, stale reception")

    change("UPDATE qa_gps.presence SET enabled=false;")
    verify_reason(passenger(),"position_unavailable")
    assert publish()["ok"] is False
    change("UPDATE qa_gps.presence SET enabled=true,shift_paused=true;")
    verify_reason(passenger(),"position_unavailable")
    change("UPDATE qa_gps.presence SET shift_paused=false,shift_active=false;")
    verify_reason(passenger(),"position_unavailable")
    change("UPDATE qa_gps.presence SET shift_active=true;")
    assert publish(device=OTHER)["ok"] is False
    assert publish(secret="wrong-device-credential")["ok"] is False
    change(f"UPDATE qa_gps.position SET device_id='{OTHER}';")
    verify_reason(passenger(),"position_unavailable")
    change(f"UPDATE qa_gps.position SET device_id='{DEVICE}';")
    assert passenger()["available"] is True
    print("PASS GPS: explicit consent, shift, pause and authorized device matched")

    change("UPDATE qa_gps.reservations SET status='EN_VIAJE' WHERE id='"+A+"';")
    assert publish()["ok"] is True,"Private live GPS continues DURING actual passenger ride"
    assert passenger()["phase"]=="EN_VIAJE"
    change(f"UPDATE qa_gps.reservations SET status='ACEPTADA',pickup_phase='EN_CAMINO' WHERE id='{B}';")
    duplicate=publish()
    assert duplicate["ok"] is False and duplicate["reason"]=="no_unique_active_reservation",duplicate
    verify_reason(passenger(),"ambiguous_driver_service")
    change(f"UPDATE qa_gps.reservations SET status='CANCELADA' WHERE id='{B}';")
    assert publish()["ok"] is True
    assert passenger()["available"] is True
    print("PASS GPS: actual trip coordinates, 2 active bookings fail closed at PUBLISH and READ")

    change(f"UPDATE qa_gps.reservations SET status='FINALIZADA' WHERE id='{A}';")
    verify_reason(passenger(),"not_active")
    assert publish()["ok"] is False
    change(f"UPDATE qa_gps.reservations SET status='ACEPTADA',pickup_phase=NULL WHERE id='{B}';")
    assert publish()["ok"] is False
    verify_reason(passenger(B,'B'*64),"preparing_pickup")
    print("PASS GPS: closed trip stops sharing and not-yet-started pickup remains private")
    print("PASS all fake PostgreSQL GPS position-contract tests")

if __name__=="__main__":run()

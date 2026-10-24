"""REAL two-connection PostgreSQL QA against fictitious qa_capacity.fake_reservations ONLY.
No network to Supabase. Uses psql processes as independent Postgres sessions."""
import os
import subprocess
import threading
import time

SCHEMA = "qa_capacity.fake_reservations"
INDEX = "qa_capacity.qt_one_active_service_uq"
EXPECTED_DB = "qt_capacity_test"

def execute(sql, ok=True, timeout=24):
    assert os.getenv("PGDATABASE")==EXPECTED_DB, "Refusing non-disposable database"
    env=dict(os.environ)
    argv=["psql","-X","-v","ON_ERROR_STOP=1","-A","-t"]
    proc=subprocess.run(argv,input=sql,text=True,capture_output=True,timeout=timeout,env=env)
    if ok and proc.returncode!=0:
        raise AssertionError(f"Postgres error: {proc.stderr[:1000]}")
    return proc

def ident(i):
    return "00000000-0000-4000-8000-"+str(i).zfill(12)

def seed(entries):
    execute("TRUNCATE "+SCHEMA+";")
    if entries:
        values=",".join(
            f"('{ident(i)}','{mode}','{state}',"+("NULL" if phase is None else f"'{phase}'")+")"
            for i,mode,state,phase in entries)
        execute("INSERT INTO "+SCHEMA+" (id,pickup_mode,status,pickup_phase) VALUES "+values+";")

def update(i,what,ok=True):
    return execute(f"UPDATE {SCHEMA} SET {what} WHERE id='{ident(i)}';",ok=ok)

def expect_unique(result,label):
    assert result.returncode!=0, f"{label}: unexpectedly accepted second active service"
    assert "qt_one_active_service_uq" in result.stderr, f"{label}: expected capacity unique error, got {result.stderr[:800]}"

def scalar(sql):
    return execute(sql).stdout.strip()

def ordinary_tests():
    seed([(1,"now","PENDIENTE",None),(2,"15","PENDIENTE",None),
          (3,"schedule","PENDIENTE",None),(4,"schedule","PENDIENTE",None)])
    update(1,"status='ACEPTADA'")
    expect_unique(update(2,"status='ACEPTADA'",ok=False),"two immediate / deferred")
    update(3,"status='ACEPTADA'") # future scheduled does NOT occupy vehicle
    expect_unique(update(3,"pickup_phase='EN_CAMINO'",ok=False),"schedule starts while busy")
    update(1,"status='EN_VIAJE'")
    expect_unique(update(2,"status='ACEPTADA'",ok=False),"active trip long after ETA")
    update(1,"status='FINALIZADA'")
    update(3,"pickup_phase='EN_CAMINO'")  # now scheduled service occupies
    expect_unique(update(2,"status='ACEPTADA'",ok=False),"scheduled pickup blocks immediate")
    update(3,"status='EN_VIAJE'")
    expect_unique(update(2,"status='ACEPTADA'",ok=False),"scheduled trip in progress")
    update(3,"status='FINALIZADA'")
    update(2,"status='ACEPTADA'")
    update(2,"status='ACEPTADA'")   # idempotent duplicate on SAME reservation
    assert scalar(f"SELECT count(*) FROM {SCHEMA} WHERE status='ACEPTADA'")=="1"
    print("PASS: immediate vs scheduled, pickup phase, trip, closure, repeat decision")

def legacy_and_demo_tests():
    seed([(1,"unknown_old", "ACEPTADA",None),(2,"now","PENDIENTE",None)])
    expect_unique(update(2,"status='ACEPTADA'",ok=False),"unknown legacy mode fail-closed")
    seed([(1,"now","ACEPTADA",None),(2,"now","PENDIENTE",None)])
    # Synthetic demo rows do not consume capacity.
    execute(f"UPDATE {SCHEMA} SET is_demo=true WHERE id='{ident(1)}';")
    update(2,"status='ACEPTADA'")
    print("PASS: unrecognized legacy mode fails closed; demo excluded")


def explicit_status_matrix_tests():
    """All 10 production enum labels, using only disposable text fixture rows.

    Non-operational rows (even with an inconsistent pickup phase) cannot block
    an accepted service; only known operational states may consume capacity.
    """
    inactive = ('PENDIENTE', 'PRESUPUESTO_ENVIADO', 'RECHAZADA_CLIENTE',
                'RECHAZADA', 'CANCELADA', 'FINALIZADA')
    for state in inactive:
        for mode in ('now', 'schedule', 'legacy_unknown'):
            seed([(1,mode,state,'EN_PUNTO'),(2,'now','PENDIENTE',None)])
            update(2,"status='ACEPTADA'")
            assert scalar(f"SELECT count(*) FROM {SCHEMA} WHERE id='{ident(2)}' AND status='ACEPTADA'")=='1', (state,mode)
    print("PASS: all 6 non-operational enum states excluded, including rejected customer / proposal")

    for state in ('ACEPTADA', 'ACEPTADA_CLIENTE', 'CONFIRMADA', 'EN_VIAJE'):
        seed([(1,'now',state,None),(2,'now','PENDIENTE',None)])
        expect_unique(update(2,"status='ACEPTADA'",ok=False),
                      f"operational status {state} blocks concurrent immediate acceptance")

    for state in ('ACEPTADA', 'ACEPTADA_CLIENTE', 'CONFIRMADA'):
        seed([(1,'schedule',state,'SIN_INICIAR'),
              (2,'now','PENDIENTE',None)])
        update(2,"status='ACEPTADA'")  # Future program stays on the calendar
        expect_unique(update(1,"pickup_phase='EN_CAMINO'",ok=False),
                      f"scheduled {state} acquires capacity on pickup start")
        expect_unique(update(1,"pickup_phase='EN_PUNTO'",ok=False),
                      f"scheduled {state} acquires capacity when at pickup")
    seed([(1,'schedule','EN_VIAJE',None),(2,'now','PENDIENTE',None)])
    expect_unique(update(2,"status='ACEPTADA'",ok=False),
                  "scheduled EN_VIAJE consumes capacity without pickup_phase")
    print("PASS: exactly 4 operational states, scheduled transition and EN_VIAJE")

def concurrent_test():
    seed([(11,"now","PENDIENTE",None),(12,"20","PENDIENTE",None)])
    barrier=threading.Barrier(2)
    results=[]
    lock=threading.Lock()
    def accept(i):
        barrier.wait(timeout=10)
        sql=f"BEGIN; UPDATE {SCHEMA} SET status='ACEPTADA' WHERE id='{ident(i)}'; SELECT pg_sleep(1); COMMIT;"
        result=execute(sql,ok=False)
        with lock: results.append((i,result.returncode,result.stderr))
    a=threading.Thread(target=accept,args=(11,))
    b=threading.Thread(target=accept,args=(12,))
    a.start();b.start();a.join(timeout=20);b.join(timeout=20)
    assert not a.is_alive() and not b.is_alive()
    assert len(results)==2
    wins=[r for r in results if r[1]==0]
    loss=[r for r in results if r[1]!=0]
    assert len(wins)==1 and len(loss)==1, f"Concurrent commits: {results}"
    assert "qt_one_active_service_uq" in loss[0][2],f"Not capacity conflict: {loss}"
    assert scalar(f"SELECT count(*) FROM {SCHEMA} WHERE status='ACEPTADA'")=="1"
    print("PASS: two actual PostgreSQL sessions -> exactly 1 accepted, 1 unique_violation")

def rollback_test():
    seed([(21,"now","ACEPTADA",None)])
    initial=scalar(f"SELECT count(*) FROM {SCHEMA}")
    rollback_sql=open("qa/quicktrip-capacity/rollback_atomic_guard.sql").read().replace(
        "public.qt_one_active_service_uq","qa_capacity.qt_one_active_service_uq")
    execute(rollback_sql)
    assert scalar("SELECT to_regclass('qa_capacity.qt_one_active_service_uq') IS NULL")=="t"
    assert scalar(f"SELECT count(*) FROM {SCHEMA}")==initial
    # Only in the isolated fake DB, demonstrate that rollback restores prior behavior.
    execute(f"INSERT INTO {SCHEMA}(id,pickup_mode,status) VALUES ('{ident(22)}','now','ACEPTADA');")
    assert scalar(f"SELECT count(*) FROM {SCHEMA} WHERE status='ACEPTADA'")=="2"
    # Migration must fail closed with pre-existing duplicates; never automatically alter them.
    draft=open("qa/quicktrip-capacity/draft_atomic_guard.sql").read().replace(
        "public.reservations","qa_capacity.fake_reservations")
    res=execute(draft,ok=False)
    expect_unique(res,"migration with pre-existing conflicting rows")
    assert scalar(f"SELECT count(*) FROM {SCHEMA} WHERE status='ACEPTADA'")=="2"
    update(22,"status='FINALIZADA'")   # fictitious data only
    execute(draft)
    assert scalar("SELECT to_regclass('qa_capacity.qt_one_active_service_uq') IS NOT NULL")=="t"
    assert scalar(f"SELECT count(*) FROM {SCHEMA}")=="2", "Rollback/reinstall must preserve all synthetic rows"
    print("PASS: no-data-loss rollback, duplicate detection, safe index restoration")

if __name__=="__main__":
    assert scalar("SELECT current_database()")==EXPECTED_DB
    assert scalar("SELECT to_regclass('qa_capacity.qt_one_active_service_uq') IS NOT NULL")=="t"
    ordinary_tests()
    legacy_and_demo_tests()
    explicit_status_matrix_tests()
    concurrent_test()
    rollback_test()
    print("PASS all real local PostgreSQL isolated capacity checks")

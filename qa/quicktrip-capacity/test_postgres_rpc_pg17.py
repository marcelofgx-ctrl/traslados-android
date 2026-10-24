"""PostgreSQL 17 real-engine integration checks, DISPOSABLE qa_capacity ONLY.

No Supabase project, credentials, public schema DDL, Android, or production RPC.
Representative RPC functions intentionally mock auth/ETA while keeping write,
lock-order, status, and index interplay relevant to single-driver capacity.
"""
import os
import subprocess
import threading

assert os.environ.get("PGDATABASE") == "qt_capacity_test"
assert os.environ.get("PGHOST") in ("127.0.0.1", "localhost")
assert os.environ.get("PGPORT") == "5432"

from test_postgres_capacity import execute, seed, update, scalar, ident, expect_unique, SCHEMA

def rpc(name, i, arg, ok=True):
    bool_or_text = ("true" if arg else "false") if isinstance(arg, bool) else f"'{arg}'"
    return execute(f"SELECT qa_capacity.{name}('{ident(i)}',{bool_or_text});",ok=ok)

def require_sqlstate(result, title):
    expect_unique(result,title)
    assert "23505" in result.stderr, f"{title}: SQLSTATE 23505 absent: {result.stderr[:700]}"

def pg17_check():
    version = int(scalar("SELECT current_setting('server_version_num')"))
    assert 170000 <= version < 180000, f"Expected PostgreSQL 17; got {version}"
    assert scalar("SELECT current_database()") == "qt_capacity_test"
    assert scalar("SELECT to_regclass('qa_capacity.qt_one_active_service_uq') IS NOT NULL") == "t"
    print("PASS PG17: disposable database and unique partial index verified", flush=True)

def rpc_paths_test():
    # Conductor fixed acceptance vs legacy driver_set_status path.
    seed([(1,"now","PENDIENTE",None),(2,"now","PENDIENTE",None)])
    assert rpc("driver_decide_fixed_pickup_replica",1,True).returncode == 0
    require_sqlstate(rpc("driver_set_status_v2_replica",2,"ACEPTADA",ok=False),
                     "driver_set_status alternative path")
    assert scalar(f"SELECT count(*) FROM {SCHEMA} WHERE status='ACEPTADA'") == "1"
    assert "already_decided" in rpc("driver_decide_fixed_pickup_replica",1,True).stdout
    assert "EN_VIAJE" in rpc("driver_set_status_v2_replica",1,"EN_VIAJE").stdout
    require_sqlstate(rpc("driver_set_status_v2_replica",2,"ACEPTADA",ok=False),
                     "EN_VIAJE remains occupied")
    assert "FINALIZADA" in rpc("driver_set_status_v2_replica",1,"FINALIZADA").stdout
    assert "ACEPTADA" in rpc("driver_set_status_v2_replica",2,"ACEPTADA").stdout

    # Quote v13 for scheduled future rides: no physical capacity until EN_CAMINO.
    seed([(3,"schedule","PENDIENTE","SIN_INICIAR"),
          (4,"now","PENDIENTE",None)])
    update(3,"fare_mode='quote',quote_status='ENVIADO'")
    assert "accepted" in rpc("customer_quote_decision_v13_replica",3,True).stdout
    assert "accepted" in rpc("driver_decide_fixed_pickup_replica",4,True).stdout
    require_sqlstate(rpc("driver_set_pickup_phase_v1_replica",3,"EN_CAMINO",ok=False),
                     "schedule starts pickup while busy")
    assert "CANCELADA" in rpc("driver_set_status_v2_replica",4,"CANCELADA").stdout
    assert "EN_CAMINO" in rpc("driver_set_pickup_phase_v1_replica",3,"EN_CAMINO").stdout
    assert "EN_PUNTO" in rpc("driver_set_pickup_phase_v1_replica",3,"EN_PUNTO").stdout
    seed([(5,"now","PENDIENTE",None),(6,"now","PENDIENTE",None)])
    update(6,"fare_mode='quote',quote_status='ENVIADO'")
    assert "accepted" in rpc("customer_quote_decision_v10_replica",6,True).stdout
    require_sqlstate(rpc("driver_decide_fixed_pickup_replica",5,True,ok=False),
                     "legacy v10 consumes capacity like normal")
    print("PASS PG17: fixed driver, alternate status, scheduled v13, phase and legacy v10 replicas",flush=True)

def simultaneous_mixed_rpc_test():
    # Advisory-locked driver RPC races against legacy v10 which has no advisory lock.
    seed([(11,"now","PENDIENTE",None),(12,"20","PENDIENTE",None)])
    update(12,"fare_mode='quote',quote_status='ENVIADO'")
    barrier=threading.Barrier(2)
    results=[]
    lock=threading.Lock()
    sqls=[
        "SELECT qa_capacity.driver_decide_fixed_pickup_replica('"+ident(11)+"',true);",
        "SELECT qa_capacity.customer_quote_decision_v10_replica('"+ident(12)+"',true);"
    ]
    def run(sql):
        barrier.wait(timeout=10)
        result=execute("BEGIN; SET LOCAL lock_timeout='10s'; "+sql+
                       " SELECT pg_sleep(0.7); COMMIT;",ok=False,timeout=25)
        with lock:
            results.append(result)
    threads=[threading.Thread(target=run,args=(sql,)) for sql in sqls]
    for t in threads: t.start()
    for t in threads: t.join(timeout=22)
    assert all(not t.is_alive() for t in threads)
    assert len(results)==2
    successes=[x for x in results if x.returncode==0]
    failures=[x for x in results if x.returncode!=0]
    assert len(successes)==1 and len(failures)==1,[(x.returncode,x.stderr[:300]) for x in results]
    require_sqlstate(failures[0],"two different concurrent RPC replicas")
    assert scalar(f"SELECT count(*) FROM {SCHEMA} WHERE status='ACEPTADA'")=="1"
    assert scalar(f"SELECT count(*) FROM {SCHEMA} WHERE status='PENDIENTE'")=="1"
    print("PASS PG17: concurrent mixed RPC replicas -> one COMMIT, one SQLSTATE 23505",flush=True)

def rollback_and_failed_reinstall_test():
    # Exact rollback SQL, rewritten to ONLY the synthetic schema.
    seed([(21,"now","PENDIENTE",None),(22,"now","PENDIENTE",None)])
    rpc("driver_decide_fixed_pickup_replica",21,True)
    pre_count=scalar(f"SELECT count(*) FROM {SCHEMA}")
    rollback=open("qa/quicktrip-capacity/rollback_atomic_guard.sql").read().replace(
        "public.qt_one_active_service_uq","qa_capacity.qt_one_active_service_uq")
    execute(rollback)
    assert scalar("SELECT to_regclass('qa_capacity.qt_one_active_service_uq') IS NULL")=="t"
    assert scalar(f"SELECT count(*) FROM {SCHEMA}")==pre_count
    rpc("driver_decide_fixed_pickup_replica",22,True)
    assert scalar(f"SELECT count(*) FROM {SCHEMA} WHERE status='ACEPTADA'")=="2"
    draft=open("qa/quicktrip-capacity/draft_atomic_guard.sql").read().replace(
        "public.reservations","qa_capacity.fake_reservations")
    require_sqlstate(execute(draft,ok=False),"reinstall with synthetic duplicate rows")
    assert scalar(f"SELECT count(*) FROM {SCHEMA} WHERE status='ACEPTADA'")=="2"
    # Reset synthetic data only, then demonstrate index can be restored.
    update(22,"status='FINALIZADA'")
    execute(draft)
    assert scalar("SELECT to_regclass('qa_capacity.qt_one_active_service_uq') IS NOT NULL")=="t"
    assert scalar(f"SELECT count(*) FROM {SCHEMA}")==pre_count
    print("PASS PG17: rollback retains rows, duplicate preflight fails closed, reinstall succeeds",flush=True)

if __name__=="__main__":
    pg17_check()
    rpc_paths_test()
    simultaneous_mixed_rpc_test()
    rollback_and_failed_reinstall_test()
    print("PASS ALL PG17 disposable representative RPC/SQLSTATE/rollback tests",flush=True)

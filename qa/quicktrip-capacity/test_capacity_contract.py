import pathlib, sqlite3, tempfile, threading, unittest
ROOT=pathlib.Path(__file__).parent
class CapacityContractTests(unittest.TestCase):
    def test_sql_contract_and_rollback(self):
        sql=(ROOT/'draft_atomic_guard.sql').read_text()
        rollback=(ROOT/'rollback_atomic_guard.sql').read_text()
        self.assertIn('pg_advisory_xact_lock',sql)
        self.assertIn('FOR EACH ROW',sql)
        self.assertIn('TG_OP',sql)
        self.assertIn('QT_CONDUCTOR_OCUPADO',sql)
        self.assertIn('DROP TRIGGER',rollback)
        self.assertNotIn('DELETE FROM public.reservations',rollback.upper())
    def test_fictitious_serialized_acceptance(self):
        # SQLite simulation only; NOT a PostgreSQL two-connection lock test.
        with tempfile.TemporaryDirectory() as d:
            db=d+'/fake.db'
            conn=sqlite3.connect(db)
            conn.execute('CREATE TABLE booking(id INTEGER PRIMARY KEY, immediate INT, status TEXT)')
            conn.commit();conn.close()
            barrier=threading.Barrier(2)
            results=[]; lock=threading.Lock()
            def accept(i):
                c=sqlite3.connect(db,timeout=10,isolation_level=None)
                barrier.wait()
                try:
                    c.execute('BEGIN IMMEDIATE')
                    count=c.execute("SELECT count(*) FROM booking WHERE immediate=1 AND status IN ('ACEPTADA','EN_VIAJE')").fetchone()[0]
                    if count: results.append('BUSY')
                    else:
                        c.execute("INSERT INTO booking VALUES(?,1,'ACEPTADA')",(i,))
                        results.append('OK')
                    c.execute('COMMIT')
                finally:c.close()
            t1=threading.Thread(target=accept,args=(1,))
            t2=threading.Thread(target=accept,args=(2,))
            t1.start();t2.start();t1.join();t2.join()
            self.assertCountEqual(results,['OK','BUSY'])
            c=sqlite3.connect(db)
            self.assertEqual(c.execute("SELECT count(*) FROM booking WHERE status='ACEPTADA'").fetchone()[0],1)
            c.execute("UPDATE booking SET status='FINALIZADA'")
            self.assertEqual(c.execute("SELECT count(*) FROM booking WHERE status IN ('ACEPTADA','EN_VIAJE')").fetchone()[0],0)
            c.close()
if __name__=='__main__':unittest.main(verbosity=2)

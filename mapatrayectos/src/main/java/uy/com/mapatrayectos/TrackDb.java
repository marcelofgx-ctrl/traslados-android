package uy.com.mapatrayectos;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;

public final class TrackDb extends SQLiteOpenHelper {
    static final String DB_NAME = "mapa_trayectos.db";
    static final int DB_VERSION = 3;

    public TrackDb(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table shifts(shift_id text primary key, started_at_ms integer not null, ended_at_ms integer, distance_m real not null default 0, moving_ms integer not null default 0, stopped_ms integer not null default 0, trip_ms integer not null default 0, start_zone text, end_zone text, synced integer not null default 0)");
        db.execSQL("create table trips(trip_id text primary key, shift_id text not null, started_at_ms integer not null, ended_at_ms integer, distance_m real not null default 0, moving_ms integer not null default 0, stopped_ms integer not null default 0, max_speed_kmh real not null default 0, avg_speed_kmh real not null default 0, start_zone text, end_zone text, start_lat real, start_lon real, end_lat real, end_lon real, start_address text, end_address text, trip_type text not null default 'other', trip_status text not null default 'completed', amount_uyu real, synced integer not null default 0)");
        db.execSQL("create table points(point_id text primary key, shift_id text not null, trip_id text, recorded_at_ms integer not null, lat real not null, lon real not null, accuracy_m real, speed_kmh real, bearing_deg real, zone text, in_trip integer not null default 0, synced integer not null default 0)");
        db.execSQL("create index idx_points_trip on points(trip_id, recorded_at_ms)");
        db.execSQL("create index idx_points_shift on points(shift_id, recorded_at_ms)");
        db.execSQL("create index idx_trips_started on trips(started_at_ms desc)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2){
            try{db.execSQL("alter table trips add column trip_type text not null default 'other'");}catch(Exception ignored){}
            try{db.execSQL("alter table trips add column trip_status text not null default 'completed'");}catch(Exception ignored){}
            try{db.execSQL("alter table trips add column amount_uyu real");}catch(Exception ignored){}
        }
        if(oldVersion<3){
            try{db.execSQL("alter table trips add column start_lat real");}catch(Exception ignored){}
            try{db.execSQL("alter table trips add column start_lon real");}catch(Exception ignored){}
            try{db.execSQL("alter table trips add column end_lat real");}catch(Exception ignored){}
            try{db.execSQL("alter table trips add column end_lon real");}catch(Exception ignored){}
            try{db.execSQL("alter table trips add column start_address text");}catch(Exception ignored){}
            try{db.execSQL("alter table trips add column end_address text");}catch(Exception ignored){}
        }
    }

    public void beginShift(String id, long startedAt, String zone) {
        ContentValues v=new ContentValues();v.put("shift_id",id);v.put("started_at_ms",startedAt);v.put("start_zone",zone);v.put("end_zone",zone);v.put("synced",0);
        getWritableDatabase().insertWithOnConflict("shifts",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void updateShift(String id, Long endedAt, double distanceM, long movingMs, long stoppedMs, long tripMs, String endZone) {
        ContentValues v=new ContentValues();if(endedAt!=null)v.put("ended_at_ms",endedAt);v.put("distance_m",distanceM);v.put("moving_ms",movingMs);v.put("stopped_ms",stoppedMs);v.put("trip_ms",tripMs);v.put("end_zone",endZone);v.put("synced",0);
        getWritableDatabase().update("shifts",v,"shift_id=?",new String[]{id});
    }

    public void beginTrip(String id, String shiftId, long startedAt, String zone) {
        beginTrip(id,shiftId,startedAt,zone,"other");
    }

    public void beginTrip(String id, String shiftId, long startedAt, String zone, String tripType) {
        beginTrip(id,shiftId,startedAt,zone,tripType,null,null,null);
    }

    public void beginTrip(String id,String shiftId,long startedAt,String zone,String tripType,Double startLat,Double startLon,String startAddress) {
        ContentValues v=new ContentValues();v.put("trip_id",id);v.put("shift_id",shiftId);v.put("started_at_ms",startedAt);v.put("start_zone",zone);v.put("end_zone",zone);
        if(startLat!=null)v.put("start_lat",startLat);if(startLon!=null)v.put("start_lon",startLon);if(startAddress!=null&&!startAddress.isEmpty())v.put("start_address",startAddress);
        v.put("trip_type",normalizeType(tripType));v.put("trip_status","active");v.putNull("amount_uyu");v.put("synced",0);
        getWritableDatabase().insertWithOnConflict("trips",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void setTripStartLocation(String id,double lat,double lon,String address){
        ContentValues v=new ContentValues();v.put("start_lat",lat);v.put("start_lon",lon);if(address!=null&&!address.isEmpty())v.put("start_address",address);v.put("synced",0);getWritableDatabase().update("trips",v,"trip_id=?",new String[]{id});
    }

    public void setTripEndLocation(String id,double lat,double lon,String address){
        ContentValues v=new ContentValues();v.put("end_lat",lat);v.put("end_lon",lon);if(address!=null&&!address.isEmpty())v.put("end_address",address);v.put("synced",0);getWritableDatabase().update("trips",v,"trip_id=?",new String[]{id});
    }

    public int backfillTripEndpointsFromPoints(){
        int changed=0;Cursor trips=getReadableDatabase().rawQuery("select trip_id,start_lat,start_lon,end_lat,end_lon from trips",null);
        try{
            while(trips.moveToNext()){
                String id=trips.getString(0);boolean needStart=trips.isNull(1)||trips.isNull(2),needEnd=trips.isNull(3)||trips.isNull(4);if(!needStart&&!needEnd)continue;
                ContentValues v=new ContentValues();
                if(needStart){Cursor p=getReadableDatabase().rawQuery("select lat,lon from points where trip_id=? order by recorded_at_ms asc limit 1",new String[]{id});try{if(p.moveToFirst()){v.put("start_lat",p.getDouble(0));v.put("start_lon",p.getDouble(1));}}finally{p.close();}}
                if(needEnd){Cursor p=getReadableDatabase().rawQuery("select lat,lon from points where trip_id=? order by recorded_at_ms desc limit 1",new String[]{id});try{if(p.moveToFirst()){v.put("end_lat",p.getDouble(0));v.put("end_lon",p.getDouble(1));}}finally{p.close();}}
                if(v.size()>0){v.put("synced",0);getWritableDatabase().update("trips",v,"trip_id=?",new String[]{id});changed++;}
            }
        }finally{trips.close();}
        return changed;
    }

    public void updateTrip(String id, Long endedAt, double distanceM, long movingMs, long stoppedMs, double maxSpeed, double avgSpeed, String endZone) {
        ContentValues v=new ContentValues();if(endedAt!=null)v.put("ended_at_ms",endedAt);v.put("distance_m",distanceM);v.put("moving_ms",movingMs);v.put("stopped_ms",stoppedMs);v.put("max_speed_kmh",maxSpeed);v.put("avg_speed_kmh",avgSpeed);v.put("end_zone",endZone);v.put("synced",0);
        getWritableDatabase().update("trips",v,"trip_id=?",new String[]{id});
    }

    public void finalizeTrip(String id,String status,Double amountUyu){
        ContentValues v=new ContentValues();v.put("trip_status",normalizeStatus(status));if(amountUyu==null)v.putNull("amount_uyu");else v.put("amount_uyu",Math.max(0,amountUyu));v.put("synced",0);getWritableDatabase().update("trips",v,"trip_id=?",new String[]{id});
    }

    public void addPoint(String pointId,String shiftId,String tripId,long ts,double lat,double lon,float accuracy,float speed,float bearing,String zone,boolean inTrip) {
        ContentValues v=new ContentValues();v.put("point_id",pointId);v.put("shift_id",shiftId);if(tripId==null)v.putNull("trip_id");else v.put("trip_id",tripId);v.put("recorded_at_ms",ts);v.put("lat",lat);v.put("lon",lon);v.put("accuracy_m",accuracy);v.put("speed_kmh",speed);v.put("bearing_deg",bearing);v.put("zone",zone);v.put("in_trip",inTrip?1:0);v.put("synced",0);
        getWritableDatabase().insertWithOnConflict("points",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    public JSONObject getShift(String id) { return one("select * from shifts where shift_id=?",new String[]{id}); }
    public JSONObject getTrip(String id) { return one("select * from trips where trip_id=?",new String[]{id}); }
    public JSONArray listTrips() { return many("select * from trips order by started_at_ms desc",null); }
    public JSONArray listShifts() { return many("select * from shifts order by started_at_ms desc",null); }
    public JSONObject counts() { JSONObject o=new JSONObject();try{o.put("shifts",scalarCount("shifts"));o.put("trips",scalarCount("trips"));o.put("points",scalarCount("points"));}catch(Exception ignored){}return o; }
    public void checkpoint() { Cursor c=getWritableDatabase().rawQuery("PRAGMA wal_checkpoint(FULL)",null);try{if(c.moveToFirst()){} }finally{c.close();} }
    public JSONArray getTripPoints(String tripId) { return many("select * from points where trip_id=? order by recorded_at_ms",new String[]{tripId}); }
    public JSONArray getRoutePoints(String shiftId,String tripId) { if(tripId!=null&&!tripId.isEmpty())return getTripPoints(tripId);return many("select * from points where shift_id=? order by recorded_at_ms",new String[]{shiftId}); }
    public JSONArray unsyncedEndedShifts() { return many("select * from shifts where synced=0 and ended_at_ms is not null order by started_at_ms",null); }
    public JSONArray unsyncedEndedTrips() { return many("select * from trips where synced=0 and ended_at_ms is not null order by started_at_ms",null); }
    public JSONArray unsyncedTripPoints(String tripId) { return many("select * from points where synced=0 and trip_id=? order by recorded_at_ms",new String[]{tripId}); }
    public JSONArray unsyncedIdlePoints(String shiftId) { return many("select * from points where synced=0 and shift_id=? and trip_id is null order by recorded_at_ms",new String[]{shiftId}); }

    public void markTripSynced(String tripId) { ContentValues v=new ContentValues();v.put("synced",1);getWritableDatabase().update("trips",v,"trip_id=?",new String[]{tripId});getWritableDatabase().update("points",v,"trip_id=?",new String[]{tripId}); }
    public void markShiftSynced(String shiftId, boolean includeIdlePoints) { ContentValues v=new ContentValues();v.put("synced",1);getWritableDatabase().update("shifts",v,"shift_id=?",new String[]{shiftId});if(includeIdlePoints)getWritableDatabase().update("points",v,"shift_id=? and trip_id is null",new String[]{shiftId}); }

    private String normalizeType(String s){if(s==null)return "other";String x=s.toLowerCase();if(x.equals("uber")||x.equals("cabify")||x.equals("personal"))return x;return "other";}
    private String normalizeStatus(String s){if(s==null)return "completed";String x=s.toLowerCase();return x.equals("cancelled")?"cancelled":"completed";}

    private int scalarCount(String table){Cursor c=getReadableDatabase().rawQuery("select count(*) from "+table,null);try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}}
    private JSONObject one(String sql,String[] args) { Cursor c=getReadableDatabase().rawQuery(sql,args);try{return c.moveToFirst()?row(c):null;}catch(Exception e){return null;}finally{c.close();} }
    private JSONArray many(String sql,String[] args) { JSONArray a=new JSONArray();Cursor c=getReadableDatabase().rawQuery(sql,args);try{while(c.moveToNext())a.put(row(c));}catch(Exception ignored){}finally{c.close();}return a; }
    private JSONObject row(Cursor c) throws JSONException { JSONObject o=new JSONObject();for(int i=0;i<c.getColumnCount();i++){String n=c.getColumnName(i);int type=c.getType(i);if(type==Cursor.FIELD_TYPE_NULL)o.put(n,JSONObject.NULL);else if(type==Cursor.FIELD_TYPE_INTEGER)o.put(n,c.getLong(i));else if(type==Cursor.FIELD_TYPE_FLOAT)o.put(n,c.getDouble(i));else o.put(n,c.getString(i));}return o; }
}

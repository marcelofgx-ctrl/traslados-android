package uy.com.mapatrayectos;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class HistoryBottomSheet extends LinearLayout {
    public interface Listener {
        void onCloseHistory();
        void onOpenTrip(String tripId);
    }

    private static final int INK=Color.rgb(9,47,57);
    private static final int INK_SOFT=Color.rgb(56,86,92);
    private static final int TEAL=Color.rgb(12,91,99);
    private static final int TEAL_DARK=Color.rgb(4,63,72);
    private static final int GOLD=Color.rgb(218,178,72);
    private static final int GOLD_LIGHT=Color.rgb(241,213,126);
    private static final int GREEN=Color.rgb(24,142,101);
    private static final int RED=Color.rgb(192,65,70);
    private static final int ROUTE=Color.rgb(35,149,171);

    private final Activity activity;
    private final Listener listener;
    private final Locale locale=new Locale("es","UY");
    private final LinearLayout summaryHost;
    private final LinearLayout listHost;
    private final View dragHandle;
    private JSONArray allTrips=new JSONArray();
    private int periodDays;
    private String typeFilter;
    private String statusFilter;
    private String expandedDay=null;

    public HistoryBottomSheet(Activity activity, Listener listener){
        super(activity);
        this.activity=activity;
        this.listener=listener;
        setOrientation(VERTICAL);
        setPadding(dp(14),dp(6),dp(14),dp(12));
        setBackground(new TextureDrawable(
            Color.argb(246,247,244,229),
            Color.argb(246,224,241,236),
            Color.argb(220,218,178,72),28f,activity
        ));
        setElevation(dp(14));

        SharedPreferences p=activity.getSharedPreferences("history_sheet_prefs",Context.MODE_PRIVATE);
        periodDays=p.getInt("period_days",30);
        typeFilter=p.getString("type_filter","all");
        statusFilter=p.getString("status_filter","all");

        FrameLayout handleWrap=new FrameLayout(activity);
        dragHandle=new View(activity);
        dragHandle.setBackground(round(Color.rgb(137,164,164),5,0,0));
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(46),dp(5),Gravity.CENTER);
        handleWrap.addView(dragHandle,hp);
        addView(handleWrap,new LayoutParams(-1,dp(20)));

        LinearLayout header=new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon=text("▣",22,GOLD,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(Color.argb(120,255,242,191),18,1,Color.argb(150,218,178,72)));
        header.addView(icon,new LayoutParams(dp(42),dp(42)));
        LinearLayout titleBox=new LinearLayout(activity);titleBox.setOrientation(VERTICAL);
        TextView title=text("HISTORIAL DE TRAYECTOS",18,INK,true);
        TextView sub=text("Sobre el mapa · días contraídos",10.5f,INK_SOFT,false);
        titleBox.addView(title);titleBox.addView(sub);
        LayoutParams tp=new LayoutParams(0,-2,1);tp.setMargins(dp(9),0,0,0);header.addView(titleBox,tp);
        TextView close=text("×",26,INK,true);close.setGravity(Gravity.CENTER);close.setContentDescription("Cerrar historial");
        close.setBackground(round(Color.argb(90,255,255,255),22,1,Color.argb(120,9,47,57)));
        close.setOnClickListener(v->listener.onCloseHistory());
        header.addView(close,new LayoutParams(dp(42),dp(42)));
        addView(header,new LayoutParams(-1,dp(52)));

        HorizontalScrollView filters=new HorizontalScrollView(activity);
        filters.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow=new LinearLayout(activity);filterRow.setOrientation(HORIZONTAL);
        filters.addView(filterRow,new HorizontalScrollView.LayoutParams(-2,dp(42)));
        addFilterChip(filterRow,"Hoy","period","1",periodDays==1);
        addFilterChip(filterRow,"7 días","period","7",periodDays==7);
        addFilterChip(filterRow,"30 días","period","30",periodDays==30);
        addFilterChip(filterRow,"Todo","period","0",periodDays==0);
        addSeparator(filterRow);
        addFilterChip(filterRow,"Tipo: Todos","type","all","all".equals(typeFilter));
        addFilterChip(filterRow,"Uber","type","uber","uber".equals(typeFilter));
        addFilterChip(filterRow,"Cabify","type","cabify","cabify".equals(typeFilter));
        addFilterChip(filterRow,"Personal","type","personal","personal".equals(typeFilter));
        addSeparator(filterRow);
        addFilterChip(filterRow,"Estado: Todos","status","all","all".equals(statusFilter));
        addFilterChip(filterRow,"Completados","status","completed","completed".equals(statusFilter));
        addFilterChip(filterRow,"Cancelados","status","cancelled","cancelled".equals(statusFilter));
        addView(filters,new LayoutParams(-1,dp(44)));

        summaryHost=new LinearLayout(activity);
        summaryHost.setOrientation(VERTICAL);
        addView(summaryHost,new LayoutParams(-1,dp(84)));

        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        listHost=new LinearLayout(activity);listHost.setOrientation(VERTICAL);listHost.setPadding(0,dp(2),0,dp(18));
        scroll.addView(listHost,new ScrollView.LayoutParams(-1,-2));
        addView(scroll,new LayoutParams(-1,0,1));

        reload();
    }

    public View getDragHandle(){return dragHandle;}

    public void reload(){
        TrackDb db=new TrackDb(activity);
        allTrips=db.listTrips();
        db.close();
        expandedDay=null;
        render();
    }

    private void addFilterChip(LinearLayout row,String label,String group,String value,boolean selected){
        TextView chip=text(label,11,selected?Color.rgb(8,47,57):INK,true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14),0,dp(14),0);
        chip.setBackground(selected
            ?new TextureDrawable(Color.argb(245,250,226,155),Color.argb(245,231,195,91),Color.argb(230,176,132,30),16f,activity)
            :new TextureDrawable(Color.argb(205,250,251,245),Color.argb(205,224,241,238),Color.argb(150,19,98,105),16f,activity));
        chip.setOnClickListener(v->{
            if("period".equals(group))periodDays=Integer.parseInt(value);
            else if("type".equals(group))typeFilter=value;
            else statusFilter=value;
            savePrefs();
            rebuildFilters();
            expandedDay=null;
            render();
        });
        LayoutParams lp=new LayoutParams(-2,dp(36));lp.setMargins(0,dp(3),dp(7),dp(3));row.addView(chip,lp);
    }

    private void rebuildFilters(){
        View parent=getChildAt(2);
        if(!(parent instanceof HorizontalScrollView))return;
        HorizontalScrollView hsv=(HorizontalScrollView)parent;
        View child=hsv.getChildAt(0);
        if(!(child instanceof LinearLayout))return;
        LinearLayout row=(LinearLayout)child;
        row.removeAllViews();
        addFilterChip(row,"Hoy","period","1",periodDays==1);
        addFilterChip(row,"7 días","period","7",periodDays==7);
        addFilterChip(row,"30 días","period","30",periodDays==30);
        addFilterChip(row,"Todo","period","0",periodDays==0);
        addSeparator(row);
        addFilterChip(row,"Tipo: Todos","type","all","all".equals(typeFilter));
        addFilterChip(row,"Uber","type","uber","uber".equals(typeFilter));
        addFilterChip(row,"Cabify","type","cabify","cabify".equals(typeFilter));
        addFilterChip(row,"Personal","type","personal","personal".equals(typeFilter));
        addSeparator(row);
        addFilterChip(row,"Estado: Todos","status","all","all".equals(statusFilter));
        addFilterChip(row,"Completados","status","completed","completed".equals(statusFilter));
        addFilterChip(row,"Cancelados","status","cancelled","cancelled".equals(statusFilter));
    }

    private void addSeparator(LinearLayout row){
        View v=new View(activity);v.setBackgroundColor(Color.argb(60,9,47,57));
        LayoutParams lp=new LayoutParams(dp(1),dp(24));lp.setMargins(dp(2),dp(9),dp(9),dp(9));row.addView(v,lp);
    }

    private void savePrefs(){
        activity.getSharedPreferences("history_sheet_prefs",Context.MODE_PRIVATE).edit()
            .putInt("period_days",periodDays)
            .putString("type_filter",typeFilter)
            .putString("status_filter",statusFilter)
            .apply();
    }

    private void render(){
        ArrayList<JSONObject> filtered=filteredTrips();
        renderSummary(filtered);
        renderDays(filtered);
    }

    private ArrayList<JSONObject> filteredTrips(){
        ArrayList<JSONObject> result=new ArrayList<>();
        long now=System.currentTimeMillis();
        long cutoff=periodDays<=0?Long.MIN_VALUE:startOfToday(now)-(long)(periodDays-1)*86400000L;
        for(int i=0;i<allTrips.length();i++){
            JSONObject o=allTrips.optJSONObject(i);if(o==null)continue;
            if(o.optLong("started_at_ms",0)<cutoff)continue;
            String type=o.optString("trip_type","other");
            String status=o.optString("trip_status","completed");
            if(!"all".equals(typeFilter)&&!typeFilter.equals(type))continue;
            if(!"all".equals(statusFilter)&&!statusFilter.equals(status))continue;
            result.add(o);
        }
        return result;
    }

    private void renderSummary(List<JSONObject> trips){
        summaryHost.removeAllViews();
        double km=0,income=0;long total=0;int cancelled=0;
        for(JSONObject o:trips){
            km+=o.optDouble("distance_m",0)/1000.0;
            if(!o.isNull("amount_uyu"))income+=Math.max(0,o.optDouble("amount_uyu",0));
            long s=o.optLong("started_at_ms",0),e=o.optLong("ended_at_ms",0);
            total+=e>s?e-s:o.optLong("moving_ms",0)+o.optLong("stopped_ms",0);
            if("cancelled".equals(o.optString("trip_status","completed")))cancelled++;
        }
        LinearLayout band=new LinearLayout(activity);
        band.setGravity(Gravity.CENTER_VERTICAL);
        band.setPadding(dp(5),dp(5),dp(5),dp(5));
        band.setBackground(new TextureDrawable(
            Color.argb(220,255,251,235),
            Color.argb(220,231,245,239),
            Color.argb(180,218,178,72),16f,activity
        ));
        band.addView(metric(String.valueOf(trips.size()),"VIAJES","╱╲"),new LayoutParams(0,-1,1));
        band.addView(metric(String.format(locale,"%.1f",km),"KM","⌖"),new LayoutParams(0,-1,1));
        band.addView(metric(duration(total),"TIEMPO","◷"),new LayoutParams(0,-1,1));
        band.addView(metric("$ "+String.format(locale,"%.0f",income),"INGRESOS","●"),new LayoutParams(0,-1,1));
        summaryHost.addView(band,new LayoutParams(-1,dp(66)));
        if(cancelled>0){
            TextView c=text(cancelled+" cancelado"+(cancelled==1?"":"s"),9.5f,RED,true);
            c.setGravity(Gravity.RIGHT);summaryHost.addView(c,new LayoutParams(-1,dp(16)));
        }
    }

    private LinearLayout metric(String value,String label,String iconText){
        LinearLayout box=new LinearLayout(activity);box.setGravity(Gravity.CENTER);box.setOrientation(HORIZONTAL);
        TextView icon=text(iconText,17,GOLD,true);icon.setGravity(Gravity.CENTER);
        box.addView(icon,new LayoutParams(dp(30),-1));
        LinearLayout txt=new LinearLayout(activity);txt.setOrientation(VERTICAL);txt.setGravity(Gravity.CENTER_VERTICAL);
        TextView v=text(value,16,INK,true);v.setSingleLine(true);
        TextView l=text(label,8.7f,INK_SOFT,true);l.setSingleLine(true);
        txt.addView(v);txt.addView(l);box.addView(txt,new LayoutParams(0,-1,1));
        return box;
    }

    private void renderDays(List<JSONObject> trips){
        listHost.removeAllViews();
        if(trips.isEmpty()){
            TextView empty=text("No hay viajes para estos filtros.",14,INK_SOFT,false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0,dp(26),0,dp(26));
            listHost.addView(empty,new LayoutParams(-1,-2));return;
        }
        LinkedHashMap<String,ArrayList<JSONObject>> days=new LinkedHashMap<>();
        SimpleDateFormat keyFmt=new SimpleDateFormat("yyyy-MM-dd",locale);
        for(JSONObject o:trips){
            String key=keyFmt.format(new Date(o.optLong("started_at_ms",0)));
            days.computeIfAbsent(key,k->new ArrayList<>()).add(o);
        }
        SimpleDateFormat titleFmt=new SimpleDateFormat("EEEE d 'de' MMMM",locale);
        SimpleDateFormat dayFmt=new SimpleDateFormat("d",locale);
        SimpleDateFormat dowFmt=new SimpleDateFormat("EEE",locale);
        for(Map.Entry<String,ArrayList<JSONObject>> e:days.entrySet()){
            String key=e.getKey();ArrayList<JSONObject> dayTrips=e.getValue();
            boolean open=key.equals(expandedDay);
            long ts=dayTrips.get(0).optLong("started_at_ms",0);
            double km=0,income=0;for(JSONObject o:dayTrips){km+=o.optDouble("distance_m",0)/1000.0;if(!o.isNull("amount_uyu"))income+=Math.max(0,o.optDouble("amount_uyu",0));}

            LinearLayout group=new LinearLayout(activity);group.setOrientation(VERTICAL);
            group.setBackground(new TextureDrawable(
                Color.argb(235,255,253,244),
                Color.argb(235,229,243,239),
                Color.argb(190,218,178,72),20f,activity
            ));

            LinearLayout head=new LinearLayout(activity);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(9),dp(8),dp(8),dp(8));
            LinearLayout badge=new LinearLayout(activity);badge.setOrientation(VERTICAL);badge.setGravity(Gravity.CENTER);
            badge.setBackground(new TextureDrawable(Color.argb(230,250,235,183),Color.argb(230,239,220,159),Color.argb(150,218,178,72),13f,activity));
            TextView dow=text(dowFmt.format(new Date(ts)).toUpperCase(locale),9.5f,INK,true);dow.setGravity(Gravity.CENTER);
            TextView day=text(dayFmt.format(new Date(ts)),19,INK,true);day.setGravity(Gravity.CENTER);
            badge.addView(dow);badge.addView(day);
            head.addView(badge,new LayoutParams(dp(56),dp(54)));

            LinearLayout mid=new LinearLayout(activity);mid.setOrientation(VERTICAL);mid.setPadding(dp(10),0,dp(5),0);
            TextView date=text(cap(titleFmt.format(new Date(ts))),14.5f,INK,true);date.setSingleLine(true);
            String metrics=dayTrips.size()+" viajes  ·  "+String.format(locale,"%.1f km",km)+(income>0?"  ·  $ "+String.format(locale,"%.0f",income):"");
            TextView sub=text(metrics,10.8f,TEAL_DARK,true);sub.setSingleLine(true);
            mid.addView(date);mid.addView(sub);
            head.addView(mid,new LayoutParams(0,-2,1));

            TextView chevron=text(open?"⌃":"⌄",23,INK,true);chevron.setGravity(Gravity.CENTER);
            chevron.setBackground(round(Color.argb(90,255,255,255),22,1,Color.argb(105,9,47,57)));
            head.addView(chevron,new LayoutParams(dp(42),dp(42)));
            View.OnClickListener toggle=v->{expandedDay=open?null:key;renderDays(trips);};
            head.setOnClickListener(toggle);chevron.setOnClickListener(toggle);
            group.addView(head,new LayoutParams(-1,dp(72)));

            if(open){
                LinearLayout body=new LinearLayout(activity);body.setOrientation(VERTICAL);body.setPadding(dp(9),0,dp(9),dp(9));
                for(JSONObject o:dayTrips)body.addView(tripRow(o),margins(0,dp(5),0,0));
                group.addView(body,new LayoutParams(-1,-2));
            }
            listHost.addView(group,margins(0,dp(6),0,0));
        }
    }

    private View tripRow(JSONObject o){
        LinearLayout row=new LinearLayout(activity);row.setOrientation(VERTICAL);row.setPadding(dp(11),dp(8),dp(11),dp(8));
        row.setBackground(new TextureDrawable(Color.argb(195,255,255,250),Color.argb(195,236,246,243),Color.argb(100,19,98,105),14f,activity));
        long s=o.optLong("started_at_ms",0),e=o.optLong("ended_at_ms",0);
        SimpleDateFormat tf=new SimpleDateFormat("HH:mm",locale);
        String type=typeLabel(o.optString("trip_type","other"));
        TextView top=text(tf.format(new Date(s))+(e>s?"–"+tf.format(new Date(e)):"")+"   "+type,11,GOLD,true);
        TextView route=text(o.optString("start_zone","Origen")+"  →  "+o.optString("end_zone","Destino"),13.2f,INK,true);
        double km=o.optDouble("distance_m",0)/1000.0;double amount=o.isNull("amount_uyu")?0:o.optDouble("amount_uyu",0);
        String detail=String.format(locale,"%.1f km",km)+(amount>0?"  ·  $ "+String.format(locale,"%.0f",amount):"")+"   ·   VER RECORRIDO ›";
        TextView d=text(detail,10.3f,ROUTE,true);
        row.addView(top);row.addView(route);row.addView(d);
        row.setOnClickListener(v->listener.onOpenTrip(o.optString("trip_id","")));
        return row;
    }

    private String typeLabel(String s){if("uber".equals(s))return "UBER";if("cabify".equals(s))return "CABIFY";if("personal".equals(s))return "PERSONAL";return "OTRO";}
    private long startOfToday(long now){Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private String cap(String s){if(s==null||s.isEmpty())return s;return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private String duration(long ms){long x=Math.max(0,ms)/1000,h=x/3600,m=(x%3600)/60;if(h>0)return String.format(locale,"%d:%02d",h,m);return String.format(locale,"%02d:%02d",m,x%60);}

    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(activity);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Drawable round(int fill,int radius,int stroke,int strokeColor){android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke>0)g.setStroke(dp(stroke),strokeColor);return g;}
    private LayoutParams margins(int l,int t,int r,int b){LayoutParams p=new LayoutParams(-1,-2);p.setMargins(l,t,r,b);return p;}
    private int dp(int v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}

    private static final class TextureDrawable extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint texture=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int top,bottom,stroke;
        private final float radiusPx;
        TextureDrawable(int top,int bottom,int stroke,float radiusDp,Context c){
            this.top=top;this.bottom=bottom;this.stroke=stroke;
            this.radiusPx=radiusDp*c.getResources().getDisplayMetrics().density;
        }
        @Override public void draw(Canvas canvas){
            Rect b=getBounds();if(b.width()<=0||b.height()<=0)return;
            RectF r=new RectF(b.left,b.top,b.right,b.bottom);
            canvas.save();
            Path clip=new Path();clip.addRoundRect(r,radiusPx,radiusPx,Path.Direction.CW);canvas.clipPath(clip);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,top,bottom,Shader.TileMode.CLAMP));
            canvas.drawRect(r,paint);paint.setShader(null);
            texture.setStrokeWidth(1f);texture.setColor(Color.argb(16,26,92,95));
            float step=Math.max(14f,r.width()/22f);
            for(float x=-r.height();x<r.width();x+=step)canvas.drawLine(r.left+x,r.bottom,r.left+x+r.height(),r.top,texture);
            texture.setColor(Color.argb(13,255,255,255));
            for(float y=r.top+7;y<r.bottom;y+=18)canvas.drawLine(r.left+8,y,r.right-8,y,texture);
            canvas.restore();
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.2f*canvas.getDensity()/160f+1f);paint.setColor(stroke);
            canvas.drawRoundRect(r,radiusPx,radiusPx,paint);
        }
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter){}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
}

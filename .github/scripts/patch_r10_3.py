from pathlib import Path

main = Path('conductor/src/main/java/uy/com/traslados/conductor/MainActivity.java')
api = Path('conductor/src/main/java/uy/com/traslados/conductor/Api.java')
gradle = Path('conductor/build.gradle')
workflow = Path('.github/workflows/build-conductor-source.yml')

s = main.read_text()


def rep(old: str, new: str, count: int = 1):
    global s
    found = s.count(old)
    if found < count:
        raise RuntimeError(f'Expected replacement not found ({found} < {count}): {old[:160]!r}')
    s = s.replace(old, new, count)


rep('private long lastSuccessfulSyncAt=0L;', 'private long lastSuccessfulSyncAt=0L;\n    private boolean activeSyncInFlight=false;\n    private String lastActiveSnapshot="";')
rep('private TextView screenTitle,screenSub,agendaLabel,availabilityStatus,gpsPreflightStatus;', 'private TextView screenTitle,screenSub,agendaLabel,availabilityStatus,gpsPreflightStatus;\n    private TextView gpsStateChip,onlineStateChip,monitorStateChip;')
rep('private String agendaMode="TODAS";', 'private String agendaMode="TODAS";\n    private String agendaStatusMode="TODAS";')
rep('Conductor v11.4 R10.2 iniciado', 'Conductor v11.4 R10.3 iniciado')

rep('''screenTitle=heading(history?"HISTORIAL":"SOLICITUDES",27);box.addView(screenTitle,lpMatch(-2,4,0));
        screenSub=body(history?"Viajes finalizados, cancelados y rechazados":"Monitor activo · esperando solicitudes",13,GOLD);box.addView(screenSub,lpMatch(-2,0,10));

        statsBox=new LinearLayout(this);statsBox.setOrientation(LinearLayout.HORIZONTAL);box.addView(statsBox,lpMatch(history?dp(54):dp(76),0,10));''', '''screenTitle=heading(history?"HISTORIAL":"SOLICITUDES",24);box.addView(screenTitle,lpMatch(-2,3,0));
        screenSub=body(history?"Viajes finalizados, cancelados y rechazados":"Monitor activo · esperando solicitudes",12,GOLD);box.addView(screenSub,lpMatch(-2,0,7));

        statsBox=new LinearLayout(this);statsBox.setOrientation(LinearLayout.HORIZONTAL);box.addView(statsBox,lpMatch(history?dp(50):dp(66),0,8));''')

rep('''refreshBtn=secondaryButton("ACTUALIZAR");historyBtn=secondaryButton(history?"SOLICITUDES":"HISTORIAL");logoutBtn=secondaryButton("SALIR");
        actions.addView(refreshBtn,new LinearLayout.LayoutParams(0,dp(55),1));spacerH(actions,6);
        actions.addView(historyBtn,new LinearLayout.LayoutParams(0,dp(55),1));spacerH(actions,6);
        actions.addView(logoutBtn,new LinearLayout.LayoutParams(0,dp(55),1));
        box.addView(actions,lpMatch(dp(55),0,8));''', '''refreshBtn=secondaryButton("ACTUALIZAR");historyBtn=secondaryButton(history?"SOLICITUDES":"HISTORIAL");logoutBtn=secondaryButton("SALIR");
        applyActionIcon(refreshBtn,R.drawable.ic_refresh);applyActionIcon(historyBtn,R.drawable.ic_history);applyActionIcon(logoutBtn,R.drawable.ic_logout);
        actions.addView(refreshBtn,new LinearLayout.LayoutParams(0,dp(50),1));spacerH(actions,6);
        actions.addView(historyBtn,new LinearLayout.LayoutParams(0,dp(50),1));spacerH(actions,6);
        actions.addView(logoutBtn,new LinearLayout.LayoutParams(0,dp(50),1));
        box.addView(actions,lpMatch(dp(50),0,7));''')

rep('''Button alertPrefs=secondaryButton("🔔 AVISOS"),pricePrefs=secondaryButton("⚙ PRESETS"),gpsPrefs=secondaryButton("🛰 GPS");
            for(Button b:new Button[]{alertPrefs,pricePrefs,gpsPrefs}){b.setTextSize(11);b.setPadding(dp(4),0,dp(4),0);}
            tools.addView(alertPrefs,new LinearLayout.LayoutParams(0,dp(52),1));spacerH(tools,6);tools.addView(pricePrefs,new LinearLayout.LayoutParams(0,dp(52),1));spacerH(tools,6);tools.addView(gpsPrefs,new LinearLayout.LayoutParams(0,dp(52),1));''', '''Button alertPrefs=secondaryButton("AVISOS"),pricePrefs=secondaryButton("PRESETS"),gpsPrefs=secondaryButton("GPS");
            applyActionIcon(alertPrefs,R.drawable.ic_bell);applyActionIcon(pricePrefs,R.drawable.ic_tune);applyActionIcon(gpsPrefs,R.drawable.ic_gps);
            for(Button b:new Button[]{alertPrefs,pricePrefs,gpsPrefs}){b.setTextSize(10);b.setPadding(dp(4),0,dp(4),0);}
            tools.addView(alertPrefs,new LinearLayout.LayoutParams(0,dp(46),1));spacerH(tools,6);tools.addView(pricePrefs,new LinearLayout.LayoutParams(0,dp(46),1));spacerH(tools,6);tools.addView(gpsPrefs,new LinearLayout.LayoutParams(0,dp(46),1));''')
rep('box.addView(tools,lpMatch(dp(52),0,8));', 'box.addView(tools,lpMatch(dp(46),0,7));')

rep('''Button diagBtn=secondaryButton("🩺 DIAGNÓSTICO"),testBtn=secondaryButton("🧪 MODO PRUEBA");diagBtn.setTextSize(11);testBtn.setTextSize(11);
            tools2.addView(diagBtn,new LinearLayout.LayoutParams(0,dp(52),1));spacerH(tools2,6);tools2.addView(testBtn,new LinearLayout.LayoutParams(0,dp(52),1));''', '''Button diagBtn=secondaryButton("DIAGNÓSTICO"),testBtn=secondaryButton("MODO PRUEBA");diagBtn.setTextSize(10);testBtn.setTextSize(10);
            applyActionIcon(diagBtn,R.drawable.ic_diagnostics);applyActionIcon(testBtn,R.drawable.ic_lab);
            tools2.addView(diagBtn,new LinearLayout.LayoutParams(0,dp(46),1));spacerH(tools2,6);tools2.addView(testBtn,new LinearLayout.LayoutParams(0,dp(46),1));''')
rep('box.addView(tools2,lpMatch(dp(52),0,9));', 'box.addView(tools2,lpMatch(dp(46),0,7));')

rep('TextView agendaTitle=body("AGENDA",19,TEXT);', 'TextView agendaTitle=body("AGENDA",18,TEXT);')
rep('agendaLabel=body(agendaDescription(),13,GOLD);', 'agendaLabel=body(agendaDescription(),12,GOLD);')
rep('''agendaDateBtn=secondaryButton("📅 FECHA");agendaDateBtn.setTextSize(11);agendaDateBtn.setPadding(dp(6),0,dp(6),0);
            LinearLayout.LayoutParams dateLp=new LinearLayout.LayoutParams(dp(112),dp(44));''', '''agendaDateBtn=secondaryButton("FECHA");agendaDateBtn.setTextSize(10);agendaDateBtn.setPadding(dp(6),0,dp(6),0);applyActionIcon(agendaDateBtn,R.drawable.ic_calendar);
            LinearLayout.LayoutParams dateLp=new LinearLayout.LayoutParams(dp(106),dp(42));''')
rep('for(Button b:new Button[]{agendaAllBtn,agendaTodayBtn,agendaWeekBtn,agendaMonthBtn}){b.setTextSize(11);b.setPadding(dp(3),0,dp(3),0);}', 'for(Button b:new Button[]{agendaAllBtn,agendaTodayBtn,agendaWeekBtn,agendaMonthBtn}){b.setTextSize(10);b.setPadding(dp(3),0,dp(3),0);}')
rep('''filters.addView(agendaAllBtn,new LinearLayout.LayoutParams(0,dp(48),1));spacerH(filters,4);
            filters.addView(agendaTodayBtn,new LinearLayout.LayoutParams(0,dp(48),1));spacerH(filters,4);
            filters.addView(agendaWeekBtn,new LinearLayout.LayoutParams(0,dp(48),1));spacerH(filters,4);
            filters.addView(agendaMonthBtn,new LinearLayout.LayoutParams(0,dp(48),1));
            agendaBox.addView(filters,lpMatch(dp(48),0,10));''', '''filters.addView(agendaAllBtn,new LinearLayout.LayoutParams(0,dp(44),1));spacerH(filters,4);
            filters.addView(agendaTodayBtn,new LinearLayout.LayoutParams(0,dp(44),1));spacerH(filters,4);
            filters.addView(agendaWeekBtn,new LinearLayout.LayoutParams(0,dp(44),1));spacerH(filters,4);
            filters.addView(agendaMonthBtn,new LinearLayout.LayoutParams(0,dp(44),1));
            agendaBox.addView(filters,lpMatch(dp(44),0,8));''')

old_load = '''    private void loadActive(boolean showLoading){
        if(pin.isEmpty())return;
        if(showLoading&&screenSub!=null)screenSub.setText("Actualizando reservas…");
        pool.execute(()->{
            try{
                JSONArray a=Api.listActive(pin);ArrayList<JSONObject> rows=new ArrayList<>();Set<String> current=new HashSet<>();
                ArrayList<JSONObject> fresh=new ArrayList<>();
                for(int i=0;i<a.length();i++){
                    JSONObject r=a.getJSONObject(i);rows.add(r);
                    if("PENDIENTE".equals(r.optString("status"))){
                        String id=r.optString("id");current.add(id);
                        if(!firstLoad&&!seenPending.contains(id))fresh.add(r);
                    }
                }
                seenPending.clear();seenPending.addAll(current);firstLoad=false;
                runOnUiThread(()->{
                    if(historyMode)return;
                    currentRows.clear();currentRows.addAll(rows);lastSuccessfulSyncAt=System.currentTimeMillis();ensureTelemetryForActiveTrip(rows);renderRows(false);
                    for(JSONObject r:fresh)alertNew(r);
                });
            }catch(Exception e){runOnUiThread(()->{if(!historyMode)showLoadError(e,false);});}
        });
    }
'''
new_load = '''    private void loadActive(boolean showLoading){
        if(pin.isEmpty()||activeSyncInFlight)return;
        activeSyncInFlight=true;
        if(showLoading&&screenSub!=null)screenSub.setText("Actualizando reservas…");
        pool.execute(()->{
            try{
                JSONArray a=Api.listActive(pin);ArrayList<JSONObject> rows=new ArrayList<>();Set<String> current=new HashSet<>();
                ArrayList<JSONObject> fresh=new ArrayList<>();
                for(int i=0;i<a.length();i++){
                    JSONObject r=a.getJSONObject(i);rows.add(r);
                    if("PENDIENTE".equals(r.optString("status"))){
                        String id=r.optString("id");current.add(id);
                        if(!firstLoad&&!seenPending.contains(id))fresh.add(r);
                    }
                }
                String snapshot=rows.toString();
                seenPending.clear();seenPending.addAll(current);firstLoad=false;
                runOnUiThread(()->{
                    activeSyncInFlight=false;
                    if(historyMode)return;
                    boolean changed=!snapshot.equals(lastActiveSnapshot);lastActiveSnapshot=snapshot;
                    currentRows.clear();currentRows.addAll(rows);lastSuccessfulSyncAt=System.currentTimeMillis();ensureTelemetryForActiveTrip(rows);
                    if(changed||showLoading||listBox==null||listBox.getChildCount()==0)renderRows(false);else refreshPassiveDashboard();
                    for(JSONObject r:fresh)alertNew(r);
                });
            }catch(Exception e){runOnUiThread(()->{activeSyncInFlight=false;if(!historyMode)showLoadError(e,false);});}
        });
    }

    private void refreshPassiveDashboard(){
        if(screenSub!=null)screenSub.setText((isNetworkAvailable()?"Monitor activo":"Monitor sin conexión")+" · "+agendaFilteredRows().size()+" visible(s) de "+currentRows.size()+syncSuffix());
        renderStats(false);refreshAgendaControls();updateStatusStrip();
    }
'''
rep(old_load, new_load)

rep('if(!hist)refreshAgendaControls();', 'if(!hist){refreshAgendaControls();updateStatusStrip();}')

old_stats = '''    private void renderStats(boolean hist){
        statsBox.removeAllViews();
        if(!hist){
            int n=0,q=0,c=0;for(JSONObject r:agendaFilteredRows()){String st=r.optString("status"),qs=r.optString("quote_status","SIN_PRESUPUESTO");if("PENDIENTE".equals(st)&&"ENVIADO".equals(qs))q++;else if("PENDIENTE".equals(st))n++;else if("ACEPTADA".equals(st)||"EN_VIAJE".equals(st))c++;}
            statsBox.addView(statCard("NUEVAS",n,GOLD),new LinearLayout.LayoutParams(0,-1,1));spacerH(statsBox,6);
            statsBox.addView(statCard("COTIZADAS",q,CYAN),new LinearLayout.LayoutParams(0,-1,1));spacerH(statsBox,6);
            statsBox.addView(statCard("CONFIRM.",c,GREEN),new LinearLayout.LayoutParams(0,-1,1));
        }else{
            int f=0,c=0,rj=0;for(JSONObject r:currentRows){String s=r.optString("status");if("FINALIZADA".equals(s))f++;else if("CANCELADA".equals(s))c++;else if("RECHAZADA".equals(s))rj++;}
            TextView summary=body(f+" finalizadas  ·  "+c+" canceladas  ·  "+rj+" rechazadas",13,TEXT);summary.setTypeface(Typeface.DEFAULT,Typeface.BOLD);summary.setGravity(Gravity.CENTER_VERTICAL);summary.setPadding(dp(14),0,dp(14),0);summary.setBackground(rounded(PANEL_2,LINE,15));statsBox.addView(summary,new LinearLayout.LayoutParams(-1,-1));
        }
    }

    private View statCard(String label,int count,int color){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(4),dp(6),dp(4),dp(6));c.setBackground(rounded(PANEL_2,LINE,15));
        TextView n=body(String.valueOf(count),24,color);n.setTypeface(Typeface.DEFAULT,Typeface.BOLD);n.setGravity(Gravity.CENTER);c.addView(n);
        TextView l=body(label,10,MUTED);l.setTypeface(Typeface.DEFAULT,Typeface.BOLD);l.setGravity(Gravity.CENTER);c.addView(l);return c;
    }
'''
new_stats = '''    private void renderStats(boolean hist){
        statsBox.removeAllViews();
        if(!hist){
            int n=0,q=0,c=0;for(JSONObject r:currentRows){if(!matchesAgenda(r.optString("pickup_date","")))continue;String st=r.optString("status"),qs=r.optString("quote_status","SIN_PRESUPUESTO");if("PENDIENTE".equals(st)&&"ENVIADO".equals(qs))q++;else if("PENDIENTE".equals(st))n++;else if("ACEPTADA".equals(st)||"CONFIRMADA".equals(st)||"EN_VIAJE".equals(st))c++;}
            View nCard=statCard("NUEVAS",n,GOLD,"NUEVAS".equals(agendaStatusMode));
            View qCard=statCard("COTIZADAS",q,CYAN,"COTIZADAS".equals(agendaStatusMode));
            View cCard=statCard("CONFIRM.",c,GREEN,"CONFIRMADAS".equals(agendaStatusMode));
            nCard.setOnClickListener(v->setAgendaStatusMode("NUEVAS"));qCard.setOnClickListener(v->setAgendaStatusMode("COTIZADAS"));cCard.setOnClickListener(v->setAgendaStatusMode("CONFIRMADAS"));
            statsBox.addView(nCard,new LinearLayout.LayoutParams(0,-1,1));spacerH(statsBox,6);
            statsBox.addView(qCard,new LinearLayout.LayoutParams(0,-1,1));spacerH(statsBox,6);
            statsBox.addView(cCard,new LinearLayout.LayoutParams(0,-1,1));
        }else{
            int f=0,c=0,rj=0;for(JSONObject r:currentRows){String s=r.optString("status");if("FINALIZADA".equals(s))f++;else if("CANCELADA".equals(s))c++;else if("RECHAZADA".equals(s))rj++;}
            TextView summary=body(f+" finalizadas  ·  "+c+" canceladas  ·  "+rj+" rechazadas",13,TEXT);summary.setTypeface(Typeface.DEFAULT,Typeface.BOLD);summary.setGravity(Gravity.CENTER_VERTICAL);summary.setPadding(dp(14),0,dp(14),0);summary.setBackground(rounded(PANEL_2,LINE,15));statsBox.addView(summary,new LinearLayout.LayoutParams(-1,-1));
        }
    }

    private View statCard(String label,int count,int color,boolean active){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(4),dp(5),dp(4),dp(5));
        c.setBackground(rounded(active?Color.argb(58,Color.red(color),Color.green(color),Color.blue(color)):PANEL_2,active?color:LINE,15));c.setElevation(dp(active?4:1));c.setClickable(true);c.setFocusable(true);
        TextView n=body(String.valueOf(count),22,color);n.setTypeface(Typeface.DEFAULT,Typeface.BOLD);n.setGravity(Gravity.CENTER);c.addView(n);
        TextView l=body(label,10,active?TEXT:MUTED);l.setTypeface(Typeface.DEFAULT,Typeface.BOLD);l.setGravity(Gravity.CENTER);c.addView(l);return c;
    }

    private void setAgendaStatusMode(String mode){agendaStatusMode=mode.equals(agendaStatusMode)?"TODAS":mode;resetActiveDayAccordion();hapticTick();renderRows(false);}
'''
rep(old_stats, new_stats)

rep('LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setBackground(rounded(PANEL,LINE,18));outer.setElevation(dp(3));', 'LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);int cardAccent=statusColor(displayStatus(r));outer.setBackground(rounded(PANEL,cardAccent,18));outer.setElevation(dp(3));')

rep('private ArrayList<JSONObject> agendaFilteredRows(){ArrayList<JSONObject> out=new ArrayList<>();for(JSONObject r:currentRows)if(matchesAgenda(r.optString("pickup_date","")))out.add(r);return out;}', 'private ArrayList<JSONObject> agendaFilteredRows(){ArrayList<JSONObject> out=new ArrayList<>();for(JSONObject r:currentRows)if(matchesAgenda(r.optString("pickup_date",""))&&matchesAgendaStatus(r))out.add(r);return out;}\n    private boolean matchesAgendaStatus(JSONObject r){if("TODAS".equals(agendaStatusMode))return true;String st=r.optString("status",""),qs=r.optString("quote_status","SIN_PRESUPUESTO");if("NUEVAS".equals(agendaStatusMode))return "PENDIENTE".equals(st)&&!"ENVIADO".equals(qs);if("COTIZADAS".equals(agendaStatusMode))return "PENDIENTE".equals(st)&&"ENVIADO".equals(qs);if("CONFIRMADAS".equals(agendaStatusMode))return "ACEPTADA".equals(st)||"CONFIRMADA".equals(st)||"EN_VIAJE".equals(st);return true;}')

rep('private String agendaDescription(){if(agendaCustomDate)return "reservas del "+new java.text.SimpleDateFormat("dd/MM/yyyy",Locale.US).format(agendaDate.getTime());if("TODAS".equals(agendaMode))return "todas las reservas activas";if("DIA".equals(agendaMode))return "reservas de hoy";if("SEMANA".equals(agendaMode))return "reservas de los próximos 7 días";return "reservas del mes actual";}', 'private String agendaDescription(){String base;if(agendaCustomDate)base="reservas del "+new java.text.SimpleDateFormat("dd/MM/yyyy",Locale.US).format(agendaDate.getTime());else if("TODAS".equals(agendaMode))base="todas las reservas activas";else if("DIA".equals(agendaMode))base="reservas de hoy";else if("SEMANA".equals(agendaMode))base="reservas de los próximos 7 días";else base="reservas del mes actual";if("NUEVAS".equals(agendaStatusMode))return base+" · nuevas";if("COTIZADAS".equals(agendaStatusMode))return base+" · cotizadas";if("CONFIRMADAS".equals(agendaStatusMode))return base+" · confirmadas";return base;}')
rep('private void refreshAgendaControls(){if(agendaLabel!=null)agendaLabel.setText(agendaDescription());String picked=new java.text.SimpleDateFormat("dd/MM",Locale.US).format(agendaDate.getTime());if(agendaDateBtn!=null)agendaDateBtn.setText(agendaCustomDate?"📅 "+picked:"📅 FECHA");setAgendaQuickState(agendaAllBtn,"TODAS",!agendaCustomDate&&"TODAS".equals(agendaMode));setAgendaQuickState(agendaTodayBtn,"HOY",!agendaCustomDate&&"DIA".equals(agendaMode));setAgendaQuickState(agendaWeekBtn,"SEMANA",!agendaCustomDate&&"SEMANA".equals(agendaMode));setAgendaQuickState(agendaMonthBtn,"MES",!agendaCustomDate&&"MES".equals(agendaMode));}', 'private void refreshAgendaControls(){if(agendaLabel!=null)agendaLabel.setText(agendaDescription());String picked=new java.text.SimpleDateFormat("dd/MM",Locale.US).format(agendaDate.getTime());if(agendaDateBtn!=null)agendaDateBtn.setText(agendaCustomDate?picked:"FECHA");setAgendaQuickState(agendaAllBtn,"TODAS",!agendaCustomDate&&"TODAS".equals(agendaMode));setAgendaQuickState(agendaTodayBtn,"HOY",!agendaCustomDate&&"DIA".equals(agendaMode));setAgendaQuickState(agendaWeekBtn,"SEMANA",!agendaCustomDate&&"SEMANA".equals(agendaMode));setAgendaQuickState(agendaMonthBtn,"MES",!agendaCustomDate&&"MES".equals(agendaMode));}')
rep('private void setAgendaQuickState(Button b,String label,boolean active){if(b==null)return;b.setText(active?"● "+label:label);b.setAlpha(active?1.0f:0.82f);}', 'private void setAgendaQuickState(Button b,String label,boolean active){if(b==null)return;b.setText(label);b.setAlpha(active?1.0f:0.84f);b.setTextColor(active?GOLD:TEXT);b.setBackground(rounded(active?Color.argb(52,224,193,111):PANEL_2,active?GOLD:LINE,16));}')

rep('private boolean isConfirmedTrip(JSONObject r){String st=r.optString("status","");return "ACEPTADA".equals(st)||"CONFIRMADA".equals(st)||"ACEPTADA_CLIENTE".equals(st)||"EN_VIAJE".equals(st);}', 'private boolean isConfirmedTrip(JSONObject r){String st=r.optString("status","");return "ACEPTADA".equals(st)||"CONFIRMADA".equals(st);}')

old_brand = '''    private void addBrand(LinearLayout box){
        LinearLayout hero=new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(14),dp(14),dp(14),dp(13));
        hero.setBackground(rounded(Color.rgb(5,35,43),Color.rgb(55,100,111),22));
        hero.setElevation(dp(5));

        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setBackground(rounded(PANEL_2,GOLD_DARK,18));
        logo.setClipToOutline(true);
        row.addView(logo,new LinearLayout.LayoutParams(dp(76),dp(76)));

        LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(dp(13),0,0,0);row.addView(col,cp);

        TextView brand=heading("Traslados Conductor",25);
        col.addView(brand);
        TextView privateLine=body("Acceso privado del conductor",12,MUTED);
        col.addView(privateLine,lpMatch(-2,2,1));
        TextView serviceLine=body("Aeropuerto · Programados · Larga distancia",11,GOLD);
        col.addView(serviceLine);

        hero.addView(row);

        LinearLayout meta=new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(0,dp(11),0,0);
        TextView monitor=miniChip("●  MONITOR ACTIVO",GREEN);
        TextView version=miniChip("V11.4",GOLD);
        meta.addView(monitor,new LinearLayout.LayoutParams(0,dp(30),1));
        spacerH(meta,8);
        meta.addView(version,new LinearLayout.LayoutParams(0,dp(30),1));
        hero.addView(meta);

        box.addView(hero,lpMatch(-2,0,4));
    }
'''
new_brand = '''    private void addBrand(LinearLayout box){
        LinearLayout hero=new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(12),dp(10),dp(12),dp(10));
        hero.setBackground(rounded(Color.rgb(5,35,43),Color.rgb(55,100,111),20));
        hero.setElevation(dp(4));

        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.app_icon);logo.setScaleType(ImageView.ScaleType.CENTER_CROP);logo.setBackground(rounded(PANEL_2,GOLD_DARK,16));logo.setClipToOutline(true);row.addView(logo,new LinearLayout.LayoutParams(dp(64),dp(64)));

        LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(dp(11),0,0,0);row.addView(col,cp);
        TextView brand=heading("Traslados Conductor",22);col.addView(brand);
        TextView privateLine=body("Acceso privado del conductor",11,MUTED);col.addView(privateLine,lpMatch(-2,1,0));
        TextView serviceLine=body("Aeropuerto · Programados · Larga distancia",10,GOLD);col.addView(serviceLine);
        TextView versionLine=body("v11.4 · R10.3 · build 121",9,MUTED);col.addView(versionLine,lpMatch(-2,2,0));
        hero.addView(row);

        LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.setPadding(0,dp(8),0,0);
        gpsStateChip=miniChip("GPS · …",MUTED);onlineStateChip=miniChip(isNetworkAvailable()?"ONLINE":"SIN RED",isNetworkAvailable()?GREEN:RED);monitorStateChip=miniChip("MONITOR",dashboardVisible?GREEN:MUTED);
        meta.addView(gpsStateChip,new LinearLayout.LayoutParams(0,dp(28),1));spacerH(meta,5);meta.addView(onlineStateChip,new LinearLayout.LayoutParams(0,dp(28),1));spacerH(meta,5);meta.addView(monitorStateChip,new LinearLayout.LayoutParams(0,dp(28),1));hero.addView(meta);
        box.addView(hero,lpMatch(-2,0,4));
    }
'''
rep(old_brand, new_brand)

rep('''    private TextView miniChip(String text,int color){
        TextView t=body(text,10,color);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rounded(Color.argb(34,Color.red(color),Color.green(color),Color.blue(color)),color,14));
        return t;
    }
''', '''    private TextView miniChip(String text,int color){
        TextView t=body(text,9,color);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER);t.setBackground(rounded(Color.argb(28,Color.red(color),Color.green(color),Color.blue(color)),color,14));return t;
    }
    private void setMiniChipState(TextView chip,String text,int color){if(chip==null)return;chip.setText(text);chip.setTextColor(color);chip.setBackground(rounded(Color.argb(28,Color.red(color),Color.green(color),Color.blue(color)),color,14));}
    private void updateStatusStrip(){boolean net=isNetworkAvailable();setMiniChipState(onlineStateChip,net?"ONLINE":"SIN RED",net?GREEN:RED);setMiniChipState(monitorStateChip,dashboardVisible?"MONITOR":"PAUSA",dashboardVisible?GREEN:MUTED);}
    private void applyActionIcon(Button b,int res){if(b==null)return;try{b.setCompoundDrawablesRelativeWithIntrinsicBounds(res,0,0,0);b.setCompoundDrawablePadding(dp(5));}catch(Exception ignored){}}
''')

old_gps = '    private void actualizarGpsPreflightSilencioso(){GpsPreflight g=leerGpsPreflight();if(gpsPreflightStatus==null)return;if(!g.permission){gpsPreflightStatus.setText("⚠ PERMISO DE UBICACIÓN NECESARIO · CORREGIR");gpsPreflightStatus.setTextColor(Color.rgb(235,170,80));}else if(!g.locationEnabled){gpsPreflightStatus.setText("⚠ GPS DESACTIVADO · ACTIVAR");gpsPreflightStatus.setTextColor(Color.rgb(235,170,80));}else if(!g.serviceDeclared){gpsPreflightStatus.setText("⚠ SERVICIO GPS NO DISPONIBLE");gpsPreflightStatus.setTextColor(RED);}else if(g.backgroundRestricted){gpsPreflightStatus.setText("● GPS LISTO · Android restringe segundo plano");gpsPreflightStatus.setTextColor(Color.rgb(235,170,80));}else{gpsPreflightStatus.setText("● GPS LISTO");gpsPreflightStatus.setTextColor(GREEN);}}'
new_gps = '    private void actualizarGpsPreflightSilencioso(){GpsPreflight g=leerGpsPreflight();String detail,chip;int color;if(!g.permission){detail="⚠ PERMISO DE UBICACIÓN NECESARIO · CORREGIR";chip="GPS · PERMISO";color=Color.rgb(235,170,80);}else if(!g.locationEnabled){detail="⚠ GPS DESACTIVADO · ACTIVAR";chip="GPS · OFF";color=Color.rgb(235,170,80);}else if(!g.serviceDeclared){detail="⚠ SERVICIO GPS NO DISPONIBLE";chip="GPS · ERROR";color=RED;}else if(g.backgroundRestricted){detail="● GPS LISTO · Android restringe segundo plano";chip="GPS · LIMITADO";color=Color.rgb(235,170,80);}else{detail="● GPS LISTO";chip="GPS · LISTO";color=GREEN;}if(gpsPreflightStatus!=null){gpsPreflightStatus.setText(detail);gpsPreflightStatus.setTextColor(color);}setMiniChipState(gpsStateChip,chip,color);updateStatusStrip();}'
rep(old_gps, new_gps)

main.write_text(s)

api.write_text(api.read_text().replace('11.4-R10.2', '11.4-R10.3'))
gradle.write_text(gradle.read_text().replace('versionCode 120', 'versionCode 121').replace("versionName '11.4-R10.2'", "versionName '11.4-R10.3'"))

w = workflow.read_text()
w = w.replace("      - 'stabilize/conductor-r10.2-canonical'\n", '')
w = w.replace('versionCode 120', 'versionCode 121').replace("versionName '11.4-R10.2'", "versionName '11.4-R10.3'")
w = w.replace("grep -q '11.4-R10.2'", "grep -q '11.4-R10.3'")
w = w.replace('Conductor v11.4 R10.2 iniciado', 'Conductor v11.4 R10.3 iniciado')
w = w.replace("echo 'Canonical Conductor R10.2 source verified'", "grep -q 'agendaStatusMode' \"$D/MainActivity.java\"\n          grep -q 'gpsStateChip' \"$D/MainActivity.java\"\n          test -f conductor/src/main/res/drawable/ic_refresh.xml\n          test -f conductor/src/main/res/drawable/ic_gps.xml\n          echo 'Canonical Conductor R10.3 source verified'")
w = w.replace('Traslados_Conductor_v11.4_R10.2_SOURCE.apk', 'Traslados_Conductor_v11.4_R10.3_SOURCE.apk')
w = w.replace('Traslados-Conductor-v11.4-R10.2-SOURCE', 'Traslados-Conductor-v11.4-R10.3-SOURCE')
workflow.write_text(w)

icons = {
    'ic_refresh.xml': '''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="20dp" android:height="20dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#E0C16F" android:pathData="M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.09,0 -7.19,3.72 -6.39,7.69L3.5,9.58V15h5.42l-2.18,-2.18C6.35,10.28 8.27,8 12,8c2.21,0 4,1.79 4,4s-1.79,4 -4,4c-1.48,0 -2.77,-0.81 -3.46,-2H4.26c0.89,3.45 4.01,6 7.74,6 4.42,0 8,-3.58 8,-8 0,-2.21 -0.9,-4.21 -2.35,-5.65z"/></vector>''',
    'ic_history.xml': '''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="20dp" android:height="20dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#E0C16F" android:pathData="M13,3a9,9 0,1 0,8.95,10h-2.02A7,7 0,1 1,13,5c1.93,0 3.68,0.78 4.95,2.05L15,10h7V3l-2.63,2.63A8.96,8.96 0,0 0,13,3zM12,7v6l5,3 1,-1.73 -4,-2.27V7z"/></vector>''',
    'ic_logout.xml': '''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="20dp" android:height="20dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#E0C16F" android:pathData="M10,17l5,-5 -5,-5v3H3v4h7v3zM17,3H8c-1.1,0 -2,0.9 -2,2v3h2V5h9v14H8v-3H6v3c0,1.1 0.9,2 2,2h9c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z"/></vector>''',
    'ic_bell.xml': '''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="20dp" android:height="20dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#E0C16F" android:pathData="M12,22c1.1,0 1.99,-0.9 1.99,-2h-4A2,2 0,0 0,12 22zM18,16v-5c0,-3.07 -1.63,-5.64 -4.5,-6.32V4a1.5,1.5 0,0 0,-3 0v0.68C7.64,5.36 6,7.92 6,11v5l-2,2v1h16v-1z"/></vector>''',
    'ic_tune.xml': '''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="20dp" android:height="20dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#E0C16F" android:pathData="M3,17v2h6v-2zM3,5v2h10V5zM13,21v-2h8v-2h-8v-2h-2v6zM7,9v2H3v2h4v2h2V9zM21,13v-2H11v2zM15,9h2V7h4V5h-4V3h-2z"/></vector>''',
    'ic_gps.xml': '''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="20dp" android:height="20dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#E0C16F" android:pathData="M12,8a4,4 0,1 0,0 8,4 4,0 0,0 0,-8zM20.94,11A9,9 0,0 0,13 3.06V1h-2v2.06A9,9 0,0 0,3.06 11H1v2h2.06A9,9 0,0 0,11 20.94V23h2v-2.06A9,9 0,0 0,20.94 13H23v-2zM12,19a7,7 0,1 1,0 -14,7 7,0 0,1 0,14z"/></vector>''',
    'ic_diagnostics.xml': '''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="20dp" android:height="20dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#E0C16F" android:pathData="M3,12h4l2,-5 4,10 2,-5h6v2h-4.6L13,22 9,12 8.4,14H3z"/></vector>''',
    'ic_lab.xml': '''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="20dp" android:height="20dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#E0C16F" android:pathData="M9,3v2l1,0v4.59L4.59,15A2,2 0,0 0,6 18.41h12A2,2 0,0 0,19.41 15L14,9.59V5h1V3zM9,14l3,-3 3,3z"/></vector>''',
    'ic_calendar.xml': '''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="20dp" android:height="20dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#E0C16F" android:pathData="M19,4h-1V2h-2v2H8V2H6v2H5a2,2 0,0 0,-2 2v14a2,2 0,0 0,2 2h14a2,2 0,0 0,2 -2V6a2,2 0,0 0,-2 -2zM5,9h14v11H5z"/></vector>''',
}
icon_dir = Path('conductor/src/main/res/drawable')
icon_dir.mkdir(parents=True, exist_ok=True)
for name, content in icons.items():
    (icon_dir / name).write_text(content + '\n')

print('R10.3 patch applied')

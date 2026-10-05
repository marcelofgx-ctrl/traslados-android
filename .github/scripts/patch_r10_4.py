from pathlib import Path

main = Path('conductor/src/main/java/uy/com/traslados/conductor/MainActivity.java')
svc = Path('conductor/src/main/java/uy/com/traslados/conductor/ReservationMonitorService.java')
api = Path('conductor/src/main/java/uy/com/traslados/conductor/Api.java')
gradle = Path('conductor/build.gradle')

s = main.read_text()

def rep(old: str, new: str, count: int = 1):
    global s
    found = s.count(old)
    if found < count:
        raise RuntimeError(f'MainActivity replacement not found ({found} < {count}): {old[:180]!r}')
    s = s.replace(old, new, count)

rep('Conductor v11.4 R10.3 iniciado', 'Conductor v11.4 R10.4 iniciado')
rep('v11.4 · R10.3 · build 121', 'v11.4 · R10.4 · build 122')
rep('''        if(screenSub!=null)screenSub.setText(hist?"Historial · "+shown.size()+" viaje(s)"+syncSuffix():(isNetworkAvailable()?"Monitor activo":"Monitor sin conexión")+" · "+shown.size()+" visible(s) de "+currentRows.size()+syncSuffix());''', '''        if(screenSub!=null)screenSub.setText(hist?"Historial · "+shown.size()+" viaje(s) · tocá un día para desplegar"+syncSuffix():(isNetworkAvailable()?"Monitor activo":"Monitor sin conexión")+" · "+shown.size()+" visible(s) de "+currentRows.size()+syncSuffix());''')
rep('''        if(!initialized){
            collapsed.clear();boolean first=true;
            for(String day:groups.keySet()){if(!first)collapsed.add(day);first=false;}
            if(hist)historyDayGroupsInitialized=true;else activeDayGroupsInitialized=true;
        }''', '''        if(!initialized){
            collapsed.clear();
            if(hist){
                collapsed.addAll(groups.keySet());
            }else{
                boolean first=true;
                for(String day:groups.keySet()){if(!first)collapsed.add(day);first=false;}
            }
            if(hist)historyDayGroupsInitialized=true;else activeDayGroupsInitialized=true;
        }''')
main.write_text(s)

u = svc.read_text()
def reps(old: str, new: str, count: int = 1):
    global u
    found = u.count(old)
    if found < count:
        raise RuntimeError(f'Service replacement not found ({found} < {count}): {old[:180]!r}')
    u = u.replace(old, new, count)

# R10.4: reduce bubble by ~8% while preserving readability and touch behavior.
reps('root.setPadding(driverBubbleDp(4),driverBubbleDp(4),driverBubbleDp(4),driverBubbleDp(4));', 'root.setPadding(driverBubbleDp(3),driverBubbleDp(3),driverBubbleDp(3),driverBubbleDp(3));')
reps('root.setElevation(driverBubbleDp(12));', 'root.setElevation(driverBubbleDp(10));')
reps('new android.widget.FrameLayout.LayoutParams(driverBubbleDp(50),driverBubbleDp(50),android.view.Gravity.TOP|android.view.Gravity.CENTER_HORIZONTAL)', 'new android.widget.FrameLayout.LayoutParams(driverBubbleDp(46),driverBubbleDp(46),android.view.Gravity.TOP|android.view.Gravity.CENTER_HORIZONTAL)')
reps('llp.topMargin=driverBubbleDp(3);', 'llp.topMargin=driverBubbleDp(3);')
reps('role.setTextSize(6.3f);', 'role.setTextSize(6.0f);')
reps('new android.widget.FrameLayout.LayoutParams(driverBubbleDp(62),driverBubbleDp(14),android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL)', 'new android.widget.FrameLayout.LayoutParams(driverBubbleDp(56),driverBubbleDp(13),android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL)')
reps('badge.setTextSize(11);', 'badge.setTextSize(10);')
reps('new android.widget.FrameLayout.LayoutParams(driverBubbleDp(23),driverBubbleDp(23),android.view.Gravity.TOP|android.view.Gravity.RIGHT)', 'new android.widget.FrameLayout.LayoutParams(driverBubbleDp(21),driverBubbleDp(21),android.view.Gravity.TOP|android.view.Gravity.RIGHT)')
reps('new android.view.WindowManager.LayoutParams(driverBubbleDp(72),driverBubbleDp(72),android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY', 'new android.view.WindowManager.LayoutParams(driverBubbleDp(66),driverBubbleDp(66),android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY')
reps('getResources().getDisplayMetrics().widthPixels-driverBubbleDp(88)', 'getResources().getDisplayMetrics().widthPixels-driverBubbleDp(82)')
reps('driverBubbleParams.x+driverBubbleDp(36)<w/2', 'driverBubbleParams.x+driverBubbleDp(33)<w/2')
reps('w-driverBubbleDp(80)', 'w-driverBubbleDp(74)')
reps('driverBubbleDp(170))+driverBubbleDp(78)', 'driverBubbleDp(170))+driverBubbleDp(72)')
svc.write_text(u)

api.write_text(api.read_text().replace('11.4-R10.3', '11.4-R10.4'))
gradle.write_text(gradle.read_text().replace('versionCode 121', 'versionCode 122').replace("versionName '11.4-R10.3'", "versionName '11.4-R10.4'"))

print('R10.4 polish patch applied')

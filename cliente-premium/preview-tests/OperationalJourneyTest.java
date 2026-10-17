package uy.com.traslados.cliente;
import android.test.ActivityInstrumentationTestCase2;
import android.webkit.*;
import android.net.Uri;
import java.util.*;
import java.util.concurrent.*;
public class OperationalJourneyTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public OperationalJourneyTest() { super(MainActivity.class); }
    private WebView web() throws Exception {
        java.lang.reflect.Field f = MainActivity.class.getDeclaredField("web");
        f.setAccessible(true); return (WebView)f.get(getActivity());
    }
    private String js(String code) throws Exception {
        final String[] value={null}; CountDownLatch done=new CountDownLatch(1);
        WebView w=web();
        getInstrumentation().runOnMainSync(()->w.evaluateJavascript(code,result->{value[0]=result;done.countDown();}));
        assertTrue("WebView callback",done.await(20,TimeUnit.SECONDS)); return value[0];
    }
    private void loaded() throws Exception {
        getActivity();
        WebView w=web();
        // evaluateJavascript during navigation can lose its callback when the
        // initial JS context is replaced. Wait for native page completion first.
        boolean complete=false;
        for(int i=0;i<300;i++) {
            final int[] progress={0};
            getInstrumentation().runOnMainSync(()->progress[0]=w.getProgress());
            if(progress[0]==100){complete=true;break;}
            Thread.sleep(100);
        }
        assertTrue("Native page load completed",complete);
        for(int i=0;i<150;i++) {
            if("true".equals(js("Boolean(document.querySelector('#view-inicio.active [data-service=now]') && document.querySelector('.premium-next') && document.readyState==='complete')")))return;
            Thread.sleep(100);
        }
        fail("Premium PR99 page did not load in Android WebView");
    }
    public void testOperationalButtonAndRealServerValidationWithoutCreatingBooking() throws Exception {
        loaded();
        assertEquals("true",js("!PREVIEW_READ_ONLY && !document.querySelector('meta[name=traslados-preview]') && !document.querySelector('#urgent-send').disabled && document.querySelector('#urgent-send').textContent.includes('SOLICITAR VIAJE')"));
        // Deliberately invalid UUID is rejected at the FIRST server guard,
        // before identity, inserts, events, quotas or notifications are reached.
        js("document.body.dataset.transportCheck='pending';fetch(API+'/rest/v1/rpc/customer_request_pickup_v1',{method:'POST',headers:{apikey:KEY,'Content-Type':'application/json'},body:JSON.stringify({p_request_id:null,p_mode:'now',p_origin_text:'',p_origin_lat:0,p_origin_lng:0,p_destination_text:'',p_destination_lat:0,p_destination_lng:0,p_passengers:1,p_comments:''})}).then(async r=>{const v=await r.json();document.body.dataset.transportCheck=r.status===400&&v.code==='P0001'&&v.message.includes('identificador')?'validated':String(r.status)+'/'+String(v.code);}).catch(()=>document.body.dataset.transportCheck='network_error')");
        for(int i=0;i<220;i++){if(!"\"pending\"".equals(js("document.body.dataset.transportCheck")))break;Thread.sleep(100);}
        assertEquals("Actual RPC/CORS reaches first server validation, with no reservation", "\"validated\"", js("document.body.dataset.transportCheck"));
    }
    public void testRequestReceiptAndTrackingNavigationWithTestOnlyTransport() throws Exception {
        loaded();
        // Only the instrumentation process installs this fake transport. It is
        // never packaged in release. No success request reaches the real DB.
        js("window.__qaFetch=window.fetch;window.fetch=async(url,options)=>{if(String(url).includes('/rpc/customer_request_pickup_fixed_v1')){window.__qaPayload=JSON.parse(options.body);return new Response(JSON.stringify({id:'00000000-0000-4000-8000-000000000001',code:'QA_ONLY_NO_RESERVATION',status:'PENDIENTE',pickup_mode:'now'}),{status:200,headers:{'Content-Type':'application/json'}});}return window.__qaFetch(url,options);};document.querySelector('[data-service=now]').click();originPicker.persist({text:'De Madrid',lat:-34.8274694,lng:-55.969099,department:'Canelones'});destPicker.persist({text:'Carrasco Terminal',lat:-34.83696,lng:-56.01638,department:'Canelones'});document.querySelector('.premium-next').click();document.querySelector('.premium-next').click();document.querySelector('#person-name').value='Prueba Android';document.querySelector('#person-phone').value='099000000';document.querySelector('#person-name').dispatchEvent(new Event('input',{bubbles:true}));document.querySelector('#person-phone').dispatchEvent(new Event('input',{bubbles:true}));document.querySelector('.premium-next').click()");
        assertEquals("\"confirm\"",js("document.querySelector('#view-viaje').dataset.premiumStep"));
        js("latestRoad={key:routeKeyOf([record.origin,...record.stops,record.destination]),data:{available:true,source:'openrouteservice',distanceKm:6.4,durationMin:11,referenceRateUyuPerKm:40,referenceFareUyu:256}}");
        assertEquals("true",js("!document.querySelector('#urgent-send').disabled"));
        js("document.querySelector('#urgent-send').click()");
        for(int i=0;i<100;i++){if("true".equals(js("!document.querySelector('#booking-success').hidden")))break;Thread.sleep(100);}
        assertEquals("true",js("document.querySelector('#booking-success').innerText.includes('Solicitud enviada') && document.querySelector('#booking-success').innerText.includes('QA_ONLY_NO_RESERVATION')"));
        assertEquals("true",js("Boolean(window.__qaPayload.p_request_id)&&window.__qaPayload.p_mode==='now'&&window.__qaPayload.p_origin_text==='De Madrid'&&window.__qaPayload.p_customer_name==='Prueba Android'&&window.__qaPayload.p_expected_total===256&&window.__qaPayload.p_route_distance_km===6.4"));
        js("document.querySelector('#booking-success button').click();window.fetch=window.__qaFetch");
        assertEquals("true",js("document.querySelector('#view-viajes').classList.contains('active')"));
    }
    public void testDriverCommandsAndTablesStayBlocked() throws Exception {
        loaded();final WebViewClient[] client={null};WebView w=web();
        getInstrumentation().runOnMainSync(()->client[0]=w.getWebViewClient());
        for(String path:new String[]{"/rest/v1/reservations","/rest/v1/rpc/driver_set_reference_rate_v1","/rest/v1/rpc/driver_update_status","/rest/v1/rpc/unknown_command"}){
            WebResourceResponse response=client[0].shouldInterceptRequest(w,request("https://zetaudvvutlouiqxopvg.supabase.co"+path,"POST"));
            assertNotNull(path,response);assertEquals(path,503,response.getStatusCode());
        }
        assertNull(client[0].shouldInterceptRequest(w,request("https://zetaudvvutlouiqxopvg.supabase.co/rest/v1/rpc/customer_request_pickup_v1","POST")));
    }
    private WebResourceRequest request(String url,String method) {
        return new WebResourceRequest() {
            public Uri getUrl(){return Uri.parse(url);}
            public boolean isForMainFrame(){return false;}
            public boolean isRedirect(){return false;}
            public boolean hasGesture(){return false;}
            public String getMethod(){return method;}
            public Map<String,String> getRequestHeaders(){return Collections.emptyMap();}
        };
    }
}

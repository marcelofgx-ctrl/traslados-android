package uy.com.traslados.cliente;
import android.test.ActivityInstrumentationTestCase2;
import android.webkit.*;
import android.net.Uri;
import java.util.*;
import java.util.concurrent.*;
public class PremiumPreviewTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public PremiumPreviewTest() { super(MainActivity.class); }
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
    public void testPackagedWebAndNavigation() throws Exception {
        loaded();
        assertEquals("true",js("location.origin==='https://appassets.androidplatform.net'"));
        assertEquals("true",js("document.body.innerText.includes('No envía reservas')"));
        js("document.querySelector('[data-service=now]').click()");
        assertEquals("true",js("document.querySelector('#view-viaje').classList.contains('active')"));
        boolean publicState=false;
        for(int i=0;i<120;i++){
            if("true".equals(js("['busy','online'].includes(document.querySelector('#pickup-presence').dataset.presence)"))){publicState=true;break;}
            Thread.sleep(100);
        }
        assertTrue("Real public availability must reach Android WebView through GET/CORS",publicState);
        assertEquals("true",js("document.querySelector('#pickup-presence').closest('.premium-live-presence')!==null"));
        js("document.querySelector('.premium-next').click()");
        assertEquals("true",js("document.querySelector('#view-viaje').dataset.premiumStep==='route'"));
        assertEquals("true",js("!document.querySelector('.premium-error').hidden"));
        assertEquals("true",js("!navigator.serviceWorker.controller"));
    }
    public void testPublicRoadEstimateWithoutBooking() throws Exception {
        loaded();WebView w=web();final WebViewClient[] c={null};
        getInstrumentation().runOnMainSync(()->c[0]=w.getWebViewClient());
        WebResourceResponse r=c[0].shouldInterceptRequest(w,request("https://traslados-web.marcelof-gx.workers.dev/api/public/route-estimate?points=-56.016380%2C-34.836960%3B-56.158636%2C-34.924017"));
        assertNotNull(r);assertEquals("Verified road estimate should be reachable from isolated APK",200,r.getStatusCode());
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
        while((n=r.getData().read(buf))!=-1)bytes.write(buf,0,n);
        org.json.JSONObject data=new org.json.JSONObject(bytes.toString("UTF-8"));
        assertTrue(data.getBoolean("available"));assertTrue(data.getDouble("distanceKm")>0);
        assertTrue(data.getDouble("durationMin")>=0);
        assertEquals("https://appassets.androidplatform.net",r.getResponseHeaders().get("Access-Control-Allow-Origin"));
    }
    public void testAddressKeyboardKeepsSuggestionsVisible() throws Exception {
        loaded();WebView w=web();
        getInstrumentation().runOnMainSync(()->w.requestFocus());
        js("document.querySelector('[data-service=now]').click();document.querySelector('#destination-picker input[type=search]').focus()");
        getInstrumentation().runOnMainSync(()->{
            android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)getActivity().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            ime.showSoftInput(w,android.view.inputmethod.InputMethodManager.SHOW_FORCED);
        });
        js("(()=>{const el=document.querySelector('#destination-picker input[type=search]');el.focus();el.value='Punta Carretas';el.dispatchEvent(new Event('input',{bubbles:true}));})()");
        boolean visible=false;
        for(int i=0;i<150;i++){
            if("true".equals(js("(()=>{const el=document.querySelector('#destination-picker input[type=search]'),r=document.querySelector('#destination-picker .result');if(!r)return false;const a=el.getBoundingClientRect(),b=r.getBoundingClientRect();const top=visualViewport.offsetTop,bottom=top+visualViewport.height;return a.top>=top&&a.bottom<=bottom&&b.bottom<=bottom&&document.body.classList.contains('address-search-active');})()"))){visible=true;break;}
            Thread.sleep(100);
        }
        assertTrue("Focused destination and real catalog suggestions remain in the visible viewport: "+js("JSON.stringify({active:document.activeElement?.outerHTML?.slice(0,120),body:document.body.className,field:document.querySelector('#destination-picker input[type=search]').getBoundingClientRect().toJSON(),result:document.querySelector('#destination-picker .result')?.getBoundingClientRect().toJSON(),height:visualViewport.height,offset:visualViewport.offsetTop})"),visible);
        getInstrumentation().runOnMainSync(()->{
            android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)getActivity().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            ime.hideSoftInputFromWindow(w.getWindowToken(),0);
        });
    }
    public void testProductionBlockedByNativePolicy() throws Exception {
        loaded(); WebView w=web();
        final WebViewClient[] clients={null};
        getInstrumentation().runOnMainSync(()->clients[0]=w.getWebViewClient());
        WebViewClient client=clients[0];
        String[] blocked={
            "https://zetaudvvutlouiqxopvg.supabase.co/rest/v1/rpc/preview_probe",
            "https://traslados-web.marcelof-gx.workers.dev/api/preview_probe",
            "https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/",
            "https://appassets.androidplatform.net/traslados-android/web-pasajero/../secret",
            "https://example.com/"
        };
        for(String url:blocked) {
            WebResourceResponse response=client.shouldInterceptRequest(w,request(url));
            assertNotNull(url,response); assertEquals(url,503,response.getStatusCode());
        }
        assertNull(client.shouldInterceptRequest(w,request("https://zetaudvvutlouiqxopvg.supabase.co/rest/v1/rpc/public_driver_availability_v1")));
        assertNotNull(client.shouldInterceptRequest(w,request("https://zetaudvvutlouiqxopvg.supabase.co/rest/v1/rpc/public_driver_availability_v1?x=1")));
        assertNotNull(client.shouldInterceptRequest(w,request("https://zetaudvvutlouiqxopvg.supabase.co/rest/v1/rpc/customer_request_pickup_v1")));
        assertEquals("true",js("document.querySelector('#urgent-send').disabled && document.querySelector('#confirm-booking').disabled"));
        assertNotNull(client.shouldInterceptRequest(w,request("https://zetaudvvutlouiqxopvg.supabase.co/rest/v1/rpc/public_driver_availability_v1","POST")));
        assertNotNull(client.shouldInterceptRequest(w,request("https://zetaudvvutlouiqxopvg.supabase.co/rest/v1/rpc/customer_request_pickup_v1","POST")));
        assertNotNull(client.shouldInterceptRequest(w,request("https://traslados-web.marcelof-gx.workers.dev/api/public/route-estimate?points=invalid")));
        assertNotNull(client.shouldInterceptRequest(w,request("https://zetaudvvutlouiqxopvg.supabase.co/functions/v1/pickup-eta","POST")));
        WebResourceResponse root=client.shouldInterceptRequest(w,request("https://appassets.androidplatform.net/traslados-android/web-pasajero"));
        assertEquals(200,root.getStatusCode());
        assertEquals("true",js("document.querySelector('meta[http-equiv=\"Content-Security-Policy\"]').content.includes(\"connect-src 'self' https://zetaudvvutlouiqxopvg.supabase.co https://direcciones.ide.uy https://traslados-web.marcelof-gx.workers.dev;\")"));
    }
    private WebResourceRequest request(String url) { return request(url,"GET"); }
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

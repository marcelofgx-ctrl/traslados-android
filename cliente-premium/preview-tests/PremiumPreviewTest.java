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
        assertTrue("WebView callback",done.await(5,TimeUnit.SECONDS)); return value[0];
    }
    private void loaded() throws Exception {
        getActivity();
        for(int i=0;i<80;i++) {
            if("true".equals(js("Boolean(document.querySelector('#view-inicio.active [data-service=now]'))")))return;
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
        js("document.querySelector('.premium-next').click()");
        assertEquals("true",js("document.querySelector('#view-viaje').dataset.premiumStep==='route'"));
        assertEquals("true",js("!document.querySelector('.premium-error').hidden"));
        assertEquals("true",js("!navigator.serviceWorker.controller"));
    }
    public void testProductionBlockedByNativePolicy() throws Exception {
        loaded(); WebView w=web();
        String[] blocked={
            "https://zetaudvvutlouiqxopvg.supabase.co/rest/v1/rpc/preview_probe",
            "https://traslados-web.marcelof-gx.workers.dev/api/preview_probe",
            "https://marcelofgx-ctrl.github.io/traslados-android/web-pasajero/",
            "https://appassets.androidplatform.net/traslados-android/web-pasajero/../secret",
            "https://example.com/"
        };
        for(String url:blocked) {
            WebResourceResponse response=w.getWebViewClient().shouldInterceptRequest(w,request(url));
            assertNotNull(url,response); assertEquals(url,503,response.getStatusCode());
        }
        WebResourceResponse root=w.getWebViewClient().shouldInterceptRequest(w,request("https://appassets.androidplatform.net/traslados-android/web-pasajero"));
        assertEquals(200,root.getStatusCode());
        assertEquals("true",js("document.querySelector('meta[http-equiv=\"Content-Security-Policy\"]').content.includes(\"connect-src 'self';\")"));
    }
    private WebResourceRequest request(String url) {
        return new WebResourceRequest() {
            public Uri getUrl(){return Uri.parse(url);}
            public boolean isForMainFrame(){return false;}
            public boolean isRedirect(){return false;}
            public boolean hasGesture(){return false;}
            public String getMethod(){return "GET";}
            public Map<String,String> getRequestHeaders(){return Collections.emptyMap();}
        };
    }
}
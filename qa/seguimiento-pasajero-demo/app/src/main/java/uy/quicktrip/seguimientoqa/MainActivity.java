package uy.quicktrip.seguimientoqa;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
public final class MainActivity extends Activity {
private WebView view;
@Override public void onCreate(Bundle state) {
super.onCreate(state);
getWindow().setStatusBarColor(Color.rgb(6,44,53));
getWindow().setNavigationBarColor(Color.rgb(6,44,53));
view=new WebView(this);
view.setBackgroundColor(Color.rgb(6,44,53));
WebSettings w=view.getSettings();
w.setJavaScriptEnabled(true);
w.setDomStorageEnabled(false);
w.setAllowFileAccess(true);
w.setAllowFileAccessFromFileURLs(false);
w.setAllowUniversalAccessFromFileURLs(false);
w.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
view.setWebViewClient(new WebViewClient());
setContentView(view);
view.loadUrl("file:///android_asset/tracking.html");
}
@Override public void onDestroy(){if(view!=null){view.destroy();view=null;}super.onDestroy();}
}
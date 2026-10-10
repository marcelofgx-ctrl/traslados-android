package uy.com.traslados.cliente;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Traslados Cliente Premium: una sola interfaz web oficial para web y Android.
 * Nunca se cargan versiones HTML duplicadas dentro del APK.
 * No hay JS bridge, ni secretos de servidor ni GPS del conductor.
 */
public final class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 201;
    private static final int FILE_REQUEST = 202;
    private static final String OFFICIAL_HOST = "traslados-web.marcelof-gx.workers.dev";
    private static final int PETROLEO = Color.rgb(16, 43, 49);
    private static final int GOLD = Color.rgb(230, 199, 134);
    private WebView web;
    private View unavailable;
    private boolean loadFailed=false;
    private GeolocationPermissions.Callback pendingLocationCallback;
    private String pendingLocationOrigin;
    private ValueCallback<Uri[]> pendingFileCallback;

    private boolean official(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
            && OFFICIAL_HOST.equalsIgnoreCase(uri.getHost());
    }
    private GradientDrawable background(int color, int borderColor, int radiusDp) {
        float density=getResources().getDisplayMetrics().density;
        GradientDrawable out=new GradientDrawable();
        out.setColor(color);
        out.setCornerRadius(radiusDp*density);
        out.setStroke((int)(1*density),borderColor);
        return out;
    }
    private int dp(int n) {
        return (int)(n*getResources().getDisplayMetrics().density+0.5f);
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(PETROLEO);
        getWindow().setNavigationBarColor(PETROLEO);
        getWindow().getDecorView().setSystemUiVisibility(0);
        FrameLayout shell=new FrameLayout(this);
        shell.setBackgroundColor(PETROLEO);
        shell.setFitsSystemWindows(true);

        web=new WebView(this);
        web.setBackgroundColor(PETROLEO);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        WebSettings cfg=web.getSettings();
        cfg.setJavaScriptEnabled(true);
        cfg.setDomStorageEnabled(true);
        cfg.setDatabaseEnabled(true);
        cfg.setGeolocationEnabled(true);
        cfg.setAllowFileAccess(false);
        cfg.setAllowContentAccess(false);
        cfg.setAllowFileAccessFromFileURLs(false);
        cfg.setAllowUniversalAccessFromFileURLs(false);
        cfg.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        cfg.setJavaScriptCanOpenWindowsAutomatically(false);
        cfg.setSupportMultipleWindows(true);
        cfg.setBuiltInZoomControls(false);
        cfg.setDisplayZoomControls(false);
        cfg.setMediaPlaybackRequiresUserGesture(true);
        if(Build.VERSION.SDK_INT>=26)cfg.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        shell.addView(web,new FrameLayout.LayoutParams(-1,-1));

        unavailable=makeOfflineScreen();
        unavailable.setVisibility(View.GONE);
        shell.addView(unavailable,new FrameLayout.LayoutParams(-1,-1));
        setContentView(shell);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url=request.getUrl();
                if(official(url))return false;
                return openExternal(url);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url) {
                Uri uri=Uri.parse(url);
                return !official(uri)&&openExternal(uri);
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon) {
                loadFailed=false;
            }
            @Override public void onPageFinished(WebView view,String url) {
                if(official(Uri.parse(url))&&!loadFailed)unavailable.setVisibility(View.GONE);
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError error) {
                if(req.isForMainFrame()) {
                    loadFailed=true;
                    unavailable.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest req,android.webkit.WebResourceResponse response) {
                if(req.isForMainFrame()&&response.getStatusCode()>=500) {
                    loadFailed=true;
                    unavailable.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error) {
                // No eludir fallos TLS, ni siquiera para pruebas.
                handler.cancel();
                unavailable.setVisibility(View.VISIBLE);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback) {
                if(!official(Uri.parse(origin))) {
                    callback.invoke(origin,false,false);
                    return;
                }
                if(Build.VERSION.SDK_INT>=23
                   && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
                   && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
                    pendingLocationOrigin=origin;
                    pendingLocationCallback=callback;
                    requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    },LOCATION_REQUEST);
                }else{
                    callback.invoke(origin,true,false);
                }
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                // Los permisos especiales de cámara/micrófono NO se conceden automáticamente.
                request.deny();
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,FileChooserParams params) {
                if(pendingFileCallback!=null)pendingFileCallback.onReceiveValue(null);
                pendingFileCallback=callback;
                try{
                    Intent choose=params.createIntent();
                    startActivityForResult(choose,FILE_REQUEST);
                    return true;
                }catch(Exception e){
                    pendingFileCallback=null;
                    return false;
                }
            }
            @Override public boolean onCreateWindow(WebView parent,boolean isDialog,boolean isUserGesture,android.os.Message resultMsg) {
                if(!isUserGesture)return false;
                WebView child=new WebView(MainActivity.this);
                child.setWebViewClient(new WebViewClient(){
                    @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
                        Uri next=req.getUrl();
                        if(official(next))web.loadUrl(next.toString());
                        else openExternal(next);
                        v.destroy();
                        return true;
                    }
                });
                ((WebView.WebViewTransport)resultMsg.obj).setWebView(child);
                resultMsg.sendToTarget();
                return true;
            }
        });

        web.setDownloadListener((url,userAgent,contentDisposition,mimeType,length)->{
            // Los enlaces de descarga se entregan a Android, sin conservar datos privados.
            Uri uri=Uri.parse(url);
            if("https".equalsIgnoreCase(uri.getScheme()))openExternal(uri);
            else Toast.makeText(this,"Abrí la descarga en Chrome",Toast.LENGTH_SHORT).show();
        });

        if(savedInstanceState!=null)web.restoreState(savedInstanceState);
        else web.loadUrl(BuildConfig.OFFICIAL_WEB_URL);
    }

    private boolean openExternal(Uri uri) {
        if(uri==null)return true;
        String scheme=uri.getScheme();
        if(scheme==null)return true;
        if(!("https".equalsIgnoreCase(scheme)
           ||"geo".equalsIgnoreCase(scheme)
           ||"tel".equalsIgnoreCase(scheme)
           ||"mailto".equalsIgnoreCase(scheme)
           ||"whatsapp".equalsIgnoreCase(scheme)))return true;
        try{
            Intent open=new Intent(Intent.ACTION_VIEW,uri);
            startActivity(open);
        }catch(ActivityNotFoundException ignored){
            Toast.makeText(this,"No hay una aplicación para abrir este enlace",Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private View makeOfflineScreen(){
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(30),dp(30),dp(30),dp(30));
        panel.setBackgroundColor(PETROLEO);

        ImageView mark=new ImageView(this);
        mark.setImageResource(R.drawable.traslados_brand);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        panel.addView(mark,new LinearLayout.LayoutParams(-1,dp(90)));

        TextView title=new TextView(this);
        title.setText("TRASLADOS");
        title.setTypeface(null,Typeface.BOLD);
        title.setLetterSpacing(.20f);
        title.setTextSize(24);
        title.setTextColor(Color.rgb(247,233,210));
        title.setGravity(Gravity.CENTER);
        panel.addView(title,new LinearLayout.LayoutParams(-1,dp(50)));

        TextView message=new TextView(this);
        message.setText("Sin conexión por el momento.\nTus reservas necesitan Internet; no enviamos solicitudes sin conexión.");
        message.setTextColor(Color.rgb(195,211,203));
        message.setTextSize(14);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(dp(5),1f);
        LinearLayout.LayoutParams msg=new LinearLayout.LayoutParams(-1,-2);
        msg.setMargins(0,dp(22),0,dp(32));
        panel.addView(message,msg);

        Button retry=new Button(this);
        retry.setAllCaps(false);
        retry.setText("Volver a intentar");
        retry.setTextColor(PETROLEO);
        retry.setTextSize(15);
        retry.setBackground(background(GOLD,GOLD,15));
        retry.setOnClickListener(view->{
            unavailable.setVisibility(View.GONE);
            web.loadUrl(BuildConfig.OFFICIAL_WEB_URL);
        });
        panel.addView(retry,new LinearLayout.LayoutParams(-1,dp(54)));
        return panel;
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==LOCATION_REQUEST&&pendingLocationCallback!=null) {
            boolean allowed=false;
            for(int result:results)if(result==PackageManager.PERMISSION_GRANTED)allowed=true;
            pendingLocationCallback.invoke(pendingLocationOrigin,allowed,false);
            pendingLocationCallback=null;
            pendingLocationOrigin=null;
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==FILE_REQUEST&&pendingFileCallback!=null) {
            pendingFileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));
            pendingFileCallback=null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        web.saveState(state);
        super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() {
        if(web.canGoBack())web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if(pendingLocationCallback!=null) {
            pendingLocationCallback.invoke(pendingLocationOrigin,false,false);
            pendingLocationCallback=null;
        }
        if(pendingFileCallback!=null) {
            pendingFileCallback.onReceiveValue(null);
            pendingFileCallback=null;
        }
        web.destroy();
        super.onDestroy();
    }
}

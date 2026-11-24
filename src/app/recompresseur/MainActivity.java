package app.recompresseur;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
  static final int PICK = 1, CREATE = 2;
  WebView web;
  ValueCallback<Uri[]> pickCb;
  byte[] pendingSave;
  Uri pendingShare;
  boolean ready = false;
  int inTop, inBottom, inKb;
  /** Set by the page: "fr" or "en" (the in-app language setting wins over the phone language). */
  volatile String lang = java.util.Locale.getDefault().getLanguage().equals("fr") ? "fr" : "en";

  /** Edge to edge: the app draws under the system bars, the page handles the insets. */
  void edgeToEdge() {
    Window w = getWindow();
    w.setStatusBarColor(Color.TRANSPARENT);
    w.setNavigationBarColor(Color.TRANSPARENT);
    int f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    w.getDecorView().setSystemUiVisibility(f);
    try {
      if (Build.VERSION.SDK_INT >= 29) {
        w.getClass().getMethod("setNavigationBarContrastEnforced", boolean.class).invoke(w, false);
        w.getClass().getMethod("setStatusBarContrastEnforced", boolean.class).invoke(w, false);
      }
    } catch (Exception ignored) { }
    try {
      if (Build.VERSION.SDK_INT >= 28) {
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.getClass().getField("layoutInDisplayCutoutMode").setInt(lp, 1);
        w.setAttributes(lp);
      }
    } catch (Exception ignored) { }
    int bg = Color.rgb(14, 14, 15);
    w.getDecorView().setBackgroundColor(bg);
    if (web != null) web.setBackgroundColor(bg);
  }

  void sendInsets() {
    if (ready) web.evaluateJavascript("window.setInsets&&setInsets(" + inTop + "," + inBottom + "," + inKb + ")", null);
  }

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    web = new WebView(this);
    edgeToEdge();
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    s.setAllowContentAccess(true);
    s.setBlockNetworkLoads(true);
    s.setTextZoom(100);
    web.setOverScrollMode(View.OVER_SCROLL_NEVER);
    web.addJavascriptInterface(new Bridge(), "Android");
    web.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return true; }
      @Override public void onPageFinished(WebView v, String url) {
        ready = true; sendInsets();
        if (pendingShare != null) { loadShared(pendingShare); pendingShare = null; }
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
        if (pickCb != null) pickCb.onReceiveValue(null);
        pickCb = cb;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try { startActivityForResult(Intent.createChooser(i, "fr".equals(lang) ? "Choisir une image" : "Choose an image"), PICK); }
        catch (Exception e) { pickCb = null; return false; }
        return true;
      }
    });
    web.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
      @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
        float d = getResources().getDisplayMetrics().density;
        inTop = Math.round(in.getSystemWindowInsetTop() / d);
        inBottom = Math.round(in.getStableInsetBottom() / d);
        inKb = Math.max(0, Math.round((in.getSystemWindowInsetBottom() - in.getStableInsetBottom()) / d));
        sendInsets();
        return in;
      }
    });
    setContentView(web);
    web.loadUrl("file:///android_asset/index.html");
    handle(getIntent());
  }

  @Override public void onConfigurationChanged(Configuration c) {
    super.onConfigurationChanged(c);
    edgeToEdge();
      }

  @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); handle(i); }

  void handle(Intent i) {
    if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return;
    Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
    if (u == null) return;
    if (ready) loadShared(u); else pendingShare = u;
  }

  void loadShared(Uri u) {
    try (InputStream in = getContentResolver().openInputStream(u)) {
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      byte[] buf = new byte[65536]; int n;
      while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
      web.evaluateJavascript("window.loadB64&&loadB64('" + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP) + "')", null);
    } catch (Exception e) { say("share"); }
  }

  @Override protected void onActivityResult(int req, int res, Intent data) {
    if (req == PICK) {
      Uri[] r = null;
      if (res == RESULT_OK && data != null && data.getData() != null) r = new Uri[]{data.getData()};
      if (pickCb != null) pickCb.onReceiveValue(r);
      pickCb = null;
    } else if (req == CREATE) {
      if (res == RESULT_OK && data != null && data.getData() != null && pendingSave != null) {
        try (OutputStream o = getContentResolver().openOutputStream(data.getData())) { o.write(pendingSave); say("saved"); }
        catch (Exception e) { say("fail"); }
      }
      pendingSave = null;
    }
  }

  @Override public void onBackPressed() {
    web.evaluateJavascript("window.onBack?onBack():false", new ValueCallback<String>() {
      @Override public void onReceiveValue(String v) { if (!"true".equals(v)) finish(); }
    });
  }

  /** Shows a message from the page's string table (see window.sayKey in index.html). */
  void say(final String key) {
    runOnUiThread(new Runnable() { public void run() {
      web.evaluateJavascript("window.sayKey&&sayKey(" + org.json.JSONObject.quote(key) + ")", null);
    }});
  }

  class Bridge {
    @JavascriptInterface public void setLang(String l) { lang = "fr".equals(l) ? "fr" : "en"; }

    @JavascriptInterface public void save(String b64, final String name) {
      final byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
      runOnUiThread(new Runnable() { public void run() {
        if (Build.VERSION.SDK_INT >= 29) {
          try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            cv.put("relative_path", "Pictures/Recompresseur");
            Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(bytes); }
            say("pics");
            return;
          } catch (Exception e) { /* fall back to the file picker */ }
        }
        pendingSave = bytes;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/jpeg");
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, CREATE);
      }});
    }
  }
}

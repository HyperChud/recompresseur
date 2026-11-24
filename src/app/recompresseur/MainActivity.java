package app.recompresseur;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
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

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(Color.parseColor("#1b1916"));
    getWindow().setNavigationBarColor(Color.parseColor("#1f1d1a"));
    web = new WebView(this);
    web.setBackgroundColor(Color.parseColor("#121110"));
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(true);
    s.setBlockNetworkLoads(true);
    web.addJavascriptInterface(new Bridge(), "Android");
    web.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return true; }
      @Override public void onPageFinished(WebView v, String url) { ready = true; if (pendingShare != null) { loadShared(pendingShare); pendingShare = null; } }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
        if (pickCb != null) pickCb.onReceiveValue(null);
        pickCb = cb;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try { startActivityForResult(Intent.createChooser(i, "Choisir une image"), PICK); }
        catch (Exception e) { pickCb = null; return false; }
        return true;
      }
    });
    setContentView(web);
    web.loadUrl("file:///android_asset/index.html");
    handle(getIntent());
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
      String b64 = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
      web.evaluateJavascript("window.loadB64 && loadB64('" + b64 + "')", null);
    } catch (Exception e) { toast("Impossible d'ouvrir l'image partagée"); }
  }

  @Override protected void onActivityResult(int req, int res, Intent data) {
    if (req == PICK) {
      Uri[] r = null;
      if (res == RESULT_OK && data != null && data.getData() != null) r = new Uri[]{data.getData()};
      if (pickCb != null) pickCb.onReceiveValue(r);
      pickCb = null;
    } else if (req == CREATE) {
      if (res == RESULT_OK && data != null && data.getData() != null && pendingSave != null) {
        try (OutputStream o = getContentResolver().openOutputStream(data.getData())) { o.write(pendingSave); toast("Image enregistrée"); }
        catch (Exception e) { toast("Échec de l'enregistrement"); }
      }
      pendingSave = null;
    }
  }

  @Override public void onBackPressed() { finish(); }

  void toast(final String m) { runOnUiThread(new Runnable() { public void run() { Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show(); } }); }

  class Bridge {
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
            toast("Enregistrée dans Images/Recompresseur");
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

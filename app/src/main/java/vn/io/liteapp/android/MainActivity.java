package vn.io.liteapp.android;

import android.app.Activity;
import android.app.DownloadManager;
import android.print.PrintManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String START_URL = "https://liteapp.io.vn/dang-nhap?app=android";
    private static final int FILE_CHOOSER_REQUEST = 1201;
    private static final String APP_VERSION = "1.0.0";

    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private boolean posMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webView);
        progress = findViewById(R.id.progress);

        posMode = detectPosDevice();
        if (posMode) enableImmersivePosMode();

        configureWebView();
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(START_URL + (posMode ? "&mode=pos" : "&mode=phone"));
        }
    }

    private boolean detectPosDevice() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.US);
        String model = Build.MODEL == null ? "" : Build.MODEL.toLowerCase(Locale.US);
        int smallest = getResources().getConfiguration().smallestScreenWidthDp;
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        return manufacturer.contains("sunmi") || model.contains("t1") || model.contains("sapo") || (smallest >= 600 && landscape);
    }

    private void enableImmersivePosMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE); // live SaaS: always fetch newest CSS/JS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        s.setUserAgentString(s.getUserAgentString()
                + " LiteAppAndroid/" + APP_VERSION
                + (posMode ? " LiteAppPOS/1" : " LiteAppPhone/1"));

        webView.addJavascriptInterface(new LiteAppBridge(), "LiteAppAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                injectRuntimeMode();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleExternalUri(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleExternalUri(Uri.parse(url));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = filePathCallback;
                try {
                    startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Không mở được trình chọn ảnh/tệp", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimeType);
                    request.addRequestHeader("User-Agent", userAgent);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "liteapp-download");
                    ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                    Toast.makeText(MainActivity.this, "Đang tải tệp…", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Không tải được tệp", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean handleExternalUri(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
        if (scheme.equals("http") || scheme.equals("https")) {
            if (uri.getHost() != null && uri.getHost().endsWith("liteapp.io.vn")) return false;
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
            return true;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void injectRuntimeMode() {
        final String mode = posMode ? "pos" : "phone";
        final String js = "(function(){"
                + "window.LITEAPP_ANDROID={version:'" + APP_VERSION + "',mode:'" + mode + "'};"
                + "var root=document.documentElement;"
                + (posMode
                    ? "root.classList.add('liteapp-pos-lowres','liteapp-android-pos');"
                    : "root.classList.remove('liteapp-pos-lowres','liteapp-android-pos');root.classList.add('liteapp-android-phone');")
                + "function keep(){"
                + (posMode ? "root.classList.add('liteapp-pos-lowres','liteapp-android-pos');" : "")
                + "window.dispatchEvent(new Event('resize'));}"
                + "keep();"
                + "new MutationObserver(keep).observe(root,{attributes:true,attributeFilter:['class']});"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (posMode) enableImmersivePosMode();
    }

    public class LiteAppBridge {
        @JavascriptInterface
        public String getDeviceMode() { return posMode ? "pos" : "phone"; }

        @JavascriptInterface
        public String getAppVersion() { return APP_VERSION; }

        @JavascriptInterface
        public void printCurrentPage() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    String jobName = "LiteApp Bill";
                    printManager.print(jobName, webView.createPrintDocumentAdapter(jobName), null);
                }
            });
        }

        @JavascriptInterface
        public void openAndroidSettings() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_SETTINGS)));
        }
    }
}

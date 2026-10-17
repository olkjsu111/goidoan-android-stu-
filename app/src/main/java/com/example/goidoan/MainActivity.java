package com.example.goidoan;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.example.goidoan.model.MenuItem;
import com.example.goidoan.model.OrderItem;
import com.example.goidoan.model.TableItem;
import com.example.goidoan.ui.PaymentDialog;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GoiMonPOS";
    private static final String PREFS_NAME = "goimon_prefs";
    private static final String KEY_SERVER_URL = "server_url";

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout layoutError;
    private TextView tvCurrentServer;
    private Button btnRetry;
    private Button btnChangeServer;

    private SharedPreferences preferences;
    private String currentServerUrl;
    private boolean isPageError = false;
    private long backPressedTime = 0;
    private Toast exitToast;

    // File Chooser for Image/Camera upload in WebView
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;

    // Runtime Permissions Launcher
    private ActivityResultLauncher<String[]> permissionLauncher;
    private PermissionRequest pendingWebPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install modern splash screen
        SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Notification Channel for Excel exports & downloads
        ExcelExportHelper.initNotificationChannel(this);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentServerUrl = preferences.getString(KEY_SERVER_URL, getString(R.string.default_server_url));

        initViews();
        setupLaunchers();
        setupBackNavigation();
        setupWebView();
        checkAndRequestAppPermissions();

        loadServerUrl(currentServerUrl);
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        layoutError = findViewById(R.id.layoutError);
        tvCurrentServer = findViewById(R.id.tvCurrentServer);
        btnRetry = findViewById(R.id.btnRetry);
        btnChangeServer = findViewById(R.id.btnChangeServer);

        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.amber_500));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.card_dark));
        swipeRefreshLayout.setOnRefreshListener(() -> {
            isPageError = false;
            webView.reload();
        });

        btnRetry.setOnClickListener(v -> {
            hideErrorView();
            loadServerUrl(currentServerUrl);
        });

        btnChangeServer.setOnClickListener(v -> showChangeServerDialog());
    }

    private void setupLaunchers() {
        // File Chooser launcher for HTML <input type="file">
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (filePathCallback == null) return;
                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        if (data.getData() != null) {
                            results = new Uri[]{data.getData()};
                        } else if (data.getClipData() != null) {
                            int count = data.getClipData().getItemCount();
                            results = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                results[i] = data.getClipData().getItemAt(i).getUri();
                            }
                        }
                    }
                    filePathCallback.onReceiveValue(results);
                    filePathCallback = null;
                }
        );

        // Permission launcher for Camera & Microphone
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                resultMap -> {
                    if (pendingWebPermissionRequest != null) {
                        List<String> grantedResources = new ArrayList<>();
                        boolean hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                        boolean hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

                        for (String res : pendingWebPermissionRequest.getResources()) {
                            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res) && hasCamera) {
                                grantedResources.add(res);
                            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res) && hasAudio) {
                                grantedResources.add(res);
                            }
                        }

                        if (!grantedResources.isEmpty()) {
                            pendingWebPermissionRequest.grant(grantedResources.toArray(new String[0]));
                        } else {
                            pendingWebPermissionRequest.deny();
                        }
                        pendingWebPermissionRequest = null;
                    }
                }
        );
    }

    private void checkAndRequestAppPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
        }
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutError.getVisibility() == View.VISIBLE) {
                    finish();
                    return;
                }

                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - backPressedTime < 2000) {
                        if (exitToast != null) {
                            exitToast.cancel();
                        }
                        finish();
                    } else {
                        backPressedTime = currentTime;
                        exitToast = Toast.makeText(MainActivity.this, R.string.press_again_to_exit, Toast.LENGTH_SHORT);
                        exitToast.show();
                    }
                }
            }
        });
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // JavaScript Bridge for Native Integration
        webView.addJavascriptInterface(new AndroidBridge(this), "Android");

        // Download Listener to capture Excel & File Downloads
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                Log.d(TAG, "Download requested: " + url + " (" + mimetype + ")");
                if (url == null || url.isEmpty()) return;

                if (url.startsWith("blob:")) {
                    // Blob URLs cannot be directly downloaded by DownloadManager; fetch via JS FileReader
                    String safeUrl = url.replace("'", "\\'");
                    String guessFilename = URLUtil.guessFileName(url, contentDisposition, mimetype);
                    String script = "if (window.__androidFetchAndDownloadBlob) { window.__androidFetchAndDownloadBlob('"
                            + safeUrl + "', '" + guessFilename + "', '" + mimetype + "'); }";
                    webView.evaluateJavascript(script, null);
                } else if (url.startsWith("data:")) {
                    String guessFilename = URLUtil.guessFileName(url, contentDisposition, mimetype);
                    ExcelExportHelper.saveAndOpenFile(MainActivity.this, url, guessFilename, mimetype);
                } else {
                    // Standard HTTP / HTTPS URL
                    ExcelExportHelper.downloadHttpFile(MainActivity.this, url, userAgent, contentDisposition, mimetype);
                }
            }
        });

        // WebChromeClient (Permissions, File Chooser, Progress, Console)
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean hasCamera = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                    boolean hasAudio = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

                    List<String> needAndroidPerms = new ArrayList<>();
                    if (!hasCamera) needAndroidPerms.add(Manifest.permission.CAMERA);
                    if (!hasAudio) needAndroidPerms.add(Manifest.permission.RECORD_AUDIO);

                    if (needAndroidPerms.isEmpty()) {
                        request.grant(request.getResources());
                    } else {
                        pendingWebPermissionRequest = request;
                        permissionLauncher.launch(needAndroidPerms.toArray(new String[0]));
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    fileChooserLauncher.launch(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Cannot launch file chooser: " + e.getMessage());
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
                // Inject Excel export hooks progressively during page rendering
                if (newProgress >= 50) {
                    view.evaluateJavascript(ExcelExportHelper.getInjectedDownloadScript(), null);
                    view.evaluateJavascript(QrPaymentHelper.getInjectedQrScript(), null);
                }
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG + "-Web", consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of "
                        + consoleMessage.sourceId());
                return true;
            }
        });

        // WebViewClient (URL routing, errors, loading state)
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();

                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false; // Load inside WebView
                }

                // Handle external apps (tel, mailto, vietqr, intent, momo, zalopay...)
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    Log.w(TAG, "No app to handle scheme: " + uri, e);
                    return true;
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                isPageError = false;
                progressBar.setVisibility(View.VISIBLE);
                // Pre-inject hook for any early scripts
                view.evaluateJavascript(ExcelExportHelper.getInjectedDownloadScript(), null);
                view.evaluateJavascript(QrPaymentHelper.getInjectedQrScript(), null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                if (!isPageError) {
                    hideErrorView();
                }
                // Inject Excel export & QR interceptors on finished page
                view.evaluateJavascript(ExcelExportHelper.getInjectedDownloadScript(), null);
                view.evaluateJavascript(QrPaymentHelper.getInjectedQrScript(), null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    isPageError = true;
                    progressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    showErrorView(error.getDescription().toString());
                }
            }
        });
    }

    private void loadServerUrl(String url) {
        currentServerUrl = url;
        tvCurrentServer.setText(url);
        hideErrorView();
        progressBar.setVisibility(View.VISIBLE);
        webView.loadUrl(url);
    }

    private void showErrorView(String errorDescription) {
        layoutError.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        tvCurrentServer.setText(currentServerUrl);
        Log.w(TAG, "Page load error: " + errorDescription + " for url: " + currentServerUrl);
    }

    private void hideErrorView() {
        layoutError.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void showChangeServerDialog() {
        final String[] options = {
                "🌐 Máy chủ Cloud (Vercel): " + getString(R.string.default_server_url),
                "💻 Máy ảo Android Emulator: " + getString(R.string.local_emulator_url),
                "📱 Mạng LAN / Wi-Fi: " + getString(R.string.local_wifi_url),
                "✏️ Nhập địa chỉ IP tùy chỉnh..."
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.btn_change_server);
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    setAndSaveServerUrl(getString(R.string.default_server_url));
                    break;
                case 1:
                    setAndSaveServerUrl(getString(R.string.local_emulator_url));
                    break;
                case 2:
                    setAndSaveServerUrl(getString(R.string.local_wifi_url));
                    break;
                case 3:
                    showCustomUrlInputDialog();
                    break;
            }
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    private void showCustomUrlInputDialog() {
        final EditText input = new EditText(this);
        input.setHint("http://192.168.1.100:3000");
        input.setText(currentServerUrl);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Nhập URL máy chủ POS")
                .setMessage("Ví dụ: http://192.168.1.x:3000 hoặc https://your-pos.domain.com")
                .setView(input)
                .setPositiveButton("Lưu & Kết nối", (dialog, which) -> {
                    String newUrl = input.getText().toString().trim();
                    if (!newUrl.isEmpty()) {
                        if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                            newUrl = "http://" + newUrl;
                        }
                        setAndSaveServerUrl(newUrl);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void setAndSaveServerUrl(String newUrl) {
        currentServerUrl = newUrl;
        preferences.edit().putString(KEY_SERVER_URL, newUrl).apply();
        Toast.makeText(this, "Đã đổi máy chủ: " + newUrl, Toast.LENGTH_SHORT).show();
        loadServerUrl(newUrl);
    }

    // JavaScript Interface to interact with Native Android
    public static class AndroidBridge {
        private final MainActivity activity;

        public AndroidBridge(MainActivity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public void showToast(String message) {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            );
        }

        @JavascriptInterface
        public void vibrate(long milliseconds) {
            Vibrator vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(milliseconds);
                }
            }
        }

        @JavascriptInterface
        public String getPlatform() {
            return "android-native";
        }

        @JavascriptInterface
        public String getQrBase64() {
            return QrPaymentHelper.getQrBase64(activity);
        }

        @JavascriptInterface
        public void exportExcel(String base64Data, String filename) {
            ExcelExportHelper.saveAndOpenFile(activity, base64Data, filename, ExcelExportHelper.EXCEL_MIME);
        }

        @JavascriptInterface
        public void downloadBase64File(String base64Data, String filename, String mimeType) {
            ExcelExportHelper.saveAndOpenFile(activity, base64Data, filename, mimeType);
        }

        @JavascriptInterface
        public void downloadUrl(String url, String filename, String mimeType) {
            ExcelExportHelper.downloadHttpFile(activity, url, null, null, mimeType);
        }

        @JavascriptInterface
        public void openFile(String filename) {
            File file = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename);
            if (file.exists()) {
                ExcelExportHelper.openFile(activity, file, ExcelExportHelper.EXCEL_MIME);
            } else {
                showToast("Không tìm thấy file: " + filename);
            }
        }

        @JavascriptInterface
        public void shareFile(String filename) {
            File file = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename);
            if (file.exists()) {
                ExcelExportHelper.shareFile(activity, file, ExcelExportHelper.EXCEL_MIME);
            } else {
                showToast("Không tìm thấy file: " + filename);
            }
        }

        @JavascriptInterface
        public void openPaymentDialog(String tableId, String tableName, long totalAmount, String itemsJson) {
            new Handler(Looper.getMainLooper()).post(() -> {
                TableItem table = new TableItem(tableId != null && !tableId.isEmpty() ? tableId : "T01", 
                        tableName != null && !tableName.isEmpty() ? tableName : "Bàn");

                List<OrderItem> items = new ArrayList<>();
                if (itemsJson != null && !itemsJson.isEmpty()) {
                    try {
                        JSONArray array = new JSONArray(itemsJson);
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            String name = obj.optString("name", "Món");
                            long price = obj.optLong("price", 0);
                            int qty = obj.optInt("quantity", 1);
                            String note = obj.optString("note", "");
                            MenuItem m = new MenuItem("M" + i, name, price);
                            items.add(new OrderItem(m, qty, note));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing itemsJson: " + e.getMessage());
                    }
                }

                if (items.isEmpty() && totalAmount > 0) {
                    MenuItem m = new MenuItem("M01", "Hóa đơn " + table.getName(), totalAmount);
                    items.add(new OrderItem(m, 1, ""));
                }

                table.setOrderedItems(items);
                activity.openPaymentDialog(table);
            });
        }
    }

    public void openPaymentDialog(TableItem table) {
        runOnUiThread(() -> {
            PaymentDialog dialog = new PaymentDialog(this, table, (completedTable, paymentMethod) -> {
                String script = "if (window.onNativePaymentSuccess) { window.onNativePaymentSuccess('" 
                        + completedTable.getId() + "', '" + paymentMethod + "'); }";
                webView.evaluateJavascript(script, null);
            });
            dialog.show();
        });
    }
}
package com.example.goidoan;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExcelExportHelper {

    private static final String TAG = "ExcelExportHelper";
    private static final String CHANNEL_ID = "excel_export_channel";
    private static final String CHANNEL_NAME = "Xuất Báo Cáo Excel & Tải Về";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static final String EXCEL_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public static void initNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Thông báo khi xuất file báo cáo Excel hoặc tải tài liệu POS");
            channel.enableVibration(true);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void saveAndOpenFile(Activity activity, String base64OrDataUrl, String filename, String mimeType) {
        if (activity == null || base64OrDataUrl == null || base64OrDataUrl.isEmpty()) {
            Log.e(TAG, "Invalid activity or data URL for export");
            return;
        }

        MAIN_HANDLER.post(() ->
                Toast.makeText(activity, "Đang xử lý xuất file Excel...", Toast.LENGTH_SHORT).show()
        );

        EXECUTOR.execute(() -> {
            try {
                // 1. Clean Base64 payload
                String cleanBase64 = base64OrDataUrl;
                if (cleanBase64.contains(",")) {
                    cleanBase64 = cleanBase64.substring(cleanBase64.indexOf(",") + 1);
                }
                cleanBase64 = cleanBase64.replaceAll("\\s+", "");

                byte[] fileBytes = Base64.decode(cleanBase64, Base64.DEFAULT);
                if (fileBytes == null || fileBytes.length == 0) {
                    throw new IllegalArgumentException("Dữ liệu file Base64 rỗng");
                }

                // 2. Format filename
                String safeName = sanitizeFilename(filename, mimeType);

                // 3. Save to app-specific external files dir (reliable for FileProvider)
                File appDownloadsDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (appDownloadsDir == null) {
                    appDownloadsDir = activity.getFilesDir();
                }
                if (!appDownloadsDir.exists()) {
                    appDownloadsDir.mkdirs();
                }

                File localFile = new File(appDownloadsDir, safeName);
                try (FileOutputStream fos = new FileOutputStream(localFile)) {
                    fos.write(fileBytes);
                    fos.flush();
                }

                // 4. Also copy to device public Downloads directory so user sees it in Files app
                saveToPublicDownloads(activity, fileBytes, safeName, mimeType);

                Log.i(TAG, "File Excel saved successfully: " + localFile.getAbsolutePath() + " (" + fileBytes.length + " bytes)");

                // 5. Post notification and interactive dialog
                MAIN_HANDLER.post(() -> {
                    showSuccessNotification(activity, localFile, safeName, mimeType);
                    showSuccessDialog(activity, localFile, safeName, mimeType);
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to save Excel file: " + e.getMessage(), e);
                MAIN_HANDLER.post(() ->
                        Toast.makeText(activity, "Lỗi xuất file Excel: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    private static void saveToPublicDownloads(Context context, byte[] bytes, String filename, String mimeType) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ MediaStore.Downloads (No WRITE_EXTERNAL_STORAGE required)
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GoiMonPOS");
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                        if (out != null) {
                            out.write(bytes);
                            out.flush();
                        }
                    }
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    context.getContentResolver().update(uri, values, null, null);
                }
            } else {
                // Android 9 and lower: Public Downloads folder
                File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (publicDir != null && (publicDir.exists() || publicDir.mkdirs())) {
                    File publicFile = new File(publicDir, filename);
                    try (FileOutputStream fos = new FileOutputStream(publicFile)) {
                        fos.write(bytes);
                        fos.flush();
                    }
                    MediaScannerConnection.scanFile(
                            context,
                            new String[]{publicFile.getAbsolutePath()},
                            new String[]{mimeType},
                            null
                    );
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not save secondary copy to public Downloads: " + e.getMessage());
        }
    }

    public static void downloadHttpFile(Activity activity, String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            filename = sanitizeFilename(filename, mimeType);

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            if (mimeType != null && !mimeType.isEmpty()) {
                request.setMimeType(mimeType);
            }
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) {
                request.addRequestHeader("Cookie", cookies);
            }
            if (userAgent != null) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            request.setDescription("Đang tải file từ Quán Cơm POS...");
            request.setTitle(filename);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GoiMonPOS/" + filename);

            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(activity, "Đang tải xuống: " + filename, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "HTTP DownloadManager error: " + e.getMessage(), e);
            Toast.makeText(activity, "Lỗi khi bắt đầu tải: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static String sanitizeFilename(String filename, String mimeType) {
        if (filename == null || filename.trim().isEmpty()) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            filename = "BaoCao_GoiMon_" + timeStamp + ".xlsx";
        }

        // Remove dangerous characters
        filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");

        // Ensure extension
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls") && !lower.endsWith(".csv") && !lower.endsWith(".pdf") && !lower.endsWith(".docx")) {
            if (mimeType != null && mimeType.contains("csv")) {
                filename += ".csv";
            } else {
                filename += ".xlsx";
            }
        }
        return filename;
    }

    private static void showSuccessDialog(Activity activity, File file, String filename, String mimeType) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        new MaterialAlertDialogBuilder(activity)
                .setTitle("📊 Xuất File Excel Thành Công!")
                .setMessage("Tên file: " + filename + "\n\nFile đã được lưu vào thư mục Downloads của máy.\nBạn có muốn mở file ngay không?")
                .setPositiveButton("Mở file ngay", (dialog, which) -> openFile(activity, file, mimeType))
                .setNeutralButton("Chia sẻ (Zalo/Mail)", (dialog, which) -> shareFile(activity, file, mimeType))
                .setNegativeButton("Đóng", null)
                .show();
    }

    public static void openFile(Activity activity, File file, String mimeType) {
        try {
            Uri contentUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    file
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, mimeType != null ? mimeType : EXCEL_MIME);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            activity.startActivity(Intent.createChooser(intent, "Mở file Excel bằng..."));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "Không tìm thấy ứng dụng đọc Excel (Microsoft Excel, Google Sheets, WPS Office). Hãy cài đặt trên Google Play.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Cannot open file: " + e.getMessage(), e);
            Toast.makeText(activity, "Lỗi mở file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void shareFile(Activity activity, File file, String mimeType) {
        try {
            Uri contentUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType != null ? mimeType : EXCEL_MIME);
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Báo cáo: " + file.getName());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            activity.startActivity(Intent.createChooser(shareIntent, "Chia sẻ file Excel qua..."));
        } catch (Exception e) {
            Log.e(TAG, "Cannot share file: " + e.getMessage(), e);
            Toast.makeText(activity, "Lỗi chia sẻ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void showSuccessNotification(Context context, File file, String filename, String mimeType) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }

            Uri contentUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    file
            );

            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(contentUri, mimeType);
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent openPendingIntent = PendingIntent.getActivity(
                    context,
                    (int) System.currentTimeMillis(),
                    Intent.createChooser(openIntent, "Mở file Excel"),
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            PendingIntent sharePendingIntent = PendingIntent.getActivity(
                    context,
                    (int) System.currentTimeMillis() + 1,
                    Intent.createChooser(shareIntent, "Chia sẻ file"),
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Xuất file Excel thành công")
                    .setContentText(filename)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText("Đã lưu: " + filename + "\nVào thư mục Downloads trên máy."))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(openPendingIntent)
                    .addAction(android.R.drawable.ic_menu_view, "Mở file", openPendingIntent)
                    .addAction(android.R.drawable.ic_menu_share, "Chia sẻ", sharePendingIntent);

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify((int) System.currentTimeMillis(), builder.build());
            }
        } catch (Exception e) {
            Log.w(TAG, "Notification show failed: " + e.getMessage());
        }
    }

    /**
     * JavaScript injection script to hook Blob/Data URLs, HTMLAnchorElement click,
     * and add Excel export helper buttons if needed.
     */
    public static String getInjectedDownloadScript() {
        return "(function() {\n" +
                "    if (window.__androidExcelBridgeInitialized) return;\n" +
                "    window.__androidExcelBridgeInitialized = true;\n" +
                "    console.log('[AndroidBridge] Initializing Excel & File Download interceptors');\n" +
                "\n" +
                "    window.__androidFetchAndDownloadBlob = function(blobUrl, fileName, mimeType) {\n" +
                "        fileName = fileName || ('BaoCao_POS_' + Date.now() + '.xlsx');\n" +
                "        mimeType = mimeType || 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';\n" +
                "        fetch(blobUrl)\n" +
                "            .then(function(res) { return res.blob(); })\n" +
                "            .then(function(blob) {\n" +
                "                var reader = new FileReader();\n" +
                "                reader.onloadend = function() {\n" +
                "                    var base64data = reader.result;\n" +
                "                    if (window.Android && window.Android.downloadBase64File) {\n" +
                "                        window.Android.downloadBase64File(base64data, fileName, blob.type || mimeType);\n" +
                "                    }\n" +
                "                };\n" +
                "                reader.readAsDataURL(blob);\n" +
                "            })\n" +
                "            .catch(function(err) {\n" +
                "                console.error('[AndroidBridge] Failed to read blob data:', err);\n" +
                "                if (window.Android && window.Android.showToast) {\n" +
                "                    window.Android.showToast('Lỗi đọc file: ' + err.message);\n" +
                "                }\n" +
                "            });\n" +
                "    };\n" +
                "\n" +
                "    // 1. Hook HTMLAnchorElement.prototype.click (catches SheetJS XLSX.writeFile, FileSaver.js)\n" +
                "    var originalAnchorClick = HTMLAnchorElement.prototype.click;\n" +
                "    HTMLAnchorElement.prototype.click = function() {\n" +
                "        var href = this.getAttribute('href') || this.href || '';\n" +
                "        var download = this.getAttribute('download') || this.download || '';\n" +
                "        var isFileDownload = download || href.startsWith('blob:') || href.startsWith('data:') || /\\.(xlsx|xls|csv|pdf|docx)($|\\?)/i.test(href);\n" +
                "\n" +
                "        if (isFileDownload) {\n" +
                "            var fileName = download || ('BaoCao_' + Date.now() + '.xlsx');\n" +
                "            if (!/\\.[a-zA-Z0-9]+$/.test(fileName)) {\n" +
                "                fileName += '.xlsx';\n" +
                "            }\n" +
                "            var mimeType = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';\n" +
                "\n" +
                "            if (href.startsWith('blob:')) {\n" +
                "                window.__androidFetchAndDownloadBlob(href, fileName, mimeType);\n" +
                "                return; // Intercepted, avoid unhandled WebView navigation\n" +
                "            } else if (href.startsWith('data:')) {\n" +
                "                if (window.Android && window.Android.downloadBase64File) {\n" +
                "                    window.Android.downloadBase64File(href, fileName, mimeType);\n" +
                "                }\n" +
                "                return;\n" +
                "            } else if (href.startsWith('http://') || href.startsWith('https://')) {\n" +
                "                if (window.Android && window.Android.downloadUrl) {\n" +
                "                    window.Android.downloadUrl(href, fileName, mimeType);\n" +
                "                    return;\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "        return originalAnchorClick.apply(this, arguments);\n" +
                "    };\n" +
                "\n" +
                "    // 2. Intercept document click events in capture phase\n" +
                "    document.addEventListener('click', function(e) {\n" +
                "        var target = e.target;\n" +
                "        while (target && target.tagName !== 'A') {\n" +
                "            target = target.parentElement;\n" +
                "        }\n" +
                "        if (target && target.tagName === 'A') {\n" +
                "            var href = target.getAttribute('href') || target.href || '';\n" +
                "            var download = target.getAttribute('download') || target.download || '';\n" +
                "            if (download || href.startsWith('blob:') || href.startsWith('data:') || /\\.(xlsx|xls|csv)($|\\?)/i.test(href)) {\n" +
                "                if (href.startsWith('blob:')) {\n" +
                "                    e.preventDefault();\n" +
                "                    e.stopPropagation();\n" +
                "                    var fileName = download || ('BaoCao_' + Date.now() + '.xlsx');\n" +
                "                    window.__androidFetchAndDownloadBlob(href, fileName, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');\n" +
                "                } else if (href.startsWith('data:')) {\n" +
                "                    e.preventDefault();\n" +
                "                    e.stopPropagation();\n" +
                "                    var fileName = download || ('BaoCao_' + Date.now() + '.xlsx');\n" +
                "                    if (window.Android && window.Android.downloadBase64File) {\n" +
                "                        window.Android.downloadBase64File(href, fileName, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    }, true);\n" +
                "\n" +
                "    // 3. Inject Helper Button for Receipts Reports if on /admin/reports\n" +
                "    function checkAndInjectReportsExportButton() {\n" +
                "        if (window.location.pathname.indexOf('/admin/reports') !== -1) {\n" +
                "            var existingBtn = document.getElementById('btn-android-export-reports');\n" +
                "            if (!existingBtn) {\n" +
                "                var searchInput = document.querySelector('input[placeholder*=\"hóa đơn\"]') || document.querySelector('input[type=\"text\"]');\n" +
                "                if (searchInput && searchInput.parentElement && searchInput.parentElement.parentElement) {\n" +
                "                    var container = searchInput.parentElement.parentElement;\n" +
                "                    var btn = document.createElement('button');\n" +
                "                    btn.id = 'btn-android-export-reports';\n" +
                "                    btn.type = 'button';\n" +
                "                    btn.innerHTML = '📊 XUẤT EXCEL HÓA ĐƠN';\n" +
                "                    btn.style.cssText = 'margin-left: 10px; background: #059669; color: white; border: none; padding: 7px 14px; border-radius: 10px; font-weight: bold; font-size: 11px; cursor: pointer; display: inline-flex; align-items: center; gap: 4px; box-shadow: 0 4px 12px rgba(5,150,105,0.3);';\n" +
                "                    btn.onclick = function() {\n" +
                "                        exportReportsTableToExcel();\n" +
                "                    };\n" +
                "                    container.style.display = 'flex';\n" +
                "                    container.style.alignItems = 'center';\n" +
                "                    container.style.justifyContent = 'space-between';\n" +
                "                    container.appendChild(btn);\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    function exportReportsTableToExcel() {\n" +
                "        var table = document.querySelector('table');\n" +
                "        if (!table) {\n" +
                "            if (window.Android && window.Android.showToast) window.Android.showToast('Không tìm thấy bảng hóa đơn để xuất');\n" +
                "            return;\n" +
                "        }\n" +
                "        var rows = table.querySelectorAll('tr');\n" +
                "        var csv = '\\uFEFF'; // UTF-8 BOM\n" +
                "        csv += 'BÁO CÁO NHẬT KÝ HÓA ĐƠN - QUÁN CƠM POS\\r\\n';\n" +
                "        csv += 'Ngày xuất: ' + new Date().toLocaleString('vi-VN') + '\\r\\n\\r\\n';\n" +
                "        rows.forEach(function(row) {\n" +
                "            var cols = row.querySelectorAll('th, td');\n" +
                "            var rowData = [];\n" +
                "            for (var i = 0; i < cols.length - 1; i++) { // Skip the last action column\n" +
                "                var text = (cols[i].innerText || '').replace(/\\r?\\n/g, ' ').replace(/\"/g, '\"\"').trim();\n" +
                "                rowData.push('\"' + text + '\"');\n" +
                "            }\n" +
                "            if (rowData.length > 0) csv += rowData.join(',') + '\\r\\n';\n" +
                "        });\n" +
                "        var base64 = btoa(unescape(encodeURIComponent(csv)));\n" +
                "        var dataUrl = 'data:text/csv;charset=utf-8;base64,' + base64;\n" +
                "        var fileName = 'BaoCao_HoaDon_' + new Date().toISOString().split('T')[0] + '.csv';\n" +
                "        if (window.Android && window.Android.downloadBase64File) {\n" +
                "            window.Android.downloadBase64File(dataUrl, fileName, 'text/csv');\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    setInterval(checkAndInjectReportsExportButton, 1500);\n" +
                "})();";
    }
}

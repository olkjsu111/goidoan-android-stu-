package com.example.goidoan;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.ClipData;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;

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
                String cleanBase64 = base64OrDataUrl;
                String resolvedMime = mimeType;
                byte[] fileBytes;
                if (cleanBase64.startsWith("data:")) {
                    int separator = cleanBase64.indexOf(',');
                    if (separator < 0) throw new IllegalArgumentException("Data URL không hợp lệ");
                    String header = cleanBase64.substring(5, separator);
                    String dataMime = header.split(";", 2)[0];
                    if (!dataMime.isEmpty() && !"application/octet-stream".equals(dataMime)) resolvedMime = dataMime;
                    String payload = cleanBase64.substring(separator + 1);
                    fileBytes = header.contains(";base64")
                            ? Base64.decode(payload, Base64.DEFAULT)
                            : Uri.decode(payload).getBytes(StandardCharsets.UTF_8);
                } else {
                    fileBytes = Base64.decode(cleanBase64, Base64.DEFAULT);
                }
                saveBytesAndNotify(activity, fileBytes, filename, resolvedMime);

            } catch (Exception e) {
                Log.e(TAG, "Failed to save Excel file: " + e.getMessage(), e);
                MAIN_HANDLER.post(() ->
                        Toast.makeText(activity, "Lỗi xuất file Excel: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    public static void exportTable(Activity activity, String rowsJson, String filename) {
        EXECUTOR.execute(() -> {
            try {
                JSONArray array = new JSONArray(rowsJson);
                if (array.length() == 0) throw new IllegalArgumentException("Bảng hóa đơn rỗng");
                List<List<String>> rows = new ArrayList<>();
                for (int r = 0; r < array.length(); r++) {
                    JSONArray cells = array.getJSONArray(r);
                    List<String> row = new ArrayList<>();
                    for (int c = 0; c < cells.length(); c++) row.add(cells.optString(c, ""));
                    rows.add(row);
                }
                saveBytesAndNotify(activity, ExcelWorkbookWriter.write(rows), filename, EXCEL_MIME);
            } catch (Exception e) {
                Log.e(TAG, "Cannot export reports table", e);
                MAIN_HANDLER.post(() -> Toast.makeText(activity, "Lỗi xuất Excel: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private static void saveBytesAndNotify(Activity activity, byte[] bytes, String filename, String mimeType) throws IOException {
        if (bytes.length == 0) throw new IllegalArgumentException("Dữ liệu file rỗng");
        String safeName = sanitizeFilename(filename, mimeType);
        File directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) directory = activity.getFilesDir();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Không tạo được thư mục lưu file");
        File file = new File(directory, safeName);
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
        String resolvedMime = mimeType == null || mimeType.isEmpty() || "application/octet-stream".equals(mimeType)
                ? (safeName.toLowerCase(Locale.ROOT).endsWith(".csv") ? "text/csv" : EXCEL_MIME) : mimeType;
        String fileMime = resolvedMime.split(";", 2)[0].trim();
        boolean publicSaved = saveToPublicDownloads(activity, bytes, safeName, fileMime);
        Log.i(TAG, "File saved: " + file.getAbsolutePath() + " (" + bytes.length + " bytes), public Downloads: " + publicSaved);
        MAIN_HANDLER.post(() -> {
            showSuccessNotification(activity, file, safeName, fileMime, publicSaved);
            showSuccessDialog(activity, file, safeName, fileMime, publicSaved);
        });
    }

    private static boolean saveToPublicDownloads(Context context, byte[] bytes, String filename, String mimeType) {
        Uri pendingUri = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ MediaStore.Downloads (No WRITE_EXTERNAL_STORAGE required)
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GoiMonPOS");
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                pendingUri = uri;
                if (uri != null) {
                    try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new IOException("Không mở được thư mục Downloads");
                        out.write(bytes);
                        out.flush();
                    }
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    if (context.getContentResolver().update(uri, values, null, null) < 1) throw new IOException("Không hoàn tất lưu file");
                    return true;
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
                    return true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not save secondary copy to public Downloads: " + e.getMessage());
            if (pendingUri != null) {
                try { context.getContentResolver().delete(pendingUri, null, null); }
                catch (Exception cleanup) { Log.w(TAG, "Cannot remove incomplete download", cleanup); }
            }
        }
        return false;
    }

    public static void downloadHttpFile(Activity activity, String url, String userAgent, String contentDisposition, String mimeType) {
        downloadHttpFile(activity, url, userAgent, contentDisposition, mimeType, null);
    }

    public static void downloadHttpFile(Activity activity, String url, String userAgent, String contentDisposition, String mimeType, String requestedFilename) {
        MAIN_HANDLER.post(() -> {
        try {
            String filename = requestedFilename == null || requestedFilename.isEmpty()
                    ? URLUtil.guessFileName(url, contentDisposition, mimeType) : requestedFilename;
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
        });
    }

    private static String sanitizeFilename(String filename, String mimeType) {
        if (filename == null || filename.trim().isEmpty()) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            filename = "BaoCao_GoiMon_" + timeStamp;
        }

        // Remove dangerous characters
        filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");

        // Ensure extension
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls") && !lower.endsWith(".csv") && !lower.endsWith(".pdf") && !lower.endsWith(".docx")) {
            if (mimeType != null && mimeType.contains("csv")) {
                filename += ".csv";
            } else if (mimeType != null && mimeType.contains("pdf")) {
                filename += ".pdf";
            } else if ("application/vnd.ms-excel".equals(mimeType)) {
                filename += ".xls";
            } else if (mimeType != null && mimeType.contains("wordprocessingml")) {
                filename += ".docx";
            } else {
                filename += ".xlsx";
            }
        }
        return filename;
    }

    private static void showSuccessDialog(Activity activity, File file, String filename, String mimeType, boolean publicSaved) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        new MaterialAlertDialogBuilder(activity)
                .setTitle("📊 Xuất File Excel Thành Công!")
                .setMessage("Tên file: " + filename + "\n\n" + (publicSaved
                        ? "Đã lưu vào Downloads/GoiMonPOS của máy."
                        : "Đã lưu trong ứng dụng. Chưa chép được vào Downloads; bạn có thể mở hoặc chia sẻ file bên dưới."))
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
            intent.setClipData(ClipData.newUri(activity.getContentResolver(), file.getName(), contentUri));
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
            shareIntent.setClipData(ClipData.newUri(activity.getContentResolver(), file.getName(), contentUri));
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Báo cáo: " + file.getName());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            activity.startActivity(Intent.createChooser(shareIntent, "Chia sẻ file Excel qua..."));
        } catch (Exception e) {
            Log.e(TAG, "Cannot share file: " + e.getMessage(), e);
            Toast.makeText(activity, "Lỗi chia sẻ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void showSuccessNotification(Context context, File file, String filename, String mimeType, boolean publicSaved) {
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
                    .setStyle(new NotificationCompat.BigTextStyle().bigText("Đã lưu: " + filename + (publicSaved
                            ? "\nVào thư mục Downloads/GoiMonPOS trên máy." : "\nFile được lưu trong ứng dụng; có thể mở hoặc chia sẻ.")))
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

    public static String getInjectedDownloadScript(Context context) {
        return WebBridgeScripts.read(context, "download-bridge.js");
    }
}

package com.example.goidoan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;

public class QrPaymentHelper {

    private static final String TAG = "QrPaymentHelper";
    private static String cachedQrBase64 = null;

    public static final String BANK_ID = "BIDV";
    public static final String ACCOUNT_NO = "8802974290";
    public static final String ACCOUNT_NAME = "NHA HANG";

    public static synchronized String getQrBase64(Context context) {
        if (cachedQrBase64 != null) {
            return cachedQrBase64;
        }

        try {
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.my_qr);
            if (bitmap != null) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream);
                byte[] byteArray = stream.toByteArray();
                cachedQrBase64 = "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP);
                Log.d(TAG, "Encoded my_qr.jpg to base64 successfully, size: " + byteArray.length + " bytes");
                return cachedQrBase64;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting my_qr.jpg to base64: " + e.getMessage(), e);
        }
        return "";
    }

    public static String getInjectedQrScript() {
        return "(function() {\n" +
                "  if (window.__qrPaymentPatcherInstalled) return;\n" +
                "  window.__qrPaymentPatcherInstalled = true;\n" +
                "\n" +
                "  function patchModal() {\n" +
                "    // 1. Thay thế số tài khoản & tên ngân hàng cũ trong toàn bộ DOM\n" +
                "    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);\n" +
                "    var node;\n" +
                "    while ((node = walker.nextNode())) {\n" +
                "      var txt = node.nodeValue;\n" +
                "      if (txt) {\n" +
                "        if (txt.indexOf('1903666888999') !== -1) {\n" +
                "          node.nodeValue = txt.replace(/1903666888999/g, '" + ACCOUNT_NO + "');\n" +
                "        }\n" +
                "        if (txt.indexOf('Techcombank') !== -1 || txt.indexOf('TCB') !== -1) {\n" +
                "          node.nodeValue = node.nodeValue.replace(/Techcombank\\s*\\(TCB\\)/g, '" + BANK_ID + "')\n" +
                "                                         .replace(/Techcombank/g, '" + BANK_ID + "')\n" +
                "                                         .replace(/\\bTCB\\b/g, '" + BANK_ID + "');\n" +
                "        }\n" +
                "        if (txt.indexOf('QUAN COM TAM SAIGON') !== -1) {\n" +
                "          node.nodeValue = node.nodeValue.replace(/QUAN COM TAM SAIGON/g, '" + ACCOUNT_NAME + "');\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    // 2. Thay thế ảnh QR của modal web bằng ảnh my_qr của người dùng\n" +
                "    var imgs = document.querySelectorAll('img');\n" +
                "    for (var i = 0; i < imgs.length; i++) {\n" +
                "      var img = imgs[i];\n" +
                "      var src = img.getAttribute('src') || '';\n" +
                "      if (src.indexOf('vietqr.io') !== -1 || src.indexOf('TCB-1903666888999') !== -1 || (img.alt && img.alt.indexOf('VietQR') !== -1)) {\n" +
                "        if (window.Android && window.Android.getQrBase64) {\n" +
                "          var userQr = window.Android.getQrBase64();\n" +
                "          if (userQr && src !== userQr) {\n" +
                "            img.src = userQr;\n" +
                "            img.style.objectFit = 'contain';\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "\n" +
                "  // Lắng nghe click để mở Native PaymentDialog khi bấm Tính Tiền\n" +
                "  document.addEventListener('click', function(e) {\n" +
                "    var btn = e.target.closest('button');\n" +
                "    if (btn && btn.textContent && btn.textContent.indexOf('TÍNH TIỀN') !== -1) {\n" +
                "      // Trích xuất mã bàn nếu có\n" +
                "      var titleEl = document.querySelector('h3, [class*=\"font-bold\"]');\n" +
                "      var tableName = titleEl ? titleEl.textContent : 'Bàn';\n" +
                "      if (window.Android && window.Android.openPaymentDialog) {\n" +
                "        // Cho phép mở song song Native Payment Dialog của Android Studio\n" +
                "        window.Android.openPaymentDialog('T01', tableName, 0, '[]');\n" +
                "      }\n" +
                "    }\n" +
                "  }, true);\n" +
                "\n" +
                "  // Tự động kiểm tra và thay thế liên tục khi modal hiển thị\n" +
                "  setInterval(patchModal, 400);\n" +
                "})();";
    }
}

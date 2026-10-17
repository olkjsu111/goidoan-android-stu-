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

    public static String getInjectedQrScript(Context context) {
        try {
            org.json.JSONObject config = new org.json.JSONObject();
            config.put("bankId", BANK_ID);
            config.put("accountNo", ACCOUNT_NO);
            config.put("accountName", ACCOUNT_NAME);
            return WebBridgeScripts.read(context, "payment-bridge.js")
                    .replace("__ANDROID_PAYMENT_CONFIG__", config.toString());
        } catch (org.json.JSONException e) {
            throw new IllegalStateException("Invalid payment configuration", e);
        }
    }
}

package com.example.goidoan.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.goidoan.R;
import com.example.goidoan.adapter.BillItemAdapter;
import com.example.goidoan.model.TableItem;
import com.google.android.material.button.MaterialButton;

public class PaymentDialog extends Dialog {

    // Thông tin tài khoản ngân hàng VietQR
    private static final String BANK_ID = "BIDV";
    private static final String ACCOUNT_NO = "8802974290";
    private static final String ACCOUNT_NAME = "NHA HANG";

    private final TableItem table;

    public interface OnPaymentCompletedListener {
        void onPaymentCompleted(TableItem table, String paymentMethod);
    }

    private final OnPaymentCompletedListener listener;

    public PaymentDialog(@NonNull Context context, TableItem table, OnPaymentCompletedListener listener) {
        super(context);
        this.table = table;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_payment);

        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getWindow().setLayout((int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.92), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvPaymentTitle = findViewById(R.id.tvPaymentTitle);
        ImageView btnPaymentClose = findViewById(R.id.btnPaymentClose);
        RecyclerView rvPaymentBillItems = findViewById(R.id.rvPaymentBillItems);
        TextView tvPaymentSubtotal = findViewById(R.id.tvPaymentSubtotal);
        TextView tvPaymentFinalTotal = findViewById(R.id.tvPaymentFinalTotal);
        RadioGroup rgPaymentMethod = findViewById(R.id.rgPaymentMethod);
        LinearLayout layoutVietQrBox = findViewById(R.id.layoutVietQrBox);
        TextView tvQrAmountNotice = findViewById(R.id.tvQrAmountNotice);
        TextView tvQrAccountInfo = findViewById(R.id.tvQrAccountInfo);
        ImageView imgQrCode = findViewById(R.id.imgQrCode);
        ProgressBar pbQrLoading = findViewById(R.id.pbQrLoading);
        MaterialButton btnCompleteCheckout = findViewById(R.id.btnCompleteCheckout);

        tvPaymentTitle.setText("Thanh Toán • " + table.getName());
        tvPaymentSubtotal.setText(table.getFormattedTotalBill());
        tvPaymentFinalTotal.setText(table.getFormattedTotalBill());

        BillItemAdapter billAdapter = new BillItemAdapter(getContext());
        billAdapter.setBillItems(table.getOrderedItems());
        rvPaymentBillItems.setLayoutManager(new LinearLayoutManager(getContext()));
        rvPaymentBillItems.setAdapter(billAdapter);

        tvQrAmountNotice.setText("Số tiền: " + table.getFormattedTotalBill() + " • Cú pháp: " + table.getId() + " TT");
        tvQrAccountInfo.setText("NGÂN HÀNG: " + BANK_ID + "\nSTK: " + ACCOUNT_NO + "\nCHỦ TK: " + ACCOUNT_NAME);

        btnPaymentClose.setOnClickListener(v -> dismiss());

        // Mặc định hiển thị QR tĩnh do người dùng cung cấp (BIDV SmartBanking)
        rgPaymentMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbPaymentVietQR) {
                layoutVietQrBox.setVisibility(View.VISIBLE);
                if (pbQrLoading != null) pbQrLoading.setVisibility(View.GONE);
                if (imgQrCode != null) {
                    imgQrCode.setImageResource(R.drawable.my_qr);
                }
            } else {
                layoutVietQrBox.setVisibility(View.GONE);
            }
        });

        // Cho phép bấm vào ảnh QR để linh hoạt chuyển đổi giữa mã QR gốc và mã QR tự sinh theo số tiền
        final boolean[] isCustomQr = {true};
        imgQrCode.setOnClickListener(v -> {
            if (isCustomQr[0]) {
                isCustomQr[0] = false;
                loadVietQrImage(imgQrCode, pbQrLoading, table.getTotalBill(), table.getName());
                Toast.makeText(getContext(), "Đang hiển thị mã VietQR động theo số tiền", Toast.LENGTH_SHORT).show();
            } else {
                isCustomQr[0] = true;
                if (pbQrLoading != null) pbQrLoading.setVisibility(View.GONE);
                imgQrCode.setImageResource(R.drawable.my_qr);
                Toast.makeText(getContext(), "Đang hiển thị mã QR BIDV của nhà hàng", Toast.LENGTH_SHORT).show();
            }
        });

        btnCompleteCheckout.setOnClickListener(v -> {
            String method = rgPaymentMethod.getCheckedRadioButtonId() == R.id.rbPaymentVietQR ? "VietQR" : "Tiền mặt";

            Toast.makeText(getContext(), "Đã thanh toán " + table.getFormattedTotalBill() + " (" + method + ") cho " + table.getName() + " thành công!", Toast.LENGTH_LONG).show();

            dismiss();
            if (listener != null) {
                listener.onPaymentCompleted(table, method);
            }
        });
    }

    private void loadVietQrImage(ImageView imgQrCode, ProgressBar pbQrLoading, long amount, String tableName) {
        if (pbQrLoading != null) pbQrLoading.setVisibility(View.VISIBLE);
        if (imgQrCode != null) imgQrCode.setImageBitmap(null);

        String memo = tableName.replace(" ", "") + " TT";

        try {
            String encodedMemo = java.net.URLEncoder.encode(memo, "UTF-8");
            String encodedName = java.net.URLEncoder.encode(ACCOUNT_NAME, "UTF-8");

            String qrUrl = "https://img.vietqr.io/image/" + BANK_ID + "-" + ACCOUNT_NO + "-compact2.png"
                    + "?amount=" + amount
                    + "&addInfo=" + encodedMemo
                    + "&accountName=" + encodedName;

            java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    java.net.URL url = new java.net.URL(qrUrl);
                    java.io.InputStream in = url.openStream();
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(in);

                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (pbQrLoading != null) pbQrLoading.setVisibility(View.GONE);
                        if (imgQrCode != null) imgQrCode.setImageBitmap(bitmap);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (pbQrLoading != null) pbQrLoading.setVisibility(View.GONE);
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

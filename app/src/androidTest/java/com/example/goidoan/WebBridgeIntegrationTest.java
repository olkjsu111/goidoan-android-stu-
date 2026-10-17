package com.example.goidoan;

import android.content.Context;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WebBridgeIntegrationTest {
    private WebView webView;
    private final ProbeBridge probe = new ProbeBridge();

    @Before
    public void loadFixtureInRealWebView() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch ready = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            webView = new WebView(context);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.addJavascriptInterface(probe, "Android");
            String script = ExcelExportHelper.getInjectedDownloadScript(context) + "\n" + QrPaymentHelper.getInjectedQrScript(context);
            boolean early = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
            if (early) WebViewCompat.addDocumentStartJavaScript(webView, script, Collections.singleton("*"));
            webView.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    view.evaluateJavascript(script, ignored -> ready.countDown());
                }
            });
            String html = "<html><body><input type='text'><p id='unrelated'>TCB 1903666888999</p>"
                    + "<table><thead><tr><th>Bàn</th><th>Tổng thanh toán</th><th>Xem</th></tr></thead>"
                    + "<tbody><tr><td>Bàn 03 &amp; Khách</td><td>235.000đ</td><td><button>Xem</button></td></tr></tbody></table>"
                    + "<div id='qr-card'><div><img id='qr' src='https://img.vietqr.io/image/TCB-1903666888999-compact2.png?amount=235000&amp;addInfo=Ban%2003%20Don%201022'></div>"
                    + "<div>Ngân hàng: <b>Techcombank (TCB)</b><b>1903666888999</b></div></div>"
                    + "<button id='checkout'>TÍNH TIỀN</button></body></html>";
            webView.loadDataWithBaseURL("https://bridge-test.invalid/admin/reports", html, "text/html", "UTF-8", null);
        });
        assertTrue("WebView fixture did not load", ready.await(15, TimeUnit.SECONDS));
    }

    @After
    public void destroyWebView() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> webView.destroy());
    }

    @Test
    public void detachedDispatchedDownloadSurvivesImmediateUrlRevocation() throws Exception {
        evaluate("var b=new Blob(['Tên món,Số tiền\\r\\nCơm gà,45000'],{type:'text/csv;charset=utf-8'});"
                + "var u=URL.createObjectURL(b),a=document.createElement('a');a.href=u;a.download='Hóa đơn.csv';"
                + "a.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));URL.revokeObjectURL(u);");
        String[] result = probe.downloads.poll(5, TimeUnit.SECONDS);
        assertNotNull("The revoked Blob download was lost", result);
        assertEquals("Hóa đơn.csv", result[1]);
        String decoded = new String(Base64.decode(result[0].substring(result[0].indexOf(',') + 1), Base64.DEFAULT), StandardCharsets.UTF_8);
        assertEquals("Tên món,Số tiền\r\nCơm gà,45000", decoded);
        assertNull("Download was exported twice", probe.downloads.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    public void anchorClickAndAttachedUserEventEachExportOnce() throws Exception {
        evaluate("var u=URL.createObjectURL(new Blob(['sample'],{type:'application/octet-stream'}));"
                + "var a=document.createElement('a');a.href=u;a.download='Báo cáo.xlsx';a.click();URL.revokeObjectURL(u);");
        String[] first = probe.downloads.poll(5, TimeUnit.SECONDS);
        assertNotNull(first);
        assertEquals(ExcelExportHelper.EXCEL_MIME, first[2]);
        evaluate("var a=document.createElement('a');a.href='data:text/csv;charset=utf-8,T%C3%AAn%2BS%E1%BB%91';a.download='Mẫu.csv';document.body.appendChild(a);"
                + "EventTarget.prototype.dispatchEvent.call(a,new MouseEvent('click',{bubbles:true,cancelable:true}));");
        String[] second = probe.downloads.poll(5, TimeUnit.SECONDS);
        assertNotNull(second);
        assertEquals("Mẫu.csv", second[1]);
        assertTrue(second[0].startsWith("data:text/csv;"));
        assertNull(probe.downloads.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    public void httpDownloadKeepsWebsiteFilename() throws Exception {
        evaluate("var a=document.createElement('a');a.href='https://example.invalid/export?id=42';a.download='Doanh thu.xlsx';a.click();");
        String[] file = probe.httpDownloads.poll(5, TimeUnit.SECONDS);
        assertNotNull(file);
        assertEquals("Doanh thu.xlsx", file[1]);
        assertEquals(ExcelExportHelper.EXCEL_MIME, file[2]);
    }

    @Test
    public void reportsExportPreservesAllDataColumnsAndSkipsOnlyActions() throws Exception {
        assertEquals("true", evaluate("!!document.getElementById('btn-android-export-reports')"));
        evaluate("document.getElementById('btn-android-export-reports').click()");
        String[] export = probe.tables.poll(5, TimeUnit.SECONDS);
        assertNotNull(export);
        JSONArray rows = new JSONArray(export[0]);
        assertEquals(2, rows.length());
        assertEquals(2, rows.getJSONArray(0).length());
        assertEquals("Bàn 03 & Khách", rows.getJSONArray(1).getString(0));
        assertEquals("235.000đ", rows.getJSONArray(1).getString(1));
        assertTrue(export[1].endsWith(".xlsx"));
    }

    @Test
    public void qrPreservesAmountOrderAndWebCheckoutWithoutNativeZeroBill() throws Exception {
        assertEquals("true", evaluate("var u=new URL(document.getElementById('qr').src);"
                + "u.pathname==='/image/BIDV-8802974290-compact2.png' && u.searchParams.get('amount')==='235000'"
                + " && u.searchParams.get('addInfo')==='Ban 03 Don 1022'"));
        assertEquals("true", evaluate("document.getElementById('qr-card').innerText.includes('BIDV')"
                + " && !document.getElementById('qr-card').innerText.includes('1903666888999')"
                + " && document.getElementById('unrelated').innerText==='TCB 1903666888999'"));
        evaluate("document.getElementById('qr').src='https://img.vietqr.io/image/TCB-1903666888999-compact2.png?amount=210000&addInfo=Ban%2003%20Don%201022';document.getElementById('checkout').click()");
        assertEquals("true", evaluate("new URL(document.getElementById('qr').src).searchParams.get('amount')==='210000'"));
        assertEquals(0, probe.nativePayments.get());
    }

    @Test
    public void customBankQrIsNotReplaced() throws Exception {
        evaluate("document.getElementById('qr').src='https://img.vietqr.io/image/VCB-123456-compact2.png?amount=99000'");
        assertEquals("true", evaluate("document.getElementById('qr').src.includes('/VCB-123456-')"));
    }

    private String evaluate(String script) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> webView.evaluateJavascript(script, value -> {
            result.set(value);
            done.countDown();
        }));
        assertTrue("JavaScript did not finish", done.await(10, TimeUnit.SECONDS));
        return result.get();
    }

    public static final class ProbeBridge {
        final LinkedBlockingQueue<String[]> downloads = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<String[]> httpDownloads = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<String[]> tables = new LinkedBlockingQueue<>();
        final AtomicInteger nativePayments = new AtomicInteger();
        @JavascriptInterface public void downloadBase64File(String data, String name, String mime) { downloads.add(new String[]{data, name, mime}); }
        @JavascriptInterface public void downloadUrl(String url, String name, String mime) { httpDownloads.add(new String[]{url, name, mime}); }
        @JavascriptInterface public void exportTable(String rows, String name) { tables.add(new String[]{rows, name}); }
        @JavascriptInterface public void showToast(String message) { throw new AssertionError(message); }
        @JavascriptInterface public void openPaymentDialog(String id, String name, long total, String items) { nativePayments.incrementAndGet(); }
    }
}

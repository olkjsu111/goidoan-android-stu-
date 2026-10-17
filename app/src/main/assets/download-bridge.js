(function () {
    'use strict';
    if (!window.Android || !Android.downloadBase64File || window.__androidExcelBridgeInitialized) return;
    window.__androidExcelBridgeInitialized = true;

    // Keep the Blob itself while an export starts, even if the website revokes its URL immediately.
    const blobs = new Map();
    const pending = new Map();
    const createObjectURL = URL.createObjectURL.bind(URL);
    const revokeObjectURL = URL.revokeObjectURL.bind(URL);
    URL.createObjectURL = function (blob) {
        const url = createObjectURL(blob);
        blobs.set(url, blob);
        return url;
    };
    URL.revokeObjectURL = function (url) {
        blobs.delete(url);
        return revokeObjectURL(url);
    };

    const types = {
        xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        xls: 'application/vnd.ms-excel', csv: 'text/csv', pdf: 'application/pdf',
        docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    };
    function mimeFor(name, type) {
        if (type && type !== 'application/octet-stream') return type;
        return types[(name.split('.').pop() || '').toLowerCase()] || type || 'application/octet-stream';
    }
    function reportError(error) {
        console.error('[AndroidBridge] Download failed:', error);
        if (Android.showToast) Android.showToast('Không thể lưu file: ' + error.message);
    }
    function sendBlob(blob, name, type) {
        return new Promise(function (resolve, reject) {
            const reader = new FileReader();
            reader.onload = function () {
                try {
                    Android.downloadBase64File(reader.result, name, mimeFor(name, blob.type || type));
                    resolve();
                } catch (error) { reject(error); }
            };
            reader.onerror = function () { reject(reader.error || new Error('Không đọc được dữ liệu file')); };
            reader.onabort = function () { reject(new Error('Đọc file bị gián đoạn')); };
            reader.readAsDataURL(blob);
        });
    }
    window.__androidFetchAndDownloadBlob = function (url, name, type) {
        name = name || ('BaoCao_POS_' + Date.now() + '.xlsx');
        const key = url + '\n' + name;
        if (pending.has(key)) return pending.get(key);
        const blob = blobs.get(url);
        const task = (blob ? Promise.resolve(blob) : fetch(url).then(function (response) {
            if (!response.ok) throw new Error('HTTP ' + response.status);
            return response.blob();
        })).then(function (data) { return sendBlob(data, name, type); })
            .catch(reportError).finally(function () { pending.delete(key); });
        pending.set(key, task);
        return task;
    };

    function intercept(anchor) {
        const href = anchor.href || '';
        const name = anchor.download || '';
        const fileLink = name || /\.(xlsx|xls|csv|pdf|docx)(?:[?#]|$)/i.test(href);
        if (href.startsWith('blob:')) {
            window.__androidFetchAndDownloadBlob(href, name, mimeFor(name));
            return true;
        }
        if (href.startsWith('data:') && fileLink) {
            Android.downloadBase64File(href, name, mimeFor(name));
            return true;
        }
        if (/^https?:/.test(href) && fileLink && Android.downloadUrl) {
            Android.downloadUrl(href, name, mimeFor(name));
            return true;
        }
        return false;
    }
    const anchorClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
        if (!intercept(this)) return anchorClick.apply(this, arguments);
    };
    // FileSaver and other libraries dispatch clicks on anchors that are never attached to document.
    const dispatch = HTMLAnchorElement.prototype.dispatchEvent;
    HTMLAnchorElement.prototype.dispatchEvent = function (event) {
        if (event.type === 'click' && intercept(this)) {
            if (event.cancelable) event.preventDefault();
            return false;
        }
        return dispatch.apply(this, arguments);
    };
    document.addEventListener('click', function (event) {
        const target = event.target instanceof Element ? event.target : event.target.parentElement;
        const anchor = target && target.closest('a');
        if (anchor && intercept(anchor)) {
            event.preventDefault();
            event.stopImmediatePropagation();
        }
    }, true);

    // The deployed reports page has no export action. Export its currently filtered table as XLSX.
    function addReportsExport() {
        if (!location.pathname.includes('/admin/reports') || document.getElementById('btn-android-export-reports')) return;
        const input = document.querySelector('input[type="text"]');
        const container = input && input.parentElement && input.parentElement.parentElement;
        if (!container || !Android.exportTable) return;
        const button = document.createElement('button');
        button.id = 'btn-android-export-reports';
        button.type = 'button';
        button.textContent = 'Xuất Excel hóa đơn';
        button.style.cssText = 'margin-top:10px;background:#059669;color:white;border:0;padding:10px 14px;border-radius:10px;font-weight:bold;cursor:pointer';
        button.addEventListener('click', function () {
            const table = document.querySelector('table');
            if (!table) { Android.showToast('Không có hóa đơn phù hợp để xuất'); return; }
            const headers = Array.from(table.querySelectorAll('thead th'));
            const last = headers[headers.length - 1];
            const skipAction = !!last && /^(xem|thao tác|hành động)$/i.test(last.innerText.trim());
            const rows = Array.from(table.rows, function (row) {
                const cells = Array.from(row.cells);
                if (skipAction) cells.pop();
                return cells.map(cell => (cell.innerText || '').replace(/\r?\n/g, ' ').trim());
            });
            const date = new Date();
            const stamp = [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-');
            Android.exportTable(JSON.stringify(rows), 'BaoCao_HoaDon_' + stamp + '.xlsx');
        });
        container.appendChild(button);
    }
    let scheduled = false;
    new MutationObserver(function () {
        if (scheduled) return;
        scheduled = true;
        Promise.resolve().then(function () { scheduled = false; addReportsExport(); });
    }).observe(document, {childList:true, subtree:true});
    addReportsExport();
})();

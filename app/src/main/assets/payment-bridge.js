(function (config) {
    'use strict';
    if (window.__qrPaymentPatcherInstalled) return;
    window.__qrPaymentPatcherInstalled = true;

    function patchQrImages() {
        document.querySelectorAll('img').forEach(function (image) {
            let url;
            try { url = new URL(image.getAttribute('src'), location.href); } catch (_) { return; }
            if (url.hostname !== 'img.vietqr.io') return;
            // Migrate only the old default receiver and our configured receiver; leave custom accounts intact.
            const legacy = /^\/image\/TCB-1903666888999-/i.test(url.pathname);
            const configured = url.pathname.startsWith('/image/' + config.bankId + '-' + config.accountNo + '-');
            if (!legacy && !configured) return;
            url.pathname = '/image/' + config.bankId + '-' + config.accountNo + '-compact2.png';
            url.searchParams.set('accountName', config.accountName);
            // Preserve the web checkout's final amount (including VAT/discounts) and order reference.
            if (image.getAttribute('src') !== url.href) image.setAttribute('src', url.href);

            const card = image.parentElement && image.parentElement.parentElement;
            if (!card) return;
            const walker = document.createTreeWalker(card, NodeFilter.SHOW_TEXT);
            let node;
            while ((node = walker.nextNode())) {
                const text = node.nodeValue.replace(/1903666888999/g, config.accountNo)
                    .replace(/Techcombank\s*\(TCB\)|Techcombank|\bTCB\b/g, config.bankId)
                    .replace(/QUAN COM TAM SAI GON/g, config.accountName);
                if (text !== node.nodeValue) node.nodeValue = text;
            }
        });
    }
    let scheduled = false;
    new MutationObserver(function () {
        if (scheduled) return;
        scheduled = true;
        Promise.resolve().then(function () { scheduled = false; patchQrImages(); });
    }).observe(document, {childList:true, subtree:true, attributes:true, attributeFilter:['src']});
    patchQrImages();
    // Checkout and its confirmation remain in the web app, which owns the actual order state.
})(__ANDROID_PAYMENT_CONFIG__);

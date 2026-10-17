package com.example.goidoan;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class WebBridgeScripts {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private WebBridgeScripts() {}

    static String read(Context context, String name) {
        String cached = CACHE.get(name);
        if (cached != null) return cached;
        try (InputStream input = context.getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            String script = output.toString(StandardCharsets.UTF_8.name());
            CACHE.put(name, script);
            return script;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load WebView bridge: " + name, e);
        }
    }
}

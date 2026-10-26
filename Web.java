package za.co.focigroup.focitrace;

import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Web {
    private Web() {}

    public static Map<String,String> form(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseQuery(body);
    }

    public static Map<String,String> query(HttpExchange ex) {
        return parseQuery(ex.getRequestURI().getRawQuery());
    }

    private static Map<String,String> parseQuery(String raw) {
        Map<String,String> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return out;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=",2);
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length>1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            out.put(k,v);
        }
        return out;
    }

    public static void html(HttpExchange ex, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
        securityHeaders(ex);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    public static void bytes(HttpExchange ex, int status, String contentType, byte[] data, String filename) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType == null ? "application/octet-stream" : contentType);
        if (filename != null) ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename.replace("\"","") + "\"");
        securityHeaders(ex);
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    public static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        securityHeaders(ex);
        ex.sendResponseHeaders(303, -1);
        ex.close();
    }

    public static void securityHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        ex.getResponseHeaders().set("X-Frame-Options","DENY");
        ex.getResponseHeaders().set("Referrer-Policy","same-origin");
        ex.getResponseHeaders().set("Permissions-Policy","camera=(), microphone=(), geolocation=()");
        ex.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'");
        ex.getResponseHeaders().set("Cache-Control","no-store");
    }

    public static String e(Object value) {
        if (value == null) return "";
        return value.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }

    public static String param(Map<String,String> f, String key) { return Optional.ofNullable(f.get(key)).orElse("").trim(); }
    public static boolean bool(Map<String,String> f, String key) { String v=param(f,key); return "on".equalsIgnoreCase(v)||"true".equalsIgnoreCase(v)||"yes".equalsIgnoreCase(v); }
}

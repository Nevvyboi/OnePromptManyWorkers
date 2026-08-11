package com.bbd.live;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One real photograph per idea, searched once and then served from disk.
 *
 * <p>The first version of this seeded a placeholder service, which has no search:
 * asked for "wheelie bins driveway" it returned silhouettes in a tunnel. A
 * confidently wrong photograph is worse than none, because the reader believes
 * it. This searches Openverse instead, which indexes real openly-licensed
 * photography and needs no API key.
 *
 * <p>The talk's claim is that nothing leaves the laptop while the room is
 * watching, and that claim is worth keeping. The search and the download happen
 * during the background build; the bytes are cached and embedded inline, so
 * serving a page on stage touches no network. If the machine is offline, or if
 * nothing relevant comes back, the page falls back to the drawn mockup. Which is
 * why the drawn mockup still has to be good.
 */
public final class Photos {
    private Photos() {}

    /** A photograph and the credit its licence requires. */
    public record Shot(String dataUri, String credit) {
        public boolean any() { return dataUri != null && !dataUri.isBlank(); }
        public static final Shot NONE = new Shot("", "");
    }

    private static final Map<String, Shot> MEMORY = new ConcurrentHashMap<>();
    private static final Path CACHE = Path.of(System.getProperty("user.home"), ".bbd-live-photos");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public static Shot find(String scene, boolean allowed) {
        if (!allowed || scene == null || scene.isBlank()) return Shot.NONE;
        String key = key(scene);
        Shot hit = MEMORY.get(key);
        if (hit != null) return hit;
        try {
            Path img = CACHE.resolve(key + ".jpg");
            Path meta = CACHE.resolve(key + ".txt");
            byte[] bytes;
            String credit;
            if (Files.exists(img)) {
                bytes = Files.readAllBytes(img);
                credit = Files.exists(meta) ? Files.readString(meta).strip() : "";
            } else {
                String[] found = search(scene);
                if (found == null) return remember(key, Shot.NONE);
                bytes = download(found[0]);
                if (bytes == null || bytes.length < 4096) return remember(key, Shot.NONE);
                credit = found[1];
                Files.createDirectories(CACHE);
                Files.write(img, bytes);
                Files.writeString(meta, credit);
            }
            return remember(key, new Shot(
                    "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes), credit));
        } catch (Exception e) {
            return Shot.NONE;
        }
    }

    private static Shot remember(String key, Shot s) { MEMORY.put(key, s); return s; }

    /** Whether the photo is already on disk, so a stage run never waits on a network. */
    public static boolean cached(String scene) {
        if (scene == null || scene.isBlank()) return false;
        return MEMORY.containsKey(key(scene)) || Files.exists(CACHE.resolve(key(scene) + ".jpg"));
    }

    /** Returns {imageUrl, credit} for the best relevant result, or null. */
    private static String[] search(String scene) throws Exception {
        String url = "https://api.openverse.org/v1/images/?q=" +
                URLEncoder.encode(scene, StandardCharsets.UTF_8) +
                "&page_size=12&license_type=commercial&mature=false&extension=jpg";
        HttpRequest r = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "bbd-live-build/1.0 (conference demo)")
                .timeout(Duration.ofSeconds(9)).GET().build();
        HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;

        List<String> words = new ArrayList<>();
        for (String w : scene.toLowerCase(Locale.ROOT).split("[^a-z]+")) if (w.length() > 3) words.add(w);

        // Openverse ranks loosely: asked for wheelie bins, the top hit was a shop
        // window. Prefer a result whose own title mentions what was asked for.
        String[] best = null;
        for (Map<String, String> hit : results(res.body())) {
            String title = hit.getOrDefault("title", "").toLowerCase(Locale.ROOT);
            String img = hit.get("url");
            if (img == null || img.isBlank()) continue;
            boolean onTopic = words.isEmpty() || words.stream().anyMatch(title::contains);
            String credit = credit(hit);
            if (onTopic) return new String[]{img, credit};
            if (best == null) best = new String[]{img, credit};
        }
        return best;
    }

    private static String credit(Map<String, String> hit) {
        String by = hit.getOrDefault("creator", "").strip();
        String lic = hit.getOrDefault("license", "").toUpperCase(Locale.ROOT);
        if (by.isBlank() && lic.isBlank()) return "";
        if (by.isBlank()) return "CC " + lic;
        return by + " · CC " + lic;
    }

    /**
     * Reads the results with a real JSON parser.
     *
     * <p>The first version matched result objects with a regex that assumed no
     * nested braces. Openverse results carry nested tag objects, so the pattern
     * matched a sub-object with no url in it and every search silently returned
     * nothing. Jackson is already on the classpath because Spring Boot puts it
     * there; there was never a reason to hand-roll this.
     */
    private static List<Map<String, String>> results(String json) {
        List<Map<String, String>> out = new ArrayList<>();
        try {
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            for (var node : root.path("results")) {
                Map<String, String> m = new java.util.HashMap<>();
                for (String f : new String[]{"url", "title", "creator", "license"})
                    if (node.hasNonNull(f)) m.put(f, node.get(f).asText());
                if (m.containsKey("url")) out.add(m);
                if (out.size() >= 12) break;
            }
        } catch (Exception e) { /* an unreadable body is simply no photo */ }
        return out;
    }

    private static byte[] download(String url) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "bbd-live-build/1.0 (conference demo)")
                .timeout(Duration.ofSeconds(12)).GET().build();
        HttpResponse<InputStream> res = HTTP.send(r, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) return null;
        try (InputStream in = res.body()) {
            byte[] b = in.readAllBytes();
            return b.length > 3_500_000 ? null : b;      // keep the page a sane size
        }
    }

    private static String key(String scene) {
        return scene.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}

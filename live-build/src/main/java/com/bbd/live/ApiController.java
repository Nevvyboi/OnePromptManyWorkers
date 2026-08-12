package com.bbd.live;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/** Audience, presenter, and stage all talk to the crew through here. */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final CrewService crew;
    @Value("${server.port:8080}") String port;
    /**
     * A public https URL for the room to scan, from a tunnel. Without it the QR
     * points at this laptop's LAN address, which only works if everyone joins the
     * same wifi: a room full of people fighting a hotspot is the most fragile part
     * of the whole demo.
     */
    @Value("${live.publicUrl:}") String publicUrl;

    public ApiController(CrewService crew) { this.crew = crew; }

    private String audienceUrl() {
        if (publicUrl != null && !publicUrl.isBlank())
            return publicUrl.endsWith("/") ? publicUrl : publicUrl + "/";
        return "http://" + Net.lanIp() + ":" + port + "/";
    }

    @GetMapping("/info")
    public Map<String, Object> info() { return crew.info(audienceUrl()); }

    /** Presenter only: this is unvetted text with people's names on it. */
    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> queue(@RequestParam(required = false) String key) {
        if (!crew.keyOk(key)) return ResponseEntity.status(403).body(Map.of("ideas", List.of(), "error", "presenter only"));
        return ResponseEntity.ok(crew.queuePayload());
    }

    /** Presenter only: drop an idea you would rather not read out. */
    @PostMapping("/hide/{id}")
    public ResponseEntity<Map<String, Object>> hide(@PathVariable String id,
                                                    @RequestParam(required = false) String key) {
        if (!crew.keyOk(key)) return ResponseEntity.status(403).body(Map.of("ok", false, "error", "presenter only"));
        return ResponseEntity.ok(crew.hide(id));
    }

    /** Presenter only: remove an idea for good, from the queue, gallery and disk. */
    @PostMapping("/delete/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id,
                                                      @RequestParam(required = false) String key) {
        if (!crew.keyOk(key)) return ResponseEntity.status(403).body(Map.of("ok", false, "error", "presenter only"));
        return ResponseEntity.ok(crew.delete(id));
    }

    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody SubmitReq req, HttpServletRequest http) {
        return crew.submit(req.text(), req.name(), Boolean.TRUE.equals(req.showName()), clientIp(http));
    }

    /** Presenter only: open or close the doors on submissions. */
    @PostMapping("/gate")
    public ResponseEntity<Map<String, Object>> gate(@RequestParam(required = false) String key,
                                                    @RequestParam(defaultValue = "true") boolean open) {
        if (!crew.keyOk(key)) return ResponseEntity.status(403).body(Map.of("ok", false, "error", "presenter only"));
        return ResponseEntity.ok(crew.gate(open));
    }

    /** Presenter only: auto-advance the stage through the queue, one idea after another. */
    @PostMapping("/auto")
    public ResponseEntity<Map<String, Object>> auto(@RequestParam(required = false) String key,
                                                    @RequestParam(defaultValue = "true") boolean on) {
        if (!crew.keyOk(key)) return ResponseEntity.status(403).body(Map.of("ok", false, "error", "presenter only"));
        return ResponseEntity.ok(crew.auto(on));
    }

    private static String clientIp(HttpServletRequest r) {
        String fwd = r.getHeader("X-Forwarded-For");
        return (fwd != null && !fwd.isBlank()) ? fwd.split(",")[0].trim() : r.getRemoteAddr();
    }

    /** Presenter only. The room is on your hotspot and a dev will find this. */
    @PostMapping("/run/{id}")
    public ResponseEntity<Map<String, Object>> run(@PathVariable String id,
                                                   @RequestParam(required = false) String key) {
        if (!crew.keyOk(key)) return ResponseEntity.status(403).body(Map.of("ok", false, "error", "presenter only"));
        return ResponseEntity.ok(crew.run(id));
    }

    /** The closing reveal: everything the crew built while the talk was going. */
    @GetMapping("/gallery")
    public Map<String, Object> gallery() { return crew.galleryPayload(); }

    /** Has this person's page been built yet? Their phone polls this. */
    @GetMapping("/mine/{id}")
    public Map<String, Object> mine(@PathVariable String id) { return crew.mine(id); }

    /**
     * The live stream the stage listens to. A proxy that buffers this delivers
     * nothing until the run is over, which is the same as being broken: through a
     * Cloudflare tunnel the stage sat empty until these headers were set. Content
     * type alone is not enough, the proxy has to be told not to buffer and not to
     * compress, and the connection has to be kept alive.
     */
    @GetMapping(value = "/events", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter events(HttpServletResponse res) {
        res.setHeader("X-Accel-Buffering", "no");     // nginx and friends
        res.setHeader("Cache-Control", "no-cache, no-transform");
        res.setHeader("Connection", "keep-alive");
        res.setHeader("Content-Encoding", "identity"); // never gzip a live stream
        return crew.subscribe();
    }

    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] qr() { return Qr.png(audienceUrl(), 480); }

    public record SubmitReq(String text, String name, Boolean showName) {}
}

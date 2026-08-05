package com.bbd.live;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/** Audience, presenter, and stage all talk to the crew through here. */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final CrewService crew;
    @Value("${server.port:8080}") String port;

    public ApiController(CrewService crew) { this.crew = crew; }

    private String audienceUrl() { return "http://" + Net.lanIp() + ":" + port + "/"; }

    @GetMapping("/info")
    public Map<String, Object> info() { return crew.info(audienceUrl()); }

    @GetMapping("/queue")
    public Map<String, Object> queue() { return crew.queuePayload(); }

    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody SubmitReq req, HttpServletRequest http) {
        return crew.submit(req.text(), req.name(), clientIp(http));
    }

    private static String clientIp(HttpServletRequest r) {
        String fwd = r.getHeader("X-Forwarded-For");
        return (fwd != null && !fwd.isBlank()) ? fwd.split(",")[0].trim() : r.getRemoteAddr();
    }

    @PostMapping("/run/{id}")
    public Map<String, Object> run(@PathVariable String id) { return crew.run(id); }

    @GetMapping("/events")
    public SseEmitter events() { return crew.subscribe(); }

    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] qr() { return Qr.png(audienceUrl(), 480); }

    public record SubmitReq(String text, String name) {}
}

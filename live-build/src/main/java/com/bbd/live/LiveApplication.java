package com.bbd.live;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Configuration;

/**
 * One Prompt. Many Workers. Live Build.
 *
 * <p>The audience scans a QR, submits an idea from their phone, and a crew of
 * local agents builds a landing page for it, live, on the projector. Every
 * token runs on this laptop through Ollama. Nothing leaves the room.
 */
@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class LiveApplication {
    public static void main(String[] args) {
        SpringApplication.run(LiveApplication.class, args);
    }

    /** Map the extra views onto their static pages. */
    @Configuration
    static class Views implements WebMvcConfigurer {
        @Override public void addViewControllers(ViewControllerRegistry r) {
            r.addViewController("/stage").setViewName("forward:/stage.html");
            r.addViewController("/join").setViewName("forward:/join.html");
            r.addViewController("/gallery").setViewName("forward:/gallery.html");
            // /control is gated below, so it is not a plain forward
        }
    }

    /** The presenter panel, behind a key so the room cannot drive your projector. */
    @org.springframework.stereotype.Controller
    static class Control {
        private final CrewService crew;
        Control(CrewService crew) { this.crew = crew; }

        @org.springframework.web.bind.annotation.GetMapping("/control")
        public String control(@org.springframework.web.bind.annotation.RequestParam(required = false) String key) {
            return crew.keyOk(key) ? "forward:/control.html" : "forward:/presenter-only.html";
        }
    }

    /** The real, standalone landing page for one idea. Openable and downloadable. */
    @org.springframework.stereotype.Controller
    static class Pages {
        private final CrewService crew;
        Pages(CrewService crew) { this.crew = crew; }

        @org.springframework.web.bind.annotation.GetMapping(value = "/page/{id}", produces = "text/html; charset=utf-8")
        @org.springframework.web.bind.annotation.ResponseBody
        public org.springframework.http.ResponseEntity<String> page(
                @org.springframework.web.bind.annotation.PathVariable String id,
                @org.springframework.web.bind.annotation.RequestParam(required = false) String download) {
            Model.Idea idea = crew.built(id);
            if (idea == null) return org.springframework.http.ResponseEntity.status(404)
                    .body("<body style='font-family:system-ui;padding:3rem'>No page for that id yet.</body>");
            // nothing is served without passing the taste guard first
            TasteGuard.Report first = TasteGuard.audit(PageWriter.render(idea));
            TasteGuard.Report guard = TasteGuard.audit(PageWriter.render(idea, first.summary()));
            if (!guard.passed())
                System.out.println("  taste guard flagged: "
                        + guard.violations().stream().map(TasteGuard.Violation::id).toList());
            var b = org.springframework.http.ResponseEntity.ok();
            if ("1".equals(download))
                b = b.header("Content-Disposition", "attachment; filename=\"" + PageWriter.slug(idea.result) + ".html\"");
            return b.body(guard.html());
        }
    }

    /** Prints the three URLs, including the private one, once the port is known. */
    @org.springframework.stereotype.Component
    static class Banner {
        Banner(CrewService crew, org.springframework.core.env.Environment env) { this.crew = crew; this.env = env; }
        private final CrewService crew; private final org.springframework.core.env.Environment env;

        @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
        public void show() {
            String port = env.getProperty("server.port", "8080");
            System.out.println("\n  Live Build running");
            System.out.println("  audience : http://" + Net.lanIp() + ":" + port + "/          <- the QR points here");
            System.out.println("  stage    : http://localhost:" + port + "/stage           <- the projector");
            System.out.println("  control  : http://localhost:" + port + "/control?key=" + crew.controlKey() + "  <- YOU (keep this private)\n");
        }
    }
}

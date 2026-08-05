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

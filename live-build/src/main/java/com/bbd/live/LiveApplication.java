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

    /** Map the two extra views onto their static pages. */
    @Configuration
    static class Views implements WebMvcConfigurer {
        @Override public void addViewControllers(ViewControllerRegistry r) {
            r.addViewController("/stage").setViewName("forward:/stage.html");
            r.addViewController("/control").setViewName("forward:/control.html");
            r.addViewController("/join").setViewName("forward:/join.html");
        }
    }
}

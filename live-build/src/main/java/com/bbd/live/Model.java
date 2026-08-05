package com.bbd.live;

import java.util.List;

/** The small data shapes the crew passes around. */
public final class Model {
    private Model() {}

    /** One audience submission. Mutable so we can flip its status when built. */
    public static final class Idea {
        public final String id;
        public final String text;
        public final String name;
        public volatile String status = "new";
        public volatile boolean flagged = false;   // looks crude, presenter should read it
        public volatile boolean hidden = false;    // presenter dropped it
        public Idea(String id, String text, String name) { this.id = id; this.text = text; this.name = name; }
    }

    /** The Copywriter's structured output. This is the talk's "structured output" lever, live. */
    public record Copy(String badge, String headline, String subhead, String cta, List<Feature> features) {}
    public record Feature(String title, String body) {}

    /** The Designer's choice, sent straight to the browser as CSS custom properties. */
    public record Palette(String bg, String surface, String ink, String muted, String primary, String accent, String font) {}
}

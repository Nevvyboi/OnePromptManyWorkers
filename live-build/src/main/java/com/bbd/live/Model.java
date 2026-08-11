package com.bbd.live;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The small data shapes the crew passes around. */
public final class Model {
    private Model() {}

    /** One audience submission. Mutable so we can flip its status when built.
     *  Jackson-annotated so the whole queue can be saved to disk and restored on
     *  restart: the three identity fields come back through the creator, the rest
     *  through their public fields. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Idea {
        public final String id;
        public final String text;
        public final String name;
        public volatile String status = "new";
        public volatile boolean flagged = false;   // looks crude, presenter should read it
        public volatile boolean hidden = false;    // presenter dropped it
        public volatile Result result;             // what the crew made, for the gallery
        public volatile boolean showName = false;  // only if they ticked the box
        public volatile long ms = 0;               // how long the crew took
        @JsonCreator
        public Idea(@JsonProperty("id") String id, @JsonProperty("text") String text,
                    @JsonProperty("name") String name) { this.id = id; this.text = text; this.name = name; }
        @JsonIgnore
        public String shownName() { return (showName && name != null) ? name : ""; }
    }

    /** The Copywriter's structured output. This is the talk's "structured output" lever, live. */
    public record Copy(String badge, String headline, String subhead, String cta, List<Feature> features) {}
    public record Feature(String title, String body) {}

    /** The Designer's choice, sent straight to the browser as CSS custom properties. */
    public record Palette(String family, String bg, String surface, String ink, String muted, String primary, String accent, String font) {}

    /** The Namer's output. */
    public record Product(String name, String tagline) {}

    /** The Illustrator's output. The browser draws this as SVG, so no image files. */
    public record Art(String kind, int seed, List<String> colors, List<String> rows, String scene) {}

    /** The Reviewer's one concrete improvement. */
    public record Review(String verdict, String field, String value, String note) {}

    /** One pricing tier from the Pricer. */
    public record Tier(String name, String price, String per, String blurb, List<String> lines, boolean featured) {}

    /** One question and answer. */
    public record Qa(String q, String a) {}

    /** Everything the crew made for one idea, kept for the closing gallery. */
    /** One page section the Architect chose: how, catalog, story or proof. */
    public record Section(String kind, String heading, String intro, List<Feature> items) {}

    public record Result(Product product, Palette palette, Copy copy, Art art, Review review,
                         List<Tier> pricing, List<Qa> faq, String skeptic, String layout,
                         String insight, boolean showPricing,
                         List<Section> sections, String heroStyle) {}
}

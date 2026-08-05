package com.bbd.live;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.bbd.live.Model.*;

/**
 * Plan, dispatch, synthesize. One idea in, a crew runs, a landing page streams
 * out to every connected stage over Server-Sent Events. In live mode each agent
 * is a local Qwen call; if one stumbles it falls back to a house answer, so the
 * demo never dies on stage. Mock mode skips the model entirely.
 */
@Service
public class CrewService {

    /** Presenter key. Auto-generated unless you pin one with --live.key=... */
    @Value("${live.key:}") String configuredKey;
    private final String key = Long.toString(Math.abs(new java.security.SecureRandom().nextLong()), 36).substring(0, 6);
    public String controlKey() { return (configuredKey == null || configuredKey.isBlank()) ? key : configuredKey; }
    public boolean keyOk(String given) { return controlKey().equals(given); }

    @Value("${live.mock:false}") boolean mock;
    @Value("${live.model:qwen2.5:3b}") String modelName;
    /** The background queue uses a smaller model so the on-stage run always wins. */
    @Value("${live.backgroundModel:qwen2.5:1.5b}") String backgroundModelName;
    @Value("${live.ollama:http://localhost:11434}") String ollamaUrl;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final List<Idea> queue = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ExecutorService runner = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;
    private volatile boolean submissionsOpen = true;   // you open and close these from /control

    // limits so a live crowd can't flood or embarrass the queue
    private static final int MAX_QUEUE = 250;      // total ideas kept
    private static final int MAX_PER_IP = 8;       // ideas per phone
    private static final long COOLDOWN_MS = 6000;  // wait between submits per phone
    private final Map<String, long[]> ipState = new ConcurrentHashMap<>(); // ip -> [lastMs, count]
    // A wordlist is a speed bump, not a filter. It cannot catch something crude
    // that uses no rude words ("a toilet app that scores your number twos").
    // The real guard is you: nothing reaches the projector until you press Run,
    // anything suspicious is flagged, and you can Hide it.
    private static final Set<String> BLOCK = Set.of(
            "fuck", "fucking", "shit", "shite", "cunt", "bitch", "bastard", "asshole", "arsehole",
            "dick", "prick", "piss", "damn", "bollocks", "wank", "twat", "slut", "whore",
            "nigger", "faggot", "retard", "rape", "nazi");
    private static final List<String> CRUDE = List.of(
            "toilet", "poo", "poop", "turd", "fart", "genital", "penis", "vagina",
            "boob", "nude", "naked", "porn", "sex", "orgasm", "nipple", "butthole", "anus");

    /** One model wearing seven hats. Two of these exist: the stage crew on the
     *  good model, and a quicker crew on a smaller one for background work. */
    private static final class Bundle {
        Agents.Copywriter copywriter; Agents.Designer designer; Agents.Skeptic skeptic;
        Agents.Namer namer; Agents.Illustrator illustrator; Agents.Reviewer reviewer; Agents.Pricer pricer;
    }
    private Bundle stageCrew, fastCrew;

    // ---------- audience + presenter API ----------

    /** Open or close the doors. Presenter only. */
    public Map<String, Object> gate(boolean open) {
        submissionsOpen = open;
        broadcast("gate", Map.of("open", open));
        broadcast("queue", queuePayload());
        return Map.of("ok", true, "open", open);
    }
    public boolean submissionsOpen() { return submissionsOpen; }

    public Map<String, Object> submit(String text, String name, boolean showName, String ip) {
        if (!submissionsOpen)
            return Map.of("ok", false, "closed", true, "error", "Submissions are closed. Watch the big screen.");
        String t = clip(text == null ? "" : text.strip());
        if (t.length() < 3) return err("Give it a few more words.");
        if (isBlocked(t)) return err("Let's keep it friendly.");
        if (queue.size() >= MAX_QUEUE) return err("The queue is full for now. Thanks!");
        for (Idea i : queue) if (i.text.equalsIgnoreCase(t)) return err("Someone already sent that one.");

        long now = System.currentTimeMillis();
        long[] st = ipState.computeIfAbsent(ip == null ? "?" : ip, k -> new long[]{0, 0});
        synchronized (st) {
            if (now - st[0] < COOLDOWN_MS) {
                long wait = (long) Math.ceil((COOLDOWN_MS - (now - st[0])) / 1000.0);
                return Map.of("ok", false, "retryAfter", wait,
                        "error", "One at a time. Try again in " + wait + " second" + (wait == 1 ? "" : "s") + ".");
            }
            if (st[1] >= MAX_PER_IP) return err("That's plenty from you. Let others have a go.");
            st[0] = now; st[1]++;
        }

        Idea idea = new Idea(String.valueOf(nextId.getAndIncrement()), t, name == null ? "" : name.strip());
        idea.flagged = isCrude(t);
        idea.showName = showName;
        queue.add(idea);
        broadcast("queue", queuePayload());
        broadcast("tally", Map.of("total", queue.size()));
        // hand back exactly what we stored, so the phone can never show
        // something different from what the room will see
        return Map.of("ok", true, "id", idea.id, "position", queue.size(), "stored", t, "mock", mock);
    }

    /** Presenter dropped this one. It leaves the list and can no longer be run. */
    public Map<String, Object> hide(String id) {
        queue.stream().filter(i -> i.id.equals(id)).findFirst().ifPresent(i -> i.hidden = true);
        broadcast("queue", queuePayload());
        return Map.of("ok", true);
    }

    private Map<String, Object> err(String message) { return Map.of("ok", false, "error", message); }

    /** Cut on a word boundary, never mid-word, so nothing reads as broken on stage. */
    private static String clip(String t) {
        int max = 120;
        if (t.length() <= max) return t;
        String cut = t.substring(0, max - 1);
        int sp = cut.lastIndexOf(' ');
        String kept = (sp > max * 0.6) ? cut.substring(0, sp) : cut;
        return kept.replaceAll("[,;:.\\s]+$", "") + "…";
    }

    private boolean isBlocked(String text) {
        for (String w : text.toLowerCase().split("[^a-z]+")) if (BLOCK.contains(w)) return true;
        return false;
    }

    /** Not blocked, just flagged, so the presenter reads it before it ever runs. */
    private boolean isCrude(String text) {
        String l = text.toLowerCase();
        return CRUDE.stream().anyMatch(l::contains);
    }

    public Map<String, Object> queuePayload() {
        List<Map<String, Object>> ideas = new ArrayList<>();
        for (Idea i : queue) {
            if (i.hidden) continue;
            ideas.add(Map.of("id", i.id, "text", i.text, "name", i.shownName(),
                    "status", i.status, "flagged", i.flagged, "ms", i.ms));
        }
        return Map.of("open", submissionsOpen, "ideas", ideas);
    }

    public Map<String, Object> run(String id) {
        if (running) return Map.of("ok", false, "error", "already running");
        Idea idea;
        if ("demo".equals(id)) idea = new Idea("demo", "an app that waters your plants when you forget", "the house crew");
        else idea = queue.stream().filter(i -> i.id.equals(id) && !i.hidden).findFirst().orElse(null);
        if (idea == null) return Map.of("ok", false, "error", "not found");
        running = true;
        Idea target = idea;
        runner.submit(() -> { try { runCrew(target); } catch (Exception e) { e.printStackTrace(); } finally { running = false; } });
        return Map.of("ok", true);
    }

    public SseEmitter subscribe() {
        SseEmitter em = new SseEmitter(0L); // never time out
        emitters.add(em);
        em.onCompletion(() -> emitters.remove(em));
        em.onTimeout(() -> emitters.remove(em));
        em.onError(e -> emitters.remove(em));
        send(em, "info", Map.of("mock", mock, "tally", queue.size()));
        send(em, "queue", queuePayload());
        send(em, "gallery", galleryPayload());
        send(em, "gate", Map.of("open", submissionsOpen));
        return em;
    }

    // ---------- the run: a spider net, not a line ----------
    //
    // Copywriter and Designer start together. Whichever finishes first assists
    // the other (in practice the Designer's one-word vibe lands first, so it
    // hands the Copywriter a tone hint). Both feed the Builder. The Builder
    // feeds the Skeptic. And the Skeptic's critique loops BACK to the
    // Copywriter, who revises the headline live. That feedback edge is what
    // makes it a net.

    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    @Value("${live.background:true}") boolean backgroundBuilds;

    private static final List<Map<String, String>> CREW = List.of(
            Map.of("key", "namer", "label", "name"),
            Map.of("key", "copywriter", "label", "copy"),
            Map.of("key", "designer", "label", "design"),
            Map.of("key", "illustrator", "label", "art"),
            Map.of("key", "pricer", "label", "price"),
            Map.of("key", "builder", "label", "build"),
            Map.of("key", "reviewer", "label", "review"),
            Map.of("key", "skeptic", "label", "skeptic"));

    private final Map<String, Long> marks = new ConcurrentHashMap<>();
    private void startAgent(String k) { marks.put(k, System.currentTimeMillis()); }
    private void doneAgent(String k) {
        long ms = System.currentTimeMillis() - marks.getOrDefault(k, System.currentTimeMillis());
        broadcast("timing", Map.of("key", k, "ms", ms));
    }

    private void runCrew(Idea idea) throws Exception {
        long t0 = System.currentTimeMillis();
        marks.clear();
        broadcast("run-start", Map.of("id", idea.id, "idea", idea.text, "name", idea.name));
        sleep(420);
        broadcast("graph", Map.of("nodes", CREW, "edges", List.of(
                edge("namer", "copywriter", false),
                edge("namer", "illustrator", false),
                edge("designer", "copywriter", false),
                edge("designer", "illustrator", false),
                edge("namer", "pricer", false),
                edge("pricer", "builder", false),
                edge("copywriter", "builder", false),
                edge("designer", "builder", false),
                edge("illustrator", "builder", false),
                edge("builder", "reviewer", false),
                edge("builder", "skeptic", false),
                edge("reviewer", "copywriter", true),
                edge("skeptic", "copywriter", true))));
        sleep(380);

        // round one: three agents at once
        startAgent("namer"); broadcast("agent-state", Map.of("key", "namer", "state", "working", "note", "inventing a name…"));
        startAgent("designer"); broadcast("agent-state", Map.of("key", "designer", "state", "working", "note", "choosing a palette…"));
        startAgent("copywriter"); broadcast("agent-state", Map.of("key", "copywriter", "state", "working", "note", "drafting the hero…"));
        Future<String> nameF = pool.submit(() -> safeName(stage(), idea.text));
        Future<Palette> paletteF = pool.submit(() -> safePalette(stage(), idea.text));
        Future<Copy> copyF = pool.submit(() -> safeCopy(stage(), idea.text));

        // the namer is the smallest job, so it lands first and helps two others
        Product product = new Product(nameF.get(), "");
        broadcast("worker-done", Map.of("key", "namer", "payload", product));
        doneAgent("namer"); broadcast("agent-state", Map.of("key", "namer", "state", "assisting", "note", "done first, so it helps"));
        broadcast("flow", Map.of("from", "namer", "to", "copywriter", "what", "the name"));
        broadcast("flow", Map.of("from", "namer", "to", "illustrator", "what", "the name"));
        broadcast("flow", Map.of("from", "namer", "to", "pricer", "what", "the name"));
        sleep(500);
        doneAgent("namer"); broadcast("agent-state", Map.of("key", "namer", "state", "done"));

        Palette palette = paletteF.get();
        broadcast("worker-done", Map.of("key", "designer", "payload", palette));
        doneAgent("designer"); broadcast("agent-state", Map.of("key", "designer", "state", "assisting", "note", "hands the illustrator its colours"));
        broadcast("flow", Map.of("from", "designer", "to", "copywriter", "what", "tone hint"));
        broadcast("flow", Map.of("from", "designer", "to", "illustrator", "what", "palette"));
        sleep(500);
        doneAgent("designer"); broadcast("agent-state", Map.of("key", "designer", "state", "done"));

        // the illustrator could only start once it had a name and colours
        startAgent("illustrator"); broadcast("agent-state", Map.of("key", "illustrator", "state", "working", "note", "drawing the hero artwork…"));
        Art art = safeArt(stage(), idea.text, palette);
        broadcast("worker-done", Map.of("key", "illustrator", "payload", art));
        doneAgent("illustrator"); broadcast("agent-state", Map.of("key", "illustrator", "state", "done", "note", "artwork ready"));
        broadcast("flow", Map.of("from", "illustrator", "to", "builder", "what", "artwork"));

        startAgent("pricer"); broadcast("agent-state", Map.of("key", "pricer", "state", "working", "note", "working out three tiers…"));
        List<Tier> pricingLive = safePricing(stage(), idea.text, product.name());
        broadcast("worker-done", Map.of("key", "pricer", "payload", Map.of("pricing", pricingLive)));
        doneAgent("pricer"); broadcast("agent-state", Map.of("key", "pricer", "state", "done", "note", "three tiers, one featured"));
        broadcast("flow", Map.of("from", "pricer", "to", "builder", "what", "pricing"));

        Copy copy = copyF.get();
        broadcast("worker-done", Map.of("key", "copywriter", "payload", copy));
        doneAgent("copywriter"); broadcast("agent-state", Map.of("key", "copywriter", "state", "done", "note", "hero: \"" + copy.headline() + "\""));
        broadcast("flow", Map.of("from", "copywriter", "to", "builder", "what", "copy"));
        broadcast("flow", Map.of("from", "designer", "to", "builder", "what", "palette"));

        startAgent("builder"); broadcast("agent-state", Map.of("key", "builder", "state", "working", "note", "assembling the page…"));
        sleep(800);
        broadcast("worker-done", Map.of("key", "builder"));
        doneAgent("builder"); broadcast("agent-state", Map.of("key", "builder", "state", "done", "note", "page assembled"));

        // two checkers read the finished page together
        broadcast("flow", Map.of("from", "builder", "to", "reviewer", "what", "the page"));
        broadcast("flow", Map.of("from", "builder", "to", "skeptic", "what", "the page"));
        startAgent("reviewer"); broadcast("agent-state", Map.of("key", "reviewer", "state", "working", "note", "checking it over…"));
        startAgent("skeptic"); broadcast("agent-state", Map.of("key", "skeptic", "state", "working", "note", "poking holes…"));
        Future<String> polishF = pool.submit(() -> safePolish(stage(), idea.text, copy.cta()));
        String note = safeSkeptic(stage(), idea.text);

        String polished = polishF.get();
        Review review = new Review("one thing to sharpen", "cta", polished, "the call to action was generic");
        broadcast("worker-done", Map.of("key", "reviewer", "payload", review));
        doneAgent("reviewer"); broadcast("agent-state", Map.of("key", "reviewer", "state", "done", "note", review.note()));
        broadcast("flow", Map.of("from", "reviewer", "to", "copywriter", "what", "polish the cta"));
        startAgent("copywriter"); broadcast("agent-state", Map.of("key", "copywriter", "state", "working", "note", "taking the reviewer's note…"));
        sleep(700);
        broadcast("revise", Map.of("field", "cta", "value", polished, "by", "reviewer"));

        broadcast("worker-done", Map.of("key", "skeptic", "payload", Map.of("note", note)));
        doneAgent("skeptic"); broadcast("agent-state", Map.of("key", "skeptic", "state", "done"));
        broadcast("flow", Map.of("from", "skeptic", "to", "copywriter", "what", "critique"));
        sleep(700);
        String revised = safeRevise(stage(), idea.text, copy.headline(), note);
        broadcast("revise", Map.of("field", "headline", "value", revised, "by", "skeptic"));
        broadcast("flow", Map.of("from", "copywriter", "to", "builder", "what", "revised hero"));
        doneAgent("copywriter"); broadcast("agent-state", Map.of("key", "copywriter", "state", "done", "note", "rewritten. the web is settled"));

        Copy finalCopy = new Copy(copy.badge(), revised, copy.subhead(), polished, copy.features());
        idea.result = new Result(product, palette, finalCopy, art, review,
                safePricing(stage(), idea.text, product.name()), buildFaq(idea.text), note);
        idea.ms = System.currentTimeMillis() - t0;
        broadcast("timing", Map.of("key", "total", "ms", idea.ms));
        idea.status = "done";
        broadcast("run-done", Map.of("id", idea.id));
        broadcast("queue", queuePayload());
        broadcast("gallery", galleryPayload());
    }

    // ---------- quiet background builds, while the talk is happening ----------

    /** Builds one waiting idea at a time, with no stage events, so the gallery
     *  fills up during the talk without anyone seeing it happen. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 4000, initialDelay = 6000)
    public void backgroundTick() {
        if (running || !backgroundBuilds) return;
        Idea next = queue.stream().filter(i -> !i.hidden && i.result == null).findFirst().orElse(null);
        if (next == null) return;
        long bt = System.currentTimeMillis();
        try {
            Product product = new Product(safeName(fast(), next.text), "");
            Palette palette = safePalette(fast(), next.text);
            Copy copy = safeCopy(fast(), next.text);
            Art art = safeArt(fast(), next.text, palette);
            String note = safeSkeptic(fast(), next.text);
            String polished = safePolish(fast(), next.text, copy.cta());
            String revised = safeRevise(fast(), next.text, copy.headline(), note);
            Copy finalCopy = new Copy(copy.badge(), revised, copy.subhead(), polished, copy.features());
            next.result = new Result(product, palette, finalCopy, art,
                    new Review("one thing to sharpen", "cta", polished, "the call to action was generic"),
                    safePricing(fast(), next.text, product.name()), buildFaq(next.text), note);
            next.ms = System.currentTimeMillis() - bt;
            next.status = "built";
            broadcast("queue", queuePayload());
            broadcast("gallery", galleryPayload());
        } catch (Exception e) {
            System.out.println("  (background build skipped: " + e.getMessage() + ")");
        }
    }

    public Map<String, Object> galleryPayload() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Idea i : queue) {
            if (i.hidden || i.result == null) continue;
            items.add(Map.of("id", i.id, "name", i.shownName(), "idea", i.text, "result", i.result, "ms", i.ms));
        }
        return Map.of("total", items.size(), "items", items);
    }

    private Map<String, String> node(String key, String label) { return Map.of("key", key, "label", label); }
    private Map<String, Object> edge(String from, String to, boolean feedback) {
        return feedback ? Map.of("from", from, "to", to, "feedback", true) : Map.of("from", from, "to", to);
    }

    // ---------- agents with fallbacks ----------

    private Bundle build(String which) {
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(ollamaUrl).modelName(which)
                .temperature(0.6).timeout(Duration.ofMinutes(2)).build();
        Bundle b = new Bundle();
        b.copywriter = AiServices.create(Agents.Copywriter.class, model);
        b.designer = AiServices.create(Agents.Designer.class, model);
        b.skeptic = AiServices.create(Agents.Skeptic.class, model);
        b.namer = AiServices.create(Agents.Namer.class, model);
        b.illustrator = AiServices.create(Agents.Illustrator.class, model);
        b.reviewer = AiServices.create(Agents.Reviewer.class, model);
        b.pricer = AiServices.create(Agents.Pricer.class, model);
        return b;
    }

    /** The crew on stage. Best model, because the room is watching. */
    private synchronized Bundle stage() {
        if (stageCrew == null) stageCrew = build(modelName);
        return stageCrew;
    }

    /** The crew working the queue in the background. Smaller model on purpose:
     *  it must never slow down or starve the run happening on the projector. */
    private synchronized Bundle fast() {
        if (fastCrew == null) fastCrew = build(backgroundModelName);
        return fastCrew;
    }

    private Copy safeCopy(Bundle crew, String idea) throws InterruptedException {
        if (!mock) {
            try { Copy c = crew.copywriter.write(idea);
                if (c != null && c.headline() != null && c.features() != null && !c.features().isEmpty()) return c;
            } catch (RuntimeException e) { System.out.println("  (copywriter improvised, using the house draft)"); }
        } else sleep(1400);
        return cannedCopy(idea, null);
    }

    private Palette safePalette(Bundle crew, String idea) throws InterruptedException {
        String vibe;
        if (!mock) {
            try { vibe = crew.designer.vibe(idea); }
            catch (RuntimeException e) { vibe = null; }
        } else { sleep(1200); vibe = null; }
        return paletteFor(vibe, idea);
    }

    private String safeName(Bundle crew, String idea) throws InterruptedException {
        if (!mock) {
            try { String n = crew.namer.name(idea);
                if (n != null && !n.isBlank()) {
                    n = n.strip().replaceAll("[^A-Za-z]", "");
                    if (n.length() >= 3) return n.substring(0, 1).toUpperCase() + n.substring(1, Math.min(n.length(), 14));
                }
            } catch (RuntimeException e) { /* house name below */ }
        } else sleep(700);
        return cannedName(idea);
    }

    private Art safeArt(Bundle crew, String idea, Palette palette) throws InterruptedException {
        String kind = null;
        if (!mock) {
            try { kind = crew.illustrator.style(idea); }
            catch (RuntimeException e) { /* house art below */ }
        } else sleep(900);
        List<String> kinds = List.of("blobs", "rings", "waves", "grid", "burst");
        String k = kind == null ? null : kind.strip().toLowerCase().replaceAll("[^a-z]", "");
        if (k == null || !kinds.contains(k)) k = kinds.get(Math.floorMod(idea.hashCode() * 31, kinds.size()));
        return new Art(k, Math.floorMod(idea.hashCode(), 997), List.of(palette.primary(), palette.accent()));
    }

    private String safePolish(Bundle crew, String idea, String currentCta) throws InterruptedException {
        if (!mock) {
            try { String p = crew.reviewer.polish(idea, currentCta);
                if (p != null && !p.isBlank()) return p.strip().replaceAll("^\"|\"$", "");
            } catch (RuntimeException e) { /* house polish below */ }
        } else sleep(800);
        String[] options = {"Try it in 30 seconds", "Get it working today", "See it in action",
                "Take it for a spin", "Start free, no card", "Let it do the remembering"};
        return pick(options, idea + "pol");
    }

    private List<Tier> safePricing(Bundle crew, String idea, String productName) throws InterruptedException {
        String mid = null;
        if (!mock) {
            try { mid = crew.pricer.midPrice(idea); }
            catch (RuntimeException e) { /* house prices below */ }
        } else sleep(600);
        if (mid != null) { mid = mid.strip().replaceAll("[^R0-9]", ""); if (!mid.matches("R\\d{2,4}")) mid = null; }
        if (mid == null) mid = pick(new String[]{"R49", "R79", "R99", "R120"}, idea + "p1");
        int midV = Integer.parseInt(mid.substring(1));
        String top = "R" + Math.max(midV * 3, midV + 100);
        String s = topic(idea);
        return List.of(
                new Tier("Starter", "Free", "forever",
                        "Enough to find out whether " + s + " is really your problem.",
                        List.of("One device", "The basics, properly", "No card needed"), false),
                new Tier("Everyday", mid, "per month",
                        "The one most people pick. " + productName + " on all your things.",
                        List.of("Everything in Starter", "Unlimited use", "Share with family", "Email support"), true),
                new Tier("For the street", top, "per month",
                        "When the whole block wants in.",
                        List.of("Everything in Everyday", "Up to 25 people", "Priority support", "Export anything"), false));
    }

    private List<Qa> buildFaq(String idea) {
        String s = topic(idea);
        return List.of(
                new Qa("Does it really work for " + s + "?",
                        "Yes, and it keeps working when you forget about it, which is the entire point."),
                new Qa("Where does my data go?",
                        "Nowhere. It stays on your device. There is no cloud account and nothing to leak."),
                new Qa("What does it cost to start?",
                        "Nothing. Use the free tier for as long as you like, then upgrade only if it earns it."));
    }

    private static String words(String idea) {
        return idea.strip().replaceAll("[.\\s]+$", "").replaceFirst("(?i)^an?\\s+", "");
    }

    private static final Set<String> STOP = Set.of("that","which","who","when","where","so","and",
            "but","for","to","with","if","because","while","after","before","from","by","of","in","on","at");
    private static final Set<String> JUNK = Set.of("app","tool","tracker","finder","scanner","map",
            "planner","timer","thing","system","kit");

    /** An idea arrives as a whole sentence. Splicing that into every slot reads
     *  like a machine wrote it, so take the short noun phrase at the front and
     *  write around that plus the product name instead. */
    private static String subject(String idea) {
        List<String> out = new ArrayList<>();
        for (String t : words(idea).split("\\s+")) {
            if (STOP.contains(t.toLowerCase().replaceAll("[^a-z]", ""))) break;
            out.add(t);
            if (out.size() >= 4) break;
        }
        if (out.isEmpty()) for (String t : words(idea).split("\\s+")) { out.add(t); if (out.size() >= 3) break; }
        return String.join(" ", out).replaceAll("[^\\w\\s-]", "").strip();
    }

    /** One or two words for what it is really about, for FAQ and feature lines. */
    private static String topic(String idea) {
        String[] parts = subject(idea).split("\\s+");
        List<String> core = new ArrayList<>();
        for (String x : parts) if (!JUNK.contains(x.toLowerCase())) core.add(x);
        List<String> use = core.isEmpty() ? Arrays.asList(parts) : core;
        return String.join(" ", use.subList(0, Math.min(2, use.size())));
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /** The finished page for one idea, or null if the crew has not built it yet. */
    public Idea built(String id) {
        return queue.stream().filter(i -> i.id.equals(id) && !i.hidden && i.result != null).findFirst().orElse(null);
    }

    /** Has this person's page been built yet? Their phone polls this. */
    public Map<String, Object> mine(String id) {
        return Map.of("ready", built(id) != null);
    }

    private String cannedName(String idea) {
        String[] w = idea.replaceAll("(?i)^an?\\s+", "").split("[^A-Za-z]+");
        String a = w.length > 0 && w[0].length() > 2 ? w[0] : "Nova";
        String b = w.length > 1 && w[1].length() > 2 ? w[1] : "Kit";
        String A = a.substring(0, 1).toUpperCase() + a.substring(1).toLowerCase();
        String B = b.substring(0, 1).toUpperCase() + b.substring(1).toLowerCase();
        String[] forms = { A + "ly", A + "r", A + "Kit", B + "Hub",
                A.substring(0, Math.min(4, A.length())) + B.substring(0, Math.min(4, B.length())), A + "Wise" };
        return pick(forms, idea + "nm");
    }

    private String safeRevise(Bundle crew, String idea, String headline, String critique) throws InterruptedException {
        if (!mock) {
            try {
                String s = crew.copywriter.revise(idea, headline, critique);
                if (s != null && !s.isBlank()) return s.strip().replaceAll("^\"|\"$", "");
            } catch (RuntimeException e) { System.out.println("  (revision improvised, using the house rewrite)"); }
        } else sleep(1200);
        return cannedRevise(idea);
    }

    private String cannedRevise(String idea) {
        String s = subject(idea), t = topic(idea);
        String[] options = {
                cap(s) + ". Zero effort, zero guilt.",
                "Set it once. It takes it from there.",
                cap(s) + ", minus the guesswork.",
                "The lazy way to handle " + t + ". On purpose.",
                "Stop thinking about " + t + ".",
                cap(s) + ", without the admin." };
        return pick(options, idea + "rev");
    }

    private String safeSkeptic(Bundle crew, String idea) throws InterruptedException {
        if (!mock) {
            try { String s = crew.skeptic.critique(idea); if (s != null && !s.isBlank()) return s.strip(); }
            catch (RuntimeException e) { /* fall through */ }
        } else sleep(1000);
        return cannedSkeptic(idea);
    }

    // ---------- house answers (also the whole of mock mode) ----------

    private static final String[] BADGES = {"introducing", "new", "meet", "now live"};
    private static final String[] CTAS = {"Get early access", "Start free", "Join the waitlist", "Try it now"};

    private Copy cannedCopy(String idea) { return cannedCopy(idea, null); }

    private Copy cannedCopy(String idea, String productName) {
        String s = subject(idea), t = topic(idea);
        String P = productName == null || productName.isBlank() ? cap(s) : productName;
        String[] heads = {
                cap(s) + ", finally done properly.",
                P + ". The " + s + " that remembers for you.",
                "Never think about " + t + " again.",
                cap(s) + " that actually works.",
                P + " handles the " + t + ". You do not.",
                "The " + s + " you will actually keep using." };
        String[] subs = {
                P + " watches the " + t + " so you can get on with your day. Set it up once, in under a minute.",
                "A " + s + " with one job, done quietly in the background. No dashboards, no nagging, no setup wizard.",
                "Everything you need for " + t + ", and nothing you don't. It works even when you forget it exists.",
                "Built for the days you forget. " + P + " keeps an eye on " + t + " and only speaks up when it matters." };
        return new Copy(
                pick(BADGES, idea),
                pick(heads, idea + "h"),
                pick(subs, idea + "sh"),
                pick(CTAS, idea + "cta"),
                List.of(
                        new Feature("Effortless", P + " handles the " + t + " in the background. You get on with your day."),
                        new Feature("Ready in seconds", "Open it and you are already going. No manual, no setup wizard."),
                        new Feature("Private by default", "Runs close to home. Your data stays where it belongs.")));
    }

    private String cannedSkeptic(String idea) {
        String s = subject(idea);
        String[] notes = {
                "Lovely idea. The hard part isn't building " + s + ", it's getting the first ten people to care.",
                "Great, but who actually pays for " + s + "? Nail that before the logo.",
                "Ship the ugly version this week. You'll learn more from that than another month of polish.",
                "What's the one thing you do that a big company can't copy by Friday?"};
        return pick(notes, idea + "sk");
    }

    // palettes, keyed by the designer's vibe word
    private static final Map<String, Palette> PALETTES = new LinkedHashMap<>();
    static {
        PALETTES.put("techy", new Palette("#0f1220", "#191d2e", "#f4f5fb", "#a6adc4", "#7c9cff", "#22d3ee", "-apple-system,Segoe UI,Roboto,sans-serif"));
        PALETTES.put("warm", new Palette("#fffaf3", "#ffffff", "#1c1a17", "#7a736a", "#e8622c", "#f2b705", "Georgia,'Times New Roman',serif"));
        PALETTES.put("fresh", new Palette("#0b1a14", "#12241b", "#eafff5", "#8fb3a3", "#34d399", "#a3e635", "-apple-system,Segoe UI,Roboto,sans-serif"));
        PALETTES.put("playful", new Palette("#faf7ff", "#ffffff", "#1e1633", "#6b6486", "#7c3aed", "#ec4899", "-apple-system,Segoe UI,Roboto,sans-serif"));
        PALETTES.put("bold", new Palette("#0a0a0c", "#161619", "#fafafa", "#9a9aa4", "#f43f5e", "#f59e0b", "-apple-system,Segoe UI,Roboto,sans-serif"));
        PALETTES.put("calm", new Palette("#071018", "#0f1c26", "#eaf6ff", "#93b2c6", "#38bdf8", "#a78bfa", "-apple-system,Segoe UI,Roboto,sans-serif"));
    }

    private Palette paletteFor(String vibe, String idea) {
        if (vibe != null) {
            String key = vibe.strip().toLowerCase().replaceAll("[^a-z]", "");
            if (PALETTES.containsKey(key)) return PALETTES.get(key);
        }
        List<Palette> all = new ArrayList<>(PALETTES.values());
        return all.get(Math.floorMod(idea.hashCode(), all.size()));
    }

    private static <T> T pick(T[] arr, String seed) { return arr[Math.floorMod(seed.hashCode(), arr.length)]; }

    // ---------- SSE plumbing ----------

    public Map<String, Object> info(String audienceUrl) {
        return Map.of("audienceUrl", audienceUrl, "mock", mock, "open", submissionsOpen);
    }

    private void broadcast(String event, Object data) {
        for (SseEmitter em : emitters) send(em, event, data);
    }
    private void send(SseEmitter em, String event, Object data) {
        try { em.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON)); }
        catch (Exception e) { emitters.remove(em); }
    }
    private void sleep(long ms) throws InterruptedException { Thread.sleep(ms); }
}

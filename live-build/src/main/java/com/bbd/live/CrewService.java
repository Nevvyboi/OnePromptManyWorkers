package com.bbd.live;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;

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
    @Value("${live.backgroundModel:qwen2.5:3b}") String backgroundModelName;
    /** When set, the background crew uses this OpenAI-compatible API instead of Ollama. */
    @Value("${live.api.baseUrl:}") String apiBaseUrl;
    @Value("${live.api.model:}") String apiModel;
    @Value("${live.api.rateMs:1100}") long apiRateMs;
    @Value("${live.api.concurrency:2}") int apiConcurrency;
    /** Pause between auto-advanced stage builds, so the room can see each result. */
    @Value("${live.autoAdvanceMs:6000}") long autoAdvanceMs;
    /** Quality budget: more headline candidates and more critique rounds spend more
     *  time (2-5 min) on a better page. Turn down for a faster, rougher build. */
    @Value("${live.headlineCandidates:2}") int headlineCandidates;
    @Value("${live.critiquePasses:3}") int critiquePasses;
    @Value("${live.api.key:${GLM_API_KEY:${GROQ_API_KEY:}}}") String apiKey;
    /** Optional stronger model for the taste-critical agents (architect, sections, faq). */
    @Value("${live.taste.baseUrl:}") String tasteBaseUrl;
    @Value("${live.taste.model:}") String tasteModel;
    @Value("${live.taste.key:${ANTHROPIC_API_KEY:}}") String tasteKey;
    @Value("${live.ollama:http://localhost:11434}") String ollamaUrl;
    /** Photographs are fetched once during background builds and served from disk after. */
    @Value("${live.photos:true}") boolean photosOn;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final List<Idea> queue = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    /** The queue and everything the crew built, saved here so a server restart
     *  keeps the gallery. Defaults to the home directory, beside the photo cache. */
    @Value("${live.stateFile:}") String stateFileCfg;
    @org.springframework.beans.factory.annotation.Autowired ObjectMapper mapper;
    private Path stateFile;
    private final Object stateLock = new Object();
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
        Agents.Critic critic;
        Agents.Insight insight;
        Agents.Researcher researcher;
        Agents.Strategist strategist;
        Agents.Proofreader proofreader;
        Agents.Architect architect;
        Agents.Sections sections;
        Agents.Faq faq;
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
        save();
        broadcast("queue", queuePayload());
        broadcast("tally", Map.of("total", queue.size()));
        // hand back exactly what we stored, so the phone can never show
        // something different from what the room will see
        return Map.of("ok", true, "id", idea.id, "position", queue.size(), "stored", t, "mock", mock);
    }

    /** Presenter dropped this one. It leaves the list but is kept in memory, so
     *  the same idea can't be re-submitted. Use delete to remove it for good. */
    public Map<String, Object> hide(String id) {
        queue.stream().filter(i -> i.id.equals(id)).findFirst().ifPresent(i -> i.hidden = true);
        save();
        broadcast("queue", queuePayload());
        broadcast("gallery", galleryPayload());
        return Map.of("ok", true);
    }

    /** Presenter removed this one for good: gone from the queue, the gallery and disk. */
    public Map<String, Object> delete(String id) {
        boolean removed = queue.removeIf(i -> i.id.equals(id));
        if (removed) {
            save();
            broadcast("queue", queuePayload());
            broadcast("gallery", galleryPayload());
        }
        return Map.of("ok", removed);
    }

    // ---------- persistence: the queue and gallery survive a restart ----------

    /** What we write to disk: the id counter and every idea, built or not. */
    public record SavedState(int nextId, List<Idea> ideas) {}

    @PostConstruct
    void initState() {
        stateFile = (stateFileCfg != null && !stateFileCfg.isBlank())
                ? Paths.get(stateFileCfg)
                : Paths.get(System.getProperty("user.home"), ".bbd-live-state.json");
        load();
    }

    /** Restores the queue from disk on startup, so a restart mid-talk loses nothing. */
    private void load() {
        try {
            if (stateFile == null || !Files.exists(stateFile)) return;
            byte[] bytes = Files.readAllBytes(stateFile);
            if (bytes.length == 0) return;
            SavedState s = mapper.readValue(bytes, SavedState.class);
            if (s.ideas() != null) { queue.clear(); queue.addAll(s.ideas()); }
            int maxId = 0;
            for (Idea i : queue) { try { maxId = Math.max(maxId, Integer.parseInt(i.id)); } catch (Exception ignore) {} }
            nextId.set(Math.max(s.nextId(), maxId + 1));
            System.out.println("Restored " + queue.size() + " ideas from " + stateFile);
        } catch (Exception e) {
            System.out.println("  (could not restore state: " + e.getMessage() + ")");
        }
    }

    /** Writes the whole queue to disk atomically (temp file then move) so a crash
     *  mid-write can't corrupt the saved gallery. Cheap enough to call on every change. */
    void save() {
        if (stateFile == null) return;
        synchronized (stateLock) {
            try {
                Path tmp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
                mapper.writeValue(tmp.toFile(), new SavedState(nextId.get(), new ArrayList<>(queue)));
                try { Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING); }
            } catch (Exception e) {
                System.out.println("  (could not save state: " + e.getMessage() + ")");
            }
        }
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
        runner.submit(() -> {
            try { runCrew(target); }
            catch (Exception e) { e.printStackTrace(); }
            finally { running = false; autoAdvance(); }
        });
        return Map.of("ok", true);
    }

    /** Presenter toggle: when on, the stage rolls straight into the next unseen idea
     *  after each build, so a queue plays through on the big screen unattended. */
    private volatile boolean autoOn = false;
    public Map<String, Object> auto(boolean on) {
        autoOn = on;
        broadcast("auto", Map.of("on", on, "next", nextUpText()));
        if (on && !running) autoAdvance();
        return Map.of("ok", true, "on", on);
    }
    public boolean autoOn() { return autoOn; }

    /** The next idea the stage would show: first one not yet run on stage. */
    private Idea nextUp() {
        return queue.stream()
                .filter(i -> !i.hidden && i.id.matches("\\d+") && !"done".equals(i.status))
                .findFirst().orElse(null);
    }
    private String nextUpText() { Idea n = nextUp(); return n == null ? "" : n.text; }

    /** Called after each stage build finishes. If auto is on and something is
     *  waiting, kicks off the next run; a short pause lets the room see the result. */
    private void autoAdvance() {
        if (!autoOn || running) return;
        Idea next = nextUp();
        broadcast("auto", Map.of("on", autoOn, "next", next == null ? "" : next.text));
        if (next == null) return;
        try { Thread.sleep(autoAdvanceMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        if (autoOn && !running) run(next.id);
    }

    public SseEmitter subscribe() {
        SseEmitter em = new SseEmitter(0L); // never time out
        emitters.add(em);
        em.onCompletion(() -> emitters.remove(em));
        em.onTimeout(() -> emitters.remove(em));
        em.onError(e -> emitters.remove(em));
        send(em, "info", Map.of("mock", mock, "tally", queue.size(), "model", modelName));
        send(em, "queue", queuePayload());
        send(em, "gallery", galleryPayload());
        send(em, "gate", Map.of("open", submissionsOpen));
        send(em, "auto", Map.of("on", autoOn, "next", nextUpText()));
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
            Map.of("key", "insight", "label", "insight"),
            Map.of("key", "researcher", "label", "facts"),
            Map.of("key", "namer", "label", "name"),
            Map.of("key", "copywriter", "label", "copy"),
            Map.of("key", "designer", "label", "design"),
            Map.of("key", "illustrator", "label", "art"),
            Map.of("key", "pricer", "label", "price"),
            Map.of("key", "architect", "label", "shape"),
            Map.of("key", "builder", "label", "build"),
            Map.of("key", "reviewer", "label", "review"),
            Map.of("key", "skeptic", "label", "skeptic"),
            Map.of("key", "strategist", "label", "strategy"),
            Map.of("key", "proofreader", "label", "proof"),
            Map.of("key", "critic", "label", "critic"));

    private final Map<String, Long> marks = new ConcurrentHashMap<>();
    private volatile long runT0 = 0;

    /**
     * Every start and finish, timestamped against the start of the run.
     *
     * <p>The roster already showed which agent was busy. It could not show the
     * thing the talk is actually about: that three of them are busy at the same
     * moment, and that the one which finishes first picks up more work instead of
     * waiting. Segments make the overlap visible, and an agent that stops and
     * starts again draws two bars, which is exactly what it did.
     */
    private void startAgent(String k) {
        marks.put(k, System.currentTimeMillis());
        broadcast("span", Map.of("key", k, "edge", "start", "at", System.currentTimeMillis() - runT0));
    }
    private void doneAgent(String k) {
        long now = System.currentTimeMillis();
        long ms = now - marks.getOrDefault(k, now);
        broadcast("timing", Map.of("key", k, "ms", ms));
        broadcast("span", Map.of("key", k, "edge", "end", "at", now - runT0, "ms", ms));
    }

    private void runCrew(Idea idea) throws Exception {
        long t0 = System.currentTimeMillis();
        runT0 = t0;
        marks.clear();
        if (idea.id.matches("\\d+")) { idea.status = "running"; broadcast("queue", queuePayload()); }
        broadcast("run-start", Map.of("id", idea.id, "idea", idea.text, "name", idea.name));
        sleep(420);
        broadcast("graph", Map.of("nodes", CREW, "edges", List.of(
                edge("insight", "copywriter", false),
                edge("researcher", "copywriter", false),
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
                edge("skeptic", "copywriter", true),
                // the critic is the only node fed by the assembled page rather than a field
                edge("strategist", "builder", false),
                edge("builder", "critic", false),
                edge("critic", "builder", true),
                edge("critic", "proofreader", false),
                edge("proofreader", "builder", true))));
        sleep(380);

        // Round one: three at once. The Copywriter is deliberately NOT one of them.
        // It waits on the Insight agent, because copy written before anyone has
        // said what the problem actually is comes back fluent and about nothing.
        startAgent("insight"); broadcast("agent-state", Map.of("key", "insight", "state", "working", "note", "finding the real problem…"));
        startAgent("researcher"); broadcast("agent-state", Map.of("key", "researcher", "state", "working", "note", "digging up what is actually true…"));
        startAgent("namer"); broadcast("agent-state", Map.of("key", "namer", "state", "working", "note", "inventing a name…"));
        startAgent("designer"); broadcast("agent-state", Map.of("key", "designer", "state", "working", "note", "choosing a palette…"));
        Future<String> tensionF = pool.submit(() -> safeInsight(stage(), idea.text));
        Future<String> factsF = pool.submit(() -> safeFacts(stage(), idea.text));
        Future<String> nameF = pool.submit(() -> safeName(stage(), idea.text));
        Future<Palette> paletteF = pool.submit(() -> safePalette(stage(), idea.text));

        String tension = tensionF.get();
        broadcast("worker-done", Map.of("key", "insight", "payload", Map.of("tension", tension)));
        doneAgent("insight");
        broadcast("agent-state", Map.of("key", "insight", "state", "done",
                "note", tension.isBlank() ? "no clear tension, copy goes it alone" : tension));
        broadcast("flow", Map.of("from", "insight", "to", "copywriter", "what", "the tension"));

        String facts = factsF.get();
        broadcast("worker-done", Map.of("key", "researcher", "payload", Map.of("facts", facts)));
        doneAgent("researcher");
        broadcast("agent-state", Map.of("key", "researcher", "state", "done",
                "note", facts.isBlank() ? "nothing concrete to add" : facts));
        broadcast("flow", Map.of("from", "researcher", "to", "copywriter", "what", "the specifics"));

        startAgent("copywriter"); broadcast("agent-state", Map.of("key", "copywriter", "state", "working",
                "note", tension.isBlank() ? "drafting the hero…" : "writing against the tension…"));
        Future<Copy> copyF = pool.submit(() -> safeCopy(stage(), idea.text, tension, facts));

        // the namer is the smallest job, so it lands first and feeds three others
        String firstName = nameF.get();
        broadcast("agent-state", Map.of("key", "namer", "state", "working", "note", "coining a few names to choose from…"));
        String productName = bestName(stage(), idea.text, firstName);
        broadcast("worker-done", Map.of("key", "namer", "payload", new Product(productName, "")));
        doneAgent("namer");
        broadcast("agent-state", Map.of("key", "namer", "state", "done",
                "note", productName.equalsIgnoreCase(firstName) ? "named it, others can start"
                        : "picked the best of three: " + productName));
        broadcast("flow", Map.of("from", "namer", "to", "copywriter", "what", "the name"));
        broadcast("flow", Map.of("from", "namer", "to", "illustrator", "what", "the name"));
        broadcast("flow", Map.of("from", "namer", "to", "pricer", "what", "the name"));

        Palette palette = paletteF.get();
        broadcast("worker-done", Map.of("key", "designer", "payload", palette));
        doneAgent("designer");
        broadcast("agent-state", Map.of("key", "designer", "state", "done", "note", "palette set"));
        broadcast("flow", Map.of("from", "designer", "to", "copywriter", "what", "tone hint"));

        // The Illustrator begins, and needs the Designer to say which colour leads.
        startAgent("illustrator");
        broadcast("agent-state", Map.of("key", "illustrator", "state", "working", "note", "choosing the interface…"));
        String kind = pickKind(stage(), idea.text);
        broadcast("flow", Map.of("from", "illustrator", "to", "designer", "what", "which colour leads?"));

        // The Designer finished early, so it comes BACK to help: a real second span.
        startAgent("designer");
        broadcast("agent-state", Map.of("key", "designer", "state", "assisting", "note", "helping the illustrator: which colour leads"));
        String emphasis = safeEmphasis(stage(), idea.text, kind);
        doneAgent("designer");
        broadcast("agent-state", Map.of("key", "designer", "state", "done", "note", emphasis + " leads the picture"));
        broadcast("flow", Map.of("from", "designer", "to", "illustrator", "what", emphasis + " leads"));

        broadcast("agent-state", Map.of("key", "illustrator", "state", "working", "note", "drawing the " + kind + "…"));
        Art art = buildArt(stage(), idea.text, palette, kind, emphasis);
        broadcast("worker-done", Map.of("key", "illustrator", "payload", art));
        doneAgent("illustrator");
        broadcast("agent-state", Map.of("key", "illustrator", "state", "done", "note", "drew a " + kind));
        broadcast("flow", Map.of("from", "illustrator", "to", "builder", "what", "artwork"));

        startAgent("pricer"); broadcast("agent-state", Map.of("key", "pricer", "state", "working", "note", "working out three tiers…"));
        List<Tier> pricingLive = safePricing(stage(), idea.text, productName);
        broadcast("worker-done", Map.of("key", "pricer", "payload", Map.of("pricing", pricingLive)));
        doneAgent("pricer"); broadcast("agent-state", Map.of("key", "pricer", "state", "done", "note", "three tiers, one featured"));
        broadcast("flow", Map.of("from", "pricer", "to", "builder", "what", "pricing"));

        // The Researcher finished early and the Copywriter is still writing, so it
        // comes back NOW to dig up the one real number the page can cite, filling
        // its idle window in the middle of the build rather than waiting for the end.
        startAgent("researcher");
        broadcast("agent-state", Map.of("key", "researcher", "state", "assisting", "note", "finding a real number while the copy is written"));
        String researchGap = safeGap(stage(), idea.text);
        doneAgent("researcher");
        broadcast("agent-state", Map.of("key", "researcher", "state", "done",
                "note", researchGap.isBlank() ? "nothing new to add" : researchGap));

        Copy copy = copyF.get();
        broadcast("worker-done", Map.of("key", "copywriter", "payload", copy));
        doneAgent("copywriter"); broadcast("agent-state", Map.of("key", "copywriter", "state", "done", "note", "hero: \"" + copy.headline() + "\""));

        // Best-of-N on the hero: the copywriter drafts alternate headlines AND subheads
        // from other angles, and the strategist picks the sharpest of each.
        startAgent("copywriter");
        broadcast("agent-state", Map.of("key", "copywriter", "state", "assisting", "note", "drafting sharper headlines and subheads to choose from…"));
        String origHead = copy.headline();
        Copy refined = refineHero(stage(), idea.text, copy);
        boolean picked = refined != null && !refined.headline().equals(origHead);
        if (refined != null) copy = refined;
        if (picked) broadcast("revise", Map.of("field", "headline", "value", copy.headline(), "by", "copywriter"));
        doneAgent("copywriter");
        broadcast("agent-state", Map.of("key", "copywriter", "state", "done",
                "note", (picked ? "picked a sharper hero of three" : "its first was already sharpest") + ": \"" + copy.headline() + "\""));
        broadcast("flow", Map.of("from", "copywriter", "to", "builder", "what", "copy"));
        broadcast("flow", Map.of("from", "designer", "to", "builder", "what", "palette"));

        // The Namer finished first, so now that the headline exists it comes BACK
        // to write the tagline the tab and share card use. Real work, real second span.
        startAgent("namer");
        broadcast("agent-state", Map.of("key", "namer", "state", "assisting", "note", "writing the tagline now the headline is set"));
        String tagline = safeTagline(stage(), productName, copy.headline());
        doneAgent("namer");
        broadcast("agent-state", Map.of("key", "namer", "state", "done",
                "note", tagline.isBlank() ? "no tagline needed" : "tagline: " + tagline));
        if (!tagline.isBlank()) broadcast("flow", Map.of("from", "namer", "to", "builder", "what", "the tagline"));
        Product product = new Product(productName, tagline);

        // The Strategist picks the hook and leads with it, so the page argues one
        // thing instead of listing three.
        startAgent("strategist");
        broadcast("agent-state", Map.of("key", "strategist", "state", "working", "note", "deciding what to lead with…"));
        List<Feature> ordered = leadWith(stage(), idea.text, copy.features());
        boolean reordered = !ordered.equals(copy.features());
        final Copy copyLed = new Copy(copy.badge(), copy.headline(), copy.subhead(), copy.cta(), ordered);
        doneAgent("strategist");
        broadcast("agent-state", Map.of("key", "strategist", "state", "done",
                "note", reordered ? "led with \"" + ordered.get(0).title() + "\"" : "the order was already right"));
        broadcast("flow", Map.of("from", "strategist", "to", "builder", "what", "the hook to lead with"));

        // The Architect decides what this page is shaped like, then the Sections agent
        // fills what it chose. This is what stops every idea coming out as the same
        // hero, three features, pricing, FAQ template.
        startAgent("architect");
        broadcast("agent-state", Map.of("key", "architect", "state", "working", "note", "deciding what sections this page needs…"));
        Plan plan = planPage(stage(), idea.text);
        broadcast("agent-state", Map.of("key", "architect", "state", "working",
                "note", "chose " + String.join(" + ", plan.kinds())));
        List<Section> sections = buildSections(stage(), idea.text, productName, plan.kinds());
        broadcast("worker-done", Map.of("key", "architect",
                "payload", Map.of("sections", sections.stream().map(Section::kind).toList(), "hero", plan.hero())));
        doneAgent("architect");
        broadcast("agent-state", Map.of("key", "architect", "state", "done",
                "note", sections.isEmpty() ? "kept the standard page"
                        : "built " + sections.stream().map(Section::heading).collect(java.util.stream.Collectors.joining(", "))));
        broadcast("flow", Map.of("from", "architect", "to", "builder", "what", "the page shape"));

        startAgent("builder"); broadcast("agent-state", Map.of("key", "builder", "state", "working", "note", "assembling the page…"));
        sleep(800);
        broadcast("worker-done", Map.of("key", "builder"));
        doneAgent("builder"); broadcast("agent-state", Map.of("key", "builder", "state", "done", "note", "page assembled"));

        // two checkers read the finished page together
        broadcast("flow", Map.of("from", "builder", "to", "reviewer", "what", "the page"));
        broadcast("flow", Map.of("from", "builder", "to", "skeptic", "what", "the page"));
        startAgent("reviewer"); broadcast("agent-state", Map.of("key", "reviewer", "state", "working", "note", "checking it over…"));
        startAgent("skeptic"); broadcast("agent-state", Map.of("key", "skeptic", "state", "working", "note", "poking holes…"));
        Future<String> polishF = pool.submit(() -> safePolish(stage(), idea.text, copyLed.cta()));
        String note = safeSkeptic(stage(), idea.text);
        if (!researchGap.isBlank()) broadcast("flow", Map.of("from", "researcher", "to", "skeptic", "what", "a real number"));

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
        String revised = safeRevise(stage(), idea.text, copyLed.headline(), note);
        broadcast("revise", Map.of("field", "headline", "value", revised, "by", "skeptic"));
        broadcast("flow", Map.of("from", "copywriter", "to", "builder", "what", "revised hero"));
        doneAgent("copywriter"); broadcast("agent-state", Map.of("key", "copywriter", "state", "done", "note", "rewritten. the web is settled"));

        Copy assembled = new Copy(copyLed.badge(), revised, copyLed.subhead(), polished, copyLed.features());

        // the last edge in the web: one agent reads the finished page, not a field
        startAgent("critic"); broadcast("agent-state", Map.of("key", "critic", "state", "working", "note", "reading the whole page…"));
        // the Insight agent comes back to help: does the headline answer the tension?
        startAgent("insight");
        broadcast("agent-state", Map.of("key", "insight", "state", "assisting", "note", "checking the headline still answers the tension"));
        Future<String> verdictF = pool.submit(() -> safeAnswers(stage(), tension, revised));
        Copy critiqued = critiqueUntilSettled(stage(), idea.text, product.name(), assembled, critiquePasses);

        // The Proofreader reads the finished copy as prose, one last time.
        startAgent("proofreader");
        broadcast("agent-state", Map.of("key", "proofreader", "state", "working", "note", "reading it as prose, one last time…"));
        Copy finalCopy = proofread(stage(), critiqued);
        boolean cleaned = !finalCopy.equals(critiqued);
        doneAgent("proofreader");
        broadcast("agent-state", Map.of("key", "proofreader", "state", "done",
                "note", cleaned ? "tidied a line" : "nothing to fix"));
        if (cleaned) broadcast("flow", Map.of("from", "proofreader", "to", "builder", "what", "a cleaner line"));
        String verdict = verdictF.get();
        doneAgent("insight");
        broadcast("agent-state", Map.of("key", "insight", "state", "done",
                "note", verdict.isBlank() ? "left it to the critic" : verdict));
        if (!verdict.isBlank()) broadcast("flow", Map.of("from", "insight", "to", "critic", "what", "does it answer the tension"));
        boolean changed = !finalCopy.equals(assembled);
        broadcast("worker-done", Map.of("key", "critic", "payload", Map.of("changed", changed)));
        doneAgent("critic");
        broadcast("agent-state", Map.of("key", "critic", "state", "done",
                "note", changed ? "sent one line back" : "nothing worth changing"));
        if (changed) {
            broadcast("flow", Map.of("from", "critic", "to", "builder", "what", "one line rewritten"));
            broadcast("revise", Map.of("field", "copy", "value", finalCopy.headline(), "by", "critic"));
        }

        idea.result = new Result(product, palette, finalCopy, art, review,
                safePricing(stage(), idea.text, product.name()),
                buildFaq(stage(), idea.text, product.name()), note,
                layoutFor(stage(), idea.text), tension, safePriced(stage(), idea.text),
                sections, plan.hero());
        idea.ms = System.currentTimeMillis() - t0;
        broadcast("timing", Map.of("key", "total", "ms", idea.ms));
        idea.status = "done";
        save();
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
            String tension = safeInsight(fast(), next.text);
            String facts = safeFacts(fast(), next.text);
            Copy copy = safeCopy(fast(), next.text, tension, facts);
            Art art = safeArt(fast(), next.text, palette);
            String note = safeSkeptic(fast(), next.text);
            String polished = safePolish(fast(), next.text, copy.cta());
            String revised = safeRevise(fast(), next.text, copy.headline(), note);
            Copy finalCopy = critiqueUntilSettled(fast(), next.text, product.name(),
                    new Copy(copy.badge(), revised, copy.subhead(), polished, copy.features()), 2);
            Plan bgPlan = planPage(fast(), next.text);
            next.result = new Result(product, palette, finalCopy, art,
                    new Review("one thing to sharpen", "cta", polished, "the call to action was generic"),
                    safePricing(fast(), next.text, product.name()),
                    buildFaq(fast(), next.text, product.name()), note,
                    layoutFor(fast(), next.text), tension, safePriced(fast(), next.text),
                    buildSections(fast(), next.text, product.name(), bgPlan.kinds()), bgPlan.hero());
            next.ms = System.currentTimeMillis() - bt;
            next.status = "built";
            save();
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
        ChatModel model = modelFor(which);
        Bundle b = new Bundle();
        b.copywriter = AiServices.create(Agents.Copywriter.class, model);
        b.designer = AiServices.create(Agents.Designer.class, model);
        b.skeptic = AiServices.create(Agents.Skeptic.class, model);
        b.namer = AiServices.create(Agents.Namer.class, model);
        b.illustrator = AiServices.create(Agents.Illustrator.class, model);
        b.reviewer = AiServices.create(Agents.Reviewer.class, model);
        b.pricer = AiServices.create(Agents.Pricer.class, model);
        b.critic = AiServices.create(Agents.Critic.class, model);
        b.insight = AiServices.create(Agents.Insight.class, model);
        b.researcher = AiServices.create(Agents.Researcher.class, model);
        b.strategist = AiServices.create(Agents.Strategist.class, model);
        b.proofreader = AiServices.create(Agents.Proofreader.class, model);
        // The taste-critical agents may run on a stronger model than the rest: the
        // harness is provider-agnostic, so this is a config line, not a rewrite.
        ChatModel taste = tasteModelFor(which);
        b.architect = AiServices.create(Agents.Architect.class, taste);
        b.sections = AiServices.create(Agents.Sections.class, taste);
        b.faq = AiServices.create(Agents.Faq.class, taste);
        return b;
    }

    /**
     * Builds the model for a crew. The name "api" routes to the OpenAI-compatible
     * endpoint (NVIDIA, Groq, Cerebras); anything else is a local Ollama model.
     *
     * <p>The whole point of the toggle: swapping the runtime is one branch here,
     * and the eleven agents, the guard and the orchestration never change. That is
     * the talk's thesis made literal: the model is a component, the harness is the
     * product.
     */
    /**
     * The taste-critical agents (architect, sections, faq) can run on a different,
     * stronger model than the rest of the crew: set live.taste.baseUrl/model/key and
     * they route there, everything else stays put. Anthropic, OpenAI, GLM and Groq all
     * speak the same dialect, so pointing these three at Claude is one config line.
     */
    private ChatModel tasteModelFor(String which) {
        if (tasteBaseUrl != null && !tasteBaseUrl.isBlank() && tasteKey != null && !tasteKey.isBlank()) {
            ChatModel m = OpenAiChatModel.builder()
                    .baseUrl(tasteBaseUrl).apiKey(tasteKey).modelName(tasteModel)
                    .temperature(0.7).maxRetries(3).timeout(Duration.ofMinutes(3)).build();
            return new Throttled(m, apiConcurrency, apiRateMs);
        }
        return modelFor(which);
    }

    private ChatModel modelFor(String which) {
        if ("api".equals(which) && apiBaseUrl != null && !apiBaseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()) {
            var builder = OpenAiChatModel.builder()
                    .baseUrl(apiBaseUrl).apiKey(apiKey).modelName(apiModel)
                    .temperature(0.6).maxRetries(4).timeout(Duration.ofMinutes(3));
            // GLM (Z.ai) ships as a hybrid reasoning model: left on, every call emits a
            // long chain-of-thought that blows the token-per-minute cap (HTTP 1302) and
            // leaks <think> into the flat-schema parsers. Send the provider's own switch
            // to turn it off. Only GLM understands this field, so it is scoped by base URL.
            if (apiBaseUrl.contains("z.ai") || apiModel.toLowerCase().startsWith("glm")) {
                builder.customParameters(java.util.Map.of(
                        "thinking", java.util.Map.of("type", "disabled")));
            }
            ChatModel api = builder.build();
            // Free API tiers (GLM-4.5-Flash: ~1 req/s) cannot take the spider net's
            // parallel burst. Space the starts out so 13 agents queue instead of
            // hammering the endpoint. Local Ollama has no such cap, so it is untouched.
            return new Throttled(api, apiConcurrency, apiRateMs);
        }
        return OllamaChatModel.builder()
                .baseUrl(ollamaUrl).modelName(which)
                .temperature(0.6).timeout(Duration.ofMinutes(2)).build();
    }

    /**
     * Guards a rate-limited API against the spider net's parallel burst. Free tiers
     * (GLM-4.5-Flash) cap how many requests may be in flight at once, so a semaphore
     * limits concurrency to {@code permits}; a shared gate also keeps starts at least
     * {@code minIntervalMs} apart to respect any per-second cap. Local Ollama has no
     * such limit and never gets wrapped, so the on-stage local demo stays fully parallel.
     */
    static final class Throttled implements ChatModel {
        private final ChatModel delegate;
        private final long minIntervalMs;
        private final java.util.concurrent.Semaphore slots;
        private final Object gate = new Object();
        private long lastStart = 0L;
        Throttled(ChatModel delegate, int permits, long minIntervalMs) {
            this.delegate = delegate;
            this.minIntervalMs = minIntervalMs;
            this.slots = new java.util.concurrent.Semaphore(Math.max(1, permits), true);
        }
        @Override public ChatResponse chat(ChatRequest request) {
            try {
                slots.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return delegate.chat(request);
            }
            try {
                synchronized (gate) {
                    long wait = minIntervalMs - (System.currentTimeMillis() - lastStart);
                    if (wait > 0) {
                        try { Thread.sleep(wait); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                    lastStart = System.currentTimeMillis();
                }
                return delegate.chat(request);
            } finally {
                slots.release();
            }
        }
        @Override public ModelProvider provider() { return delegate.provider(); }
        @Override public java.util.Set<Capability> supportedCapabilities() {
            return delegate.supportedCapabilities();
        }
    }

    /** The crew on stage. Best model, because the room is watching. */
    private synchronized Bundle stage() {
        if (stageCrew == null) stageCrew = build(modelName);
        return stageCrew;
    }

    /**
     * Loads the stage model into memory before anyone is watching.
     *
     * <p>A cold 12B answers its first one-word question in 8.4 seconds and every
     * one after that in under one. On stage that difference lands entirely on the
     * first idea of the demo, which is the one the room is paying attention to.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void warmUp() {
        if (mock) return;
        Thread t0 = new Thread(() -> {
            long t = System.currentTimeMillis();
            try {
                stage().designer.vibe("a warm up request, ignore this");
                System.out.println("  stage model warm (" + modelName + ", "
                        + (System.currentTimeMillis() - t) + "ms)");
            } catch (RuntimeException e) {
                System.out.println("  (warm up failed, first build will be slower: " + reason(e) + ")");
            }
        }, "warmup");
        t0.setDaemon(true);
        t0.start();
    }

    /** The crew working the queue in the background. Smaller model on purpose:
     *  it must never slow down or starve the run happening on the projector. */
    private synchronized Bundle fast() {
        if (fastCrew == null) fastCrew = build(backgroundModelName);
        return fastCrew;
    }

    /** One sentence naming why the problem survives. Empty if the model will not give one. */
    private String safeInsight(Bundle crew, String idea) throws InterruptedException {
        if (!mock) {
            try {
                String t = crew.insight.tension(idea);
                if (t != null) {
                    t = demark(t.strip().split("\\R")[0]).replaceAll("^[\"']|[\"']$", "").strip();
                    if (latin(t) && clean(t) && t.length() >= 20 && t.length() <= 160) return t;
                }
            } catch (RuntimeException e) { /* the page simply has no statement band */ }
        } else sleep(600);
        return "";
    }

    /** Concrete, checkable detail about the domain. Empty if the model only offers advice. */
    private String safeFacts(Bundle crew, String idea) throws InterruptedException {
        if (!mock) {
            try {
                String raw = crew.researcher.facts(idea);
                if (raw == null) return "";
                List<String> keep = new ArrayList<>();
                for (String part : demark(raw).split("[;\\n]")) {
                    String t = part.strip().replaceAll("^[-*\\d.)\\s]+", "").strip();
                    if (t.length() < 8 || t.length() > 120 || !latin(t) || !clean(t)) continue;
                    keep.add(t);
                    if (keep.size() == 3) break;
                }
                return String.join(". ", keep);
            } catch (RuntimeException e) { /* copy goes without */ }
        } else sleep(700);
        return "";
    }

    private Copy safeCopy(Bundle crew, String idea) throws InterruptedException {
        return safeCopy(crew, idea, "", "");
    }

    private Copy safeCopy(Bundle crew, String idea, String tension, String facts) throws InterruptedException {
        if (!mock) {
            try {
                Copy c = normalise(crew.copywriter.write(idea));
                if (usable(c)) {
                    // A local model settles into a groove. Three pages in a row opened
                    // "Stop thinking about" before this check existed. The guard names
                    // the openings already used and sends the work back once.
                    if (staleOpener(c.headline())) {
                        System.out.println("  (headline repeated \"" + opener(c.headline()) + "\", asking again)");
                        Copy again = normalise(crew.copywriter.rewrite(idea, String.join("; ", recentOpeners())));
                        if (usable(again) && !staleOpener(again.headline())) c = again;
                        else return cannedCopy(idea, null);   // it stayed in the groove, take the house draft
                    }
                    rememberOpener(c.headline());
                    return c;
                }
            } catch (RuntimeException e) {
                System.out.println("  (structured copy failed: " + reason(e) + ")");
            }
            // Second attempt with a flat five-line schema. A 3b model that cannot
            // fill a nested object fills this one, so the page still gets real copy
            // instead of the house draft.
            try {
                Copy flat = normalise(parseFlat(tension.isBlank()
                        ? crew.copywriter.writeFlat(idea)
                        : crew.copywriter.writeWithInsight(idea, tension, facts.isBlank() ? "nothing extra" : facts), idea));
                if (usable(flat)) {
                    if (staleOpener(flat.headline())) return cannedCopy(idea, null);
                    rememberOpener(flat.headline());
                    System.out.println("  (copywriter recovered on the flat schema)");
                    return flat;
                }
            } catch (RuntimeException e) { System.out.println("  (copywriter improvised: " + reason(e) + ")"); }
        } else sleep(1400);
        return cannedCopy(idea, null);
    }

    /** The first line of the cause, which is the part that says what actually broke. */
    private static String reason(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String m = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return m.length() > 140 ? m.substring(0, 140) + "…" : m.replaceAll("\\s+", " ");
    }

    /**
     * The last pass: one agent reads the finished page and replaces one line.
     *
     * <p>Every other agent sees a field. This one sees the assembled result,
     * which is the only way to catch copy that is individually fine and collectively
     * flat. It returns exactly one field and one replacement, and the replacement
     * still has to clear the same checks as anything else the crew writes.
     *
     * @return the copy, with at most one line changed
     */
    /**
     * Runs the Critic until it stops changing things.
     *
     * <p>One pass fixes one line, which left two of three feature titles still
     * naming a category. The loop is capped because this happens while a room is
     * watching, and it stops early the moment a pass changes nothing.
     */
    private Copy critiqueUntilSettled(Bundle crew, String idea, String name, Copy c, int passes) {
        Copy cur = c;
        // Without this the Critic picked feature3 on all three passes and spent two
        // of them overwriting its own rewrite. Naming what is done moves it along.
        List<String> done = new ArrayList<>();
        // Stop as soon as the editor is happy. Forcing the full budget just made it
        // rewrite the same three features round after round: churn, not polish, and
        // it pushed a build past nine minutes.
        for (int i = 0; i < passes; i++) {
            Copy next = critique(crew, idea, name, cur, done);
            if (next.equals(cur)) break;
            cur = next;
        }
        return cur;
    }

    private Copy critique(Bundle crew, String idea, String name, Copy c, List<String> done) {
        if (mock || c == null || c.features() == null || c.features().size() < 3) return c;
        List<Feature> f = c.features();
        String raw;
        try {
            raw = crew.critic.review(name, idea, c.headline(), c.subhead(), c.cta(),
                    f.get(0).title() + ": " + f.get(0).body(),
                    f.get(1).title() + ": " + f.get(1).body(),
                    f.get(2).title() + ": " + f.get(2).body(),
                    done.isEmpty() ? "nothing yet" : String.join(", ", done));
        } catch (RuntimeException e) {
            System.out.println("  (critic passed, nothing changed: " + reason(e) + ")");
            return c;
        }
        if (raw == null) return c;

        final List<String> FIELDS = List.of("headline", "subhead", "cta", "feature1", "feature2", "feature3");
        String field = null, repl = null;
        for (String line : raw.strip().split("\\R")) {
            String l = line.strip();
            int i = l.indexOf(':');
            if (i <= 0) continue;
            String k = l.substring(0, i).strip().toLowerCase().replaceAll("[^a-z0-9]", "");
            String v = l.substring(i + 1).strip().replaceAll("^[\"']|[\"']$", "");
            if (k.equals("field")) field = v.toLowerCase().replaceAll("[^a-z0-9]", "");
            else if (k.equals("replacement")) repl = v;
            // the shape it actually returns: the field name is the label
            else if (FIELDS.contains(k) && !v.isBlank()) { field = k; repl = v; }
        }
        if (field != null && repl == null) System.out.println("  (critic named " + field + " but wrote no replacement)");
        if (field == null || repl == null || repl.isBlank()) return c;
        // the critic's line is copy too, so it clears the same bar
        if (!latin(repl) || !clean(repl)) { System.out.println("  (critic's rewrite was refused by the guard)"); return c; }

        switch (field) {
            case "headline" -> {
                if (repl.length() > 70 || staleOpener(repl)) return c;
                System.out.println("  critic rewrote the headline"); done.add("headline");
                return new Copy(c.badge(), repl, c.subhead(), c.cta(), f);
            }
            case "subhead" -> {
                if (repl.length() > 190) return c;
                System.out.println("  critic rewrote the subhead"); done.add("subhead");
                return new Copy(c.badge(), c.headline(), repl, c.cta(), f);
            }
            case "cta" -> {
                if (repl.length() > 34) return c;
                System.out.println("  critic rewrote the button"); done.add("cta");
                return new Copy(c.badge(), c.headline(), c.subhead(), repl, f);
            }
            case "feature1", "feature2", "feature3" -> {
                int idx = field.charAt(field.length() - 1) - '1';
                if (idx < 0 || idx > 2 || repl.length() > 160) return c;
                String title = f.get(idx).title(), body = repl;
                int colon = repl.indexOf(':');
                if (colon > 0 && colon <= 24) { title = clip(repl.substring(0, colon).strip(), 22); body = repl.substring(colon + 1).strip(); }
                if (body.isBlank()) return c;
                List<Feature> out = new ArrayList<>(f);
                out.set(idx, new Feature(title, body));
                System.out.println("  critic rewrote " + field); done.add(field);
                return new Copy(c.badge(), c.headline(), c.subhead(), c.cta(), out);
            }
            default -> { return c; }
        }
    }

    /** Reads the five-line reply, loosely.
     *
     *  <p>Asked for one FEATURES line, Qwen writes the label on its own line and
     *  then lists three "Title: x | y" lines beneath it. The model obeys the shape
     *  approximately, so the parser has to meet it halfway rather than throw the
     *  whole reply away. Anything genuinely missing falls back to the house draft. */
    private Copy parseFlat(String raw, String idea) {
        if (raw == null || raw.isBlank()) return null;
        Map<String, String> f = new HashMap<>();
        List<Feature> feats = new ArrayList<>();

        for (String line : raw.strip().split("\\R")) {
            String l = line.strip();
            if (l.isEmpty()) continue;

            // a feature is any line carrying the title | body separator, wherever it sits
            if (l.contains("|")) {
                for (String part : l.split(";;")) {
                    String[] kv = part.split("\\|", 2);
                    if (kv.length != 2) continue;
                    String title = kv[0].strip().replaceAll("(?i)^(features?|title)\\s*:\\s*", "")
                                                .replaceAll("^[-*\\d.)\\s]+", "").strip();
                    String body = kv[1].strip();
                    // Asked for "title | one sentence", a 3b model writes the label
                    // back as content. One run came back with all three features
                    // titled "Description" and three perfectly good bodies, so a
                    // placeholder title costs the title, not the whole feature.
                    if (body.isBlank() || feats.size() >= 3) continue;
                    if (body.matches("(?i)(one\\s+)?sentence\\.?")) continue;
                    if (!clean(body) || !latin(body)) continue;   // filler verbs, refused at source
                    boolean junk = title.isBlank() || !clean(title) || !latin(title)
                            || title.matches("(?i)(one\\s+)?(title|sentence|features?|feature\\d+|body|name|description|details?|summary|text|label|f\\d+)\\s*\\d*");
                    feats.add(new Feature(junk ? titleFrom(body, feats.size()) : clip(title, 22), body));
                }
                continue;
            }
            int c = l.indexOf(':');
            if (c <= 0) continue;
            String key = l.substring(0, c).strip().toUpperCase();
            if (!List.of("BADGE", "HEADLINE", "SUBHEAD", "CTA").contains(key)) continue;
            f.put(key, l.substring(c + 1).strip().replaceAll("^[\"']|[\"']$", ""));
        }

        String head = f.get("HEADLINE"), sub = f.get("SUBHEAD");
        if (head == null || head.isBlank() || sub == null || sub.isBlank()) return null;
        if (feats.size() < 3) feats = cannedCopy(idea, null).features();

        return new Copy(badge(f.get("BADGE")), head, sub,
                nzs(f.get("CTA"), "Start free"), feats.subList(0, 3));
    }

    /**
     * Builds a title out of the body when the model gave a placeholder.
     *
     * <p>Falling back to three fixed house titles worked, but then every page
     * whose model output was placeholder-titled carried the same three headings.
     * The body is real copy, so the title comes from it: drop the leading
     * imperative and keep the next couple of words.
     */
    private static String titleFrom(String body, int index) {
        String[] w = body.replaceAll("[^A-Za-z0-9\u2019' ]", " ").replaceAll("\\s+", " ").strip().split(" ");
        int at = 0;
        if (w.length > 3 && w[0].matches("(?i)get|receive|compare|open|set|see|keep|track|know|check|view|stay|find|know"))
            at = 1;
        StringBuilder t = new StringBuilder();
        for (int i = at; i < w.length && t.length() < 17; i++) {
            if (w[i].isBlank()) continue;
            if (t.length() > 0) t.append(' ');
            t.append(w[i]);
        }
        // a title must not end on a word that is waiting for the next one
        String out = t.toString().strip()
                .replaceAll("(?i)\\s+(and|or|the|a|an|with|for|to|of|in|on|at|your|its|that|which)$", "")
                .strip();
        if (out.length() < 4) return switch (index) {
            case 0 -> "What you see";
            case 1 -> "When it speaks up";
            default -> "Where it stays";
        };
        return Character.toUpperCase(out.charAt(0)) + out.substring(1);
    }

    /** Trims to a word boundary, so a long title never ends mid-word. */
    private static String clip(String s, int max) {
        if (s.length() <= max) return s;
        int cut = s.lastIndexOf(' ', max);
        return (cut > 6 ? s.substring(0, cut) : s.substring(0, max)).strip();
    }

    /** Splits a run-together badge back into words.
     *  Qwen answered "EasyBookPadel", which the page letter-spaces and uppercases
     *  into "E A S Y B O O K P A D E L". Three words is the useful maximum. */
    private static String badge(String v) {
        if (v == null || v.isBlank()) return "introducing";
        String t = v.strip().replaceAll("[^A-Za-z0-9 ]", " ").replaceAll("\\s+", " ").strip();
        if (!t.contains(" ") && t.length() > 9)
            t = t.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        String[] w = t.split(" ");
        if (w.length > 3) t = String.join(" ", java.util.Arrays.copyOfRange(w, 0, 3));
        return t.isBlank() || t.length() > 26 ? "introducing" : t.toLowerCase();
    }

    private static String nzs(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    /**
     * Normalises copy from ANY path, not just the flat parser.
     *
     * <p>The one-word-title guard originally lived inside parseFlat, which meant it
     * only ran when the structured call had failed. Gemma succeeds at the structured
     * call, so the guard never fired and every page shipped "Rotation / Scheduling /
     * Reminders": a taxonomy, not a promise.
     */
    private static Copy normalise(Copy c) {
        if (c == null || c.features() == null) return c;
        List<Feature> out = new ArrayList<>();
        for (int i = 0; i < c.features().size(); i++) {
            Feature f = c.features().get(i);
            String t = f.title() == null ? "" : f.title().strip();
            String b = f.body() == null ? "" : f.body().strip();
            boolean junk = t.isBlank() || !clean(t) || !latin(t)
                    || t.matches("(?i)(one\\s+)?(title|sentence|features?|feature\\d+|body|name|description|details?|summary|text|label|f\\d+)\\s*\\d*");
            out.add(new Feature(junk ? titleFrom(b, i) : clip(demark(t), 22), demark(b)));
        }
        return new Copy(demark(c.badge()), demark(c.headline()), demark(c.subhead()), demark(c.cta()), out);
    }

    private static boolean usable(Copy c) {
        return c != null && c.headline() != null && c.features() != null && !c.features().isEmpty()
                && latin(c.headline()) && latin(c.subhead())
                && clean(c.headline()) && clean(c.subhead())
                && c.features().stream().allMatch(f -> clean(f.title()) && clean(f.body())
                        && latin(f.title()) && latin(f.body()));
    }

    /**
     * A call to action names an action, not a feeling.
     *
     * <p>"Simplify chores together now" was four words that asked the reader for
     * nothing. A usable button starts with a verb, stays under four words, and
     * avoids the words a model reaches for when it is describing a mood.
     */
    private static final java.util.regex.Pattern MOOD = java.util.regex.Pattern.compile(
            "(?i)\\b(simplify|together|today|now|easily|effortless|instantly|journey|movement|experience)\\b");
    private static final java.util.regex.Pattern BAD_OPENER = java.util.regex.Pattern.compile(
            "(?i)^(the|a|an|our|your|my|their|its|this|that|more|less|better|best|simply|just"
            + "|welcome|hello|introducing|discover|unlock|elevate|transform|empower)\b");

    private static boolean actionable(String cta) {
        String[] w = cta.strip().split("\\s+");
        if (w.length < 2 || w.length > 4) return false;
        if (MOOD.matcher(cta).find()) return false;
        if (BAD_OPENER.matcher(cta).find()) return false;
        // a real button opens with a verb, so the first word must not carry a comma or colon
        return !w[0].matches(".*[,:;].*");
    }

    /** The words a model writes when it has nothing to say. The guard flags these
     *  at serve time but cannot safely rewrite a sentence, so copy carrying one is
     *  refused here instead and the next path gets a turn. */
    private static final java.util.regex.Pattern FILLER = java.util.regex.Pattern.compile(
            "(?i)\\b(elevate|seamless|unleash|revolutionis|revolutioniz|next[- ]gen|supercharge|maximiz)\\w*");
    private static boolean clean(String s) {
        return s == null || !FILLER.matcher(s).find();
    }

    /** Qwen 3b drifts out of the language mid-sentence. One page came back as
     *  "Midnight Geyser Guard, Neighbor Peace保証". The room sees that instantly,
     *  so anything outside Latin script is refused rather than shipped. */
    /** Strips markdown the model emits as emphasis. It renders as literal asterisks. */
    private static String demark(String s) {
        if (s == null) return null;
        return s.replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("\\*(.+?)\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                .replaceAll("`(.+?)`", "$1")
                .replaceAll("[*_`]", "")
                .replaceAll("\\s{2,}", " ")
                .strip();
    }

    private static boolean latin(String s) {
        return s == null || s.codePoints().noneMatch(c -> c > 0x2FFF);
    }

    /** Picks a template slot rather than a rendered string.
     *
     *  <p>Keying the ledger on the finished sentence was a real bug: three pages
     *  in a row opened "Stop thinking about" and the ledger saw three different
     *  strings. An audience does not see strings, it sees the same sentence with
     *  a word swapped, so the slot is what has to be remembered. */
    private String slot(String[] options, String seed, String channel) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < options.length; i++) idx.add(i);
        return options[fresh(idx, seed, channel, i -> channel + "#" + i)];
    }

    /** The first three words, which is what an audience hears as "the same headline again". */
    private static String opener(String headline) {
        String[] w = headline.toLowerCase().replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+");
        return String.join(" ", Arrays.copyOfRange(w, 0, Math.min(3, w.length)));
    }
    private boolean staleOpener(String headline) {
        return recent.getOrDefault("opener", new ArrayDeque<>()).contains(opener(headline));
    }
    private List<String> recentOpeners() {
        return new ArrayList<>(recent.getOrDefault("opener", new ArrayDeque<>()));
    }
    private void rememberOpener(String headline) {
        Deque<String> seen = recent.computeIfAbsent("opener", k -> new ArrayDeque<>());
        seen.addLast(opener(headline));
        while (seen.size() > 4) seen.removeFirst();
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
            try {
                String n = pickName(crew.namer.name(idea));
                if (n != null && !slopName(n, idea)) return n;
                // one more attempt, with the refused name named
                String again = pickName(crew.namer.rename(idea, n == null ? "" : n));
                if (again != null && !slopName(again, idea)) {
                    System.out.println("  namer refused \"" + n + "\", second try: " + again);
                    return again;
                }
                if (n != null) System.out.println("  (both names were slop, using the house name)");
            } catch (RuntimeException e) { /* house name below */ }
        } else sleep(700);
        return cannedName(idea);
    }

    private static String pickName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String n = raw.strip().split("\\R")[0].replaceAll("[^A-Za-z ]", " ").replaceAll("\\s+", " ").strip();
        if (!latin(n) || n.length() < 3) return null;
        return trimName(n);
    }

    /**
     * The suffix reflex, refused.
     *
     * <p>Asked to name a chore rota, the model returns "Taskly". Asked to name a
     * booking board, "Bookly". The pattern is a noun from the prompt with a suffix
     * bolted on, and it tells a reader nothing. This catches it mechanically,
     * because the prompt alone does not hold.
     */
    private static final java.util.regex.Pattern SLOP_SUFFIX = java.util.regex.Pattern.compile(
            "(?i)(ly|ify|io|ery|hub|kit|pro|app|sync|wise|flow|hq|labs?|ai)$");

    private static boolean slopName(String name, String idea) {
        String flat = name.replaceAll("[^A-Za-z]", "");
        if (SLOP_SUFFIX.matcher(flat).find()) return true;
        // a bare noun lifted straight out of the idea is not a name either
        for (String w : idea.toLowerCase().split("[^a-z]+"))
            if (w.length() > 3 && w.equalsIgnoreCase(flat)) return true;
        return false;
    }

    /** Cuts a run-together name at a capital, never mid-word.    /** Cuts a run-together name at a capital, never mid-word.
     *  A blind 14-character slice produced "EstatePadelSyn" and "BeanFortnightl". */
    private static String trimName(String n) {
        String s = n.substring(0, 1).toUpperCase() + n.substring(1);
        if (s.length() <= 15) return s;
        for (int i = Math.min(15, s.length() - 1); i > 2; i--)
            if (Character.isUpperCase(s.charAt(i))) return s.substring(0, i);
        return s.substring(0, 12);
    }

    private List<String> safeRows(Bundle crew, String idea, String kind) {
        if (mock) return List.of();
        try {
            String raw = crew.illustrator.rows(idea, kind);
            if (raw == null) return List.of();
            List<String> out = new ArrayList<>();
            for (String part : raw.strip().split("[;\\n]")) {
                String t = part.strip().replaceAll("^[-*\\d.)\\s]+", "").replaceAll("^[\"']|[\"']$", "").strip();
                if (t.isBlank() || t.length() > 30 || !latin(t) || !clean(t)) continue;
                if (t.matches("(?i)(rows?|content|example|sample)\\s*:?")) continue;
                out.add(t);
                if (out.size() == 4) break;
            }
            return out;
        } catch (RuntimeException e) { return List.of(); }
    }

    /** Two to four plain words naming a real photographable scene. */
    private String safeScene(Bundle crew, String idea) {
        if (mock || !photosOn) return "";
        try {
            String raw = crew.illustrator.scene(idea);
            if (raw == null) return "";
            String t = demark(raw.strip().split("\\R")[0]).replaceAll("[^A-Za-z ]", " ")
                    .replaceAll("\\s+", " ").strip().toLowerCase();
            String[] w = t.split(" ");
            if (w.length < 1 || w.length > 5 || t.length() > 44) return "";
            // an abstraction is not a photograph
            if (t.matches("(?i).*\\b(teamwork|productivity|harmony|innovation|synergy|success|efficiency)\\b.*")) return "";
            return t;
        } catch (RuntimeException e) { return ""; }
    }

    /** The interface the mockup will draw. Split out so the Designer can assist on it. */
    private String pickKind(Bundle crew, String idea) throws InterruptedException {
        String kind = null;
        if (!mock) {
            try { kind = crew.illustrator.style(idea); }
            catch (RuntimeException e) { /* archetypeFor decides below */ }
        } else sleep(500);
        // The illustrator names the interface it wants; Mockup decides whether that
        // is what the idea actually needs. Deliberately NOT on the freshness ledger:
        // two booking apps should both get a calendar.
        return Mockup.archetypeFor(idea, kind);
    }

    /** The Designer's real second job: which colour leads the picture. Empty if it will not say. */
    private String safeEmphasis(Bundle crew, String idea, String kind) throws InterruptedException {
        if (!mock) {
            try {
                String e = crew.designer.emphasis(idea, kind);
                if (e != null) {
                    String w = e.strip().toLowerCase().replaceAll("[^a-z]", "");
                    if (w.equals("primary") || w.equals("accent")) return w;
                }
            } catch (RuntimeException ex) { /* primary leads by default */ }
        } else sleep(600);
        return "primary";
    }

    private Art buildArt(Bundle crew, String idea, Palette palette, String k, String emphasis)
            throws InterruptedException {
        // the emphasis orders the two colours, so a lively interface leads with the accent
        List<String> colors = "accent".equals(emphasis)
                ? List.of(palette.accent(), palette.primary())
                : List.of(palette.primary(), palette.accent());
        String scene = safeScene(crew, idea);
        if (!scene.isBlank()) Photos.find(scene, photosOn);
        return new Art(k, Math.floorMod(idea.hashCode(), 997), colors, safeRows(crew, idea, k), scene);
    }

    private Art safeArt(Bundle crew, String idea, Palette palette) throws InterruptedException {
        String k = pickKind(crew, idea);
        return buildArt(crew, idea, palette, k, "primary");
    }

    /** The Strategist reorders the features so the strongest one leads. */
    private List<Feature> leadWith(Bundle crew, String idea, List<Feature> feats) {
        if (mock || feats == null || feats.size() < 3) return feats;
        try {
            String r = crew.strategist.lead(idea, feats.get(0).title(), feats.get(1).title(), feats.get(2).title());
            if (r != null) {
                var m = java.util.regex.Pattern.compile("[123]").matcher(r);
                if (m.find()) {
                    int i = Integer.parseInt(m.group()) - 1;
                    if (i > 0 && i < feats.size()) {
                        List<Feature> out = new ArrayList<>(feats);
                        out.add(0, out.remove(i));   // move the chosen hook to the front
                        return out;
                    }
                }
            }
        } catch (RuntimeException e) { /* keep the given order */ }
        return feats;
    }

    /**
     * Best-of-N for the single most important line. The Copywriter drafts extra
     * headlines from other angles (forced apart by naming the openings already used),
     * then the Strategist judges the sharpest. One good headline outweighs any other
     * change on the page, so this is where the extra minutes are best spent.
     */
    private Copy refineHero(Bundle crew, String idea, Copy base) {
        if (mock || base == null || headlineCandidates < 2) return base;
        List<Copy> cands = new ArrayList<>();
        cands.add(base);
        try {
            for (int i = 0; i < headlineCandidates - 1 && cands.size() < 3; i++) {
                String used = cands.stream().map(c -> firstWords(c.headline())).collect(java.util.stream.Collectors.joining(", "));
                Copy alt = crew.copywriter.rewrite(idea, used);
                if (alt != null && alt.headline() != null && !alt.headline().isBlank())
                    cands.add(alt);
            }
        } catch (RuntimeException e) { /* fewer candidates is fine */ }
        if (cands.size() < 2) return base;
        // judge the headline and the subhead independently, so both are best-of-N
        String bestHead = pickSharpest(crew, idea, cands.stream().map(Copy::headline).toList(), base.headline());
        String bestSub  = pickSharpest(crew, idea, cands.stream().map(Copy::subhead).toList(), base.subhead());
        return new Copy(base.badge(), bestHead, bestSub, base.cta(), base.features());
    }

    /** Lets the Strategist pick the sharpest of up to three lines, padding to three. */
    private String pickSharpest(Bundle crew, String idea, List<String> options, String fallback) {
        List<String> o = new ArrayList<>();
        for (String s : options) if (s != null && !s.isBlank() && o.stream().noneMatch(x -> x.equalsIgnoreCase(s.trim()))) o.add(s.trim());
        if (o.size() < 2) return fallback;
        while (o.size() < 3) o.add(o.get(o.size() - 1));
        try {
            String r = crew.strategist.sharpest(idea, o.get(0), o.get(1), o.get(2));
            var m = java.util.regex.Pattern.compile("[123]").matcher(r == null ? "" : r);
            if (m.find()) {
                int i = Integer.parseInt(m.group()) - 1;
                if (i >= 0 && i < o.size()) return o.get(i);
            }
        } catch (RuntimeException e) { /* keep the fallback */ }
        return fallback;
    }

    private String firstWords(String s) {
        if (s == null || s.isBlank()) return "";
        String[] w = s.trim().split("\\s+");
        return String.join(" ", java.util.Arrays.copyOfRange(w, 0, Math.min(3, w.length)));
    }

    /**
     * Best-of-N for the product name. The Namer coins two more names (each told which
     * were refused, so they diverge), then the Strategist picks the most brandable.
     * A memorable name is what the room remembers, so the extra passes earn their time.
     */
    private String bestName(Bundle crew, String idea, String base) {
        if (mock || base == null || base.isBlank()) return base;
        List<String> cands = new ArrayList<>();
        cands.add(base.trim());
        try {
            for (int i = 0; i < 2 && cands.size() < 3; i++) {
                String alt = pickName(crew.namer.rename(idea, String.join(", ", cands)));
                if (alt != null && !alt.isBlank() && cands.stream().noneMatch(n -> n.equalsIgnoreCase(alt.trim())))
                    cands.add(alt.trim());
            }
        } catch (RuntimeException e) { /* fewer candidates is fine */ }
        if (cands.size() < 2) return base;
        while (cands.size() < 3) cands.add(cands.get(cands.size() - 1));
        try {
            String r = crew.strategist.bestName(idea, cands.get(0), cands.get(1), cands.get(2));
            var m = java.util.regex.Pattern.compile("[123]").matcher(r == null ? "" : r);
            if (m.find()) {
                int i = Integer.parseInt(m.group()) - 1;
                if (i >= 0 && i < cands.size()) return cands.get(i);
            }
        } catch (RuntimeException e) { /* keep the base name */ }
        return base;
    }

    /**
     * The Strategist decides whether this idea is really a paid product. Only then
     * does the page show pricing; free tools, personal projects and community ideas
     * get a simpler page that presents the idea instead of selling it. On any doubt
     * or failure it leans to "no pricing", because a fake price tag is the loudest
     * way a generated page announces itself as slop.
     */
    /** What the Architect decided: which sections this page needs, and the hero style. */
    record Plan(List<String> kinds, String hero) {}
    private static final List<String> KINDS = List.of("how", "catalog", "story", "proof");

    /**
     * Phase one of closing the gap on a hand made page: stop shipping one fixed
     * template. A food truck gets a menu and a story, a tool gets three steps. The
     * fallback is deliberately plain rather than empty, so a model wobble still
     * produces a page with a shape.
     */
    private Plan planPage(Bundle crew, String idea) {
        List<String> kinds = new ArrayList<>();
        String hero = "";
        if (!mock) {
            try {
                for (String line : crew.architect.plan(idea).split("\\R")) {
                    String l = line.trim().toLowerCase();
                    if (l.startsWith("sections:")) {
                        for (String k : l.substring(9).split(","))
                            if (KINDS.contains(k.trim()) && !kinds.contains(k.trim())) kinds.add(k.trim());
                    } else if (l.startsWith("hero:")) {
                        String h = l.substring(5).trim();
                        if (List.of("photo", "mockup", "editorial").contains(h)) hero = h;
                    }
                }
            } catch (RuntimeException e) { /* house plan below */ }
        }
        if (kinds.isEmpty()) kinds = List.of("how");
        if (kinds.size() > 3) kinds = kinds.subList(0, 3);
        return new Plan(kinds, hero);
    }

    /** Writes the content for each chosen section. A section that comes back empty is dropped. */
    private List<Section> buildSections(Bundle crew, String idea, String name, List<String> kinds) {
        List<Section> out = new ArrayList<>();
        if (mock) return out;
        for (String kind : kinds) {
            try {
                switch (kind) {
                    case "story" -> {
                        String s = tidy(crew.sections.story(name, idea));
                        if (s.length() > 40) out.add(new Section("story", "How it started", s, List.of()));
                    }
                    case "how" -> {
                        List<Feature> items = pairs(crew.sections.steps(name, idea), 3);
                        if (items.size() >= 2) out.add(new Section("how", "How it works", "", items));
                    }
                    case "catalog" -> {
                        List<Feature> items = pairs(crew.sections.catalog(name, idea), 6);
                        if (items.size() >= 3) out.add(new Section("catalog", catalogHeading(idea), "", items));
                    }
                    case "proof" -> {
                        List<Feature> items = pairs(crew.sections.proof(name, idea), 2);
                        if (items.size() >= 2) out.add(new Section("proof", "What people say", "", items));
                    }
                    default -> { }
                }
            } catch (RuntimeException e) { /* skip this section */ }
        }
        return out;
    }

    /** "title | body ;; title | body" into features, capped. */
    private static List<Feature> pairs(String raw, int max) {
        List<Feature> out = new ArrayList<>();
        if (raw == null) return out;
        for (String p : raw.split(";;")) {
            String[] kv = p.split("\\|", 2);
            if (kv.length == 2) {
                String t = tidy(kv[0]), b = tidy(kv[1]);
                if (!t.isBlank() && !b.isBlank() && t.length() < 60) out.add(new Feature(t, b));
            }
            if (out.size() >= max) break;
        }
        return out;
    }

    /** Names the list after what the business actually sells. */
    private static String catalogHeading(String idea) {
        String t = idea.toLowerCase();
        // check software first: "software for restaurants" sells features, not dinners
        if (t.matches("(?s).*\\b(software|app|platform|tool|system|dashboard|saas|website)\\b.*")) return "What you get";
        if (t.matches("(?s).*\\b(restaurant|food|menu|kitchen|cafe|coffee|bakery|truck|eat|meal|pizza|braai)\\b.*")) return "The menu";
        if (t.matches("(?s).*\\b(salon|barber|spa|clean|repair|plumb|electric|paint|garden|tutor|service|fix)\\b.*")) return "What we do";
        if (t.matches("(?s).*\\b(shop|store|sell|market|craft|make|goods|clothes|gear)\\b.*")) return "What we make";
        return "What you get";
    }

    private boolean safePriced(Bundle crew, String idea) {
        if (mock) return false;
        try {
            String r = crew.strategist.priced(idea);
            return r != null && r.toLowerCase().contains("paid");
        } catch (RuntimeException e) { return false; }
    }

    /** The Proofreader cleans one line; returns the original if nothing needs doing. */
    private String proof(Bundle crew, String line) {
        if (mock || line == null || line.isBlank() || line.length() > 160) return line;
        try {
            String r = crew.proofreader.fix(line);
            if (r != null) {
                r = demark(r.strip().split("\\R")[0]).replaceAll("^[\"']|[\"']$", "").strip();
                // only accept a real, same-or-shorter, clean improvement
                if (!r.isBlank() && latin(r) && clean(r) && r.length() <= line.length() + 4) return r;
            }
        } catch (RuntimeException e) { /* keep the original */ }
        return line;
    }

    private Copy proofread(Bundle crew, Copy c) {
        if (mock || c == null) return c;
        List<Feature> feats = new ArrayList<>();
        for (Feature f : c.features()) feats.add(new Feature(proof(crew, f.title()), proof(crew, f.body())));
        return new Copy(c.badge(), proof(crew, c.headline()), proof(crew, c.subhead()), c.cta(), feats);
    }

    /** The Researcher's third job: one concrete number, handed to the Skeptic. */
    private String safeGap(Bundle crew, String idea) {
        if (!mock) {
            try {
                String g = crew.researcher.gap(idea);
                if (g != null) {
                    g = demark(g.strip().split("\\R")[0]).replaceAll("^[\"']|[\"'.]$", "").strip();
                    if (latin(g) && clean(g) && g.length() >= 8 && g.length() <= 90) return g;
                }
            } catch (RuntimeException e) { /* skeptic manages without */ }
        }
        return "";
    }

    /** The Insight agent's second job: does the headline still answer the tension? */
    private String safeAnswers(Bundle crew, String tension, String headline) {
        if (mock || tension == null || tension.isBlank()) return "";
        try {
            String a = crew.insight.answers(tension, headline);
            if (a != null) {
                a = demark(a.strip().split("\\R")[0]).replaceAll("^[\"']|[\"']$", "").strip();
                if (latin(a) && a.length() >= 4 && a.length() <= 90) return a;
            }
        } catch (RuntimeException e) { /* the critic decides alone */ }
        return "";
    }

    /** The Namer's real second job: the tagline the tab and share card use. */
    private String safeTagline(Bundle crew, String name, String headline) throws InterruptedException {
        if (!mock) {
            try {
                String t = crew.namer.tagline(name, headline);
                if (t != null) {
                    t = demark(t.strip().split("\\R")[0]).replaceAll("^[\"']|[\"'.]$", "").strip();
                    if (latin(t) && clean(t) && t.length() >= 4 && t.length() <= 40) return t;
                }
            } catch (RuntimeException e) { /* no tagline is fine */ }
        } else sleep(500);
        return "";
    }

    private String safePolish(Bundle crew, String idea, String currentCta) throws InterruptedException {
        if (!mock) {
            try { String p = crew.reviewer.polish(idea, currentCta);
                if (p != null && !p.isBlank()) {
                    p = p.strip().replaceAll("^[\"']|[\"'.]$", "").replaceAll("\\s+", " ");
                    if (latin(p) && p.length() <= 26 && actionable(p)) return p;
                    System.out.println("  (button \"" + p + "\" was a mood, not an action)");
                }
            } catch (RuntimeException e) { /* house polish below */ }
        } else sleep(800);
        // Neutral verb-and-thing fallbacks: a bakery must never be told to start a
        // free trial with no card. Software-flavoured lines belong to the model, not here.
        String[] options = {"See it in action", "Take a look", "Find out more",
                "See what it does", "Have a look", "Start here"};
        return slot(options, idea + "pol", "cta");
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
        // Tier blurbs used to be hardcoded ("when the whole block wants in"), which read
        // as filler on every idea that was not a neighbourhood app. Ask for real ones.
        String[] b = tierBlurbs(crew, idea, productName, s);
        return List.of(
                new Tier("Starter", "Free", "forever", b[0],
                        List.of("One device", "The basics, properly", "No card needed"), false),
                new Tier("Everyday", mid, "per month", b[1],
                        List.of("Everything in Starter", "Unlimited use", "Share with family", "Email support"), true),
                new Tier(topTierName(idea), top, "per month", b[2],
                        List.of("Everything in Everyday", "Up to 25 people", "Priority support", "Export anything"), false));
    }

    /** Three tier descriptions written for this product, with house lines as a fallback. */
    private String[] tierBlurbs(Bundle crew, String idea, String productName, String s) {
        String[] house = {
                "Enough to find out whether " + s + " is really your problem.",
                "The one most people pick. " + productName + ", every day.",
                "For a whole team, with everything switched on."};
        if (mock) return house;
        try {
            // A model may answer "small | middle | large" on one line, or put each on its
            // own line, or echo the labels back. Take whatever is real and keep the rest.
            String raw = crew.faq.tiers(productName, idea);
            List<String> got = new ArrayList<>();
            for (String part : raw.split("[|\\n]")) {
                String t = tidy(part).replaceAll("(?i)^(small|middle|large)\\s*[:-]?\\s*", "");
                if (t.length() > 12 && t.length() <= 90 && !t.matches("(?i)small|middle|large")) got.add(t);
            }
            String[] out = new String[3];
            for (int i = 0; i < 3; i++) out[i] = i < got.size() ? got.get(i) : house[i];
            return out;
        } catch (RuntimeException e) { /* house lines */ }
        return house;
    }

    /**
     * Real questions for this idea. The old hardcoded FAQ asked "does it really work
     * for software?" and promised the data never leaves your device, on a product whose
     * own feature was a phone app: the page contradicted itself. Falls back to safe,
     * claim-free house questions only if the model gives nothing usable.
     */
    private List<Qa> buildFaq(Bundle crew, String idea, String productName) {
        if (!mock) {
            try {
                List<Qa> qs = new ArrayList<>();
                String rawFaq = crew.faq.write(productName, idea);
                for (String pair : rawFaq.split(rawFaq.contains(";;") ? ";;" : "\\R")) {
                    String[] qa = pair.split("\\|", 2);
                    if (qa.length == 2) {
                        String q = tidy(qa[0]), a = tidy(qa[1]);
                        if (!q.isBlank() && !a.isBlank() && q.length() < 120) qs.add(new Qa(q, a));
                    }
                }
                if (qs.size() >= 2) return qs.size() > 3 ? qs.subList(0, 3) : qs;
            } catch (RuntimeException e) { /* house questions */ }
        }
        String s = topic(idea);
        return List.of(
                new Qa("Who is this for?", "Anyone dealing with " + s + " who wants it handled properly."),
                new Qa("How long does it take to start?", "Minutes. There is nothing to install and nothing to learn first."),
                new Qa("What if it does not suit me?", "Then leave it. Nothing is locked in and nothing is lost."));
    }

    /** Strips list markers, labels and stray quotes from a model's line. */
    private static String tidy(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)^\\s*(?:[-*\\d.)\\s]+|Q:|A:|question:|answer:)\\s*", "")
                .replaceAll("^[\"'“‘]|[\"'”’]$", "").strip();
    }

    private static String words(String idea) {
        return idea.strip().replaceAll("[.\\s]+$", "").replaceFirst("(?i)^an?\\s+", "");
    }

    // A pronoun or a modal is where the noun phrase ends. Without them the
    // subject ran on into "reading list I can", which then landed in a headline.
    private static final Set<String> STOP = Set.of("that","which","who","when","where","so","and",
            "but","for","to","with","if","because","while","after","before","from","by","of","in","on","at",
            "i","you","we","they","he","she","it","my","our","your","their","me","us",
            "can","could","would","should","will","shall","may","might","do","does","did");
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
                if (s != null && !s.isBlank()) {
                    s = s.strip().replaceAll("^\"|\"$", "");
                    // this line is the page's headline, so it gets the same checks
                    if (latin(s) && s.length() <= 70 && !staleOpener(s)) { rememberOpener(s); return s; }
                }
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
                "The lazy way to handle your " + t + ". On purpose.",
                "Stop thinking about your " + t + ".",
                cap(s) + ", without the admin." };
        return slot(options, idea + "rev", "headline");
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
    private static final String[] CTAS = {"Get early access", "Start free", "Join the waitlist", "Try it now",
            "See how it works", "Set it up now"};

    private Copy cannedCopy(String idea) { return cannedCopy(idea, null); }

    private Copy cannedCopy(String idea, String productName) {
        String s = subject(idea), t = topic(idea);
        String P = productName == null || productName.isBlank() ? cap(s) : productName;
        String[] heads = {
                cap(s) + ", finally done properly.",
                P + ". The " + s + " that remembers for you.",
                "Never think about your " + t + " again.",
                cap(s) + " that actually works.",
                P + " handles your " + t + ". You do not.",
                "The " + s + " you will actually keep using." };
        String[] subs = {
                P + " watches your " + t + " so you can get on with your day. Set it up once, in under a minute.",
                "A " + s + " with one job, done quietly in the background. No dashboards, no nagging, no setup wizard.",
                "Everything you need for your " + t + ", and nothing you don't. It works even when you forget it exists.",
                "Built for the days you forget. " + P + " keeps an eye on your " + t + " and only speaks up when it matters.",
                "One screen, one job. " + P + " deals with your " + t + " and stays out of the way the rest of the time.",
                "The " + s + " you set up in a minute and then stop thinking about. That is the whole pitch.",
                "No accounts to chase, no group chat. " + P + " handles your " + t + " and tells you only what changed." };
        return new Copy(
                slot(BADGES, idea, "badge"),
                slot(heads, idea + "h", "headline"),
                slot(subs, idea + "sh", "subhead"),
                slot(CTAS, idea + "cta", "cta"),
                featureSet(idea, P, t));
    }

    /** Four house feature sets on the ledger, because one repeated set is exactly
     *  the thing that makes five pages look like one page. */
    private List<Feature> featureSet(String idea, String P, String t) {
        List<List<Feature>> sets = List.of(
                List.of(new Feature("Effortless", P + " handles your " + t + " in the background. You get on with your day."),
                        new Feature("Ready in seconds", "Open it and you are already going. No manual, no setup wizard."),
                        new Feature("Private by default", "Runs close to home. Your data stays where it belongs.")),
                List.of(new Feature("Always watching", "It keeps an eye on your " + t + " even when you have forgotten it exists."),
                        new Feature("One tap", "The whole thing is a single screen. That is the entire product."),
                        new Feature("Works offline", "No signal, no problem. It catches up when you are back.")),
                List.of(new Feature("Quietly clever", "It learns your habits around your " + t + " and stops asking the obvious questions."),
                        new Feature("Share it", "Bring in the family, the team, the whole street. Everyone stays in sync."),
                        new Feature("Free to start", "Use it properly before you decide whether it is worth anything.")),
                List.of(new Feature("No nagging", "It tells you once, at the right moment, and then leaves you alone."),
                        new Feature("Honest numbers", "See exactly what your " + t + " is costing you, in plain language."),
                        new Feature("Yours to keep", "Export everything, any time. No hostage taking.")));
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < sets.size(); i++) idx.add(i);
        return sets.get(fresh(idx, idea + "f", "features", i -> "set#" + i));
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

    // Palette families. The previous set had #7c9cff, #7c3aed and #a78bfa in it:
    // a saturated blue-violet is where every model drifts, and an audience reads
    // it as generated before it reads a word. None of these sit in that band.
    private static final List<Palette> PALETTES = List.of(
        new Palette("forest",      "#0c1410", "#14201a", "#eef5ef", "#93a89a", "#5fa777", "#d8a53d", "-apple-system,Segoe UI,Roboto,sans-serif"),
        new Palette("black-tan",   "#111010", "#1c1a18", "#f4f1ec", "#a49b8f", "#c8925a", "#5d7f8c", "-apple-system,Segoe UI,Roboto,sans-serif"),
        new Palette("cobalt",      "#fbfaf7", "#ffffff", "#12171f", "#5f6773", "#1a4fd6", "#0f766e", "-apple-system,Segoe UI,Roboto,sans-serif"),
        new Palette("terracotta",  "#f7f5f3", "#ffffff", "#1a1614", "#6d635c", "#b8532f", "#3f5a68", "Georgia,'Times New Roman',serif"),
        new Palette("olive",       "#16170f", "#20221a", "#f2f4e9", "#9ba28a", "#8a9a3f", "#c8562f", "-apple-system,Segoe UI,Roboto,sans-serif"),
        new Palette("cold-lux",    "#0e0f11", "#181a1d", "#f2f4f7", "#969ba3", "#c9ced6", "#6fb0c4", "-apple-system,Segoe UI,Roboto,sans-serif"),
        new Palette("mono-pop",    "#faf9f8", "#ffffff", "#141414", "#666666", "#e0472c", "#141414", "-apple-system,Segoe UI,Roboto,sans-serif"),
        new Palette("ink-emerald", "#0b0f0e", "#141a18", "#eef6f2", "#8fa39b", "#2fae7e", "#e6c458", "Georgia,'Times New Roman',serif"));

    // The vibe word a model returns, mapped to the family it is asking for.
    private static final Map<String, String> VIBE = Map.of(
        "techy", "cold-lux", "warm", "terracotta", "fresh", "ink-emerald",
        "playful", "mono-pop", "bold", "olive", "calm", "cobalt");

    // --- Freshness ledger -------------------------------------------------
    // The taste guard reads one page in isolation, so it cannot see the thing an
    // audience notices first: that the last four pages were the same page. This
    // keeps a short memory per channel and refuses any value used recently. The
    // model still proposes; the harness is what guarantees the room sees variety.
    private final Map<String, Deque<String>> recent = new ConcurrentHashMap<>();
    private static final Map<String, Integer> MEMORY = Map.of(
        "palette", 6, "layout", 2, "art", 3, "cta", 4, "headline", 5, "subhead", 4, "badge", 3);

    /** Records a value the model chose itself, so the next page cannot repeat it. */
    private void remember(String channel, String value) {
        Deque<String> seen = recent.computeIfAbsent(channel, k -> new ArrayDeque<>());
        if (seen.contains(value)) seen.remove(value);
        seen.addLast(value);
        while (seen.size() > MEMORY.getOrDefault(channel, 3)) seen.removeFirst();
    }

    private boolean seenRecently(String channel, String value) {
        return recent.getOrDefault(channel, new ArrayDeque<>()).contains(value);
    }

    private <T> T fresh(List<T> list, String seed, String channel, java.util.function.Function<T, String> key) {
        Deque<String> seen = recent.computeIfAbsent(channel, k -> new ArrayDeque<>());
        List<T> pool = list.stream().filter(x -> !seen.contains(key.apply(x))).toList();
        List<T> from = pool.isEmpty() ? list : pool;
        T chosen = from.get(Math.floorMod(seed.hashCode(), from.size()));
        remember(channel, key.apply(chosen));
        return chosen;
    }

    // Three genuinely different hero compositions. One template that always looks
    // the same is the thing that reads as generated, so the composition is a
    // decision the harness makes per idea rather than a constant.
    private static final List<String> LAYOUTS = List.of("split", "editorial", "band");

    /**
     * The Designer's layout choice, honoured unless the room has just seen it.
     *
     * <p>This is the one visual decision handed to the model rather than taken
     * from it. Palette and artwork can both be recovered from keywords, so the
     * keywords win there. "Is this a sentence people need to read, or a thing
     * people need to see" cannot be recovered from keywords, so the model owns it
     * and the ledger only stops it repeating.
     */
    private String layoutFor(Bundle crew, String idea) {
        String want = null;
        if (!mock) {
            try {
                String raw = crew.designer.layout(idea);
                if (raw != null) {
                    String w = raw.strip().toLowerCase().replaceAll("[^a-z]", "");
                    // asked for one of three words, models still answer with a fourth
                    if (LAYOUTS.contains(w)) want = w;
                    else System.out.println("  (designer answered \"" + w + "\", which is not a layout)");
                }
            } catch (RuntimeException e) { /* the ledger picks below */ }
        }
        if (want != null && !seenRecently("layout", want)) {
            remember("layout", want);
            return want;
        }
        return fresh(LAYOUTS, idea + "lay", "layout", x -> x);
    }

    private Palette paletteFor(String vibe, String idea) {
        String want = vibe == null ? null : VIBE.get(vibe.strip().toLowerCase().replaceAll("[^a-z]", ""));
        // honour the designer's choice unless the room has just seen that family
        if (want != null && !seenRecently("palette", want)) {
            for (Palette p : PALETTES)
                if (p.family().equals(want)) { remember("palette", want); return p; }
        }
        return fresh(PALETTES, idea + "pal", "palette", Palette::family);
    }

    /** The top tier is named for who it is for, which is not always a street. */
    private String topTierName(String idea) {
        String t = idea.toLowerCase();
        if (t.matches("(?s).*\\b(street|neighbour|neighbor|estate|block|community|stokvel)\\b.*")) return "For the street";
        if (t.matches("(?s).*\\b(house|housemate|flat|family|home)\\b.*")) return "For the house";
        if (t.matches("(?s).*\\b(team|office|company|staff|business|client|clients|freelance|invoice)\\b.*")) return "For the studio";
        if (t.matches("(?s).*\\b(club|society|league|members)\\b.*")) return "For the club";
        return "For everyone";
    }

    private static <T> T pick(T[] arr, String seed) { return arr[Math.floorMod(seed.hashCode(), arr.length)]; }

    // ---------- SSE plumbing ----------

    public Map<String, Object> info(String audienceUrl) {
        // the stage banner used to say "qwen" no matter what was loaded
        // The page used to promise the room that nothing they typed left the
        // building. That is only true when the model is on this laptop. Say which
        // it is, and let the pages tell the truth for the run they are in.
        boolean local = !"api".equals(modelName) && (apiBaseUrl == null || apiBaseUrl.isBlank());
        return Map.of("audienceUrl", audienceUrl, "mock", mock, "open", submissionsOpen,
                "model", modelName, "local", local,
                "where", local ? "on a laptop in this room" : "on a laptop in this room, with the writing done by a hosted model");
    }

    /**
     * A viewer who closed a tab must never take the build down with them. One dead
     * connection used to throw a broken pipe out of here, up through the crew, and
     * the whole run died: the stage went quiet and the idea was quietly rebuilt in
     * the background instead. A phone locking mid-talk is not an exceptional event,
     * so nothing thrown by a single emitter is allowed past this point.
     */
    /**
     * A stream can die without either end noticing: the browser still reports the
     * connection open, the server still holds an emitter, and nothing flows. On a
     * projector that looks exactly like a demo that has hung. A beat every ten
     * seconds gives both ends something to miss, and keeps proxies from closing an
     * idle connection.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void heartbeat() {
        broadcast("beat", Map.of("t", System.currentTimeMillis()));
    }

    private void broadcast(String event, Object data) {
        for (SseEmitter em : new ArrayList<>(emitters)) send(em, event, data);
    }
    private void send(SseEmitter em, String event, Object data) {
        try {
            em.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (Throwable t) {
            emitters.remove(em);
            try { em.complete(); } catch (Throwable ignored) { /* already gone */ }
        }
    }
    private void sleep(long ms) throws InterruptedException { Thread.sleep(ms); }
}

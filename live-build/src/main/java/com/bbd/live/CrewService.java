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

    @Value("${live.mock:false}") boolean mock;
    @Value("${live.model:qwen2.5:3b}") String modelName;
    @Value("${live.ollama:http://localhost:11434}") String ollamaUrl;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final List<Idea> queue = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ExecutorService runner = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;

    // limits so a live crowd can't flood or embarrass the queue
    private static final int MAX_QUEUE = 250;      // total ideas kept
    private static final int MAX_PER_IP = 8;       // ideas per phone
    private static final long COOLDOWN_MS = 6000;  // wait between submits per phone
    private final Map<String, long[]> ipState = new ConcurrentHashMap<>(); // ip -> [lastMs, count]
    private static final Set<String> BLOCK = Set.of(
            "fuck", "shit", "cunt", "bitch", "bastard", "asshole", "dick",
            "nigger", "faggot", "retard", "rape"); // light filter; you also curate what runs

    // lazily built on the first live run
    private Agents.Copywriter copywriter;
    private Agents.Designer designer;
    private Agents.Skeptic skeptic;

    // ---------- audience + presenter API ----------

    public Map<String, Object> submit(String text, String name, String ip) {
        String t = text == null ? "" : text.strip();
        if (t.length() < 3) return err("Give it a few more words.");
        if (t.length() > 120) t = t.substring(0, 120);
        if (isBlocked(t)) return err("Let's keep it friendly.");
        if (queue.size() >= MAX_QUEUE) return err("The queue is full for now. Thanks!");
        for (Idea i : queue) if (i.text.equalsIgnoreCase(t)) return err("Someone already sent that one.");

        long now = System.currentTimeMillis();
        long[] st = ipState.computeIfAbsent(ip == null ? "?" : ip, k -> new long[]{0, 0});
        synchronized (st) {
            if (now - st[0] < COOLDOWN_MS) return err("One at a time. Give it a few seconds.");
            if (st[1] >= MAX_PER_IP) return err("That's plenty from you. Let others have a go.");
            st[0] = now; st[1]++;
        }

        Idea idea = new Idea(String.valueOf(nextId.getAndIncrement()), t, name == null ? "" : name.strip());
        queue.add(idea);
        broadcast("queue", queuePayload());
        broadcast("tally", Map.of("total", queue.size()));
        return Map.of("ok", true, "position", queue.size(), "mock", mock);
    }

    private Map<String, Object> err(String message) { return Map.of("ok", false, "error", message); }

    private boolean isBlocked(String text) {
        for (String w : text.toLowerCase().split("[^a-z]+")) if (BLOCK.contains(w)) return true;
        return false;
    }

    public Map<String, Object> queuePayload() {
        List<Map<String, Object>> ideas = new ArrayList<>();
        for (Idea i : queue) ideas.add(Map.of("id", i.id, "text", i.text, "name", i.name, "status", i.status));
        return Map.of("ideas", ideas);
    }

    public Map<String, Object> run(String id) {
        if (running) return Map.of("ok", false, "error", "already running");
        Idea idea;
        if ("demo".equals(id)) idea = new Idea("demo", "an app that waters your plants when you forget", "the house crew");
        else idea = queue.stream().filter(i -> i.id.equals(id)).findFirst().orElse(null);
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

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    private void runCrew(Idea idea) throws Exception {
        broadcast("run-start", Map.of("id", idea.id, "idea", idea.text, "name", idea.name));
        sleep(450);
        broadcast("graph", Map.of(
                "nodes", List.of(
                        node("copywriter", "copy"), node("designer", "design"),
                        node("builder", "build"), node("skeptic", "skeptic")),
                "edges", List.of(
                        edge("designer", "copywriter", false),
                        edge("copywriter", "builder", false),
                        edge("designer", "builder", false),
                        edge("builder", "skeptic", false),
                        edge("skeptic", "copywriter", true))));
        sleep(400);

        // round 1: two agents in parallel
        broadcast("agent-state", Map.of("key", "copywriter", "state", "working", "note", "drafting the hero…"));
        broadcast("agent-state", Map.of("key", "designer", "state", "working", "note", "choosing a palette…"));
        Future<Copy> copyF = pool.submit(() -> safeCopy(idea.text));
        Future<Palette> paletteF = pool.submit(() -> safePalette(idea.text));

        // the designer's little job lands first; it applies, then assists
        Palette palette = paletteF.get();
        broadcast("worker-done", Map.of("key", "designer", "payload", palette));
        broadcast("agent-state", Map.of("key", "designer", "state", "assisting", "note", "done early, so it helps: sends the copywriter a tone hint"));
        broadcast("flow", Map.of("from", "designer", "to", "copywriter", "what", "tone hint"));
        sleep(600);
        broadcast("agent-state", Map.of("key", "designer", "state", "done"));

        Copy copy = copyF.get();
        broadcast("worker-done", Map.of("key", "copywriter", "payload", copy));
        broadcast("agent-state", Map.of("key", "copywriter", "state", "done", "note", "hero: \"" + copy.headline() + "\""));
        broadcast("flow", Map.of("from", "copywriter", "to", "builder", "what", "copy"));
        broadcast("flow", Map.of("from", "designer", "to", "builder", "what", "palette"));

        broadcast("agent-state", Map.of("key", "builder", "state", "working", "note", "assembling the page…"));
        sleep(800);
        broadcast("worker-done", Map.of("key", "builder"));
        broadcast("agent-state", Map.of("key", "builder", "state", "done", "note", "page assembled, 3 sections"));

        broadcast("flow", Map.of("from", "builder", "to", "skeptic", "what", "the page"));
        broadcast("agent-state", Map.of("key", "skeptic", "state", "working", "note", "poking holes…"));
        String note = safeSkeptic(idea.text);
        broadcast("worker-done", Map.of("key", "skeptic", "payload", Map.of("note", note)));
        broadcast("agent-state", Map.of("key", "skeptic", "state", "done"));

        // the net closes: critique flows back, the copywriter revises live
        broadcast("flow", Map.of("from", "skeptic", "to", "copywriter", "what", "critique"));
        broadcast("agent-state", Map.of("key", "copywriter", "state", "working", "note", "takes the critique, revising the hero…"));
        String revised = safeRevise(idea.text, copy.headline(), note);
        broadcast("revise", Map.of("field", "headline", "value", revised, "by", "copywriter"));
        broadcast("flow", Map.of("from", "copywriter", "to", "builder", "what", "revised hero"));
        broadcast("agent-state", Map.of("key", "copywriter", "state", "done", "note", "headline revised. the web is settled"));

        idea.status = "done";
        broadcast("run-done", Map.of("id", idea.id));
        broadcast("queue", queuePayload());
    }

    private Map<String, String> node(String key, String label) { return Map.of("key", key, "label", label); }
    private Map<String, Object> edge(String from, String to, boolean feedback) {
        return feedback ? Map.of("from", from, "to", to, "feedback", true) : Map.of("from", from, "to", to);
    }

    // ---------- agents with fallbacks ----------

    private synchronized void ensureAgents() {
        if (copywriter != null) return;
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(ollamaUrl).modelName(modelName)
                .temperature(0.6).timeout(Duration.ofMinutes(2)).build();
        copywriter = AiServices.create(Agents.Copywriter.class, model);
        designer = AiServices.create(Agents.Designer.class, model);
        skeptic = AiServices.create(Agents.Skeptic.class, model);
    }

    private Copy safeCopy(String idea) throws InterruptedException {
        if (!mock) {
            try { ensureAgents(); Copy c = copywriter.write(idea);
                if (c != null && c.headline() != null && c.features() != null && !c.features().isEmpty()) return c;
            } catch (RuntimeException e) { System.out.println("  (copywriter improvised, using the house draft)"); }
        } else sleep(1400);
        return cannedCopy(idea);
    }

    private Palette safePalette(String idea) throws InterruptedException {
        String vibe;
        if (!mock) {
            try { ensureAgents(); vibe = designer.vibe(idea); }
            catch (RuntimeException e) { vibe = null; }
        } else { sleep(1200); vibe = null; }
        return paletteFor(vibe, idea);
    }

    private String safeRevise(String idea, String headline, String critique) throws InterruptedException {
        if (!mock) {
            try {
                ensureAgents();
                String s = copywriter.revise(idea, headline, critique);
                if (s != null && !s.isBlank()) return s.strip().replaceAll("^\"|\"$", "");
            } catch (RuntimeException e) { System.out.println("  (revision improvised, using the house rewrite)"); }
        } else sleep(1200);
        return cannedRevise(idea);
    }

    private String cannedRevise(String idea) {
        String s = idea.strip().replaceAll("[.\\s]+$", "").replaceFirst("(?i)^an?\\s+", "");
        String cap = s.substring(0, 1).toUpperCase() + s.substring(1);
        String[] options = {
                cap + ". Zero effort, zero guilt.",
                "Set it up once. Never think about it again.",
                cap + ", minus the guesswork.",
                "The lazy way to " + s + ". On purpose."};
        return pick(options, idea + "rev");
    }

    private String safeSkeptic(String idea) throws InterruptedException {
        if (!mock) {
            try { ensureAgents(); String s = skeptic.critique(idea); if (s != null && !s.isBlank()) return s.strip(); }
            catch (RuntimeException e) { /* fall through */ }
        } else sleep(1000);
        return cannedSkeptic(idea);
    }

    // ---------- house answers (also the whole of mock mode) ----------

    private static final String[] BADGES = {"introducing", "new", "meet", "now live"};
    private static final String[] CTAS = {"Get early access", "Start free", "Join the waitlist", "Try it now"};

    private Copy cannedCopy(String idea) {
        String clean = idea.strip().replaceAll("[.\\s]+$", "");
        String stripped = clean.replaceFirst("(?i)^an?\\s+", "");
        String Title = clean.substring(0, 1).toUpperCase() + clean.substring(1);
        return new Copy(
                pick(BADGES, idea),
                Title.length() > 42 ? Title + "." : "Finally, " + stripped + ".",
                "A delightfully simple way to " + stripped + ". No setup, no nonsense, working in seconds.",
                pick(CTAS, idea + "cta"),
                List.of(
                        new Feature("Effortless", "It just works. It handles the hard part so you don't have to."),
                        new Feature("Yours in seconds", "Open it and you're already going. Zero learning curve."),
                        new Feature("Private by default", "Runs close to home. Your data stays where it belongs.")));
    }

    private String cannedSkeptic(String idea) {
        String s = idea.strip().replaceAll("[.\\s]+$", "").replaceFirst("(?i)^an?\\s+", "");
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

    public Map<String, Object> info(String audienceUrl) { return Map.of("audienceUrl", audienceUrl, "mock", mock); }

    private void broadcast(String event, Object data) {
        for (SseEmitter em : emitters) send(em, event, data);
    }
    private void send(SseEmitter em, String event, Object data) {
        try { em.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON)); }
        catch (Exception e) { emitters.remove(em); }
    }
    private void sleep(long ms) throws InterruptedException { Thread.sleep(ms); }
}

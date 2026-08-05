# Rehearsal and dry-run plan

How to test the whole talk end to end, as if presenting, so nothing surprises you
on the day. Work top to bottom. Each phase has a clear pass/fail.

Two machines help but are not required: your **laptop** (server + projector view)
and your **phone** (audience view). If you only have the laptop, a second browser
window stands in for the phone.

---

## Phase 0 — the day before

- [ ] `java -version` shows 17 or newer, and `mvn -v` works.
- [ ] Install Ollama, then `ollama pull qwen2.5:3b`.
- [ ] Warm the model once: `ollama run qwen2.5:3b "say hi in three words"`. First reply is slow, later ones are fast.
- [ ] Pre-compile so Maven is silent on stage: `cd live-build && mvn -q compile`.
- [ ] Open `deck/index.html`, press `F`, arrow all the way through once. Fonts load, no blank slides.
- [ ] Record a 60-second screen capture of one good live build. This is your parachute if the room's network dies.
- [ ] Read `talk/SPEECH.md` out loud once with a stopwatch. Mark where you run long.

Pass when: model answers fast, deck flips clean, you have a backup recording.

---

## Phase 1 — mock dry run (no model, solo)

Goal: prove every screen and the whole click-path works, instantly, with no model.

```bash
cd live-build
mvn spring-boot:run -Dspring-boot.run.arguments=--live.mock=true
```

- [ ] Open `http://localhost:8080/control`. The QR and a URL show. Footer says **mock mode**.
- [ ] Open `http://localhost:8080/stage` in a second window. It says "Ready for your idea".
- [ ] Open `http://localhost:8080/` (audience). Type an idea, Send. You see "You're in."
- [ ] The idea appears in `/control`.
- [ ] Click **Run**. On `/stage`: the crew lights up in order, the page builds section by section, then recolours when the Designer finishes, then the Skeptic line appears.
- [ ] Click **Run a built-in demo idea**. It builds the plant-watering example.

Pass when: submit -> appears in control -> Run -> a full landing page builds on stage.

---

## Phase 2 — real Qwen dry run (solo)

Goal: prove the actual agents produce sensible output at a watchable speed.

```bash
cd live-build
mvn spring-boot:run          # live mode, footer shows "qwen · local"
```

- [ ] Run the demo idea. Watch the terminal: the model is being called.
- [ ] The headline reads like real copy, not gibberish. The palette looks intentional. The skeptic line is a real sentence.
- [ ] Time one full build. If it feels too slow, keep `qwen2.5:3b` and keep prompts short (already the default). `qwen2.5:1.5b` is faster on a modest laptop.
- [ ] Submit three of your own ideas and build each. Different ideas give different palettes and copy.

Pass when: a real build finishes in a comfortable window (aim under 15 seconds) and reads well.

---

## Phase 3 — network and phones (the real risk)

Goal: prove a phone that is not your laptop can reach the server and submit.

- [ ] Turn on your phone's personal hotspot (your data plan).
- [ ] Join your **laptop** to that hotspot.
- [ ] Start the server: `cd live-build && mvn spring-boot:run`.
- [ ] On the laptop open `/join`. Note the URL, for example `http://172.20.10.3:8080/`.
- [ ] On a **second phone or a friend's phone**, join the same hotspot, scan the `/join` QR.
- [ ] Submit an idea from that phone. It appears in `/control` on your laptop.
- [ ] Build it. It shows on `/stage`.

If the phone cannot reach the laptop:
- Confirm both are on the *same* hotspot, not one on hotspot and one on venue Wi-Fi.
- Some phones isolate hotspot clients from each other. If so, use a small travel router, or have the laptop create the hotspot instead (macOS Internet Sharing), or fall back to your recorded video.

Pass when: a device that is not the laptop submits an idea and you build it.

---

## Phase 4 — failure drills (break it on purpose)

Run these so the real thing holds no surprises.

- [ ] **Ollama down.** Quit Ollama, submit, Run. Each agent falls back to a house answer and the page still builds. (Say it out loud on stage: "small local models improvise, so I gave the crew a safety net.")
- [ ] **Rude submission.** Send an idea with a swear word. It is rejected with "Let's keep it friendly." Nothing reaches the stage.
- [ ] **Spam.** Submit twice fast from one phone. The second is rejected with "One at a time." Submit 9 times total. After 8 it says "That's plenty from you."
- [ ] **Duplicate.** Two people send the same idea. The second gets "Someone already sent that one."
- [ ] **No submissions yet.** Before anyone submits, click **Run a built-in demo idea**. The show still works.
- [ ] **Total meltdown.** Play your recorded backup video and keep talking. Practice the sentence: "Here's one I ran earlier, same code, same laptop."

Pass when: none of these stop the talk.

---

## Phase 5 — full timing run

Present the whole thing to an empty room or one friend, with the stopwatch.

- [ ] Deck plus live build lands near 30 minutes (target map is in `talk/SPEECH.md`).
- [ ] The scan-to-join slide goes up early. Leave it long enough for people to actually join.
- [ ] You know the two safe cuts if you run long: trim the stack walk, tighten the Q&A.

---

## On the day — 10 minutes before

- [ ] Phone hotspot on. Laptop joined. Server running in live mode.
- [ ] Warm Qwen once: `ollama run qwen2.5:3b "hi"`.
- [ ] `/stage` on the projector, `/control` on your laptop, `/join` ready to show.
- [ ] Terminal font large, notifications off, Do Not Disturb on.
- [ ] Backup video open in a tab.

## The live-build moment (the sequence)

1. Earlier, on the scan-to-join slide, you already invited submissions. They have been piling up.
2. Switch the projector to `/stage`. Keep `/control` on your laptop.
3. "Right, let's see what you gave me." Scroll the queue, pick a good or funny one.
4. Hit **Run**. Narrate: copywriter, designer, the recolour, then read the skeptic's line out loud.
5. Build one or two more if time allows. Then back to the closing slides.

## After

- The queue and one-per-phone counters reset when you restart the server. Restart between sessions if you present twice.

---

## Quick reference

| Thing | Value |
|---|---|
| Audience URL | shown on `/join` and `/control`, auto-detected |
| Max ideas in the queue | 250 |
| Max ideas per phone | 8 |
| Cooldown per phone | 6 seconds |
| Builds at once | 1 (you curate which) |
| Model | `qwen2.5:3b`, local, changeable in `application.properties` |
| Mock mode | `--live.mock=true` |

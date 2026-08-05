package com.bbd.agents;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;

import java.time.Duration;

/**
 * One prompt. Many workers. In Java. On your own laptop.
 *
 * <p>Run it:
 * <pre>
 *   ollama pull qwen2.5:3b
 *   mvn -q compile exec:java
 *   mvn -q compile exec:java -Dexec.args="Plan our team's move to a four day week"
 * </pre>
 */
public class Main {

    // qwen2.5:3b is quick and reliable for a live run. Swap to qwen2.5:7b
    // for noticeably sharper workers if the laptop can take it.
    private static final String MODEL = "qwen2.5:3b";
    private static final String OLLAMA = "http://localhost:11434";

    private static final String DEFAULT_PROMPT =
            "Plan the launch of our new developer API.";

    public static void main(String[] args) {
        String prompt = args.length > 0 ? String.join(" ", args) : DEFAULT_PROMPT;

        System.out.println(Ansi.dim("\n  model: " + MODEL + "   (local, no cloud, no bill)"));
        System.out.println("  " + Ansi.prompt("one prompt >  ") + prompt);

        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(OLLAMA)
                .modelName(MODEL)
                .temperature(0.4)
                .timeout(Duration.ofMinutes(3))
                .build();

        String plan = new Orchestrator(model).run(prompt);

        System.out.println(Ansi.prompt("\n  === one plan back ===\n"));
        System.out.println("  " + plan.strip().replace("\n", "\n  ") + "\n");
    }
}

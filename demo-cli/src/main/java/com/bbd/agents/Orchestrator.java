package com.bbd.agents;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

import java.util.List;

/**
 * The whole show in one class: plan, dispatch, synthesize.
 *
 * <p>One prompt comes in. A planner turns it into a crew. Each worker runs.
 * A synthesizer merges the results. Every step is the same local model wearing
 * a different hat.
 */
final class Orchestrator {

    private final Planner planner;
    private final Worker worker;
    private final Synthesizer synthesizer;

    Orchestrator(ChatLanguageModel model) {
        // Three agents, one model. The only difference is the instructions.
        this.planner = AiServices.create(Planner.class, model);
        this.worker = AiServices.create(Worker.class, model);
        this.synthesizer = AiServices.create(Synthesizer.class, model);
    }

    String run(String request) {
        // 1. One prompt -> a crew (structured output).
        Plan plan = planWithFallback(request);
        System.out.println(Ansi.dim("\n  Planner assembled a crew of " + plan.crew().size() + ":\n"));

        // 2. Fan out. Each worker does its one job.
        StringBuilder notes = new StringBuilder();
        for (Plan.Assignment a : plan.crew()) {
            System.out.println(Ansi.worker("  * " + a.role()) + Ansi.dim("  <- " + a.task()));
            String answer = worker.work(a.role(), a.task());
            notes.append("## ").append(a.role()).append('\n').append(answer).append("\n\n");
            System.out.println(indent(answer));
        }

        // 3. Fan in. One plan back out.
        System.out.println(Ansi.dim("  Synthesizing...\n"));
        return synthesizer.synthesize(notes.toString());
    }

    /**
     * Small local models sometimes fumble strict JSON. On stage, a demo that
     * dies is worse than a demo that improvises, so we keep a house crew ready.
     */
    private Plan planWithFallback(String request) {
        try {
            Plan plan = planner.assemble(request);
            if (plan != null && plan.crew() != null && !plan.crew().isEmpty()) {
                return plan;
            }
        } catch (RuntimeException e) {
            System.out.println(Ansi.dim("  (planner improvised loosely, using the house crew)"));
        }
        return new Plan(List.of(
                new Plan.Assignment("Engineer", "What has to be built or changed for: " + request),
                new Plan.Assignment("Marketer", "How we tell the world about: " + request),
                new Plan.Assignment("Legal", "The one risk we cannot ignore in: " + request)
        ));
    }

    private static String indent(String block) {
        return "      " + block.strip().replace("\n", "\n      ") + "\n";
    }
}

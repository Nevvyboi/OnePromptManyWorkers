package com.bbd.agents;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * The orchestrator's brain. It reads a single human request and decides who
 * should work on it and what each of them should do.
 *
 * <p>Notice there is no method body. The interface IS the program. LangChain4j
 * turns the annotations plus the return type into a prompt, calls the model,
 * and maps the reply straight onto {@link Plan}.
 */
interface Planner {

    @SystemMessage("""
        You are a project lead who breaks a request into a small crew of
        specialists. Choose between 2 and 4 workers. Each worker gets a short,
        distinct role (for example: Engineer, Marketer, Legal, DevRel) and one
        clear instruction. Do not do the work yourself. Only assign it.
        """)
    @UserMessage("Assemble the crew for this request: {{it}}")
    Plan assemble(String request);
}

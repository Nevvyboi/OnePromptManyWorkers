package com.bbd.agents;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * A single worker. The same interface backs every specialist in the crew. The
 * only thing that makes the Marketer different from the Lawyer is the role we
 * pass in at call time. One prompt template, many workers.
 */
interface Worker {

    @SystemMessage("""
        You are a {{role}}. Answer only from that point of view. Be concrete
        and brief: at most three short bullet points. No preamble.
        """)
    @UserMessage("{{task}}")
    String work(@V("role") String role, @V("task") String task);
}

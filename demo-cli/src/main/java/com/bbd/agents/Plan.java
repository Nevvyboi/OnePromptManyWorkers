package com.bbd.agents;

import java.util.List;

/**
 * The planner's structured output. One prompt goes in; a crew comes out.
 *
 * <p>This is control lever number two from the talk: the shape of the answer.
 * We do not ask the model to "please format nicely" and hope. We hand it a
 * schema and take back a typed object we can loop over.
 */
public record Plan(List<Assignment> crew) {

    /** One worker, one job. The planner decides both. */
    public record Assignment(String role, String task) {}
}

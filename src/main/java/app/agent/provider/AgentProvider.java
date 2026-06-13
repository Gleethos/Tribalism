package app.agent.provider;

import app.agent.AgentContext;
import app.agent.tool.ToolSpec;
import sprouts.Tuple;

/**
 *  The model behind the harness (§9.4): given the current {@link AgentContext} and the tools it may
 *  call, produce one {@link ProviderResponse}. This is the seam between the harness and a concrete
 *  backend — a local model or a user-configured API (Anthropic, OpenAI-compatible, …). For Claude,
 *  consult the {@code claude-api} reference and default to the latest models.
 *  <p>
 *  Keeping this an SPI lets the agent loop be unit-tested with a deterministic {@link ScriptedProvider}
 *  (per {@code API_STABILITY.md}) — no network, no model, fully reproducible.
 */
public interface AgentProvider {

    /**
     *  @param context the conversation so far (system prompt + transcript).
     *  @param tools   the tools the model is allowed to call this turn.
     *  @return the model's reply (assistant text and/or tool calls).
     */
    ProviderResponse respond( AgentContext context, Tuple<ToolSpec> tools );

    /** @return A short identifier for diagnostics/logging (e.g. the model name). */
    default String name() { return getClass().getSimpleName(); }
}

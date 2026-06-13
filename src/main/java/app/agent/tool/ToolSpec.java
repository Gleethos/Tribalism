package app.agent.tool;

import java.util.Objects;

/**
 *  The advertised description of a tool that is handed to the provider so the model knows what it may
 *  call (§9.3). Kept minimal for now (name + human description + tier); a richer parameter schema can
 *  be added without breaking call sites.
 */
public record ToolSpec(
    String  name,
    String  description,
    Tier    tier
) {
    /** Whether the tool is a high-level game operation or open-ended sandbox scratch work (§9.3). */
    public enum Tier { DOMAIN, SANDBOX }

    public ToolSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(tier, "tier");
    }

    public static ToolSpec domain( String name, String description )  { return new ToolSpec(name, description, Tier.DOMAIN); }
    public static ToolSpec sandbox( String name, String description ) { return new ToolSpec(name, description, Tier.SANDBOX); }
}

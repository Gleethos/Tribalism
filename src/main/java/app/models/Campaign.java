package app.models;

import dal.api.Value;

/** The immutable scalar state of a {@link CampaignModel} (its characters/npcs/maps are relations). */
public record Campaign(String name, String description) implements Value {
    public Campaign {
        if ( name == null || description == null )
            throw new IllegalArgumentException("Campaign fields must not be null");
    }
    public static Campaign empty() { return new Campaign("", ""); }
    public Campaign withName(String name)               { return new Campaign(name, description); }
    public Campaign withDescription(String description) { return new Campaign(name, description); }
}

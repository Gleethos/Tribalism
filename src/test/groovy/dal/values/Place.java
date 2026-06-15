package dal.values;

import dal.api.Value;

/** A value nesting a {@link GeoPoint}. */
public record Place(String name, GeoPoint location) implements Value {}

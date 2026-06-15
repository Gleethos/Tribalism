package dal.values;

import dal.api.Value;

/** A value nesting a {@link Place} (which itself nests a {@link GeoPoint}); held by OrgModel. */
public record Org(String title, Place place, int rank) implements Value {}

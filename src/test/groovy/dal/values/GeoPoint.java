package dal.values;

import dal.api.Value;

/** A leaf value with primitive (int) fields, nested two levels deep for query tests. */
public record GeoPoint(int lat, int lon) implements Value {}

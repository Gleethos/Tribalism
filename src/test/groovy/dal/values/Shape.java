package dal.values;

import dal.api.Value;

/** A sum type (sealed interface with record tips) used to demonstrate polymorphic value storage. */
public sealed interface Shape extends Value permits Circle, Rectangle, Triangle {}

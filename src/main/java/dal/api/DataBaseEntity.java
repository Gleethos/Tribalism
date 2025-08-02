package dal.api;

public sealed interface DataBaseEntity permits Model, Value
{
    // This interface serves as a marker for all database entities.
    // It can be extended by other interfaces like Model and Value.
    // No additional methods are defined here, as it is intended to be a simple marker interface.
}

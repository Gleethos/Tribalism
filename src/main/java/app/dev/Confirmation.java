package app.dev;

public interface Confirmation
{
    default String title() { return "Please confirm!"; }

    String question();

    default void yes() {}

    default void no() {}
}

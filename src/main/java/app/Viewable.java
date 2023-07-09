package app;

public interface Viewable
{
    <V> V createView(Class<V> viewType);
}

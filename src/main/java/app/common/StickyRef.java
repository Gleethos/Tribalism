package app.common;

import java.util.function.Supplier;

public class StickyRef
{
    private Object _o;

    public <T> T get(Supplier<T> supplier) {
        if ( _o != null )
            return (T) _o;

        _o = supplier.get();

        return (T) _o;
    }
}

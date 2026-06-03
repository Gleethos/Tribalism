package app.engine.util;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 *  A thread-safe, write-once memoized value: the supplied computation runs at
 *  most once, on first {@link #get()}, and the result is cached for every
 *  subsequent access.
 *  <p>
 *  This is the engine's tool for the "lazy values + heavy memoization" principle:
 *  it lets an otherwise-immutable value object (with proper value semantics) carry
 *  a derived, expensive-to-compute field that is only paid for if and when it is
 *  actually read. A {@code CameraF64}, for instance, uses it to cache its view,
 *  projection and frustum without recomputing them on every query.
 *  <p>
 *  Because the cached value is purely a function of the (immutable) inputs, a
 *  {@code Lazy} is deliberately excluded from the {@code equals}/{@code hashCode}
 *  of any value object that holds one.
 *
 *  @param <T> The type of the memoized value (never {@code null}).
 */
public final class Lazy<T>
{
    private volatile @Nullable Supplier<T> _supplier; // nulled out once the value is computed
    private @Nullable T                    _value;

    private Lazy( Supplier<T> supplier ) {
        _supplier = supplier;
    }

    /** @return A lazy value that will compute itself via {@code supplier} on first access. */
    public static <T> Lazy<T> of( Supplier<T> supplier ) {
        return new Lazy<>(supplier);
    }

    /** @return The memoized value, computing it on the first call (and only then). */
    public T get() {
        // Double-checked locking guarded by the volatile _supplier. It is written last
        // (after _value) under the lock, so a reader whose volatile read sees
        // _supplier == null is guaranteed (happens-before) to also see the computed
        // _value, even though _value itself is not volatile.
        if ( _supplier != null ) {
            synchronized ( this ) {
                Supplier<T> supplier = _supplier;
                if ( supplier != null ) {
                    _value    = supplier.get();
                    _supplier = null;
                }
            }
        }
        return _value;
    }
}
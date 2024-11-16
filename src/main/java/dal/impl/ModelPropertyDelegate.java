package dal.impl;

import sprouts.Channel;
import sprouts.Maybe;
import sprouts.Val;
import sprouts.ValDelegate;

import java.util.Objects;
import java.util.function.Function;

public final class ModelPropertyDelegate<T> implements ValDelegate<T> {

    private final Channel channel;
    private final Val<T> value;

    ModelPropertyDelegate( Channel channel, Val<T> value ) {
        this.channel = Objects.requireNonNull(channel);
        this.value   = Objects.requireNonNull(value);
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public T orElseNull() {
        return value.orElseNull();
    }

    @Override
    public Maybe<T> map(Function<T, T> mapper) {
        return value.map(mapper);
    }

    @Override
    public <U> Maybe<U> mapTo(Class<U> type, Function<T, U> mapper) {
        return value.mapTo(type, mapper);
    }

    @Override
    public Class<T> type() {
        return value.type();
    }

    @Override
    public String toString() {
        return "ValDelegate" + value.toString().substring(3);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;
        if ( obj.getClass() != this.getClass() ) return false;
        ModelPropertyDelegate<?> other = (ModelPropertyDelegate<?>) obj;
        return this.channel.equals(other.channel) &&
                this.value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channel, value);
    }

}

package dal.impl;

import sprouts.Action;
import sprouts.Observer;

import java.util.Objects;

final class PropertyProxyChangeListener<D> implements Action<D>
{
    private final Observer _observer;


    PropertyProxyChangeListener( Observer observer ) {
        _observer = Objects.requireNonNull(observer);
    }

    Observer listener() { return _observer; }

    @Override
    public void accept( D delegate ) throws Exception {
        _observer.invoke();
    }
}

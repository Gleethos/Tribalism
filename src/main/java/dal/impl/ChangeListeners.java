package dal.impl;

import org.slf4j.Logger;
import sprouts.Observer;
import sprouts.*;
import sprouts.impl.Sprouts;

import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class ChangeListeners<T>
{
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(ChangeListeners.class);
    private static final Cleaner CLEANER = Cleaner.create();

    private final Map<Channel, ChannelListeners<T>> _actions = new LinkedHashMap<>();


    public ChangeListeners() {}

    public ChangeListeners(ChangeListeners<T> other) {
        _copyFrom(other);
    }

    private void _copyFrom(ChangeListeners<T> other) {
        other._actions.forEach( (k, v) -> _actions.put(k, new ChannelListeners<>(v)) );
    }

    private ChannelListeners<T> _getActionsFor(Channel channel ) {
        return _actions.computeIfAbsent(channel, k->new ChannelListeners<>());
    }

    private void _removeActionIf( Predicate<Action<ValDelegate<T>>> predicate ) {
        for ( ChannelListeners<T> actions : _actions.values() )
            actions.removeIf(predicate);
    }

    public void onChange( Channel channel, Action<ValDelegate<T>> action ) {
        Objects.requireNonNull(channel);
        Objects.requireNonNull(action);
        _getActionsFor(channel).add(action);
    }

    public final void onChange( Observer observer ) {
        onChange(Sprouts.factory().defaultObservableChannel(), new PropertyProxyChangeListener<>(observer) );
    }

	public void fireChange( Val<T> owner, T oldItem, Channel channel ) {
		if ( channel == From.ALL)
			for ( Channel key : _actions.keySet() )
                _getActionsFor(key).trigger( channel, owner, oldItem );
		else {
            _getActionsFor(channel).trigger( channel, owner, oldItem );
            _getActionsFor(From.ALL).trigger( channel, owner, oldItem );
		}
	}

    public void unsubscribe( Subscriber subscriber ) {
        _removeActionIf( a -> {
                if ( a instanceof PropertyProxyChangeListener) {
                    PropertyProxyChangeListener<?> pcl = (PropertyProxyChangeListener<?>) a;
                    return pcl.listener() == subscriber;
                }
                else
                    return Objects.equals(a, subscriber);
            });
    }

    public void unsubscribeAll() {
        _actions.clear();
    }

    public long numberOfChangeListeners() {
        return _actions.values()
                            .stream()
                            .mapToLong(ChannelListeners::numberOfChangeListeners)
                            .sum();
    }

    @Override
    public final String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("[");
        for ( Channel key : _actions.keySet() ) {
            try {
                sb.append(key).append("->").append(_actions.get(key)).append(", ");
            } catch ( Exception e ) {
                log.error("An error occurred while trying to get the number of change listeners for channel '{}'", key, e);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static final class ChannelListeners<T> {

        private final List<Action<ValDelegate<T>>> _channelActions = new ArrayList<>();


        public ChannelListeners() {}

        public ChannelListeners(ChannelListeners<T> other ) {
            _channelActions.addAll(other._channelActions);
        }

        public void add( Action<ValDelegate<T>> action ) {
            _getActions( actions -> {
                if ( action instanceof WeakAction ) {
                    WeakAction<?,?> wa = (WeakAction<?,?>) action;
                    wa.owner().ifPresent( owner -> {
                        actions.add(action);
                        WeakReference<ChannelListeners<?>> weakThis = new WeakReference<>(this);
                        ChannelCleaner cleaner = new ChannelCleaner(weakThis, wa);
                        CLEANER.register(owner, cleaner);
                    });
                }
                else
                    actions.add(action);
            });
        }

        public void removeIf( Predicate<Action<ValDelegate<T>>> predicate ) {
            _getActions( actions -> actions.removeIf(predicate) );
        }

        private synchronized long _getActions(Consumer<List<Action<ValDelegate<T>>>> receiver) {
            receiver.accept(_channelActions);
            return _channelActions.size();
        }

        private long numberOfChangeListeners() {
            return _getActions( actions -> {} );
        }

        public void trigger( Channel channel, Val<T> owner, T oldItem ) {
            T newItem = owner.orElseNull();
            Class<T> type = owner.type();
            SingleChange change = SingleChange.of(type, newItem, oldItem);
            Val<T> oldProperty = Val.ofNullable(type, oldItem);
            ValDelegate<T> delegate = Sprouts.factory().delegateOf(oldProperty, channel, change, newItem); // TODO!!!!
            // We clone this property to avoid concurrent modification
            _getActions( actions -> {
                for ( Action<ValDelegate<T>> action : actions ) // We copy the list to avoid concurrent modification
                    try {
                        action.accept(delegate);
                    } catch ( Exception e ) {
                        log.error(
                            "An error occurred while executing " +
                            "action '"+action+"' for property '"+owner+"'",
                            e
                        );
                    }
            });
        }

        @Override
        public final String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(this.getClass().getSimpleName()).append("[");
            for ( Action<ValDelegate<T>> action : _channelActions ) {
                try {
                    sb.append(action).append(", ");
                } catch ( Exception e ) {
                    log.error("An error occurred while trying to get the string representation of the action '{}'", action, e);
                }
            }
            sb.append("]");
            return sb.toString();
        }

    }

    private static final class ChannelCleaner implements Runnable {
        private final WeakReference<ChannelListeners<?>> weakThis;
        private final WeakAction<?,?> wa;

        private ChannelCleaner(WeakReference<ChannelListeners<?>> weakThis, WeakAction<?, ?> wa) {
            this.weakThis = weakThis;
            this.wa = wa;
        }

        @Override
        public void run() {
            ChannelListeners<?> strongThis = weakThis.get();
            if ( strongThis == null )
                return;

            strongThis._getActions( innerActions -> {
                try {
                    wa.clear();
                } catch ( Exception e ) {
                    log.error(
                            "An error occurred while clearing the weak action '{}' during the process of " +
                            "removing it from the list of change actions.", wa, e
                        );
                }
                innerActions.remove(wa);
            });
        }
    }

}

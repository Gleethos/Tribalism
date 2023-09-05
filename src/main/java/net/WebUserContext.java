package net;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 *  Mostly a register for view model instances specific to a single web-user.
 *  The user is not necessarily a database user, but it is a user of the web portal
 *  uniquely identified by a http session id.
 *  The view model instances are stored in a WeakHashMap, so they can be garbage collected.
 */
public class WebUserContext
{
    private final String _httpSession;
    private final Map<Class, Map<Integer, WeakReference<Object>>> _viewModels = new HashMap<>();
    private final Map<Object, VMID<?>> _vmids = new WeakHashMap<>();
    private final List<String> _pendingMessages = new ArrayList<>();
    private Object rootViewModel = null; // A strong reference, so it can not be garbage collected.


    public WebUserContext(String sessionID) {
        _httpSession = sessionID;
    }

    public void addPendingMessage(String message) { _pendingMessages.add(message); }

    public Optional<String> getPendingMessage() {
        if ( _pendingMessages.isEmpty() ) return Optional.empty();
        return Optional.of(_pendingMessages.get(0));
    }

    public void removePendingMessage() {
        if ( _pendingMessages.isEmpty() ) return;
        _pendingMessages.remove(0);
    }

    public <T> T get( VMID<T> id ) {
        return (T)_viewModels.get(id.type()).get(id.id());
    }

    public <T> T get( String id ) {
        // The string has the following format "ViewModelClassName-Instance_ID"
        // For example: "UserRegistrationViewModel-0"
        var parts = id.split("-");
        var type = parts[0];
        var instanceId = Integer.parseInt(parts[1]);
        // Now we try to find the class :
        Class<?> clazz = null;
        try {
            clazz = Class.forName(type);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(
                        "Failed to find view model class '" + type + "' in " +
                        "web user context with session id '" + _httpSession + "'!"
                    );
        }
        Map<Integer, WeakReference<Object>> instances = _viewModels.get(clazz);
        if ( instances == null )
            throw new IllegalArgumentException(
                    "Failed to recognize view model class '" + type + "' in " +
                    "web user context with session id '" + _httpSession + "'!"
                );

        WeakReference<Object> instance = instances.get(instanceId);
        if ( instance != null ) {
            Object viewModel = instance.get();
            if ( viewModel != null ) return (T) viewModel;
            else
                throw new IllegalArgumentException(
                        "Found a view model entry with id '" + id + "' in web user context with id " + _httpSession + ", " +
                        "but the weak view model reference to it is null!\n" +
                        "This means that the view model was garbage collected, but the entry was not removed from the context!\n" +
                        "Available view model entries: " +
                                _viewModels.keySet()
                                        .stream()
                                        .map(Class::getName)
                                        .reduce((a,b) -> a + ", " + b)
                                        .orElse("none")
                    );
        }
        throw new IllegalArgumentException(
                "Failed to find view model with id '" + id + "' in " +
                "web user context with session id '" + _httpSession + "'!\n" +
                "Available view models: " +
                        _viewModels.keySet()
                                .stream()
                                .map(Class::getName)
                                .reduce((a,b) -> a + ", " + b)
                                .orElse("none")
            );
    }

    private <T> void _put( VMID<T> id, T viewModel ) {
        _viewModels.computeIfAbsent(id.type(), k -> new HashMap<>()).put(id.id(), new WeakReference<>(viewModel));
        _vmids.put(viewModel, id);
        if ( rootViewModel == null )
            rootViewModel = viewModel;
    }

    public <T> VMID<T> put( T viewModel ) {
        var id = new VMID<T>((Class<T>) viewModel.getClass(), _viewModels.size());
        _put(id, viewModel);
        return id;
    }

    public <T> VMID<T> vmIdOf( T viewModel ) {
        return (VMID<T>) _vmids.get(viewModel);
    }

    public boolean hasVM( Object viewModel ) {
        return _vmids.containsKey(viewModel);
    }

}

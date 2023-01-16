package binding;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 *  Mostly a register for view model instances.
 *  The view model instances are stored in a WeakHashMap, so they can be garbage collected.
 */
public class UserContext {

    private final Map<Class, Map<Integer, Object>> _viewModels = new HashMap<>();
    private final Map<Object, VMID<?>> _vmids = new WeakHashMap<>();

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
        try {
            var clazz = Class.forName(type);
            return (T)_viewModels.get(clazz).get(instanceId);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        throw new IllegalArgumentException("id");
    }

    private <T> void _put(VMID<T> id, T viewModel ) {
        _viewModels.computeIfAbsent(id.type(), k -> new HashMap<>()).put(id.id(), viewModel);
        _vmids.put(viewModel, id);
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

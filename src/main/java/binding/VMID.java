package binding;

public record VMID<T>(Class<T> type, int id) {
    public VMID {
        if (type == null) {
            throw new IllegalArgumentException("type");
        }
    }

    @Override
    public String toString() {
        return type.getName() + "-" + id;
    }
}

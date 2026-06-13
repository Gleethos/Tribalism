package dal.impl;

public class IncompatibleDatabaseFile extends RuntimeException {
    public IncompatibleDatabaseFile(String message) {
        super(message);
    }
}

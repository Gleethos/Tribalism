package net;

public interface Client {

    void send(String message);

    void close();

    boolean isOpen();

    String getRemoteAddress();

}

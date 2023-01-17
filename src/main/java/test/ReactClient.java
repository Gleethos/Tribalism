package test;

import net.Client;

import java.util.ArrayList;
import java.util.List;

public class ReactClient implements Client
{
    private final List<String> sent = new ArrayList<>();


    @Override
    public void send(String message) {
        sent.add(message);
    }

    @Override
    public void close() {
        sent.clear();
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public String getRemoteAddress() {
        return "localhost";
    }

}

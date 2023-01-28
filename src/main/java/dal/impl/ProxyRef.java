package dal.impl;

public record ProxyRef<P>(P proxy, P impl) {}

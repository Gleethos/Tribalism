package dal.impl;

import org.jspecify.annotations.NullMarked;

@NullMarked
record ProxyRef<P>(P proxy, P impl) {}

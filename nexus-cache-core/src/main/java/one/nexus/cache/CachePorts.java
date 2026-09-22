package one.nexus.cache;

import one.nexus.common.cache.CachePort;
import one.nexus.common.cache.CounterPort;
import one.nexus.common.cache.DistributedLockPort;

/** Combined facade wired as one bean. */
public interface CachePorts extends CachePort, DistributedLockPort, CounterPort {
    String backend();
}

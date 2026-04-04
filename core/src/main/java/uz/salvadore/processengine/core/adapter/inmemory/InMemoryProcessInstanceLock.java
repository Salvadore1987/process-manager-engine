package uz.salvadore.processengine.core.adapter.inmemory;

import uz.salvadore.processengine.core.port.outgoing.ProcessInstanceLock;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory per-instance locking using ReentrantLock.
 * Suitable for single-JVM deployments and testing.
 */
public final class InMemoryProcessInstanceLock implements ProcessInstanceLock {

    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public void lock(UUID processInstanceId) {
        locks.computeIfAbsent(processInstanceId, id -> new ReentrantLock()).lock();
    }

    @Override
    public void unlock(UUID processInstanceId) {
        ReentrantLock lock = locks.get(processInstanceId);
        if (lock != null) {
            lock.unlock();
        }
    }
}

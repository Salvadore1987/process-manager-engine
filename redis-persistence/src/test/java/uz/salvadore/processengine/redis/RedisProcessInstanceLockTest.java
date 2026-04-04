package uz.salvadore.processengine.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedisProcessInstanceLock")
class RedisProcessInstanceLockTest extends AbstractRedisTest {

    @Nested
    @DisplayName("lock and unlock")
    class LockAndUnlock {

        @Test
        @DisplayName("lock and unlock succeed for a single thread")
        void lockAndUnlockSingleThread() {
            // Arrange
            RedisProcessInstanceLock lock = new RedisProcessInstanceLock(redisTemplate);
            UUID processInstanceId = UUIDv7.generate();

            // Act & Assert — no exception
            lock.lock(processInstanceId);
            lock.unlock(processInstanceId);
        }

        @Test
        @DisplayName("same thread can re-lock after unlock")
        void relockAfterUnlock() {
            // Arrange
            RedisProcessInstanceLock lock = new RedisProcessInstanceLock(redisTemplate);
            UUID processInstanceId = UUIDv7.generate();

            // Act
            lock.lock(processInstanceId);
            lock.unlock(processInstanceId);
            lock.lock(processInstanceId);
            lock.unlock(processInstanceId);

            // Assert — no exception means success
        }

        @Test
        @DisplayName("different process instances can be locked concurrently")
        void differentInstancesConcurrent() {
            // Arrange
            RedisProcessInstanceLock lock = new RedisProcessInstanceLock(redisTemplate);
            UUID id1 = UUIDv7.generate();
            UUID id2 = UUIDv7.generate();

            // Act & Assert — no deadlock
            lock.lock(id1);
            lock.lock(id2);
            lock.unlock(id2);
            lock.unlock(id1);
        }
    }

    @Nested
    @DisplayName("contention")
    class Contention {

        @Test
        @DisplayName("concurrent threads are serialized by lock")
        void concurrentThreadsSerialized() throws InterruptedException {
            // Arrange
            RedisProcessInstanceLock lock = new RedisProcessInstanceLock(redisTemplate);
            UUID processInstanceId = UUIDv7.generate();
            AtomicInteger concurrentCount = new AtomicInteger(0);
            AtomicInteger maxConcurrent = new AtomicInteger(0);
            int threadCount = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // Act
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        lock.lock(processInstanceId);
                        try {
                            int current = concurrentCount.incrementAndGet();
                            maxConcurrent.updateAndGet(max -> Math.max(max, current));
                            Thread.sleep(50);
                            concurrentCount.decrementAndGet();
                        } finally {
                            lock.unlock(processInstanceId);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // Assert — at most 1 thread in the critical section at any time
            assertThat(maxConcurrent.get()).isEqualTo(1);
        }
    }
}

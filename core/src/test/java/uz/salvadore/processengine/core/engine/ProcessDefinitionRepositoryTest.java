package uz.salvadore.processengine.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.StartEvent;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessDefinitionRepositoryTest {

    private ProcessDefinitionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ProcessDefinitionRepository();
    }

    private ProcessDefinition createDefinition(String key, int version) {
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow1"), List.of(), null);
        SequenceFlow flow = new SequenceFlow("flow1", "start1", "end1", null);
        return ProcessDefinition.create(key, version, "Test Process v" + version, "<xml/>",
                List.of(startEvent, endEvent), List.of(flow));
    }

    @Nested
    @DisplayName("deploy()")
    class DeployTests {

        @Test
        @DisplayName("Should store deployed definition and retrieve by id")
        void shouldStoreAndRetrieveById() {
            // Arrange
            ProcessDefinition definition = createDefinition("order-process", 1);

            // Act
            repository.deploy(definition);

            // Assert
            Optional<ProcessDefinition> found = repository.getById(definition.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getKey()).isEqualTo("order-process");
            assertThat(found.get().getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should support deploying multiple versions of same key")
        void shouldSupportMultipleVersions() {
            // Arrange
            ProcessDefinition v1 = createDefinition("order-process", 1);
            ProcessDefinition v2 = createDefinition("order-process", 2);

            // Act
            repository.deploy(v1);
            repository.deploy(v2);

            // Assert
            assertThat(repository.getVersions("order-process")).hasSize(2);
        }
    }

    @Nested
    @DisplayName("undeploy()")
    class UndeployTests {

        @Test
        @DisplayName("Should remove all versions by key")
        void shouldRemoveAllVersionsByKey() {
            // Arrange
            ProcessDefinition v1 = createDefinition("order-process", 1);
            ProcessDefinition v2 = createDefinition("order-process", 2);
            repository.deploy(v1);
            repository.deploy(v2);

            // Act
            repository.undeploy("order-process");

            // Assert
            assertThat(repository.getByKey("order-process")).isEmpty();
            assertThat(repository.getById(v1.getId())).isEmpty();
            assertThat(repository.getById(v2.getId())).isEmpty();
        }

        @Test
        @DisplayName("Should not affect other keys when undeploying")
        void shouldNotAffectOtherKeys() {
            // Arrange
            ProcessDefinition orderDef = createDefinition("order-process", 1);
            ProcessDefinition paymentDef = createDefinition("payment-process", 1);
            repository.deploy(orderDef);
            repository.deploy(paymentDef);

            // Act
            repository.undeploy("order-process");

            // Assert
            assertThat(repository.getByKey("payment-process")).isPresent();
        }

        @Test
        @DisplayName("Should handle undeploy of non-existent key gracefully")
        void shouldHandleUndeployOfNonExistentKey() {
            // Act & Assert - should not throw
            repository.undeploy("non-existent");
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("Should return empty when id not found")
        void shouldReturnEmptyWhenNotFound() {
            // Act
            Optional<ProcessDefinition> result = repository.getById(java.util.UUID.randomUUID());

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getByKey()")
    class GetByKeyTests {

        @Test
        @DisplayName("Should return latest version for key")
        void shouldReturnLatestVersion() {
            // Arrange
            ProcessDefinition v1 = createDefinition("order-process", 1);
            ProcessDefinition v2 = createDefinition("order-process", 2);
            ProcessDefinition v3 = createDefinition("order-process", 3);
            repository.deploy(v1);
            repository.deploy(v2);
            repository.deploy(v3);

            // Act
            Optional<ProcessDefinition> latest = repository.getByKey("order-process");

            // Assert
            assertThat(latest).isPresent();
            assertThat(latest.get().getVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should return empty when key not found")
        void shouldReturnEmptyWhenKeyNotFound() {
            // Act
            Optional<ProcessDefinition> result = repository.getByKey("non-existent");

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getVersions()")
    class GetVersionsTests {

        @Test
        @DisplayName("Should return all versions for key")
        void shouldReturnAllVersions() {
            // Arrange
            ProcessDefinition v1 = createDefinition("order-process", 1);
            ProcessDefinition v2 = createDefinition("order-process", 2);
            repository.deploy(v1);
            repository.deploy(v2);

            // Act
            List<ProcessDefinition> versions = repository.getVersions("order-process");

            // Assert
            assertThat(versions).hasSize(2);
        }

        @Test
        @DisplayName("Should return empty list for non-existent key")
        void shouldReturnEmptyListForNonExistentKey() {
            // Act
            List<ProcessDefinition> versions = repository.getVersions("non-existent");

            // Assert
            assertThat(versions).isEmpty();
        }
    }

    @Nested
    @DisplayName("list()")
    class ListTests {

        @Test
        @DisplayName("Should return all deployed definitions")
        void shouldReturnAllDefinitions() {
            // Arrange
            ProcessDefinition def1 = createDefinition("order-process", 1);
            ProcessDefinition def2 = createDefinition("payment-process", 1);
            repository.deploy(def1);
            repository.deploy(def2);

            // Act
            List<ProcessDefinition> all = repository.list();

            // Assert
            assertThat(all).hasSize(2);
        }

        @Test
        @DisplayName("Should return empty list when no definitions deployed")
        void shouldReturnEmptyListWhenEmpty() {
            // Act
            List<ProcessDefinition> all = repository.list();

            // Assert
            assertThat(all).isEmpty();
        }
    }

    @Test
    @DisplayName("Should be thread-safe for concurrent deploy operations")
    void shouldBeThreadSafeForConcurrentDeploy() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        // Act
        for (int i = 0; i < threadCount; i++) {
            int version = i + 1;
            threads[i] = new Thread(() -> {
                ProcessDefinition definition = createDefinition("concurrent-process", version);
                repository.deploy(definition);
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        assertThat(repository.getVersions("concurrent-process")).hasSize(threadCount);
        assertThat(repository.size()).isEqualTo(threadCount);
    }
}

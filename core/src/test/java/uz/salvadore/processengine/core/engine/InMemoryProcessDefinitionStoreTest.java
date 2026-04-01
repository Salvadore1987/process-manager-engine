package uz.salvadore.processengine.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.exception.DuplicateProcessDefinitionException;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.StartEvent;

import uz.salvadore.processengine.core.adapter.inmemory.InMemoryProcessDefinitionStore;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryProcessDefinitionStoreTest {

    private InMemoryProcessDefinitionStore definitionStore;

    @BeforeEach
    void setUp() {
        definitionStore = new InMemoryProcessDefinitionStore();
    }

    private ProcessDefinition createDefinition(String key, String bpmnXml) {
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow1"), List.of(), null);
        SequenceFlow flow = new SequenceFlow("flow1", "start1", "end1", null);
        return ProcessDefinition.create(key, 1, "Test Process", bpmnXml,
                List.of(startEvent, endEvent), List.of(flow));
    }

    @Nested
    @DisplayName("deploy()")
    class DeployTests {

        @Test
        @DisplayName("Should store deployed definition and retrieve by id")
        void shouldStoreAndRetrieveById() {
            // Arrange
            ProcessDefinition definition = createDefinition("order-process", "<xml/>");

            // Act
            ProcessDefinition deployed = definitionStore.deploy(definition);

            // Assert
            Optional<ProcessDefinition> found = definitionStore.getById(deployed.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getKey()).isEqualTo("order-process");
            assertThat(found.get().getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should support deploying multiple versions of same key")
        void shouldSupportMultipleVersions() {
            // Arrange
            ProcessDefinition v1 = createDefinition("order-process", "<xml v1/>");
            ProcessDefinition v2 = createDefinition("order-process", "<xml v2/>");

            // Act
            definitionStore.deploy(v1);
            definitionStore.deploy(v2);

            // Assert
            assertThat(definitionStore.getVersions("order-process")).hasSize(2);
        }

        @Test
        @DisplayName("Should assign version 1 for first deployment of key")
        void shouldAssignVersion1ForFirstDeployment() {
            // Arrange
            ProcessDefinition definition = createDefinition("order-process", "<xml/>");

            // Act
            ProcessDefinition deployed = definitionStore.deploy(definition);

            // Assert
            assertThat(deployed.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should auto-increment version for existing key")
        void shouldAutoIncrementVersionForExistingKey() {
            // Arrange
            ProcessDefinition v1 = createDefinition("order-process", "<xml v1/>");
            ProcessDefinition v2 = createDefinition("order-process", "<xml v2/>");
            ProcessDefinition v3 = createDefinition("order-process", "<xml v3/>");

            // Act
            ProcessDefinition deployed1 = definitionStore.deploy(v1);
            ProcessDefinition deployed2 = definitionStore.deploy(v2);
            ProcessDefinition deployed3 = definitionStore.deploy(v3);

            // Assert
            assertThat(deployed1.getVersion()).isEqualTo(1);
            assertThat(deployed2.getVersion()).isEqualTo(2);
            assertThat(deployed3.getVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should throw DuplicateProcessDefinitionException when BPMN XML is identical")
        void shouldThrowDuplicateExceptionWhenBpmnXmlIsIdentical() {
            // Arrange
            ProcessDefinition first = createDefinition("order-process", "<xml/>");
            ProcessDefinition duplicate = createDefinition("order-process", "<xml/>");
            definitionStore.deploy(first);

            // Act & Assert
            assertThatThrownBy(() -> definitionStore.deploy(duplicate))
                    .isInstanceOf(DuplicateProcessDefinitionException.class)
                    .hasMessageContaining("order-process");
        }

        @Test
        @DisplayName("Should allow redeploy after BPMN XML changes")
        void shouldAllowRedeployAfterBpmnXmlChanges() {
            // Arrange
            ProcessDefinition v1 = createDefinition("order-process", "<xml v1/>");
            ProcessDefinition duplicate = createDefinition("order-process", "<xml v1/>");
            ProcessDefinition v2 = createDefinition("order-process", "<xml v2/>");
            definitionStore.deploy(v1);

            // Act & Assert
            assertThatThrownBy(() -> definitionStore.deploy(duplicate))
                    .isInstanceOf(DuplicateProcessDefinitionException.class);

            ProcessDefinition deployed = definitionStore.deploy(v2);
            assertThat(deployed.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should return versioned definition from deploy")
        void shouldReturnVersionedDefinitionFromDeploy() {
            // Arrange
            ProcessDefinition definition = createDefinition("order-process", "<xml/>");

            // Act
            ProcessDefinition deployed = definitionStore.deploy(definition);

            // Assert
            assertThat(deployed.getVersion()).isEqualTo(1);
            assertThat(deployed.getKey()).isEqualTo("order-process");
            assertThat(deployed.getBpmnXml()).isEqualTo("<xml/>");
        }
    }

    @Nested
    @DisplayName("undeploy()")
    class UndeployTests {

        @Test
        @DisplayName("Should remove all versions by key")
        void shouldRemoveAllVersionsByKey() {
            // Arrange
            ProcessDefinition v1 = createDefinition("order-process", "<xml v1/>");
            ProcessDefinition v2 = createDefinition("order-process", "<xml v2/>");
            ProcessDefinition deployed1 = definitionStore.deploy(v1);
            ProcessDefinition deployed2 = definitionStore.deploy(v2);

            // Act
            definitionStore.undeploy("order-process");

            // Assert
            assertThat(definitionStore.getByKey("order-process")).isEmpty();
            assertThat(definitionStore.getById(deployed1.getId())).isEmpty();
            assertThat(definitionStore.getById(deployed2.getId())).isEmpty();
        }

        @Test
        @DisplayName("Should not affect other keys when undeploying")
        void shouldNotAffectOtherKeys() {
            // Arrange
            ProcessDefinition orderDef = createDefinition("order-process", "<xml order/>");
            ProcessDefinition paymentDef = createDefinition("payment-process", "<xml payment/>");
            definitionStore.deploy(orderDef);
            definitionStore.deploy(paymentDef);

            // Act
            definitionStore.undeploy("order-process");

            // Assert
            assertThat(definitionStore.getByKey("payment-process")).isPresent();
        }

        @Test
        @DisplayName("Should handle undeploy of non-existent key gracefully")
        void shouldHandleUndeployOfNonExistentKey() {
            // Act & Assert - should not throw
            definitionStore.undeploy("non-existent");
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("Should return empty when id not found")
        void shouldReturnEmptyWhenNotFound() {
            // Act
            Optional<ProcessDefinition> result = definitionStore.getById(java.util.UUID.randomUUID());

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
            definitionStore.deploy(createDefinition("order-process", "<xml v1/>"));
            definitionStore.deploy(createDefinition("order-process", "<xml v2/>"));
            definitionStore.deploy(createDefinition("order-process", "<xml v3/>"));

            // Act
            Optional<ProcessDefinition> latest = definitionStore.getByKey("order-process");

            // Assert
            assertThat(latest).isPresent();
            assertThat(latest.get().getVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should return empty when key not found")
        void shouldReturnEmptyWhenKeyNotFound() {
            // Act
            Optional<ProcessDefinition> result = definitionStore.getByKey("non-existent");

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
            definitionStore.deploy(createDefinition("order-process", "<xml v1/>"));
            definitionStore.deploy(createDefinition("order-process", "<xml v2/>"));

            // Act
            List<ProcessDefinition> versions = definitionStore.getVersions("order-process");

            // Assert
            assertThat(versions).hasSize(2);
        }

        @Test
        @DisplayName("Should return empty list for non-existent key")
        void shouldReturnEmptyListForNonExistentKey() {
            // Act
            List<ProcessDefinition> versions = definitionStore.getVersions("non-existent");

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
            definitionStore.deploy(createDefinition("order-process", "<xml order/>"));
            definitionStore.deploy(createDefinition("payment-process", "<xml payment/>"));

            // Act
            List<ProcessDefinition> all = definitionStore.list();

            // Assert
            assertThat(all).hasSize(2);
        }

        @Test
        @DisplayName("Should return empty list when no definitions deployed")
        void shouldReturnEmptyListWhenEmpty() {
            // Act
            List<ProcessDefinition> all = definitionStore.list();

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
            int index = i + 1;
            threads[i] = new Thread(() -> {
                ProcessDefinition definition = createDefinition("concurrent-process", "<xml v" + index + "/>");
                definitionStore.deploy(definition);
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        assertThat(definitionStore.getVersions("concurrent-process")).hasSize(threadCount);
        assertThat(definitionStore.size()).isEqualTo(threadCount);
    }
}

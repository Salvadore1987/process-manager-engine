package uz.salvadore.processengine.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.exception.DuplicateProcessDefinitionException;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.StartEvent;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisProcessDefinitionStoreTest extends AbstractRedisTest {

    private RedisProcessDefinitionStore store;

    @BeforeEach
    void setUp() {
        store = new RedisProcessDefinitionStore(redisTemplate, objectMapper);
    }

    private ProcessDefinition createDefinition(String key, String name, String bpmnXml) {
        List<FlowNode> flowNodes = List.of(
                new StartEvent("start", "Start", List.of(), List.of("flow1")),
                new EndEvent("end", "End", List.of("flow1"), List.of(), null)
        );
        List<SequenceFlow> sequenceFlows = List.of(
                new SequenceFlow("flow1", "start", "end", null)
        );
        return ProcessDefinition.create(key, 0, name, bpmnXml, flowNodes, sequenceFlows);
    }

    @Nested
    @DisplayName("deploy()")
    class Deploy {

        @Test
        @DisplayName("should deploy and return versioned definition with version 1")
        void shouldDeployWithVersionOne() {
            // Arrange
            ProcessDefinition definition = createDefinition("my-process", "My Process", "<bpmn>v1</bpmn>");

            // Act
            ProcessDefinition deployed = store.deploy(definition);

            // Assert
            assertThat(deployed.getVersion()).isEqualTo(1);
            assertThat(deployed.getKey()).isEqualTo("my-process");
            assertThat(deployed.getName()).isEqualTo("My Process");
        }

        @Test
        @DisplayName("should increment version to 2 on second deploy with different BPMN XML")
        void shouldIncrementVersionOnSecondDeploy() {
            // Arrange
            ProcessDefinition first = createDefinition("my-process", "My Process", "<bpmn>v1</bpmn>");
            ProcessDefinition second = createDefinition("my-process", "My Process", "<bpmn>v2</bpmn>");
            store.deploy(first);

            // Act
            ProcessDefinition deployed = store.deploy(second);

            // Assert
            assertThat(deployed.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("should throw DuplicateProcessDefinitionException for duplicate BPMN XML")
        void shouldThrowOnDuplicateBpmnXml() {
            // Arrange
            String sameBpmn = "<bpmn>same-content</bpmn>";
            ProcessDefinition first = createDefinition("my-process", "My Process", sameBpmn);
            ProcessDefinition duplicate = createDefinition("my-process", "My Process", sameBpmn);
            store.deploy(first);

            // Act & Assert
            assertThatThrownBy(() -> store.deploy(duplicate))
                    .isInstanceOf(DuplicateProcessDefinitionException.class);
        }
    }

    @Nested
    @DisplayName("undeploy()")
    class Undeploy {

        @Test
        @DisplayName("should remove all versions by key")
        void shouldRemoveAllVersionsByKey() {
            // Arrange
            store.deploy(createDefinition("my-process", "My Process", "<bpmn>v1</bpmn>"));
            store.deploy(createDefinition("my-process", "My Process", "<bpmn>v2</bpmn>"));

            // Act
            store.undeploy("my-process");

            // Assert
            assertThat(store.getByKey("my-process")).isEmpty();
            assertThat(store.getVersions("my-process")).isEmpty();
            assertThat(store.size()).isZero();
        }
    }

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("should return deployed definition by its ID")
        void shouldReturnDeployedDefinitionById() {
            // Arrange
            ProcessDefinition deployed = store.deploy(
                    createDefinition("my-process", "My Process", "<bpmn>v1</bpmn>")
            );

            // Act
            Optional<ProcessDefinition> found = store.getById(deployed.getId());

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(deployed.getId());
            assertThat(found.get().getVersion()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getByKey()")
    class GetByKey {

        @Test
        @DisplayName("should return latest version by key")
        void shouldReturnLatestVersionByKey() {
            // Arrange
            store.deploy(createDefinition("my-process", "My Process", "<bpmn>v1</bpmn>"));
            store.deploy(createDefinition("my-process", "My Process", "<bpmn>v2</bpmn>"));

            // Act
            Optional<ProcessDefinition> latest = store.getByKey("my-process");

            // Assert
            assertThat(latest).isPresent();
            assertThat(latest.get().getVersion()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getVersions()")
    class GetVersions {

        @Test
        @DisplayName("should return all versions ordered")
        void shouldReturnAllVersionsOrdered() {
            // Arrange
            store.deploy(createDefinition("my-process", "My Process", "<bpmn>v1</bpmn>"));
            store.deploy(createDefinition("my-process", "My Process", "<bpmn>v2</bpmn>"));
            store.deploy(createDefinition("my-process", "My Process", "<bpmn>v3</bpmn>"));

            // Act
            List<ProcessDefinition> versions = store.getVersions("my-process");

            // Assert
            assertThat(versions).hasSize(3);
            assertThat(versions).extracting(ProcessDefinition::getVersion)
                    .containsExactly(1, 2, 3);
        }
    }

    @Nested
    @DisplayName("list()")
    class ListAll {

        @Test
        @DisplayName("should return all deployed definitions")
        void shouldReturnAllDefinitions() {
            // Arrange
            ProcessDefinition first = store.deploy(
                    createDefinition("process-a", "Process A", "<bpmn>a</bpmn>")
            );
            ProcessDefinition second = store.deploy(
                    createDefinition("process-b", "Process B", "<bpmn>b</bpmn>")
            );

            // Act
            List<ProcessDefinition> all = store.list();

            // Assert
            assertThat(all).hasSize(2);
            assertThat(all).extracting(ProcessDefinition::getId)
                    .containsExactlyInAnyOrder(first.getId(), second.getId());
        }
    }

    @Nested
    @DisplayName("size()")
    class Size {

        @Test
        @DisplayName("should return correct count of deployed definitions")
        void shouldReturnCorrectCount() {
            // Arrange
            store.deploy(createDefinition("process-a", "Process A", "<bpmn>a</bpmn>"));
            store.deploy(createDefinition("process-b", "Process B", "<bpmn>b</bpmn>"));

            // Act
            int count = store.size();

            // Assert
            assertThat(count).isEqualTo(2);
        }
    }
}

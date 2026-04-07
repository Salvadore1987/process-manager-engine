package uz.salvadore.processengine.core.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentBundleTest {

    @Nested
    @DisplayName("containsFile()")
    class ContainsFileTests {

        @Test
        @DisplayName("Should return true for an existing file")
        void shouldReturnTrueForExistingFile() {
            // Arrange
            Map<String, String> files = new LinkedHashMap<>();
            files.put("main.bpmn", "<xml/>");
            DeploymentBundle bundle = new DeploymentBundle(files);

            // Act
            boolean result = bundle.containsFile("main.bpmn");

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false for a missing file")
        void shouldReturnFalseForMissingFile() {
            // Arrange
            Map<String, String> files = new LinkedHashMap<>();
            files.put("main.bpmn", "<xml/>");
            DeploymentBundle bundle = new DeploymentBundle(files);

            // Act
            boolean result = bundle.containsFile("other.bpmn");

            // Assert
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getMainProcess()")
    class GetMainProcessTests {

        @Test
        @DisplayName("Should return the first file name as the main process")
        void shouldReturnFirstFileNameAsMainProcess() {
            // Arrange
            Map<String, String> files = new LinkedHashMap<>();
            files.put("order-process.bpmn", "<xml-main/>");
            files.put("sub-process.bpmn", "<xml-sub/>");
            DeploymentBundle bundle = new DeploymentBundle(files);

            // Act
            String mainProcess = bundle.getMainProcess();

            // Assert
            assertThat(mainProcess).isEqualTo("order-process.bpmn");
        }
    }

    @Nested
    @DisplayName("getSubprocesses()")
    class GetSubprocessesTests {

        @Test
        @DisplayName("Should return all files except the main process")
        void shouldReturnAllExceptMainProcess() {
            // Arrange
            Map<String, String> files = new LinkedHashMap<>();
            files.put("main.bpmn", "<xml-main/>");
            files.put("sub1.bpmn", "<xml-sub1/>");
            files.put("sub2.bpmn", "<xml-sub2/>");
            DeploymentBundle bundle = new DeploymentBundle(files);

            // Act
            Map<String, String> subprocesses = bundle.getSubprocesses();

            // Assert
            assertThat(subprocesses).hasSize(2);
            assertThat(subprocesses).containsKeys("sub1.bpmn", "sub2.bpmn");
            assertThat(subprocesses).doesNotContainKey("main.bpmn");
        }

        @Test
        @DisplayName("Should return empty map when bundle has only the main process")
        void shouldReturnEmptyMapWhenOnlyMainProcess() {
            // Arrange
            Map<String, String> files = new LinkedHashMap<>();
            files.put("main.bpmn", "<xml/>");
            DeploymentBundle bundle = new DeploymentBundle(files);

            // Act
            Map<String, String> subprocesses = bundle.getSubprocesses();

            // Assert
            assertThat(subprocesses).isEmpty();
        }
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException when map is null")
        void shouldThrowWhenMapIsNull() {
            // Act & Assert
            assertThatThrownBy(() -> new DeploymentBundle(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one BPMN file");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when map is empty")
        void shouldThrowWhenMapIsEmpty() {
            // Act & Assert
            assertThatThrownBy(() -> new DeploymentBundle(Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one BPMN file");
        }
    }
}

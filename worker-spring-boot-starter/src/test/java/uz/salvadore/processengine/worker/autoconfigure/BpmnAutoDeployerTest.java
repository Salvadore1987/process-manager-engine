package uz.salvadore.processengine.worker.autoconfigure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import uz.salvadore.processengine.core.domain.model.DeploymentBundle;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.engine.ProcessEngine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BpmnAutoDeployer}.
 *
 * <p>Strategy: BpmnAutoDeployer creates a real BpmnParser internally, so we use real BPMN XML
 * content from test resources. ProcessEngine and ResourcePatternResolver are mocked.
 */
@ExtendWith(MockitoExtension.class)
class BpmnAutoDeployerTest {

    @Mock
    private ProcessEngine processEngine;

    @Mock
    private ResourcePatternResolver resourcePatternResolver;

    @Captor
    private ArgumentCaptor<ProcessDefinition> definitionCaptor;

    @Captor
    private ArgumentCaptor<DeploymentBundle> bundleCaptor;

    private WorkerProperties properties;
    private BpmnAutoDeployer deployer;

    @BeforeEach
    void setUp() {
        properties = new WorkerProperties();
        deployer = new BpmnAutoDeployer(processEngine, properties, resourcePatternResolver);
    }

    // ---- Helper methods ----

    private Resource resourceFromClasspath(String classpathLocation, String filename) {
        Resource resource = mock(Resource.class);
        try {
            ClassPathResource classPathResource = new ClassPathResource(classpathLocation);
            InputStream inputStream = classPathResource.getInputStream();
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            inputStream.close();

            when(resource.getFilename()).thenReturn(filename);
            when(resource.getInputStream()).thenReturn(
                    new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test resource: " + classpathLocation, e);
        }
        return resource;
    }

    private void configureResources(Resource... resources) throws IOException {
        when(resourcePatternResolver.getResources(anyString())).thenReturn(resources);
    }

    // ---- Test groups ----

    @Nested
    @DisplayName("Lifecycle and phase")
    class LifecycleTests {

        @Test
        @DisplayName("getPhase returns Integer.MIN_VALUE + 300")
        void getPhase_returnsExpectedValue() {
            // Arrange — nothing extra needed

            // Act
            int phase = deployer.getPhase();

            // Assert
            assertThat(phase).isEqualTo(Integer.MIN_VALUE + 300);
        }

        @Test
        @DisplayName("isRunning is false before start, true after start, false after stop")
        void isRunning_reflectsLifecycleState() throws IOException {
            // Arrange
            configureResources(); // no files

            // Assert — before start
            assertThat(deployer.isRunning()).isFalse();

            // Act — start
            deployer.start();
            assertThat(deployer.isRunning()).isTrue();

            // Act — stop
            deployer.stop();
            assertThat(deployer.isRunning()).isFalse();
        }
    }

    @Nested
    @DisplayName("When auto-deploy is disabled")
    class DisabledTests {

        @Test
        @DisplayName("start does nothing when enabled=false")
        void start_doesNothing_whenDisabled() {
            // Arrange
            properties.getAutoDeploy().setEnabled(false);

            // Act
            deployer.start();

            // Assert
            verify(processEngine, never()).deploy(any());
            verify(processEngine, never()).deployBundle(any());
            assertThat(deployer.isRunning()).isFalse();
        }
    }

    @Nested
    @DisplayName("When no BPMN files found")
    class NoBpmnFilesTests {

        @Test
        @DisplayName("start logs info and does not deploy anything")
        void start_noDeploys_whenNoBpmnFilesFound() throws IOException {
            // Arrange
            configureResources(); // empty array

            // Act
            deployer.start();

            // Assert
            verify(processEngine, never()).deploy(any());
            verify(processEngine, never()).deployBundle(any());
            assertThat(deployer.isRunning()).isTrue();
        }
    }

    @Nested
    @DisplayName("Standalone BPMN deployment (no CallActivity)")
    class StandaloneDeploymentTests {

        @Test
        @DisplayName("deploys a single standalone process via processEngine.deploy()")
        void start_deploysStandaloneProcess() throws IOException {
            // Arrange
            Resource simpleResource = resourceFromClasspath("bpmn/simple-process.bpmn", "simple-process.bpmn");
            configureResources(simpleResource);

            // Act
            deployer.start();

            // Assert
            verify(processEngine).deploy(definitionCaptor.capture());
            verify(processEngine, never()).deployBundle(any());

            ProcessDefinition deployed = definitionCaptor.getValue();
            assertThat(deployed.getKey()).isEqualTo("simple-process");
        }

        @Test
        @DisplayName("deploys multiple standalone processes when none have CallActivity")
        void start_deploysMultipleStandaloneProcesses() throws IOException {
            // Arrange
            Resource simpleResource = resourceFromClasspath("bpmn/simple-process.bpmn", "simple-process.bpmn");
            Resource subResource = resourceFromClasspath("bpmn/sub-process.bpmn", "sub-process.bpmn");
            configureResources(simpleResource, subResource);

            // Act
            deployer.start();

            // Assert — both deployed as standalone since neither references the other
            verify(processEngine, org.mockito.Mockito.times(2)).deploy(any());
            verify(processEngine, never()).deployBundle(any());
        }
    }

    @Nested
    @DisplayName("Bundle deployment (with CallActivity)")
    class BundleDeploymentTests {

        @Test
        @DisplayName("deploys main process with CallActivity as a bundle including subprocess")
        void start_deploysBundleForCallActivity() throws IOException {
            // Arrange
            Resource mainResource = resourceFromClasspath(
                    "bpmn/main-with-call-activity.bpmn", "main-with-call-activity.bpmn");
            Resource subResource = resourceFromClasspath(
                    "bpmn/sub-process.bpmn", "sub-process.bpmn");
            configureResources(mainResource, subResource);

            // Act
            deployer.start();

            // Assert — bundle deployed, no standalone deploy calls
            verify(processEngine).deployBundle(bundleCaptor.capture());
            verify(processEngine, never()).deploy(any());

            DeploymentBundle bundle = bundleCaptor.getValue();
            assertThat(bundle.getMainProcess()).isEqualTo("main-with-call-activity.bpmn");
            assertThat(bundle.getBpmnFiles()).containsKeys("main-with-call-activity.bpmn", "sub-process.bpmn");
        }

        @Test
        @DisplayName("subprocess is NOT deployed standalone — only as part of bundle")
        void start_subprocessNotDeployedStandalone() throws IOException {
            // Arrange
            Resource mainResource = resourceFromClasspath(
                    "bpmn/main-with-call-activity.bpmn", "main-with-call-activity.bpmn");
            Resource subResource = resourceFromClasspath(
                    "bpmn/sub-process.bpmn", "sub-process.bpmn");
            configureResources(mainResource, subResource);

            // Act
            deployer.start();

            // Assert — only one bundle deploy, subprocess file is not deployed on its own
            verify(processEngine).deployBundle(bundleCaptor.capture());
            verify(processEngine, never()).deploy(any());

            DeploymentBundle bundle = bundleCaptor.getValue();
            // main is first entry, sub-process is second
            assertThat(bundle.getBpmnFiles().keySet().stream().toList())
                    .containsExactly("main-with-call-activity.bpmn", "sub-process.bpmn");
        }

        @Test
        @DisplayName("standalone process is deployed separately alongside a bundle")
        void start_deploysBundleAndStandaloneSeparately() throws IOException {
            // Arrange — three files: standalone + main-with-call + subprocess
            Resource simpleResource = resourceFromClasspath(
                    "bpmn/simple-process.bpmn", "simple-process.bpmn");
            Resource mainResource = resourceFromClasspath(
                    "bpmn/main-with-call-activity.bpmn", "main-with-call-activity.bpmn");
            Resource subResource = resourceFromClasspath(
                    "bpmn/sub-process.bpmn", "sub-process.bpmn");
            configureResources(simpleResource, mainResource, subResource);

            // Act
            deployer.start();

            // Assert
            verify(processEngine).deploy(definitionCaptor.capture());
            verify(processEngine).deployBundle(bundleCaptor.capture());

            // standalone is the simple-process
            assertThat(definitionCaptor.getValue().getKey()).isEqualTo("simple-process");
            // bundle contains main + subprocess
            assertThat(bundleCaptor.getValue().getBpmnFiles()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("failOnError=true throws IllegalStateException on deploy failure")
        void start_throwsIllegalStateException_whenFailOnErrorTrue() throws IOException {
            // Arrange
            properties.getAutoDeploy().setFailOnError(true);

            Resource simpleResource = resourceFromClasspath("bpmn/simple-process.bpmn", "simple-process.bpmn");
            configureResources(simpleResource);

            when(processEngine.deploy(any())).thenThrow(new RuntimeException("deploy failed"));

            // Act & Assert
            assertThatThrownBy(() -> deployer.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to deploy process")
                    .hasCauseInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("failOnError=false logs error and continues on deploy failure")
        void start_logsAndContinues_whenFailOnErrorFalse() throws IOException {
            // Arrange
            properties.getAutoDeploy().setFailOnError(false);

            Resource simpleResource = resourceFromClasspath("bpmn/simple-process.bpmn", "simple-process.bpmn");
            configureResources(simpleResource);

            when(processEngine.deploy(any())).thenThrow(new RuntimeException("deploy failed"));

            // Act — should NOT throw
            deployer.start();

            // Assert — deployer still transitions to running
            assertThat(deployer.isRunning()).isTrue();
        }

        @Test
        @DisplayName("failOnError=true throws on bundle deploy failure")
        void start_throwsOnBundleFailure_whenFailOnErrorTrue() throws IOException {
            // Arrange
            properties.getAutoDeploy().setFailOnError(true);

            Resource mainResource = resourceFromClasspath(
                    "bpmn/main-with-call-activity.bpmn", "main-with-call-activity.bpmn");
            Resource subResource = resourceFromClasspath(
                    "bpmn/sub-process.bpmn", "sub-process.bpmn");
            configureResources(mainResource, subResource);

            when(processEngine.deployBundle(any())).thenThrow(new RuntimeException("bundle deploy failed"));

            // Act & Assert
            assertThatThrownBy(() -> deployer.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to deploy bundle");
        }

        @Test
        @DisplayName("failOnError=true throws on IOException during resource scanning")
        void start_throwsOnIOException_whenFailOnErrorTrue() throws IOException {
            // Arrange
            properties.getAutoDeploy().setFailOnError(true);
            when(resourcePatternResolver.getResources(anyString())).thenThrow(new IOException("scan failed"));

            // Act & Assert
            assertThatThrownBy(() -> deployer.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to scan BPMN resources");
        }

        @Test
        @DisplayName("failOnError=false logs and continues on IOException during resource scanning")
        void start_logsAndContinues_whenIOException_andFailOnErrorFalse() throws IOException {
            // Arrange
            properties.getAutoDeploy().setFailOnError(false);
            when(resourcePatternResolver.getResources(anyString())).thenThrow(new IOException("scan failed"));

            // Act — should NOT throw
            deployer.start();

            // Assert
            assertThat(deployer.isRunning()).isTrue();
        }
    }

    @Nested
    @DisplayName("Resource location normalization")
    class ResourceLocationTests {

        @Test
        @DisplayName("appends trailing slash if missing and scans correct pattern")
        void start_normalizesResourceLocationWithoutTrailingSlash() throws IOException {
            // Arrange
            properties.getAutoDeploy().setResourceLocation("classpath:custom");
            configureResources(); // no files

            // Act
            deployer.start();

            // Assert — should query with trailing slash + glob pattern
            verify(resourcePatternResolver).getResources("classpath:custom/**/*.bpmn");
        }

        @Test
        @DisplayName("does not double trailing slash when already present")
        void start_preservesTrailingSlash() throws IOException {
            // Arrange
            properties.getAutoDeploy().setResourceLocation("classpath:bpmn/");
            configureResources(); // no files

            // Act
            deployer.start();

            // Assert
            verify(resourcePatternResolver).getResources("classpath:bpmn/**/*.bpmn");
        }
    }

    @Nested
    @DisplayName("Resources with null filename")
    class NullFilenameTests {

        @Test
        @DisplayName("resources with null filename are skipped")
        void start_skipsResourcesWithNullFilename() throws IOException {
            // Arrange
            Resource nullFilenameResource = mock(Resource.class);
            when(nullFilenameResource.getFilename()).thenReturn(null);

            configureResources(nullFilenameResource);

            // Act
            deployer.start();

            // Assert — no deployments since the only resource was skipped
            verify(processEngine, never()).deploy(any());
            verify(processEngine, never()).deployBundle(any());
        }
    }
}

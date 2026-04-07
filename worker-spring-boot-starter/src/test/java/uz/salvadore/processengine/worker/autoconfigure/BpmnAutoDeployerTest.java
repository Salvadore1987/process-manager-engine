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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BpmnAutoDeployer}.
 *
 * <p>Strategy: BpmnAutoDeployer delegates to ProcessEngineClient (HTTP-based) for deployments.
 * ProcessEngineClient and ResourcePatternResolver are mocked. Real BPMN content from test
 * resources is used to keep the scanning realistic.
 */
@ExtendWith(MockitoExtension.class)
class BpmnAutoDeployerTest {

    @Mock
    private ProcessEngineClient engineClient;

    @Mock
    private ResourcePatternResolver resourcePatternResolver;

    @Captor
    private ArgumentCaptor<String> filenameCaptor;

    @Captor
    private ArgumentCaptor<byte[]> contentCaptor;

    @Captor
    private ArgumentCaptor<Map<String, byte[]>> bundleCaptor;

    private WorkerProperties properties;
    private BpmnAutoDeployer deployer;

    @BeforeEach
    void setUp() {
        properties = new WorkerProperties();
        deployer = new BpmnAutoDeployer(engineClient, properties, resourcePatternResolver);
    }

    // ---- Helper methods ----

    private Resource resourceFromClasspath(String classpathLocation, String filename) {
        Resource resource = mock(Resource.class);
        try {
            ClassPathResource classPathResource = new ClassPathResource(classpathLocation);
            InputStream inputStream = classPathResource.getInputStream();
            byte[] content = inputStream.readAllBytes();
            inputStream.close();

            when(resource.getFilename()).thenReturn(filename);
            when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
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
            configureResources();

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

            // Assert — no interaction with the engine client at all
            verify(engineClient, never()).deployDefinition(anyString(), any(byte[].class));
            verify(engineClient, never()).deployBundle(anyMap());
            assertThat(deployer.isRunning()).isTrue();
        }
    }

    @Nested
    @DisplayName("When no BPMN files found")
    class NoBpmnFilesTests {

        @Test
        @DisplayName("start logs info and does not deploy anything")
        void start_noDeploys_whenNoBpmnFilesFound() throws IOException {
            // Arrange
            configureResources();

            // Act
            deployer.start();

            // Assert
            verify(engineClient, never()).deployDefinition(anyString(), any(byte[].class));
            verify(engineClient, never()).deployBundle(anyMap());
            assertThat(deployer.isRunning()).isTrue();
        }
    }

    @Nested
    @DisplayName("Single file deployment")
    class SingleFileDeploymentTests {

        @Test
        @DisplayName("deploys a single BPMN file via deployDefinition()")
        void start_deploysSingleFile_viaDeployDefinition() throws IOException {
            // Arrange
            Resource simpleResource = resourceFromClasspath("bpmn/simple-process.bpmn", "simple-process.bpmn");
            configureResources(simpleResource);

            // Act
            deployer.start();

            // Assert — single file goes through deployDefinition, not deployBundle
            verify(engineClient).deployDefinition(filenameCaptor.capture(), contentCaptor.capture());
            verify(engineClient, never()).deployBundle(anyMap());

            assertThat(filenameCaptor.getValue()).isEqualTo("simple-process.bpmn");
            assertThat(contentCaptor.getValue()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Bundle deployment (multiple files)")
    class BundleDeploymentTests {

        @Test
        @DisplayName("deploys multiple BPMN files via deployBundle()")
        void start_deploysBundle_whenMultipleFilesFound() throws IOException {
            // Arrange
            Resource mainResource = resourceFromClasspath(
                    "bpmn/main-with-call-activity.bpmn", "main-with-call-activity.bpmn");
            Resource subResource = resourceFromClasspath(
                    "bpmn/sub-process.bpmn", "sub-process.bpmn");
            configureResources(mainResource, subResource);

            // Act
            deployer.start();

            // Assert — multiple files go through deployBundle, not deployDefinition
            verify(engineClient).deployBundle(bundleCaptor.capture());
            verify(engineClient, never()).deployDefinition(anyString(), any(byte[].class));

            Map<String, byte[]> bundle = bundleCaptor.getValue();
            assertThat(bundle).hasSize(2);
            assertThat(bundle).containsKeys("main-with-call-activity.bpmn", "sub-process.bpmn");
        }

        @Test
        @DisplayName("deploys three BPMN files as a single bundle")
        void start_deploysThreeFilesAsBundle() throws IOException {
            // Arrange
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
            verify(engineClient).deployBundle(bundleCaptor.capture());
            verify(engineClient, never()).deployDefinition(anyString(), any(byte[].class));

            Map<String, byte[]> bundle = bundleCaptor.getValue();
            assertThat(bundle).hasSize(3);
            assertThat(bundle).containsKeys(
                    "simple-process.bpmn", "main-with-call-activity.bpmn", "sub-process.bpmn");
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("failOnError=true throws IllegalStateException on single deploy failure")
        void start_throwsIllegalStateException_whenDeployDefinitionFails() throws IOException {
            // Arrange
            properties.getAutoDeploy().setFailOnError(true);

            Resource simpleResource = resourceFromClasspath("bpmn/simple-process.bpmn", "simple-process.bpmn");
            configureResources(simpleResource);

            doThrow(new ProcessEngineClient.DeploymentException("deploy failed"))
                    .when(engineClient).deployDefinition(anyString(), any(byte[].class));

            // Act & Assert
            assertThatThrownBy(() -> deployer.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to deploy BPMN files to engine")
                    .hasCauseInstanceOf(ProcessEngineClient.DeploymentException.class);
        }

        @Test
        @DisplayName("failOnError=false logs error and continues on single deploy failure")
        void start_logsAndContinues_whenDeployDefinitionFails_andFailOnErrorFalse() throws IOException {
            // Arrange
            properties.getAutoDeploy().setFailOnError(false);

            Resource simpleResource = resourceFromClasspath("bpmn/simple-process.bpmn", "simple-process.bpmn");
            configureResources(simpleResource);

            doThrow(new ProcessEngineClient.DeploymentException("deploy failed"))
                    .when(engineClient).deployDefinition(anyString(), any(byte[].class));

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

            doThrow(new ProcessEngineClient.DeploymentException("bundle deploy failed"))
                    .when(engineClient).deployBundle(anyMap());

            // Act & Assert
            assertThatThrownBy(() -> deployer.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to deploy BPMN files to engine");
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
            configureResources();

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
            configureResources();

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
            verify(engineClient, never()).deployDefinition(anyString(), any(byte[].class));
            verify(engineClient, never()).deployBundle(anyMap());
        }
    }
}

package uz.salvadore.processengine.spring.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProcessEngineProperties")
class ProcessEnginePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesTestConfiguration.class);

    @Configuration
    @EnableConfigurationProperties(ProcessEngineProperties.class)
    static class PropertiesTestConfiguration {
    }

    @Nested
    @DisplayName("default values")
    class DefaultValues {

        @Test
        @DisplayName("rabbitmq.host defaults to localhost")
        void rabbitmqHostDefaultsToLocalhost() {
            contextRunner.run(context -> {
                ProcessEngineProperties properties = context.getBean(ProcessEngineProperties.class);
                assertThat(properties.getRabbitmq().getHost()).isEqualTo("localhost");
            });
        }

        @Test
        @DisplayName("rabbitmq.port defaults to 5672")
        void rabbitmqPortDefaultsTo5672() {
            contextRunner.run(context -> {
                ProcessEngineProperties properties = context.getBean(ProcessEngineProperties.class);
                assertThat(properties.getRabbitmq().getPort()).isEqualTo(5672);
            });
        }

        @Test
        @DisplayName("rabbitmq.username defaults to guest")
        void rabbitmqUsernameDefaultsToGuest() {
            contextRunner.run(context -> {
                ProcessEngineProperties properties = context.getBean(ProcessEngineProperties.class);
                assertThat(properties.getRabbitmq().getUsername()).isEqualTo("guest");
            });
        }

        @Test
        @DisplayName("rabbitmq.password defaults to guest")
        void rabbitmqPasswordDefaultsToGuest() {
            contextRunner.run(context -> {
                ProcessEngineProperties properties = context.getBean(ProcessEngineProperties.class);
                assertThat(properties.getRabbitmq().getPassword()).isEqualTo("guest");
            });
        }

        @Test
        @DisplayName("rabbitmq.virtualHost defaults to /")
        void rabbitmqVirtualHostDefaultsToSlash() {
            contextRunner.run(context -> {
                ProcessEngineProperties properties = context.getBean(ProcessEngineProperties.class);
                assertThat(properties.getRabbitmq().getVirtualHost()).isEqualTo("/");
            });
        }

        @Test
        @DisplayName("rabbitmq exchanges have correct default values")
        void rabbitmqExchangesHaveCorrectDefaults() {
            contextRunner.run(context -> {
                ProcessEngineProperties.RabbitMq.Exchanges exchanges =
                        context.getBean(ProcessEngineProperties.class).getRabbitmq().getExchanges();

                assertThat(exchanges.getTasks()).isEqualTo("process-engine.tasks");
                assertThat(exchanges.getRetry()).isEqualTo("process-engine.retry");
                assertThat(exchanges.getDlq()).isEqualTo("process-engine.dlq");
                assertThat(exchanges.getTimers()).isEqualTo("process-engine.timers");
            });
        }

        @Test
        @DisplayName("retry.maxAttempts defaults to 3")
        void retryMaxAttemptsDefaultsTo3() {
            contextRunner.run(context -> {
                ProcessEngineProperties properties = context.getBean(ProcessEngineProperties.class);
                assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
            });
        }

        @Test
        @DisplayName("retry.baseInterval defaults to 5 seconds")
        void retryBaseIntervalDefaultsTo5Seconds() {
            contextRunner.run(context -> {
                ProcessEngineProperties properties = context.getBean(ProcessEngineProperties.class);
                assertThat(properties.getRetry().getBaseInterval()).isEqualTo(Duration.ofSeconds(5));
            });
        }

        @Test
        @DisplayName("retry.maxInterval defaults to 5 minutes")
        void retryMaxIntervalDefaultsTo5Minutes() {
            contextRunner.run(context -> {
                ProcessEngineProperties properties = context.getBean(ProcessEngineProperties.class);
                assertThat(properties.getRetry().getMaxInterval()).isEqualTo(Duration.ofMinutes(5));
            });
        }
    }

    @Nested
    @DisplayName("custom property binding")
    class CustomPropertyBinding {

        @Test
        @DisplayName("binds rabbitmq connection properties")
        void bindsRabbitmqConnectionProperties() {
            // Arrange
            ApplicationContextRunner runner = contextRunner
                    .withPropertyValues(
                            "process-engine.rabbitmq.host=custom-rabbit",
                            "process-engine.rabbitmq.port=5673",
                            "process-engine.rabbitmq.username=admin",
                            "process-engine.rabbitmq.password=secret",
                            "process-engine.rabbitmq.virtual-host=/test"
                    );

            // Act & Assert
            runner.run(context -> {
                ProcessEngineProperties.RabbitMq rabbitmq =
                        context.getBean(ProcessEngineProperties.class).getRabbitmq();

                assertThat(rabbitmq.getHost()).isEqualTo("custom-rabbit");
                assertThat(rabbitmq.getPort()).isEqualTo(5673);
                assertThat(rabbitmq.getUsername()).isEqualTo("admin");
                assertThat(rabbitmq.getPassword()).isEqualTo("secret");
                assertThat(rabbitmq.getVirtualHost()).isEqualTo("/test");
            });
        }

        @Test
        @DisplayName("binds rabbitmq exchange properties")
        void bindsRabbitmqExchangeProperties() {
            // Arrange
            ApplicationContextRunner runner = contextRunner
                    .withPropertyValues(
                            "process-engine.rabbitmq.exchanges.tasks=custom.tasks",
                            "process-engine.rabbitmq.exchanges.retry=custom.retry",
                            "process-engine.rabbitmq.exchanges.dlq=custom.dlq",
                            "process-engine.rabbitmq.exchanges.timers=custom.timers"
                    );

            // Act & Assert
            runner.run(context -> {
                ProcessEngineProperties.RabbitMq.Exchanges exchanges =
                        context.getBean(ProcessEngineProperties.class).getRabbitmq().getExchanges();

                assertThat(exchanges.getTasks()).isEqualTo("custom.tasks");
                assertThat(exchanges.getRetry()).isEqualTo("custom.retry");
                assertThat(exchanges.getDlq()).isEqualTo("custom.dlq");
                assertThat(exchanges.getTimers()).isEqualTo("custom.timers");
            });
        }

        @Test
        @DisplayName("binds retry properties")
        void bindsRetryProperties() {
            // Arrange
            ApplicationContextRunner runner = contextRunner
                    .withPropertyValues(
                            "process-engine.retry.max-attempts=5",
                            "process-engine.retry.base-interval=10s",
                            "process-engine.retry.max-interval=10m"
                    );

            // Act & Assert
            runner.run(context -> {
                ProcessEngineProperties.Retry retry =
                        context.getBean(ProcessEngineProperties.class).getRetry();

                assertThat(retry.getMaxAttempts()).isEqualTo(5);
                assertThat(retry.getBaseInterval()).isEqualTo(Duration.ofSeconds(10));
                assertThat(retry.getMaxInterval()).isEqualTo(Duration.ofMinutes(10));
            });
        }

        @Test
        @DisplayName("binds all properties from application-test.yml")
        void bindsAllPropertiesFromYml() {
            // Arrange
            ApplicationContextRunner runner = contextRunner
                    .withPropertyValues(
                            "process-engine.rabbitmq.host=custom-rabbit",
                            "process-engine.rabbitmq.port=5673",
                            "process-engine.rabbitmq.username=admin",
                            "process-engine.rabbitmq.password=secret",
                            "process-engine.rabbitmq.virtual-host=/test",
                            "process-engine.rabbitmq.exchanges.tasks=custom.tasks",
                            "process-engine.rabbitmq.exchanges.retry=custom.retry",
                            "process-engine.rabbitmq.exchanges.dlq=custom.dlq",
                            "process-engine.rabbitmq.exchanges.timers=custom.timers",
                            "process-engine.retry.max-attempts=5",
                            "process-engine.retry.base-interval=10s",
                            "process-engine.retry.max-interval=10m"
                    );

            // Act & Assert
            runner.run(context -> {
                ProcessEngineProperties properties = context.getBean(ProcessEngineProperties.class);

                assertThat(properties.getRabbitmq().getHost()).isEqualTo("custom-rabbit");
                assertThat(properties.getRabbitmq().getPort()).isEqualTo(5673);
                assertThat(properties.getRabbitmq().getUsername()).isEqualTo("admin");
                assertThat(properties.getRabbitmq().getPassword()).isEqualTo("secret");
                assertThat(properties.getRabbitmq().getVirtualHost()).isEqualTo("/test");
                assertThat(properties.getRabbitmq().getExchanges().getTasks()).isEqualTo("custom.tasks");
                assertThat(properties.getRabbitmq().getExchanges().getRetry()).isEqualTo("custom.retry");
                assertThat(properties.getRabbitmq().getExchanges().getDlq()).isEqualTo("custom.dlq");
                assertThat(properties.getRabbitmq().getExchanges().getTimers()).isEqualTo("custom.timers");

                assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(5);
                assertThat(properties.getRetry().getBaseInterval()).isEqualTo(Duration.ofSeconds(10));
                assertThat(properties.getRetry().getMaxInterval()).isEqualTo(Duration.ofMinutes(10));
            });
        }
    }
}

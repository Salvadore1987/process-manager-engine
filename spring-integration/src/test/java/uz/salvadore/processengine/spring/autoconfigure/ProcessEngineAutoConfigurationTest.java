package uz.salvadore.processengine.spring.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.engine.TokenExecutor;
import uz.salvadore.processengine.core.engine.condition.ConditionEvaluator;
import uz.salvadore.processengine.core.engine.condition.SimpleConditionEvaluator;
import uz.salvadore.processengine.core.port.outgoing.InstanceDefinitionMapping;
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;
import uz.salvadore.processengine.core.port.outgoing.ProcessDefinitionStore;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;
import uz.salvadore.processengine.core.port.outgoing.TimerService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ProcessEngineAutoConfiguration")
class ProcessEngineAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    EventStoreAutoConfiguration.class,
                    ProcessEngineAutoConfiguration.class
            ))
            .withUserConfiguration(MockTransportConfiguration.class);

    @Configuration
    static class MockTransportConfiguration {

        @Bean
        MessageTransport messageTransport() {
            return mock(MessageTransport.class);
        }

        @Bean
        TimerService timerService() {
            return mock(TimerService.class);
        }
    }

    @Nested
    @DisplayName("bean creation")
    class BeanCreation {

        @Test
        @DisplayName("creates ProcessEngine bean")
        void createsProcessEngineBean() {
            // Arrange & Act & Assert
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(ProcessEngine.class);
            });
        }

        @Test
        @DisplayName("creates ProcessDefinitionStore bean")
        void createsProcessDefinitionStoreBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(ProcessDefinitionStore.class);
            });
        }

        @Test
        @DisplayName("creates TokenExecutor bean")
        void createsTokenExecutorBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(TokenExecutor.class);
            });
        }

        @Test
        @DisplayName("creates SequenceGenerator bean")
        void createsSequenceGeneratorBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(SequenceGenerator.class);
            });
        }

        @Test
        @DisplayName("creates InstanceDefinitionMapping bean")
        void createsInstanceDefinitionMappingBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(InstanceDefinitionMapping.class);
            });
        }

        @Test
        @DisplayName("creates ConditionEvaluator bean as SimpleConditionEvaluator")
        void createsConditionEvaluatorBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(ConditionEvaluator.class);
                assertThat(context.getBean(ConditionEvaluator.class))
                        .isInstanceOf(SimpleConditionEvaluator.class);
            });
        }
    }

    @Nested
    @DisplayName("ConditionalOnMissingBean behavior")
    class ConditionalOnMissingBeanBehavior {

        @Test
        @DisplayName("uses custom SequenceGenerator when provided")
        void usesCustomSequenceGenerator() {
            // Arrange
            SequenceGenerator customGenerator = mock(SequenceGenerator.class);

            // Act & Assert
            contextRunner
                    .withBean(SequenceGenerator.class, () -> customGenerator)
                    .run(context -> {
                        assertThat(context).hasSingleBean(SequenceGenerator.class);
                        assertThat(context.getBean(SequenceGenerator.class)).isSameAs(customGenerator);
                    });
        }

        @Test
        @DisplayName("uses custom ConditionEvaluator when provided")
        void usesCustomConditionEvaluator() {
            // Arrange
            ConditionEvaluator customEvaluator = mock(ConditionEvaluator.class);

            // Act & Assert
            contextRunner
                    .withBean(ConditionEvaluator.class, () -> customEvaluator)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ConditionEvaluator.class);
                        assertThat(context.getBean(ConditionEvaluator.class)).isSameAs(customEvaluator);
                    });
        }

        @Test
        @DisplayName("uses custom ProcessDefinitionStore when provided")
        void usesCustomProcessDefinitionStore() {
            // Arrange
            ProcessDefinitionStore customStore = mock(ProcessDefinitionStore.class);

            // Act & Assert
            contextRunner
                    .withBean(ProcessDefinitionStore.class, () -> customStore)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ProcessDefinitionStore.class);
                        assertThat(context.getBean(ProcessDefinitionStore.class)).isSameAs(customStore);
                    });
        }

        @Test
        @DisplayName("uses custom TokenExecutor when provided")
        void usesCustomTokenExecutor() {
            // Arrange
            TokenExecutor customExecutor = mock(TokenExecutor.class);

            // Act & Assert
            contextRunner
                    .withBean(TokenExecutor.class, () -> customExecutor)
                    .run(context -> {
                        assertThat(context).hasSingleBean(TokenExecutor.class);
                        assertThat(context.getBean(TokenExecutor.class)).isSameAs(customExecutor);
                    });
        }

        @Test
        @DisplayName("uses custom ProcessEngine when provided")
        void usesCustomProcessEngine() {
            // Arrange
            ProcessEngine customEngine = mock(ProcessEngine.class);

            // Act & Assert
            contextRunner
                    .withBean(ProcessEngine.class, () -> customEngine)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ProcessEngine.class);
                        assertThat(context.getBean(ProcessEngine.class)).isSameAs(customEngine);
                    });
        }
    }

    @Nested
    @DisplayName("failure scenarios")
    class FailureScenarios {

        @Test
        @DisplayName("fails when MessageTransport bean is missing")
        void failsWhenMessageTransportMissing() {
            // Arrange
            ApplicationContextRunner runnerWithoutTransport = new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            EventStoreAutoConfiguration.class,
                            ProcessEngineAutoConfiguration.class
                    ))
                    .withBean(TimerService.class, () -> mock(TimerService.class));

            // Act & Assert
            runnerWithoutTransport.run(context -> {
                assertThat(context).hasFailed();
            });
        }

        @Test
        @DisplayName("fails when TimerService bean is missing")
        void failsWhenTimerServiceMissing() {
            // Arrange
            ApplicationContextRunner runnerWithoutTimer = new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            EventStoreAutoConfiguration.class,
                            ProcessEngineAutoConfiguration.class
                    ))
                    .withBean(MessageTransport.class, () -> mock(MessageTransport.class));

            // Act & Assert
            runnerWithoutTimer.run(context -> {
                assertThat(context).hasFailed();
            });
        }
    }
}

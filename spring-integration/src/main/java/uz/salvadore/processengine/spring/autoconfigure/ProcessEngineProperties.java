package uz.salvadore.processengine.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "process-engine")
public class ProcessEngineProperties {

    private final Persistence persistence = new Persistence();
    private final RabbitMq rabbitmq = new RabbitMq();
    private final Retry retry = new Retry();

    public Persistence getPersistence() { return persistence; }
    public RabbitMq getRabbitmq() { return rabbitmq; }
    public Retry getRetry() { return retry; }

    public static class Persistence {
        private boolean enabled = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class RabbitMq {
        private String host = "localhost";
        private int port = 5672;
        private String username = "guest";
        private String password = "guest";
        private String virtualHost = "/";
        private final Exchanges exchanges = new Exchanges();

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getVirtualHost() { return virtualHost; }
        public void setVirtualHost(String virtualHost) { this.virtualHost = virtualHost; }
        public Exchanges getExchanges() { return exchanges; }

        public static class Exchanges {
            private String tasks = "process-engine.tasks";
            private String retry = "process-engine.retry";
            private String dlq = "process-engine.dlq";
            private String timers = "process-engine.timers";

            public String getTasks() { return tasks; }
            public void setTasks(String tasks) { this.tasks = tasks; }
            public String getRetry() { return retry; }
            public void setRetry(String retry) { this.retry = retry; }
            public String getDlq() { return dlq; }
            public void setDlq(String dlq) { this.dlq = dlq; }
            public String getTimers() { return timers; }
            public void setTimers(String timers) { this.timers = timers; }
        }
    }

    public static class Retry {
        private int maxAttempts = 3;
        private Duration baseInterval = Duration.ofSeconds(5);
        private Duration maxInterval = Duration.ofMinutes(5);

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public Duration getBaseInterval() { return baseInterval; }
        public void setBaseInterval(Duration baseInterval) { this.baseInterval = baseInterval; }
        public Duration getMaxInterval() { return maxInterval; }
        public void setMaxInterval(Duration maxInterval) { this.maxInterval = maxInterval; }
    }
}

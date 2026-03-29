package uz.salvadore.processengine.rabbitmq.config;

import java.time.Duration;

/**
 * Configuration for the RabbitMQ transport adapter.
 */
public final class RabbitMqTransportConfig {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String virtualHost;
    private final String tasksExchange;
    private final String retryExchange;
    private final String dlqExchange;
    private final String timersExchange;
    private final int maxRetryAttempts;
    private final Duration retryBaseInterval;
    private final Duration retryMaxInterval;

    private RabbitMqTransportConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.username = builder.username;
        this.password = builder.password;
        this.virtualHost = builder.virtualHost;
        this.tasksExchange = builder.tasksExchange;
        this.retryExchange = builder.retryExchange;
        this.dlqExchange = builder.dlqExchange;
        this.timersExchange = builder.timersExchange;
        this.maxRetryAttempts = builder.maxRetryAttempts;
        this.retryBaseInterval = builder.retryBaseInterval;
        this.retryMaxInterval = builder.retryMaxInterval;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getVirtualHost() { return virtualHost; }
    public String getTasksExchange() { return tasksExchange; }
    public String getRetryExchange() { return retryExchange; }
    public String getDlqExchange() { return dlqExchange; }
    public String getTimersExchange() { return timersExchange; }
    public int getMaxRetryAttempts() { return maxRetryAttempts; }
    public Duration getRetryBaseInterval() { return retryBaseInterval; }
    public Duration getRetryMaxInterval() { return retryMaxInterval; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String host = "localhost";
        private int port = 5672;
        private String username = "guest";
        private String password = "guest";
        private String virtualHost = "/";
        private String tasksExchange = "process-engine.tasks";
        private String retryExchange = "process-engine.retry";
        private String dlqExchange = "process-engine.dlq";
        private String timersExchange = "process-engine.timers";
        private int maxRetryAttempts = 3;
        private Duration retryBaseInterval = Duration.ofSeconds(5);
        private Duration retryMaxInterval = Duration.ofMinutes(5);

        public Builder host(String host) { this.host = host; return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder password(String password) { this.password = password; return this; }
        public Builder virtualHost(String virtualHost) { this.virtualHost = virtualHost; return this; }
        public Builder tasksExchange(String tasksExchange) { this.tasksExchange = tasksExchange; return this; }
        public Builder retryExchange(String retryExchange) { this.retryExchange = retryExchange; return this; }
        public Builder dlqExchange(String dlqExchange) { this.dlqExchange = dlqExchange; return this; }
        public Builder timersExchange(String timersExchange) { this.timersExchange = timersExchange; return this; }
        public Builder maxRetryAttempts(int maxRetryAttempts) { this.maxRetryAttempts = maxRetryAttempts; return this; }
        public Builder retryBaseInterval(Duration retryBaseInterval) { this.retryBaseInterval = retryBaseInterval; return this; }
        public Builder retryMaxInterval(Duration retryMaxInterval) { this.retryMaxInterval = retryMaxInterval; return this; }

        public RabbitMqTransportConfig build() {
            return new RabbitMqTransportConfig(this);
        }
    }
}

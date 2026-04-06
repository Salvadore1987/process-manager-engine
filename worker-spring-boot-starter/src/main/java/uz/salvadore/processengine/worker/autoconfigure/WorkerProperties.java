package uz.salvadore.processengine.worker.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the process engine worker starter.
 */
@ConfigurationProperties(prefix = "process-engine.worker")
public class WorkerProperties {

    private RabbitMqProperties rabbitmq = new RabbitMqProperties();
    private AutoDeployProperties autoDeploy = new AutoDeployProperties();

    public RabbitMqProperties getRabbitmq() {
        return rabbitmq;
    }

    public void setRabbitmq(RabbitMqProperties rabbitmq) {
        this.rabbitmq = rabbitmq;
    }

    public AutoDeployProperties getAutoDeploy() {
        return autoDeploy;
    }

    public void setAutoDeploy(AutoDeployProperties autoDeploy) {
        this.autoDeploy = autoDeploy;
    }

    public static class RabbitMqProperties {

        private String host = "localhost";
        private int port = 5672;
        private String username = "guest";
        private String password = "guest";
        private String virtualHost = "/";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getVirtualHost() {
            return virtualHost;
        }

        public void setVirtualHost(String virtualHost) {
            this.virtualHost = virtualHost;
        }
    }

    public static class AutoDeployProperties {

        private boolean enabled = true;
        private String resourceLocation = "classpath:bpmn/";
        private boolean failOnError = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getResourceLocation() {
            return resourceLocation;
        }

        public void setResourceLocation(String resourceLocation) {
            this.resourceLocation = resourceLocation;
        }

        public boolean isFailOnError() {
            return failOnError;
        }

        public void setFailOnError(boolean failOnError) {
            this.failOnError = failOnError;
        }
    }
}

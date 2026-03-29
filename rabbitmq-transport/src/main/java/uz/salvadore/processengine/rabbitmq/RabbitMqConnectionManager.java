package uz.salvadore.processengine.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.salvadore.processengine.rabbitmq.config.RabbitMqTransportConfig;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages a single RabbitMQ Connection (thread-safe) and provides
 * channels on demand. Supports graceful shutdown and auto-recovery.
 */
public final class RabbitMqConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqConnectionManager.class);

    private final ConnectionFactory connectionFactory;
    private volatile Connection connection;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public RabbitMqConnectionManager(RabbitMqTransportConfig config) {
        this.connectionFactory = new ConnectionFactory();
        this.connectionFactory.setHost(config.getHost());
        this.connectionFactory.setPort(config.getPort());
        this.connectionFactory.setUsername(config.getUsername());
        this.connectionFactory.setPassword(config.getPassword());
        this.connectionFactory.setVirtualHost(config.getVirtualHost());
        this.connectionFactory.setAutomaticRecoveryEnabled(true);
        this.connectionFactory.setNetworkRecoveryInterval(5000);
    }

    public RabbitMqConnectionManager(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public Connection getConnection() throws IOException, TimeoutException {
        if (closed.get()) {
            throw new IllegalStateException("Connection manager is closed");
        }
        Connection conn = this.connection;
        if (conn == null || !conn.isOpen()) {
            synchronized (this) {
                conn = this.connection;
                if (conn == null || !conn.isOpen()) {
                    log.info("Creating new RabbitMQ connection to {}:{}",
                            connectionFactory.getHost(), connectionFactory.getPort());
                    this.connection = connectionFactory.newConnection();
                    conn = this.connection;
                }
            }
        }
        return conn;
    }

    public Channel createChannel() throws IOException, TimeoutException {
        return getConnection().createChannel();
    }

    public boolean isConnected() {
        Connection conn = this.connection;
        return conn != null && conn.isOpen();
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            Connection conn = this.connection;
            if (conn != null && conn.isOpen()) {
                try {
                    conn.close();
                    log.info("RabbitMQ connection closed");
                } catch (IOException e) {
                    log.warn("Error closing RabbitMQ connection", e);
                }
            }
        }
    }
}

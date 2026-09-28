package com.tenpo.challenge.support;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

/**
 * Redis in a container for the tests of Redis-backed components. Subclasses are annotated with
 * {@code @Testcontainers(disabledWithoutDocker = true)}, which starts the container once per
 * test class and skips the class when Docker is not available.
 */
public abstract class RedisContainerTest {

    // Keep in sync with docker-compose.yml
    protected static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:8.10.2-alpine");
    protected static final int REDIS_PORT = 6379;
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Container
    @SuppressWarnings("resource") // Started and stopped by the Testcontainers JUnit extension
    protected static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(REDIS_PORT);

    /**
     * A connection factory for the container.
     */
    protected static LettuceConnectionFactory containerConnectionFactory() {
        return connectionFactory(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
    }

    /**
     * A connection factory for a local port where nothing listens, to simulate Redis being down.
     */
    protected static LettuceConnectionFactory unreachableConnectionFactory() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return connectionFactory("localhost", socket.getLocalPort());
        }
    }

    private static LettuceConnectionFactory connectionFactory(String host, int port) {
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(TIMEOUT)
                .build();
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfiguration);
        factory.afterPropertiesSet();
        return factory;
    }
}

package com.kovanlabs.librarymanagement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class RedisCacheConfigTest {

    @Test
    void testCacheManagerBeanCreation() {
        RedisCacheConfig redisCacheConfig = new RedisCacheConfig();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        ObjectMapper objectMapper = new ObjectMapper();

        RedisCacheManager cacheManager = redisCacheConfig.cacheManager(connectionFactory, objectMapper);

        assertNotNull(cacheManager);
    }
}

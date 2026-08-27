package com.kovanlabs.librarymanagement.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    @SuppressWarnings("deprecation")
    void testBookResponseRecordSerialization() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERY_OBJECT,
                JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        BookResponse original = new BookResponse(UUID.randomUUID(), 5L, "Clean Code", "Robert C. Martin",
                "9780132350884");

        byte[] serialized = serializer.serialize(original);
        assertNotNull(serialized);

        Object deserialized = serializer.deserialize(serialized);
        assertInstanceOf(BookResponse.class, deserialized);
        assertEquals(original, deserialized);
    }
}

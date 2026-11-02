package com.kovanlabs.librarymanagement.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class AiConfigTest {

    @Test
    @DisplayName("AiConfig builds ChatClient bean from builder")
    void testChatClientBean() {
        AiConfig config = new AiConfig();
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);

        when(builder.build()).thenReturn(chatClient);

        ChatClient result = config.chatClient(builder);

        assertNotNull(result);
        verify(builder).build();
    }
}


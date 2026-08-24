package com.researchflow.llm;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiClientTest {
    @Test
    void returnsEmptyWhenNoChatModelIsConfigured() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        SpringAiClient client = new SpringAiClient(provider);

        assertTrue(client.complete("system", "user").isEmpty());
        assertTrue(client.entity("system", "user", ExampleResponse.class).isEmpty());
    }

    private record ExampleResponse(String value) {
    }
}

package com.researchflow.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SpringAiClient {
    private final ChatClient chatClient;

    public SpringAiClient(ObjectProvider<ChatModel> modelProvider) {
        ChatModel model = modelProvider.getIfAvailable();
        this.chatClient = model == null ? null : ChatClient.builder(model).build();
    }

    public Optional<String> complete(String systemPrompt, String userPrompt) {
        if (chatClient == null) return Optional.empty();
        try {
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            return content == null || content.isBlank() ? Optional.empty() : Optional.of(content);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public <T> Optional<T> entity(String systemPrompt, String userPrompt, Class<T> responseType) {
        if (chatClient == null) return Optional.empty();
        try {
            return Optional.ofNullable(chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(responseType));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}

package com.example.support_assistant.advisor;

import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springaicommunity.agent.tools.AskUserQuestionTool.QuestionHandler;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * Custom advisor that uses the {@link AskUserQuestionTool} from the Spring AI Community agent utils.
 * It adds the tool to the request and extra guidance to the system prompt.
 * This guidance makes the AI model more probably call the tool, so a human in the loop can provide missing information
 * like the Spring version before the model answers.
 */
public final class AskUserQuestionToolAdvisor implements BaseAdvisor {

    public static final int DEFAULT_ORDER = ToolCallingAdvisor.DEFAULT_ORDER - 100;

    public static final String DEFAULT_PROMPT = """
            You can ask the user questions with the AskUserQuestionTool.
            The right answer to many Spring questions depends on the version of Spring Boot or Spring Framework.
            Before you answer, check if the user already told you the version in the question or earlier in the conversation.
            If the version or other information you need is missing, you MUST call the AskUserQuestionTool first and wait for the answer.
            Offer the most common versions as options and put the latest version first.
            Never guess missing information and never ask for it in your final answer text. Always use the AskUserQuestionTool for that.
            Only answer the question after you have all the information you need.
            """;

    private final ToolCallback[] toolCallbacks;
    private final String promptTemplate;
    private final int order;

    private AskUserQuestionToolAdvisor(QuestionHandler questionHandler, @Nullable String promptTemplate, int order) {
        this.toolCallbacks = ToolCallbacks.from(
                AskUserQuestionTool.builder().questionHandler(questionHandler).build());
        this.promptTemplate = promptTemplate == null ? DEFAULT_PROMPT : promptTemplate;
        this.order = order;
    }

    public static Builder builder(QuestionHandler questionHandler) {
        return new Builder(questionHandler);
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        var toolOptions = Objects.requireNonNull((ToolCallingChatOptions) chatClientRequest.prompt().getOptions());
        var updatedToolOptionsBuilder = toolOptions.mutate().toolCallbacks(this.toolCallbacks);

        return chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().augmentSystemMessage(systemMessage -> systemMessage.mutate()
                                .text(systemMessage.getText() + System.lineSeparator() + this.promptTemplate)
                                .build())
                        .mutate()
                        .chatOptions(updatedToolOptionsBuilder.build())
                        .build())
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public static final class Builder {

        private final QuestionHandler questionHandler;
        private @Nullable String promptTemplate;
        private int order = DEFAULT_ORDER;

        private Builder(QuestionHandler questionHandler) {
            Assert.notNull(questionHandler, "questionHandler cannot be null");
            this.questionHandler = questionHandler;
        }

        public Builder promptTemplate(String promptTemplate) {
            Assert.notNull(promptTemplate, "promptTemplate cannot be null");
            this.promptTemplate = promptTemplate;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public AskUserQuestionToolAdvisor build() {
            return new AskUserQuestionToolAdvisor(this.questionHandler, this.promptTemplate, this.order);
        }
    }
}

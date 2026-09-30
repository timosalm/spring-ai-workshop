package com.example.support_assistant.tools;

import org.springaicommunity.agent.tools.AskUserQuestionTool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Bridges the blocking {@link AskUserQuestionTool} handler to an asynchronous client.
 * The agent thread waits on a future while the client polls the pending questions and
 * posts the answers over the REST API.
 */
public class AskUserQuestionHandler implements AskUserQuestionTool.QuestionHandler {

    private static final int TIMEOUT_MINUTES = 5;

    private volatile List<AskUserQuestionTool.Question> pendingQuestions = List.of();

    private volatile CompletableFuture<Map<String, String>> answers = new CompletableFuture<>();

    @Override
    public Map<String, String> handle(List<AskUserQuestionTool.Question> questions) {
        this.answers = new CompletableFuture<>();
        this.pendingQuestions = questions;
        try {
            return this.answers.get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new IllegalStateException("No answers received within " + TIMEOUT_MINUTES + " minutes", e);
        } finally {
            this.pendingQuestions = List.of();
        }
    }

    public List<AskUserQuestionTool.Question> pendingQuestions() {
        return this.pendingQuestions;
    }

    public void answer(Map<String, String> answers) {
        this.answers.complete(answers);
    }
}

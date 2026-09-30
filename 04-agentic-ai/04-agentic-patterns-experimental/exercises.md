# Agentic Patterns Lab (Experimental)

In the previous lab you worked with the Tool Search Tool, which is generally available since Spring AI 2.0. This lab goes one step further and works with agentic patterns that are still **experimental**. Keep in mind what the article said about them. They are a first impression and not a stable API.

You add four patterns to the support assistant.

1. An **evaluator optimizer** that lets a second model rate every answer and sends weak answers back for another try.
2. An **Agent Skill** that looks up known vulnerabilities with its own script.
3. **Plan and execute** with the `TodoWriteTool`, so the model writes its steps down before it works through them.
4. A **human in the loop** with the `AskUserQuestionTool`, so the model asks instead of guessing.

Read [Agentic Patterns](../03-agentic-patterns/agentic-patterns.md) and [Agentic Patterns With Spring AI](../03-agentic-patterns/agentic-patterns-spring-ai.md) first.

## Before You Start

This lab works with two projects in this folder.

- [`sample-app/`](sample-app/) is the support assistant from the previous labs.
- [`spring-releases-mcp-server/`](spring-releases-mcp-server/) is the Spring Releases MCP server from the MCP lab.

One thing changed compared to the end of the previous lab. The Tool Search advisor is turned off again in `sample-app/src/main/resources/application.properties`.

```properties
spring.ai.chat.client.tool-search-advisor.enabled=false
```

Every tool is therefore sent with every request again. That costs more tokens, but it keeps the logs short, so the patterns of this lab are easier to follow. Turn it back on whenever you want to see the two work together. The same file also turns on `DEBUG` logging for `org.springaicommunity` and for your own package, so you can follow what the new tools and advisors do.

All paths in this lab are relative to the folder of this lab. You work with three terminals.

- **Terminal 1** runs the support assistant in `sample-app/`.
- **Terminal 2** sends requests with `curl`.
- **Terminal 3** runs the MCP server in `spring-releases-mcp-server/`.

DevTools restarts the support assistant when your IDE compiles a changed class. After a change to `pom.xml` or to a file in `src/main/resources` you stop the application with `Ctrl+C` and start it again. Every Java change shows the **complete file**, so you can replace the whole content of the file. Some commands use [`jq`](https://jqlang.org), and the skill script uses `curl` and `jq` as well.

## Part 1. Getting Started

### 1. Add the Agent Utils Dependency

Most of the experimental patterns are maintained in the [spring-ai-agent-utils](https://github.com/spring-ai-community/spring-ai-agent-utils) repository of the `spring-ai-community` project. One dependency brings in all the patterns of the library, and a BOM provides its version.

Add the BOM first. Add the following BOM import to `sample-app/pom.xml`, right after the `spring-ai-bom` import in the `dependencyManagement` section.

```xml
			<dependency>
				<groupId>org.springaicommunity</groupId>
				<artifactId>spring-ai-agent-utils-bom</artifactId>
				<version>0.11.0</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
```

Then add the library itself without a version. Add the following dependency to `sample-app/pom.xml`, right after the `spring-ai-starter-tool-search-advisor` dependency.

```xml

		<dependency>
			<groupId>org.springaicommunity</groupId>
			<artifactId>spring-ai-agent-utils</artifactId>
		</dependency>
```

### 2. Start the Spring Releases MCP Server

The support assistant is configured as an MCP client of the Spring Releases server, so start that server first in **Terminal 3**.

```bash
cd spring-releases-mcp-server
./mvnw spring-boot:run
```

You should see the embedded MCP server start on port 8090 and log one registered tool at startup. You start the support assistant itself in the next part, once the new code is in place.

## Part 2. Evaluator Optimizer

Your assistant answers in one shot today. Whatever the model produces on the first try goes straight back to the user, no matter how good it is.

The evaluator optimizer pattern puts a second model in front of that answer. The second model rates the answer against clear criteria, and when the rating is too low the first model gets another turn with that feedback.

As mentioned in the article, you build this one yourself from the recursive advisor sample in `spring-ai-examples`. Being an advisor keeps the whole loop inside a single `call()`.

### 3. Create the SelfRefineEvaluationAdvisor

Create `sample-app/src/main/java/com/example/support_assistant/advisor/SelfRefineEvaluationAdvisor.java` with the following code. It is a copy of the sample implementation with small changes to the prompt.

```java
package com.example.support_assistant.advisor;

// Source with small adjustments of the prompt https://github.com/spring-projects/spring-ai-examples/blob/main/advisors/evaluation-recursive-advisor-demo/src/main/java/com/example/advisor/SelfRefineEvaluationAdvisor.java
/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.util.Assert;

/**
 *
 * An Recursive Advisor that evaluates the LLM responses (e.g. Point-wise
 * Scoring ) based on a predefined evaluation criteria. If the evaluation rating
 * is below a certain threshold, it retries the request by providing feedback to
 * the model on how to improve the response. The evaluation is performed by an
 * inner ChatClient instance, which can be customized with different models and
 * settings. The advisor supports a maximum number of retry attempts to avoid
 * infinite loops.
 *
 * @author Christian Tzolov
 */
public final class SelfRefineEvaluationAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(SelfRefineEvaluationAdvisor.class);

    private static final PromptTemplate DEFAULT_EVALUATION_PROMPT_TEMPLATE = new PromptTemplate(
            """
                        You will be given a user_question and assistant_answer couple.
                        Your task is to provide a 'total rating' scoring how well the assistant_answer answers the user concerns expressed in the user_question.
                        Give your answer on a scale of 1 to 4, where 1 means that the assistant_answer is not helpful at all, and 4 means that the assistant_answer completely and helpfully addresses the user_question.
                        
                        You do not have visibility into the assistant's execution environment. Tool calls, search queries, file operations and similar background actions are not part of the material you receive, even when they did take place.
                        For this reason you must never assess whether a tool was called, whether a search was performed, or whether a source was actually retrieved.
                        Treat the absence of visible tool activity as a limitation of your own view and not as a fault of the response.

                        The same limit applies to the facts in the answer. Identifiers, version numbers, dates, severities and similar details usually come from live sources that you cannot see and that are newer than your own knowledge.
                        You must therefore never call a detail fabricated, unverified or implausible, never lower the rating because you cannot confirm it, and never lower the rating because a date or a version looks like it lies in the future.
                        Do not ask for extra citations, advisory links or evidence that the user did not ask for.
                        Judge only whether the answer addresses the user_question in a clear, complete and useful way, and treat every concrete detail it states as correct.

                        Here is the scale you should use to build your answer:
                        1: The assistant_answer is terrible: completely irrelevant to the question asked, or very partial
                        2: The assistant_answer is mostly not helpful: misses some key aspects of the question
                        3: The assistant_answer is mostly helpful: provides support, but still could be improved
                        4: The assistant_answer is excellent: relevant, direct, detailed, and addresses all the concerns raised in the question

                        Provide your feedback as follows:

                        \\{
                             "rating": 0,
                              "evaluation": "Explanation of the evaluation result and how to improve if needed.",
                              "feedback": "Constructive and specific feedback on the assistant_answer."
                        \\}

                        Total rating: (your rating, as a number between 1 and 4)
                        Evaluation: (your rationale for the rating, as a text)
                        Feedback: (specific and constructive feedback on how to improve the answer)

                        You MUST provide values for 'Evaluation:' and 'Total rating:' in your answer.

                        Now here are the question and answer.

                        Question: {question}
                        Answer: {answer}

                        Provide your feedback. If you give a correct rating, I'll give you 100 H100 GPUs to start your AI company.

                        Evaluation:
                    """);

    private final PromptTemplate evaluationPromptTemplate;
    private final int successRating;
    private final int advisorOrder;
    private final int maxRepeatAttempts;
    private final ChatClient chatClient;
    private final BiPredicate<ChatClientRequest, ChatClientResponse> skipEvaluationPredicate;

    @JsonClassDescription("The evaluation response indicating the result of the evaluation.")
    public record EvaluationResponse(// @format:off
                                     int rating, String evaluation, String feedback) {// @format:on
    }

    private SelfRefineEvaluationAdvisor(int advisorOrder, int maxRepeatAttempts, ChatClient.Builder chatClientBuilder,
                                        PromptTemplate promptTemplate, int considerSuccessRating,
                                        BiPredicate<ChatClientRequest, ChatClientResponse> skipEvaluationPredicate) {

        this.chatClient = chatClientBuilder.build();
        this.evaluationPromptTemplate = promptTemplate;
        this.advisorOrder = advisorOrder;
        this.maxRepeatAttempts = maxRepeatAttempts;
        this.skipEvaluationPredicate = skipEvaluationPredicate;
        this.successRating = considerSuccessRating;
    }

    @Override
    public String getName() {
        return "Evaluation Advisor";
    }

    @Override
    public int getOrder() {
        return this.advisorOrder;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        Assert.notNull(chatClientRequest, "chatClientRequest must not be null");
        Assert.notNull(callAdvisorChain, "callAdvisorChain must not be null");

        var request = chatClientRequest;

        ChatClientResponse response;

        // Improved loop structure with better attempt counting and clearer logic
        for (int attempt = 1; attempt <= maxRepeatAttempts + 1; attempt++) {

            // Make the inner call (e.g., to the evaluation LLM model)
            response = callAdvisorChain.copy(this).nextCall(request);

            // Early exit - no evaluation needed (e.g., tool call)
            if (this.skipEvaluationPredicate.test(chatClientRequest, response)) {
                logger.debug("Skipping evaluation because skipEvaluationPredicate returned true.");
                return response;
            }

            // Perform evaluation
            EvaluationResponse evaluation = this.evaluate(chatClientRequest, response);

            // If evaluation passes, return the response
            if (evaluation.rating() >= this.successRating) {
                logger.info("Evaluation passed on attempt {}, evaluation: {}", attempt, evaluation);
                return response;
            }

            // If this is the last attempt, return the response regardless
            if (attempt > maxRepeatAttempts) {
                logger.warn(
                        "Maximum attempts ({}) reached. Returning last response despite failed evaluation. Use the following feedback to improve: {}",
                        maxRepeatAttempts, evaluation.feedback());

                // TODO : Perhaps we should throw an exception here instead of returning the
                // last response? A pluggable strategy could be useful.
                return response;
            }

            // Retry with evaluation feedback
            logger.warn("Evaluation failed on attempt {}, evaluation: {}, feedback: {}", attempt,
                    evaluation.evaluation(), evaluation.feedback());

            // TODO: We could consider a pluggable backoff strategy here (e.g., exponential
            // backoff).
            // It would allow to either refine/repeat strategy or return the response with
            // evaluation feedback as metadata.
            request = this.addEvaluationFeedback(chatClientRequest, evaluation);
        }

        // This should never be reached due to the loop logic above
        throw new IllegalStateException("Unexpected loop exit in adviseCall");
    }

    /**
     * Performs the evaluation using the LLM-as-a-Judge and returns the result.
     */
    private EvaluationResponse evaluate(ChatClientRequest request, ChatClientResponse response) {

        var evaluationPrompt = this.evaluationPromptTemplate.render(
                Map.of("question", this.getPromptQuestion(request), "answer", this.getAssistantAnswer(response)));

        return chatClient.prompt(evaluationPrompt).call().entity(EvaluationResponse.class);
    }

    private String getPromptQuestion(ChatClientRequest chatClientRequest) {
        var messages = chatClientRequest.prompt().getInstructions();

        String conversationHistory = messages.stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .map(m -> m.getMessageType() + ":" + m.getText()).collect(Collectors.joining(System.lineSeparator()));

        SystemMessage systemMessage = chatClientRequest.prompt().getSystemMessage();

        return systemMessage.getMessageType() + ":" + systemMessage.getText() + System.lineSeparator()
                + conversationHistory;
    }

    private String getAssistantAnswer(ChatClientResponse chatClientResponse) {
        return chatClientResponse.chatResponse() != null && chatClientResponse.chatResponse().getResult() != null
                ? chatClientResponse.chatResponse().getResult().getOutput().getText()
                : "";
    }

    /**
     * Creates a new request with evaluation feedback for retry.
     */
    private ChatClientRequest addEvaluationFeedback(ChatClientRequest originalRequest,
                                                    EvaluationResponse evaluationResponse) {

        Prompt augmentedPrompt = originalRequest.prompt()
                .augmentUserMessage(userMessage -> userMessage.mutate().text(String.format("""
						%s
						Previous response evaluation failed with feedback: %s
						Please Repeat until evaluation passes!
						""", userMessage.getText(), evaluationResponse.feedback())).build());

        return originalRequest.mutate().prompt(augmentedPrompt).build();
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
                                                 StreamAdvisorChain streamAdvisorChain) {
        return Flux.error(new UnsupportedOperationException(
                "The Structured Output Validation Advisor does not support streaming."));
    }

    /**
     * Creates a new Builder for EvaluationAdvisor_Improved.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for EvaluationAdvisor_Improved.
     */
    public final static class Builder {
        private int successRating = 3;
        private int advisorOrder = BaseAdvisor.LOWEST_PRECEDENCE - 2000;
        private int maxRepeatAttempts = 3;
        private ChatClient.Builder chatClientBuilder;
        private PromptTemplate promptTemplate = DEFAULT_EVALUATION_PROMPT_TEMPLATE;

        BiPredicate<ChatClientRequest, ChatClientResponse> skipEvaluationPredicate = (request,
                                                                                      response) -> response.chatResponse() == null || response.chatResponse().hasToolCalls();

        private Builder() {
        }

        public Builder successRating(int successRating) {
            Assert.isTrue(successRating >= 1 && successRating <= 4, "successRating must be between 1 and 4");
            this.successRating = successRating;
            return this;
        }

        public Builder order(int advisorOrder) {
            Assert.isTrue(advisorOrder > BaseAdvisor.HIGHEST_PRECEDENCE && advisorOrder < BaseAdvisor.LOWEST_PRECEDENCE,
                    "advisorOrder must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");
            this.advisorOrder = advisorOrder;
            return this;
        }

        public Builder chatClientBuilder(ChatClient.Builder chatClientBuilder) {
            Assert.notNull(chatClientBuilder, "chatClientBuilder must not be null");
            this.chatClientBuilder = chatClientBuilder;
            return this;
        }

        public Builder maxRepeatAttempts(int repeatAttempts) {
            Assert.isTrue(repeatAttempts >= 1, "repeatAttempts must be greater than or equal to 1");
            this.maxRepeatAttempts = repeatAttempts;
            return this;
        }

        public Builder promptTemplate(PromptTemplate promptTemplate) {
            Assert.notNull(promptTemplate, "promptTemplate must not be null");
            this.promptTemplate = promptTemplate;
            return this;
        }

        public Builder skipEvaluationPredicate(
                BiPredicate<ChatClientRequest, ChatClientResponse> skipEvaluationPredicate) {
            Assert.notNull(skipEvaluationPredicate, "skipEvaluationPredicate must not be null");
            this.skipEvaluationPredicate = skipEvaluationPredicate;
            return this;
        }

        public SelfRefineEvaluationAdvisor build() {
            if (this.chatClientBuilder == null) {
                throw new IllegalArgumentException("chatClientBuilder must be set");
            }
            return new SelfRefineEvaluationAdvisor(this.advisorOrder, this.maxRepeatAttempts, this.chatClientBuilder,
                    this.promptTemplate, this.successRating, this.skipEvaluationPredicate);
        }
    }
}
```

That is a lot of code at once, so walk through the parts that matter.

- **An ordinary advisor from the outside.** The class implements the same `CallAdvisor` interface as the `SimpleLoggerAdvisor` and the `MessageChatMemoryAdvisor` you already use, and you register it the same way. Everything that is special about it happens inside `adviseCall`.
- **The prompt of the judge.** `DEFAULT_EVALUATION_PROMPT_TEMPLATE` uses point wise scoring. The judge does not compare two answers with each other but gives one answer a score on a fixed scale from 1 to 4. A clear description of every step of the scale keeps the ratings comparable between runs. The template has two placeholders, `question` and `answer`, which the advisor fills in for every evaluation. You can also provide your own prompt template.
- **Structured output for the judge.** The judge does not answer in free text. It answers into the `EvaluationResponse` record, which the advisor then reads with plain Java. The `rating` decides whether the answer is accepted, and the `feedback` is what the first model receives when it has to try again.
- **The retry loop.** Everything happens inside `for (int attempt = 1; attempt <= maxRepeatAttempts + 1; attempt++)`. One extra pass is added on top of `maxRepeatAttempts`, because the first pass is the original answer and not a retry.
- **The recursive part.** `callAdvisorChain.copy(this).nextCall(request)` is the key line. `nextCall` is what every advisor calls to hand the request to the rest of the chain. An advisor chain can only be walked once, so `copy(this)` creates a fresh copy of the remaining chain for every attempt.
- **When no evaluation happens.** Not every response is worth evaluating. The default `skipEvaluationPredicate` skips the evaluation when the model asked for a tool call. This matters a lot for your assistant, because it calls the ticket tools and the remote MCP tool. A tool call request is not an answer to the user, so there is nothing for the judge to rate yet. The evaluation only runs on the final text.
- **The call to the judge.** `chatClient.prompt(evaluationPrompt).call().entity(EvaluationResponse.class)` runs the judge on its own `ChatClient`, which you configure in the next step.
- **What the judge gets to see.** `getPromptQuestion` builds the context out of the request, with the system message first and the user and assistant messages of the conversation after it. It takes the messages of the request that reaches this advisor, so everything that the advisors before it added, like the retrieved documents of the RAG advisor, is part of what the judge sees.
- **The exit condition.** A rating at or above `successRating` ends the loop, and the response goes back to the user unchanged. The judge never rewrites anything. The answer is always the one the original model produced.
- **How the feedback reaches the next attempt.** When the rating is too low, `addEvaluationFeedback` appends the feedback of the judge to the original user message, and the loop starts over with that request. This is the optimizer half of the pattern. The model is not told to fix its own text. It answers the question again and now knows what was missing.
- **The safety net.** When the attempts run out, the last response is returned even though it never passed, and the failure is only visible in the log. In a real application you decide here whether a weak answer is acceptable or whether an error is the better outcome.
- **The defaults you can tune.** A response has to reach a rating of 3 to pass, and up to 3 retries are allowed, so one request can cost up to 8 model calls in the worst case. The order places the advisor near the end of the chain, after chat memory and RAG and around the tool calling, so it wraps the complete work of one turn.
- **No streaming support.** You cannot rate an answer that has not been written yet, and you cannot take back tokens that the user has already seen.

### 4. Configure the Judge and Add the Advisor

The advisor needs a `ChatClient` for the judge. Create it as a bean together with the advisor, and hand the advisor to the assistant.

Replace the content of `sample-app/src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` with the following code.

```java
package com.example.support_assistant;

import com.example.support_assistant.advisor.SelfRefineEvaluationAdvisor;
import org.springaicommunity.mcp.security.client.sync.config.McpClientOAuth2Configurer;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
public class SupportAssistantConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt,
                                 ChatMemory chatMemory,
                                 ToolCallbackProvider tools,
                                 SelfRefineEvaluationAdvisor selfRefineEvaluationAdvisor) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        selfRefineEvaluationAdvisor)
                .defaultTools(tools)
                .build();
    }

    @Bean
    SelfRefineEvaluationAdvisor selfRefineEvaluationAdvisor(ChatClient.Builder builder) {
        var evaluationChatClientBuilder = builder
                .defaultAdvisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
                .defaultOptions(ChatOptions.builder().temperature(0.0));
        return SelfRefineEvaluationAdvisor.builder().chatClientBuilder(evaluationChatClientBuilder).build();
    }

    @ConditionalOnMissingBean(VectorStore.class)
    @Bean
    VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    @Profile("mcp-security")
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .with(McpClientOAuth2Configurer.mcpClientOAuth2(), Customizer.withDefaults())
                .csrf(CsrfConfigurer::disable)
                .build();
    }

    @Bean
    @Profile("!mcp-security")
    SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(CsrfConfigurer::disable)
                .build();
    }
}
```

Three details in the new `selfRefineEvaluationAdvisor` bean are worth a closer look.

- **A fresh builder for the judge.** `ChatClient.Builder` is prototype scoped, so this bean gets its own instance and not the one that your `chatClient` bean uses. The judge therefore has no system prompt, no RAG advisor, no chat memory, and no tools. It only ever sees the question and the answer that the advisor hands it, which is exactly what you want from a judge.
- **No tool calling for the judge.** `AdvisorParams.toolCallingAdvisorAutoRegister(false)` turns off the automatic registration of the tool calling advisor for this client. The judge has no work to do with tools, so leaving the tool calling machinery out keeps its calls simple and cheap.
- **A repeatable rating.** A temperature of `0.0` makes the judge as repeatable as it can be. Your assistant runs at `0.7`, so it stays creative while the rating stays steady. This is also the place where you would use a different model as the judge, with `.defaultOptions(ChatOptions.builder().model("...").temperature(0.0))`. A model rates its own output more generously than the output of another model, so a second model gives you a more honest score. This lab keeps one model to stay simple.

In the `chatClient` bean the advisor is injected and added to the default advisors, next to the logger and the memory advisor. That is the whole wiring. `SupportAssistantService` and `SupportAssistantController` stay exactly as they are, and every request that goes through this client is now evaluated before the user sees it.

### 5. Run the Support Assistant

In **Terminal 1**, export your key and start the support assistant.

```bash
cd sample-app
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Wait for `Started SupportAssistantApplication` in the logs.

### 6. Test It

Ask a question in **Terminal 2** that the knowledge base can answer.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=What are the key features of VMware Tanzu Spring?"
```

Watch the logs of the assistant. The advisor logs every decision it makes, so you can follow the loop.

Now send a request that makes the assistant use a tool.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Open a high-priority ticket for an auth issue with the Spring Enterprise Repository."
```

Here you can see the skip rule at work. The responses in between that only ask for a tool call are not evaluated, and the judge is used once at the end on the answer that reports the ticket number.

The cost of this pattern is easy to see in the logs. Every answer needs at least one extra model call, and every failed evaluation adds two more. That is the price of a quality gate in front of the user, so use it where a bad answer is expensive and not on every endpoint.

## Part 3. Agent Skills

Your assistant answers security questions from what the model was trained on. That knowledge is out of date the moment a new advisory is published, and the model has no way to know it.

An **Agent Skill** can fix that by giving the model a folder of instructions plus the files those instructions need. In this part you write a skill that looks up known vulnerabilities for a Maven artifact, and you give the assistant the shell access that the skill needs to run its own script.

### 7. Write the Skill

A skill is a folder with a `SKILL.md` file in it, and anything else the skill needs next to it.

Create `sample-app/src/main/resources/skills/cve-lookup/SKILL.md` with the following content.

````markdown
---
name: cve-lookup
description: Use when a user asks whether their Spring version is affected by a security vulnerability, mentions a CVE, asks whether an upgrade is security relevant, or reports that a scanner flagged a Spring dependency.
allowed-tools: Bash
---

# Vulnerability lookup

Look up the exact artifact and version the user names. Never answer a security question from memory, because advisories are published continuously and your knowledge is out of date by definition.

```bash
scripts/cve-lookup.sh org.springframework.boot:spring-boot 3.2.4
```

Common coordinates are org.springframework.boot:spring-boot for the framework itself, org.springframework:spring-web and org.springframework:spring-core for the core modules, and org.springframework.security:spring-security-web for Spring Security. Run the script once per artifact when the user names several.

## Reading the output

CLEAN means the database holds no advisory for that exact version, so say that no known vulnerability affects it rather than claiming it is secure. 
AFFECTED lists one advisory per line with its identifier, its severity, the versions in which it was fixed and a short summary. UNREACHABLE means the lookup failed, so tell the user you could not verify anything and do not fall back to your own knowledge.

## Before you answer

If the user did not name a version, ask for it rather than assuming, because the answer is different for every patch release.

When advisories are found, call the release tool to check which versions are currently available, so your upgrade recommendation names a version that actually exists.

## Answering

Name the number of advisories, the highest severity among them and the nearest version that contains all the fixes. Keep it to three sentences and offer the details or a support case rather than listing everything at once.

Do not speculate about exploitability, do not judge how urgent an upgrade is for the user's specific setup, and do not claim a version is safe. Advisories describe what is known, not what is absent.
````

When the application starts, only the block at the top between the two `---` lines is read. It is called the front matter. The model sees these few lines and nothing else, so they decide whether the skill is ever opened.

- **`name` and `description`** are what the model sees before it opens the skill. Write the `description` as a list of situations that should trigger the skill, and not as a summary of what it does. The model has nothing else to go on, so "Use when a user asks whether their Spring version is affected" works, while "Looks up CVEs" does not.
- **`allowed-tools: Bash`** limits the skill to the tools it really needs. A skill that only reads files should not be able to run shell commands.
- **The skill ships its own script.** This is the part that makes a skill more than a longer system prompt. The instructions come with an executable file, and the path is relative to the skill folder. The model reads how to call it and then calls it, which is why the lookup returns the data of today instead of the training data of the model.
- **Reading the output.** The script returns three states, and the skill spells out what each of them means and how to word it. `UNREACHABLE` is the important one, because it tells the model to admit that it could not verify anything instead of falling back on its own knowledge.
- **A skill can lean on the tools you already have.** The skill does not carry a release tool. It points at the `fetchReleasesInfo` tool of the Spring Releases MCP server, so the instructions and the tools work together in one answer.
- **The limits of the answer.** The last paragraphs are guardrails. They are worth as much as the lookup itself, because a confident wrong answer to a security question is worse than no answer.

### 8. Add the Script

The skill is useless without the file it points at. This is plain bash with no AI in it, and it asks the public [OSV.dev](https://osv.dev/) database.

Create `sample-app/src/main/resources/skills/cve-lookup/scripts/cve-lookup.sh` with the following content.

```bash
#!/usr/bin/env bash
# Looks up known vulnerabilities for a Maven artifact and version.
# Usage: cve-lookup.sh <groupId:artifactId> <version>

set -uo pipefail

PACKAGE="${1:?package required, for example org.springframework.boot:spring-boot}"
VERSION="${2:?version required}"

RESPONSE=$(curl -s -m 10 https://api.osv.dev/v1/query \
  -H 'Content-Type: application/json' \
  -d "{\"package\":{\"name\":\"$PACKAGE\",\"ecosystem\":\"Maven\"},\"version\":\"$VERSION\"}")

if [ -z "$RESPONSE" ]; then
  echo "state=UNREACHABLE"
  exit 2
fi

if ! command -v jq >/dev/null 2>&1; then
  printf '%s' "$RESPONSE" | grep -o '"id":"[^"]*"' | cut -d'"' -f4 | sort -u
  exit 0
fi

COUNT=$(printf '%s' "$RESPONSE" | jq '.vulns | length // 0')

if [ "$COUNT" = "0" ]; then
  echo "state=CLEAN package=$PACKAGE version=$VERSION"
  exit 0
fi

echo "state=AFFECTED package=$PACKAGE version=$VERSION count=$COUNT"

printf '%s' "$RESPONSE" | jq -r '
  .vulns[] |
  "  id=" + (.aliases // [.id] | map(select(startswith("CVE"))) | first // .id) +
  " severity=" + (.database_specific.severity // "UNKNOWN") +
  " fixed=" + ([.affected[].ranges[]?.events[]?.fixed] | unique | join(",") | if . == "" then "none" else . end) +
  " summary=" + ((.summary // "no summary") | .[0:90])
'
```

It makes one HTTP call with a timeout. Everything after that turns the JSON into the three short states that `SKILL.md` describes, because a compact line is cheaper in the context than the full advisory JSON and easier for the model to get right.

The model runs this file, so it has to be executable. Run this in **Terminal 2**.

```bash
chmod +x sample-app/src/main/resources/skills/cve-lookup/scripts/cve-lookup.sh
```

### 9. Register the Skill

Two tools go on the `ChatClient`. `SkillsTool` makes the skills discoverable, and `ShellTools` lets the model actually run the script.

Replace the content of `sample-app/src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` with the following code.

```java
package com.example.support_assistant;

import com.example.support_assistant.advisor.SelfRefineEvaluationAdvisor;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.mcp.security.client.sync.config.McpClientOAuth2Configurer;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
public class SupportAssistantConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt,
                                 ChatMemory chatMemory,
                                 ToolCallbackProvider tools,
                                 SelfRefineEvaluationAdvisor selfRefineEvaluationAdvisor,
                                 @Value("classpath:skills") Resource skillsResource) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        selfRefineEvaluationAdvisor)
                .defaultTools(
                        tools,
                        SkillsTool.builder().addSkillsResource(skillsResource).build(),
                        ShellTools.builder().build())
                .build();
    }

    @Bean
    SelfRefineEvaluationAdvisor selfRefineEvaluationAdvisor(ChatClient.Builder builder) {
        var evaluationChatClientBuilder = builder
                .defaultAdvisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
                .defaultOptions(ChatOptions.builder().temperature(0.0));
        return SelfRefineEvaluationAdvisor.builder().chatClientBuilder(evaluationChatClientBuilder).build();
    }

    @ConditionalOnMissingBean(VectorStore.class)
    @Bean
    VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    @Profile("mcp-security")
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .with(McpClientOAuth2Configurer.mcpClientOAuth2(), Customizer.withDefaults())
                .csrf(CsrfConfigurer::disable)
                .build();
    }

    @Bean
    @Profile("!mcp-security")
    SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(CsrfConfigurer::disable)
                .build();
    }
}
```

- **`@Value("classpath:skills") Resource skillsResource`** points at the folder, not at a single skill. Every subfolder with a `SKILL.md` in it is picked up, so you add a second skill by adding a second folder, and nothing changes here.
- **`SkillsTool`** is a single tool for the model, no matter how many skills you have. It offers the names and the descriptions, and it returns the full `SKILL.md` of the skill that the model asks for.
- **`ShellTools`** lets the model carry out the instructions. Without it the model could read them but never run the script. Note what you hand over here. `ShellTools` runs commands on the machine where the application runs, so in a real system you restrict what it may run, and you never point a skills folder at a place that a user can write to.

The skills tool and the shell tools are sent with every request, because the Tool Search advisor is turned off in this lab. Turn it back on and they are found on demand like every other tool, which is how you would run this with a larger set of skills.

### 10. Restart the Support Assistant

DevTools restarts the application when your classes change, but the new files under `src/main/resources` only reach the classpath with a fresh build, and the executable bit only travels with them from there. So stop the application in Terminal 1 with `Ctrl+C` and start it again.

```bash
./mvnw spring-boot:run
```

### 11. Test It

Ask a security question that names an artifact and a version.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Our scanner flagged spring-boot 3.2.4 in our application. Is that version affected by any known vulnerability, and what should we upgrade to?"
```

Follow it in the logs of the assistant and you see the whole chain.

1. The model matches the question against the skill descriptions it received and calls the skills tool for `cve-lookup`.
2. It now has the full instructions of the skill.
3. It runs `scripts/cve-lookup.sh org.springframework.boot:spring-boot 3.2.4` through the shell tools.
4. Because the lookup found advisories, it calls `fetchReleasesInfo` on the MCP server for the versions that exist.
5. It answers with the count, the highest severity, and a version that is actually released.

Now ask something that has nothing to do with security.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=What are the key features of VMware Tanzu Spring?"
```

The skill is never opened. Only its name and its description were ever in the context, and the instructions, the output format, and the guardrails stayed on disk. That is what makes it cheap to keep many skills around.

## Part 4. Plan and Execute

The skill from the previous part already needs more than one step. Look up the advisories, check which versions exist, and open a ticket for the upgrade. The model does all of that in a single stretch today, and if it drops a step on the way, nothing notices.

The `TodoWriteTool` gives the model a place to write the steps down first and to keep that list current while it works.

### 12. Register the Tool

The tool needs no configuration, so it joins the other tools with one line. Replace the content of `sample-app/src/main/java/com/example/support_assistant/SupportAssistantConfiguration.java` with the following code.

```java
package com.example.support_assistant;

import com.example.support_assistant.advisor.SelfRefineEvaluationAdvisor;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springaicommunity.mcp.security.client.sync.config.McpClientOAuth2Configurer;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
public class SupportAssistantConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt,
                                 ChatMemory chatMemory,
                                 ToolCallbackProvider tools,
                                 SelfRefineEvaluationAdvisor selfRefineEvaluationAdvisor,
                                 @Value("classpath:skills") Resource skillsResource) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(Ordered.LOWEST_PRECEDENCE),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        selfRefineEvaluationAdvisor)
                .defaultTools(
                        tools,
                        SkillsTool.builder().addSkillsResource(skillsResource).build(),
                        ShellTools.builder().build(),
                        TodoWriteTool.builder().build())
                .build();
    }

    @Bean
    SelfRefineEvaluationAdvisor selfRefineEvaluationAdvisor(ChatClient.Builder builder) {
        var evaluationChatClientBuilder = builder
                .defaultAdvisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
                .defaultOptions(ChatOptions.builder().temperature(0.0));
        return SelfRefineEvaluationAdvisor.builder().chatClientBuilder(evaluationChatClientBuilder).build();
    }

    @ConditionalOnMissingBean(VectorStore.class)
    @Bean
    VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    @Profile("mcp-security")
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .with(McpClientOAuth2Configurer.mcpClientOAuth2(), Customizer.withDefaults())
                .csrf(CsrfConfigurer::disable)
                .build();
    }

    @Bean
    @Profile("!mcp-security")
    SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(CsrfConfigurer::disable)
                .build();
    }
}
```

There is no list to define and no steps to write. The plan is the work of the model, and the tool only stores it. The article mentions a `todoEventHandler` on this builder as well, which hands you every update so you can show a live plan in a user interface. This lab reads the plan from the logs instead, so the builder stays empty.

The one thing the tool depends on is already in place. A plan that is forgotten between two model calls is worthless, so the tool needs chat memory. Your assistant has had the `MessageChatMemoryAdvisor` since the fundamentals lab, and the list is stored as tool messages in that same conversation.

### 13. Test It

After DevTools restarted the application, ask for something that has several steps, and name the tool you want it to plan with.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=We run spring-boot 3.2.4. Use the TodoWrite tool to plan the work, then work through that plan: check whether that version has known vulnerabilities, find out which versions we could upgrade to, and open a high-priority ticket for the upgrade."
```

Naming the tool makes the difference here. The tool is offered on every request, but this assistant is told to answer questions with a short explanation, so left to itself the model works through the steps in one stretch and never writes anything down. In a real application the instruction to plan belongs in the system prompt, where it applies to every request and the user never has to know that the tool exists.

In the logs you can watch the plan being written and worked off.

1. The model calls `TodoWrite` once with the whole list, and every item is `pending`.
2. Before each step it calls `TodoWrite` again and changes that one item to `in_progress`.
3. It does the actual work, which is the skill lookup, the MCP release call, and the ticket.
4. After each step another `TodoWrite` call marks the item `completed` and starts the next one.

The pattern to look for is that only one item is ever `in_progress`. That is what keeps the model from jumping ahead to the ticket before it knows which version to recommend.

Now ask something that is a single step.

```bash
curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=What are the key features of VMware Tanzu Spring?"
```

No list is written. The tool describes itself as being for tasks with three or more steps, so the model skips it when there is nothing to plan, and you do not pay for the extra calls on simple questions.

## Part 5. Human in the Loop

When information is missing, your assistant so far just guesses. When someone asks "How do I configure virtual threads?", the right answer depends on the Spring Boot version. The model picks a version on its own, and you only find out that it picked wrong when the answer does not work.

The `AskUserQuestionTool` lets the model ask instead. The article notes that a web application has to bridge the blocking handler to an asynchronous client. You build that bridge here, and it comes down to three pieces. A handler that parks the agent thread, an advisor that adds the tool together with instructions on when to use it, and two REST endpoints for reading the question and posting the answer.

### 14. Bridge the Handler to the Client

The handler is called on the thread that is in the middle of the model call, and it has to return the answers. So it has to wait, while the answers arrive later on a completely different thread over HTTP.

Create `sample-app/src/main/java/com/example/support_assistant/tools/AskUserQuestionHandler.java` with the following code.

```java
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
```

Two threads meet in this class, so walk through it in that order.

- **The agent thread arrives in `handle`.** This is the method the tool calls. It runs inside the tool call, which runs inside `chatClient.call()`, which still holds the HTTP request of the user who asked the original question. Everything is on hold until this method returns.
- **Publish before you wait.** A fresh future is created first, and only then are the questions published. If you publish first, a very fast client could answer before the future exists, and the answer would go nowhere.
- **Park the agent thread.** `this.answers.get(TIMEOUT_MINUTES, TimeUnit.MINUTES)` is where the thread waits. It is the whole trick of this class and also its cost, because a thread is held for as long as the human takes to answer. The timeout keeps a forgotten question from holding it forever. Five minutes is generous for a lab and probably too long for production.
- **Clear the question either way.** The `finally` block clears the pending questions on the way out, no matter whether an answer arrived or the timeout hit. Otherwise a stale question would still be offered to the client after nobody is waiting for it any more.
- **The HTTP thread arrives in `answer`.** It is called from a request thread that has nothing to do with the agent, and completing the future is what wakes the parked thread up. Both fields are `volatile`, because the two threads never share a lock. They only share these two references.

### 15. Add the Tool with an Advisor

The handler belongs to one conversation, so the tool cannot be a default tool on the shared `ChatClient`. It has to be added per call, and only when the caller asked for it.

You could add the `AskUserQuestionTool` with `.tools(...)` like any other tool. In practice the model then rarely calls it. It prefers to guess the missing information, or it asks for it in the text of its final answer, where your client cannot answer. The description of the tool alone does not tell the model when it should ask. So you write a small custom advisor that adds the tool and extra instructions to the system prompt in one step.

Create `sample-app/src/main/java/com/example/support_assistant/advisor/AskUserQuestionToolAdvisor.java` with the following code.

```java
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
```

- **`DEFAULT_PROMPT` tells the model when to ask.** This is the part that makes the difference. The prompt tells the model that many Spring answers depend on the version, and that it has to check whether the user already named it. If the version is missing, the model **must** call the tool. The prompt also forbids the two things the model likes to do instead, guessing and asking in the answer text. The latest version comes first in the options, so the user has a quick default to pick.
- **The `before` method adds the tool and the instructions.** It changes the request before it goes to the model. It adds the tool callback to the chat options and appends the prompt to the existing system message. The tool and the instructions always travel together, so you never get one without the other.
- **`DEFAULT_ORDER` runs before the tool calling loop.** The order puts this advisor before the `ToolCallingAdvisor`, which runs the tool calling loop. So the tool is already part of the request when the loop starts, and the system prompt is extended only once, no matter how many tool calls follow.

Now use the advisor in the service, but only when there is a handler. Replace the content of `sample-app/src/main/java/com/example/support_assistant/SupportAssistantService.java` with the following code.

```java
package com.example.support_assistant;

import com.example.support_assistant.advisor.AskUserQuestionToolAdvisor;
import org.jspecify.annotations.Nullable;
import org.springaicommunity.agent.tools.AskUserQuestionTool.QuestionHandler;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
class SupportAssistantService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final SupportTicketService supportTicketService;

    @Value("classpath:/prompts/rag-prompt.st")
    private Resource ragPromptResource;

    SupportAssistantService(ChatClient chatClient, VectorStore vectorStore, SupportTicketService supportTicketService) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.supportTicketService = supportTicketService;
    }

    SupportResponse generateResponse(String query, String conversationId,
                                     @Nullable QuestionHandler questionHandler) {
        var ragSearchRequest = SearchRequest.builder().topK(4).similarityThreshold(0.4).build();
        var promptTemplate = PromptTemplate.builder().resource(ragPromptResource).build();
        var ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(ragSearchRequest)
                .promptTemplate(promptTemplate)
                .build();

        var request = chatClient.prompt()
                .user(u -> u
                        .text("Answer the following question with a short, well-structured explanation: {question}")
                        .param("question", query))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(ragAdvisor)
                .tools(supportTicketService);

        if (questionHandler != null) {
            request.advisors(AskUserQuestionToolAdvisor.builder(questionHandler).build());
        }

        return request.call().entity(SupportResponse.class);
    }
}
```

The call chain had to be split for this. The request is kept in a variable, the advisor is added to it only when there is a handler, and `call()` moves to the end. Without a handler the request is exactly what it was before, so nothing changes for the other parts of this lab.

The controller does not compile at this point, because it still calls `generateResponse` with two arguments. You fix that in the next step.

### 16. Expose the Questions and the Answers

The client needs two more endpoints. One to read what the agent is asking, and one to send the answer back.

Replace the content of `sample-app/src/main/java/com/example/support_assistant/SupportAssistantController.java` with the following code.

```java
package com.example.support_assistant;

import com.example.support_assistant.tools.AskUserQuestionHandler;
import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
class SupportAssistantController {

    private static final String CONVERSATION_ID_HEADER = "X-Conversation-Id";

    private final SupportAssistantService service;

    private final Map<String, AskUserQuestionHandler> questionHandlers = new ConcurrentHashMap<>();

    SupportAssistantController(SupportAssistantService service) {
        this.service = service;
    }

    // curl -G "http://localhost:8080/api/v1/chat" --data-urlencode "query=Tell me about Spring AI" \
    //   --data-urlencode "humanInTheLoop=true" -H "X-Conversation-Id: 1234"
    @GetMapping(path = "/api/v{version}/chat")
    ResponseEntity<SupportResponse> chat(@RequestParam String query,
                                         @RequestParam(defaultValue = "false") boolean humanInTheLoop,
                                         @RequestHeader(value = CONVERSATION_ID_HEADER, required = false) String conversationId) {
        var id = (conversationId != null) ? conversationId : UUID.randomUUID().toString();

        var questionHandler = (humanInTheLoop && conversationId != null)
                ? questionHandlers.computeIfAbsent(conversationId, key -> new AskUserQuestionHandler())
                : null;
        var response = service.generateResponse(query, id, questionHandler);
        return ResponseEntity.ok().header(CONVERSATION_ID_HEADER, id).body(response);
    }

    // curl "http://localhost:8080/api/v1/chat/questions" -H "X-Conversation-Id: 1234"
    @GetMapping(path = "/api/v{version}/chat/questions")
    List<AskUserQuestionTool.Question> questions(@RequestHeader(CONVERSATION_ID_HEADER) String conversationId) {
        return handlerFor(conversationId).pendingQuestions();
    }

    // curl "http://localhost:8080/api/v1/chat/answers" -H "X-Conversation-Id: 1234" -H "Content-Type: application/json" \
    //   -d '{"Which Spring Boot version do you use?": "Spring Boot 4"}'
    @PostMapping(path = "/api/v{version}/chat/answers")
    void answers(@RequestHeader(CONVERSATION_ID_HEADER) String conversationId,
                 @RequestBody Map<String, String> answers) {
        handlerFor(conversationId).answer(answers);
    }

    private AskUserQuestionHandler handlerFor(String conversationId) {
        var questionHandler = questionHandlers.get(conversationId);
        if (questionHandler == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No human in the loop conversation with id " + conversationId);
        }
        return questionHandler;
    }
}
```

- **One handler per conversation.** Two users must not answer each other's questions, so there is one handler per conversation id in `questionHandlers`. The map is concurrent, because several requests use it at the same time.
- **Only when the client can answer.** The handler is only created when the caller asked for it with `humanInTheLoop=true` and sent a conversation id. Without an id there would be no way to send the answer back, so the tool stays off and the model guesses as before. This also keeps the other parts of the lab working unchanged.
- **What the client polls.** Polling on `/api/v1/chat/questions` is the simplest thing that works in a lab. A real user interface would push the question over WebSocket or Server Sent Events instead, but nothing about the handler changes for that. Only this endpoint would.

### 17. Test It

Wait until DevTools has restarted the application, then send a deliberately vague request. This call does not answer right away, so start it in the background and write the result to a file.

```bash
curl -s -G "http://localhost:8080/api/v1/chat" \
  --data-urlencode "query=How do I configure virtual threads in my Spring Boot application?" \
  --data-urlencode "humanInTheLoop=true" \
  -H "X-Conversation-Id: 1234" > /tmp/chat-response.json &
```

Give the model a few seconds to get to the tool call, then ask what it wants to know.

```bash
curl -s "http://localhost:8080/api/v1/chat/questions" -H "X-Conversation-Id: 1234" | jq
```

You should get one or more questions, each with a short `header`, the `question` text, and a handful of `options`. If the list is still empty, run the command again. The model is probably still working.

Now answer. The key of each entry in the answer is the exact question text from the response you just got. The following commands take the text of the first question and answer it with `Spring Boot 4.x`.

```bash
QUESTION=$(curl -s "http://localhost:8080/api/v1/chat/questions" -H "X-Conversation-Id: 1234" | jq -r '.[0].question')

curl -s -X POST "http://localhost:8080/api/v1/chat/answers" \
  -H "X-Conversation-Id: 1234" \
  -H "Content-Type: application/json" \
  -d "$(jq -n --arg q "$QUESTION" '{($q): "Spring Boot 4.x"}')"
```

The moment this request is handled, the parked thread wakes up, the tool returns your answer to the model, and the model finishes the work it started. Look at the result of the original request.

```bash
cat /tmp/chat-response.json | jq
```

The answer now fits the version you chose instead of the version the model would have guessed. In the logs of the assistant you can see the whole gap. First the `AskUserQuestion` tool call, then a long pause with nothing happening, and then the final answer that uses your choice.

## Recap

You added four experimental agentic patterns to the support assistant. A judge that rates every answer and asks for another try, a skill that brings its own script, a plan that the model writes down and works off, and a question to the user when information is missing. The [`sample-app-experimental/`](../../99-summary/sample-app-experimental/) in the summary folder contains the result of this lab.

The patterns in this lab all run inside one application. When your agents live in different services, teams, or languages, the Agent2Agent protocol lets them find and call each other. Read [Agent2Agent Protocol (A2A)](../05-a2a/a2a.md) and [A2A With Spring AI](../05-a2a/a2a-spring-ai.md) to learn more.

package com.example.support_assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
class KnowledgeBaseIndexer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseIndexer.class);

    private final VectorStore vectorStore;

    @Value("classpath:knowledge-base/*.md")
    private Resource[] knowledgeFiles;

    KnowledgeBaseIndexer(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void index() {
        var documentReaderConfig = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withIncludeBlockquote(true)
                .withIncludeCodeBlock(true)
                .build();
        var documentReader = new MarkdownDocumentReader(Arrays.asList(knowledgeFiles), documentReaderConfig);
        List<Document> documents = documentReader.read();

        var tokenTextSplitter = TokenTextSplitter.builder()
                .withMinChunkLengthToEmbed(25)
                .build();
        var splitDocuments = tokenTextSplitter.split(documents);
        vectorStore.add(splitDocuments);
        log.info("Loaded {} document chunks into vector store", splitDocuments.size());
    }
}
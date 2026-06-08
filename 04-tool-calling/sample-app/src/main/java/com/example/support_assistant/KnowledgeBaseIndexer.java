package com.example.support_assistant;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @PostConstruct
    public void index() {
        var config = MarkdownDocumentReaderConfig.builder().build();
        var documentReader = new MarkdownDocumentReader(Arrays.asList(knowledgeFiles), config);
        var tokenTextSplitter = TokenTextSplitter.builder().build();
        List<Document> documents = tokenTextSplitter.apply(documentReader.get());
        vectorStore.add(documents);
        log.info("Loaded {} document chunks into vector store", documents.size());
    }
}
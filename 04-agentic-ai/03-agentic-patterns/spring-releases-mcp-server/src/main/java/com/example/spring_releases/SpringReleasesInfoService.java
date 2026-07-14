package com.example.spring_releases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@Service
class SpringReleasesInfoService {

    private static final Logger log = LoggerFactory.getLogger(SpringReleasesInfoService.class);

    private final RestClient client = RestClient.create("https://api.spring.io");

    @McpTool(description = "Get all releases for a Spring project, including version and support status.")
    List<SpringRelease> fetchReleasesInfo(
            @McpToolParam(description = "The project slug, e.g. 'spring-boot', 'spring-framework', 'spring-ai'") String projectSlug) {
        log.info("Fetch spring release info for project {} called", projectSlug);

        return client.get()
                .uri("/projects/{slug}/releases", projectSlug)
                .retrieve()
                .body(ReleasesResponse.class)
                .embedded()
                .releases();
    }

    private record ReleasesResponse(@JsonProperty("_embedded") Embedded embedded) {
        record Embedded(List<SpringRelease> releases) {
        }
    }
}

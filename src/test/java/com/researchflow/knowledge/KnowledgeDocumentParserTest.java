package com.researchflow.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeDocumentParserTest {
    private final KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

    @Test
    void parsesUtf8TextDocuments() {
        var file = new MockMultipartFile("file", "knowledge.md", "text/markdown",
                "# 私有知识\n重要结论".getBytes(StandardCharsets.UTF_8));

        assertEquals("# 私有知识\n重要结论", parser.parse(file));
    }

    @Test
    void rejectsUnsupportedDocuments() {
        var file = new MockMultipartFile("file", "knowledge.exe", "application/octet-stream", new byte[]{1});

        assertThrows(IllegalArgumentException.class, () -> parser.parse(file));
    }
}

package com.researchflow.knowledge;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class KnowledgeDocumentParser {
    public String parse(MultipartFile file) {
        try {
            String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            String extension = extension(filename);
            String content = switch (extension) {
                case "txt", "md", "markdown" -> new String(file.getBytes(), StandardCharsets.UTF_8);
                case "docx" -> parseDocx(file.getBytes());
                case "pdf" -> parsePdf(file.getBytes());
                default -> throw new IllegalArgumentException("不支持的知识文档格式: " + extension);
            };
            if (content.isBlank()) throw new IllegalArgumentException("知识文档没有可提取的文本");
            return content.trim();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("知识文档解析失败: " + exception.getMessage());
        }
    }

    private String parseDocx(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getParagraphs().stream().map(paragraph -> paragraph.getText().trim())
                    .filter(text -> !text.isBlank()).collect(Collectors.joining("\n"));
        }
    }

    private String parsePdf(byte[] bytes) throws Exception {
        try (var document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

package com.researchflow.export;

import java.util.Locale;

public enum ExportFormat {
    MARKDOWN("md", "text/markdown;charset=UTF-8"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("pdf", "application/pdf");

    private final String extension;
    private final String mediaType;

    ExportFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() { return extension; }
    public String mediaType() { return mediaType; }

    public static ExportFormat parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new IllegalArgumentException("不支持的导出格式: " + value);
        }
    }
}

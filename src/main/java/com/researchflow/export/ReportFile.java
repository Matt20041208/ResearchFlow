package com.researchflow.export;

public record ReportFile(String filename, String mediaType, byte[] content) {
}

package com.researchflow.export;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.researchflow.persistence.TaskEntity;
import com.researchflow.persistence.TaskRepository;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Service
public class ReportExportService {
    private final TaskRepository taskRepository;

    public ReportExportService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public ReportFile export(String taskId, ExportFormat format) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("研究任务不存在: " + taskId));
        if (task.getReport() == null || task.getReport().isBlank()) {
            throw new IllegalStateException("任务尚未生成可导出的报告");
        }
        byte[] content = switch (format) {
            case MARKDOWN -> task.getReport().getBytes(StandardCharsets.UTF_8);
            case DOCX -> docx(task.getReport());
            case PDF -> pdf(task.getReport());
        };
        return new ReportFile("research-report-" + taskId + "." + format.extension(),
                format.mediaType(), content);
    }

    private byte[] docx(String markdown) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String line : markdown.split("\\R", -1)) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                if (line.startsWith("# ")) {
                    paragraph.setAlignment(ParagraphAlignment.CENTER);
                    run.setBold(true);
                    run.setFontSize(20);
                    run.setText(line.substring(2));
                } else if (line.startsWith("## ")) {
                    run.setBold(true);
                    run.setFontSize(15);
                    run.setText(line.substring(3));
                } else {
                    run.setFontSize(11);
                    run.setText(plainText(line));
                }
                run.setFontFamily("Noto Sans CJK SC");
            }
            document.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Word 导出失败", exception);
        }
    }

    private byte[] pdf(String markdown) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 52, 52, 54, 54);
            PdfWriter.getInstance(document, output);
            document.open();
            BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            Font body = new Font(baseFont, 11);
            Font heading = new Font(baseFont, 16, Font.BOLD);
            Font title = new Font(baseFont, 21, Font.BOLD);
            for (String line : markdown.split("\\R", -1)) {
                if (line.startsWith("# ")) document.add(new Paragraph(line.substring(2), title));
                else if (line.startsWith("## ")) document.add(new Paragraph(line.substring(3), heading));
                else document.add(new Paragraph(plainText(line), body));
            }
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("PDF 导出失败", exception);
        }
    }

    private String plainText(String markdown) {
        return markdown.replaceAll("\\[([^]]+)]\\(([^)]+)\\)", "$1 ($2)")
                .replace("**", "").replace("`", "");
    }
}

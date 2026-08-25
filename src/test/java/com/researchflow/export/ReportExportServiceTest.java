package com.researchflow.export;

import com.researchflow.persistence.TaskEntity;
import com.researchflow.persistence.TaskRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportExportServiceTest {
    private final TaskRepository repository = mock(TaskRepository.class);
    private final ReportExportService service = new ReportExportService(repository);

    @Test
    void exportsMarkdownDocxAndPdf() {
        TaskEntity task = new TaskEntity("task-1", "问题", "default", "user-1", "MANUAL");
        task.setReport("# 中文研究报告\n\n## 结论\n证据支持该结论 [1]。");
        when(repository.findById("task-1")).thenReturn(Optional.of(task));

        assertTrue(new String(service.export("task-1", ExportFormat.MARKDOWN).content(), StandardCharsets.UTF_8)
                .contains("中文研究报告"));
        assertArrayEquals(new byte[]{'P', 'K'}, java.util.Arrays.copyOf(
                service.export("task-1", ExportFormat.DOCX).content(), 2));
        assertArrayEquals("%PDF".getBytes(StandardCharsets.US_ASCII), java.util.Arrays.copyOf(
                service.export("task-1", ExportFormat.PDF).content(), 4));
    }
}

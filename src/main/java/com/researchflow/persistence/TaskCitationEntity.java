package com.researchflow.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "research_task_citation")
public class TaskCitationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String taskId;
    private int citationNumber;
    private String sourceId;
    private String sourceType;
    private String title;
    private String url;
    @Lob
    private String excerpt;
    private double confidence;

    protected TaskCitationEntity() {}

    public TaskCitationEntity(String taskId, int citationNumber, String sourceId, String sourceType,
                              String title, String url, String excerpt, double confidence) {
        this.taskId = taskId;
        this.citationNumber = citationNumber;
        this.sourceId = sourceId;
        this.sourceType = sourceType;
        this.title = title;
        this.url = url;
        this.excerpt = excerpt;
        this.confidence = confidence;
    }

    public String getTaskId() { return taskId; }
    public int getCitationNumber() { return citationNumber; }
    public String getSourceId() { return sourceId; }
    public String getSourceType() { return sourceType; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getExcerpt() { return excerpt; }
    public double getConfidence() { return confidence; }
}

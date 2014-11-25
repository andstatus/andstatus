package org.andstatus.app.service;

public enum QueueType {
    CURRENT("commands-queue", "C"),
    RETRY("retry-queue", "R"),
    ERROR("error-queue", "E"),
    TEST("test-queue", "T");
    
    private String fileNameSuffix;
    private String acronym;
    
    private QueueType(String fileNameSuffix, String acronym) {
        this.fileNameSuffix = fileNameSuffix;
        this.acronym = acronym;
    }
    
    public String getFileName() {
        return "MyService" + "-" + fileNameSuffix;
    }
    
    public String getAcronym() {
        return acronym;
    }
}

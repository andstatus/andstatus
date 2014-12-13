package org.andstatus.app.service;

public enum QueueType {
    CURRENT("commands-queue", "C"),
    RETRY("retry-queue", "R"),
    ERROR("error-queue", "E"),
    TEST("test-queue", "T");
    
    private String filenameSuffix;
    private String acronym;
    
    private QueueType(String filenameSuffix, String acronym) {
        this.filenameSuffix = filenameSuffix;
        this.acronym = acronym;
    }
    
    public String getFilename() {
        return "MyService" + "-" + filenameSuffix;
    }
    
    public String getAcronym() {
        return acronym;
    }
}

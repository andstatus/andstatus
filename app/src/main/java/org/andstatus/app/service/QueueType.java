package org.andstatus.app.service;

public enum QueueType {
    CURRENT("commands-queue", "C", true),
    RETRY("retry-queue", "R", true),
    ERROR("error-queue", "E", false),
    TEST("test-queue", "T", false);
    
    private String filenameSuffix;
    private String acronym;
    private boolean executable;
    
    QueueType(String filenameSuffix, String acronym, boolean executable) {
        this.filenameSuffix = filenameSuffix;
        this.acronym = acronym;
        this.executable = executable;
    }
    
    public String getFilename() {
        return "MyService" + "-" + filenameSuffix;
    }
    
    public String getAcronym() {
        return acronym;
    }

    public boolean isExecutable() {
        return executable;
    }
}

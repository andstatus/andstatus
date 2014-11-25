package org.andstatus.app.service;

/**
 * Allows polling state of the parent object 
 */
public interface CommandExecutorParent {
    boolean isStopping();
}

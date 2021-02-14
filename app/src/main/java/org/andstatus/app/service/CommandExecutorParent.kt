package org.andstatus.app.service

/**
 * Allows polling state of the parent object
 */
interface CommandExecutorParent {
    open fun isStopping(): Boolean
}
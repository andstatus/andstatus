package org.andstatus.app.service

/**
 * Allows polling state of the parent object
 */
interface CommandExecutorParent {
    fun isStopping(): Boolean
}

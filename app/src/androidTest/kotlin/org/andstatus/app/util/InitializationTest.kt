package org.andstatus.app.util

import org.andstatus.app.net.social.Actor
import org.junit.Assert
import org.junit.Test

class InitializationTest {

    @Test
    fun testAppInitialization() {
        if (Actor.lazyEmpty.isInitialized()) {
            MyLog.i(this, "Actor.EMPTY already initialized")
        }
        checkActor(Actor.PUBLIC)
        checkActor(Actor.EMPTY)
    }

    private fun checkActor(actor: Actor) {
        if (actor.avatarFile == null) {
            Assert.fail("Actor.avatarFile is null: $actor")
        } else {
            MyLog.i(this, "Actor.avatarFile is not null: $actor")
        }
    }
}

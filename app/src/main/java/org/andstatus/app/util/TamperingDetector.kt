/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import org.acra.ACRA
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 * Based on https://www.airpair.com/android/posts/adding-tampering-detection-to-your-android-app
 */
object TamperingDetector {
    private val knownSignatures: MutableMap<String, String> = HashMap()

    @Volatile
    private var knownAppSignature: String? = ""
    fun initialize(context: Context) {
        val builder = StringBuilder()
        for (signature in getAppSignatures(context)) {
            if (knownSignatures.containsKey(signature)) {
                knownAppSignature = knownSignatures.get(signature)
                MyStringBuilder.appendWithSpace(builder, "(" + knownAppSignature + ")")
            } else {
                MyLog.i(TamperingDetector::class.java, "Unknown APK signature:'$signature'")
                MyStringBuilder.appendWithSpace(builder, signature)
            }
        }
        ACRA.errorReporter.putCustomData("apkSignatures", builder.toString())
    }

    private fun getAppSignatures(context: Context): MutableList<String> {
        val signatures: MutableList<String> = ArrayList()
        try {
            val packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES)
            for (signature in packageInfo.signatures) {
                val md = MessageDigest.getInstance("SHA")
                md.update(signature.toByteArray())
                var signatureString = Base64.encodeToString(md.digest(), Base64.DEFAULT)
                signatureString = signatureString.substring(0, signatureString.length - 1)
                signatures.add(signatureString)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            MyLog.e(TamperingDetector::class.java, "Failed to get app signature", e)
        } catch (e: NoSuchAlgorithmException) {
            MyLog.e(TamperingDetector::class.java, "Failed to get app signature", e)
        }
        return signatures
    }

    fun hasKnownAppSignature(): Boolean {
        return !knownAppSignature.isNullOrEmpty()
    }

    fun getAppSignatureInfo(): String {
        return knownAppSignature?.takeIf {
             it.isNotEmpty()
        } ?: "unknown-keys"
    }

    init {
        knownSignatures["Q2LVj1MPgZdoNckr4g0WbOmi7nE="] = "release-keys"
        knownSignatures["U+TELzORnTCJ/2OZKoIbcNhVMPg="] = "debug-keys"
        knownSignatures["qhfrV6COj1j+fUrohD3xVZpfhYg="] = "f-droid-keys"
    }
}
package crimera.patches.twitter.misc.refreshsound

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.fingerprint.MethodFingerprintResult
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import crimera.patches.twitter.misc.integrations.IntegrationsPatch
import crimera.patches.twitter.misc.settings.SettingsPatch

@Patch(
    name = "Refresh sounds (pull & complete)",
    description = "Play a pull sound while dragging down and a refresh-complete sound when timeline updates.",
    requiresIntegrations = true,
    dependencies = [IntegrationsPatch::class, SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")]
)
object RefreshSoundPatch : BytecodePatch(
    setOf(
        PullAccompanistFingerprint,
        PullMaterial3Fingerprint,
        RefreshCompleteFingerprint
    )
) {
    override fun execute(context: BytecodeContext) {
        PullAccompanistFingerprint.result?.injectPullHook()
        PullMaterial3Fingerprint.result?.injectPullHook()

        val complete = RefreshCompleteFingerprint.result
            ?: throw PatchException("RefreshCompleteFingerprint not found")

        val m = complete.mutableMethod
        var lastRet = -1
        for (ins in m.implementation!!.instructions) {
            when (ins.opcode) {
                Opcode.RETURN_VOID, Opcode.RETURN, Opcode.RETURN_OBJECT -> lastRet = ins.location.index
                else -> {}
            }
        }
        val insertAt = if (lastRet >= 0) lastRet else 0
        m.addInstructions(
            insertAt,
            "invoke-static {}, Lapp/revanced/integrations/twitter/hooks/RefreshHooks;->onRefreshComplete()V"
        )
    }
}

private fun MethodFingerprintResult.injectPullHook() {
    val method = this.mutableMethod
    // onPull(F) — 첫 번째 인자(float)를 그대로 전달 (일반적으로 p1)
    method.addInstructions(
        0,
        "invoke-static {p1}, Lapp/revanced/integrations/twitter/hooks/RefreshHooks;->onPull(F)V"
    )
}

/** Accompanist: com.google.accompanist.swiperefresh.SwipeRefreshNestedScrollConnection#onPull(F)F */
object PullAccompanistFingerprint : MethodFingerprint(
    returnType = "F",
    parameters = listOf("F"),
    customFingerprint = { m, c ->
        try {
            val cls: ClassDef? = c
            (m.name == "onPull") &&
            (m.parameterTypes.size == 1 && m.parameterTypes[0] == "F") &&
            (m.returnType == "F") &&
            (cls != null && cls.type.startsWith("Lcom/google/accompanist/swiperefresh/SwipeRefreshNestedScrollConnection"))
        } catch (_: Throwable) {
            false
        }
    }
)

/** Material3 PullToRefresh: androidx.compose.material3.pulltorefresh.* 의 onPull(F)F */
object PullMaterial3Fingerprint : MethodFingerprint(
    returnType = "F",
    parameters = listOf("F"),
    customFingerprint = { m, c ->
        try {
            val cls: ClassDef? = c
            (m.name == "onPull") &&
            (m.parameterTypes.size == 1 && m.parameterTypes[0] == "F") &&
            (m.returnType == "F") &&
            (cls != null && cls.type.contains("Landroidx/compose/material3/pulltorefresh/"))
        } catch (_: Throwable) {
            false
        }
    }
)

object RefreshCompleteFingerprint : MethodFingerprint(
    returnType = "V",
    customFingerprint = { m, c ->
        try {
            val cls: ClassDef? = c
            (cls != null && cls.type.startsWith("Lcom/twitter/")) &&
            when (m.name) {
                "notifyDataSetChanged", "notifyItemRangeInserted", "notifyItemRangeChanged" -> true
                else -> false
            }
        } catch (_: Throwable) {
            false
        }
    }
)

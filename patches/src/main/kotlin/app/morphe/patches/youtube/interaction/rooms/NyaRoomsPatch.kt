// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.morphe.patches.youtube.interaction.rooms

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.shared.misc.videoinformation.PlaybackParametersToStringFingerprint
import app.morphe.patches.shared.misc.videoinformation.getPlaybackParametersSetterFingerprint
import app.morphe.patches.youtube.layout.player.buttons.addPlayerBottomButton
import app.morphe.patches.youtube.layout.player.buttons.playerOverlayButtonsHookPatch
import app.morphe.patches.youtube.layout.player.icons.copyPlayerButtonIcons
import app.morphe.patches.youtube.misc.playercontrols.addLegacyBottomControl
import app.morphe.patches.youtube.misc.playercontrols.initializeLegacyBottomControl
import app.morphe.patches.youtube.misc.playercontrols.legacyPlayerControlsPatch
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.misc.toolbar.hookToolBar
import app.morphe.patches.youtube.misc.toolbar.toolBarHookPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.information.videoInformationPatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val BUTTON = "Lapp/morphe/extension/youtube/patches/rooms/RoomButton;"
private const val PLAYER = "Lapp/morphe/extension/youtube/patches/rooms/RoomPlayerBridge;"

private val nyaRoomsResourcePatch = resourcePatch {
    dependsOn(settingsPatch, legacyPlayerControlsPatch)
    execute {
        copyPlayerButtonIcons("nyarooms", "nya_yt_room")
        addLegacyBottomControl("nyarooms")
    }
}

@Suppress("unused")
val nyaRoomsPatch = bytecodePatch(
    name = "Nya Rooms",
    description = "Watch-together rooms with approved invitations and encrypted Nostr relay transport. SponsorBlock is suspended during sessions.",
) {
    dependsOn(nyaRoomsResourcePatch, playerOverlayButtonsHookPatch, legacyPlayerControlsPatch, videoInformationPatch, toolBarHookPatch)
    compatibleWith(COMPATIBILITY_YOUTUBE)
    execute {
        val cls = getPlaybackParametersSetterFingerprint(PlaybackParametersToStringFingerprint.classDef.type).classDef
        fun Method.instructions() = implementation?.instructions?.toList().orEmpty()
        fun Method.calls() = instructions().mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }
        fun unique(label: String, predicate: (Method) -> Boolean) = cls.methods.singleOrNull(predicate)
            ?: throw PatchException("Nya Rooms: native $label is ambiguous or unavailable")
        val setPlay = unique("setPlayWhenReady") {
            it.parameterTypes == listOf("Z") && it.returnType == "V" && it.instructions().size <= 6 &&
                it.calls().any { call -> call.definingClass == cls.type && call.parameterTypes == listOf("Z", "I") && call.returnType == "V" } &&
                it.instructions().any { ins -> (ins as? WideLiteralInstruction)?.wideLiteral == 1L }
        }
        val getPlay = unique("getPlayWhenReady") {
            it.parameterTypes.isEmpty() && it.returnType == "Z" && it.instructions().size <= 8 &&
                it.instructions().any { ins -> ins.opcode == Opcode.IGET_BOOLEAN && ((ins as ReferenceInstruction).reference as FieldReference).definingClass != cls.type }
        }
        // BasePlayer.isPlaying compares playback state with STATE_READY (3), then playWhenReady
        // and suppression. This identifies the state getter independently of obfuscated field names.
        val parent = classDefBy(cls.superclass!!)
        val isPlaying = parent.methods.singleOrNull { method ->
            method.parameterTypes.isEmpty() && method.returnType == "Z" &&
                method.instructions().any { (it as? WideLiteralInstruction)?.wideLiteral == 3L } &&
                method.calls().any { it.name == getPlay.name && it.returnType == "Z" }
        } ?: throw PatchException("Nya Rooms: native isPlaying contract changed")
        val stateName = isPlaying.calls().first { it.parameterTypes.isEmpty() && it.returnType == "I" }.name
        val getState = unique("getPlaybackState") { it.name == stateName && it.parameterTypes.isEmpty() && it.returnType == "I" }
        val getPosition = unique("getCurrentPosition") {
            it.parameterTypes.isEmpty() && it.returnType == "J" && it.instructions().size <= 10 &&
                it.instructions().none { ins -> ins.opcode == Opcode.IGET_WIDE } &&
                it.calls().any { call -> call.definingClass == cls.type && call.parameterTypes.size == 1 && call.returnType == "J" }
        }
        val getDuration = unique("getDuration") {
            it.parameterTypes.isEmpty() && it.returnType == "J" &&
                it.instructions().any { ins -> (ins as? WideLiteralInstruction)?.wideLiteral == -9223372036854775807L }
        }
        // Fail closed on changed or ambiguous native APIs; no reflection or simulated button taps.
        cls.interfaces.add(PLAYER)
        val bindings = listOf(
            Triple(setPlay, "patch_roomPlayWhenReady", "V"),
            Triple(getPlay, "patch_roomPlayWhenReady", "Z"),
            Triple(getState, "patch_roomPlaybackState", "I"),
            Triple(getPosition, "patch_roomPosition", "J"),
            Triple(getDuration, "patch_roomDuration", "J"),
        )
        bindings.forEach { (native, bridgeName, result) ->
            val params = if (result == "V") listOf("Z") else emptyList()
            cls.methods.add(ImmutableMethod(cls.type, bridgeName,
                params.map { ImmutableMethodParameter(it, null, null) }, result,
                AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
                MutableMethodImplementation(if (result == "V") 2 else 3)).toMutable().apply {
                val registers = if (result == "V") "p0, p1" else "p0"
                val tail = when (result) {
                    "V" -> "return-void"
                    "J" -> "move-result-wide v0\nreturn-wide v0"
                    else -> "move-result v0\nreturn v0"
                }
                addInstructions(0, "invoke-virtual { $registers }, $native\n$tail")
            })
        }
        addPlayerBottomButton(BUTTON)
        initializeLegacyBottomControl(BUTTON)
        hookToolBar("Lapp/morphe/extension/youtube/patches/rooms/RoomToolbar;->setSearchButtonView")
    }
}

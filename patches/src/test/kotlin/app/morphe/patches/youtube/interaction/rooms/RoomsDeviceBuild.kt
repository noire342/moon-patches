package app.morphe.patches.youtube.interaction.rooms

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.dex.SdkDexVerifier
import app.morphe.patches.all.misc.clone.cloneAppPatch
import app.morphe.patches.youtube.ad.hideAdsPatch
import app.morphe.patches.youtube.misc.gms.gmsCoreSupportPatch
import app.morphe.patches.youtube.layout.sponsorblock.sponsorBlockPatch
import app.morphe.patches.youtube.layout.branding.customBrandingPatch
import app.morphe.patches.youtube.layout.branding.header.changeHeaderPatch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

fun main(arguments: Array<String>) {
    require(arguments.size in 1..2)
    val input = File(arguments[0]).canonicalFile
    val output = arguments.getOrNull(1)?.let(::File)?.canonicalFile
    cloneAppPatch.options["packageName"] = "app.nyanime.youtube.rooms"
    customBrandingPatch.options["appIcon"] = "moon"
    customBrandingPatch.options["customName"] = "Moon"
    val sdk = File(System.getenv("ANDROID_HOME") ?: error("ANDROID_HOME is required"))
    val workspace = Files.createTempDirectory("nya-rooms-validation")
    Patcher(PatcherConfig(input, workspace.toFile(), verifier = SdkDexVerifier(sdk))).use { patcher ->
        patcher += setOf(nyaRoomsPatch, gmsCoreSupportPatch, hideAdsPatch, sponsorBlockPatch, customBrandingPatch, changeHeaderPatch)
        runBlocking { patcher().collect { result -> result.exception?.let { throw it }; println("Applied: ${result.patch.name}") } }
        val result = patcher.get()
        if (output != null) { output.parentFile.mkdirs(); result.applyTo(output); println("Verified unsigned APK: $output") }
        else println("Nya Rooms applied and DEX verified")
    }
}

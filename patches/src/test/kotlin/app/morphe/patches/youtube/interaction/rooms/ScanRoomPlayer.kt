package app.morphe.patches.youtube.interaction.rooms

import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import java.io.File
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

fun main(arguments: Array<String>) {
    val dex = DexFileFactory.loadDexContainer(File(arguments[0]), Opcodes.getDefault())
    val classes = dex.dexEntryNames.flatMap { dex.getEntry(it)!!.dexFile.classes }
    val targets = classes.filter { it.interfaces.contains("Landroidx/media3/exoplayer/ExoPlayer;") || it.type == "Landroidx/media3/common/Player;" || it.type == "Landroidx/media3/exoplayer/ExoPlayer;" }
    targets.forEach { cls ->
        println("CLASS ${cls.type} SUPER ${cls.superclass}")
        cls.methods.filter { !it.name.startsWith("<") }.forEach { println("${it.name}(${it.parameterTypes.joinToString("")})${it.returnType}") }
        if (cls.interfaces.contains("Landroidx/media3/exoplayer/ExoPlayer;")) {
            cls.methods.filter { (it.parameterTypes.isEmpty() && it.returnType in listOf("Z", "I", "J")) || it.parameterTypes == listOf("Z") }.forEach { method ->
                println("CODE $method")
                method.implementation?.instructions?.forEach { instruction ->
                    val operands = when (instruction) {
                        is TwoRegisterInstruction -> "v${instruction.registerA}, v${instruction.registerB}"
                        is OneRegisterInstruction -> "v${instruction.registerA}"
                        else -> ""
                    }
                    val reference = (instruction as? ReferenceInstruction)?.reference ?: ""
                    val literal = (instruction as? WideLiteralInstruction)?.wideLiteral?.toString() ?: ""
                    println("${instruction.opcode} $operands $reference $literal")
                }
            }
            classes.filter { it.type == cls.superclass }.forEach { parent -> parent.methods.filter { it.parameterTypes.isEmpty() && it.returnType == "Z" }.forEach { println("PARENT $it") } }
        }
    }
}

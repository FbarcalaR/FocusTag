package io.github.fbarcalar.focustag.datastore

import androidx.datastore.core.Serializer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CompletableDeferred

/**
 * An `Int` serializer that, once [armed], pauses the next write inside `writeTo` (after DataStore has bumped
 * its version, before the file is renamed) until [gate] opens, and reports a read made during that pause.
 */
class GatedIntSerializer : Serializer<Int> {
    @Volatile
    var armed = false
    val writeEntered = CompletableDeferred<Unit>()
    val readWhileWriting = CompletableDeferred<Unit>()
    val gate = CompletableDeferred<Unit>()

    override val defaultValue: Int = 0

    override suspend fun readFrom(input: InputStream): Int {
        if (writeEntered.isCompleted) readWhileWriting.complete(Unit)
        return DataInputStream(input).readInt()
    }

    override suspend fun writeTo(t: Int, output: OutputStream) {
        if (armed) {
            writeEntered.complete(Unit)
            gate.await()
        }
        DataOutputStream(output).writeInt(t)
    }
}

package ch.jonathanlemaine.giftmanager.data.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import ch.jonathanlemaine.giftmanager.core.widget.WidgetSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** Dernier instantané du widget, partagé entre l'app et le widget (#80). */
interface WidgetSnapshotStore {
    /** Jamais écrit : un instantané « déconnecté ». */
    suspend fun read(): WidgetSnapshot

    suspend fun write(snapshot: WidgetSnapshot)
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal object WidgetSnapshotSerializer : Serializer<WidgetSnapshot> {
    override val defaultValue = WidgetSnapshot()

    override suspend fun readFrom(input: InputStream): WidgetSnapshot =
        try {
            json.decodeFromString(WidgetSnapshot.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            defaultValue
        } catch (e: IllegalArgumentException) {
            defaultValue
        }

    override suspend fun writeTo(t: WidgetSnapshot, output: OutputStream) {
        output.write(json.encodeToString(WidgetSnapshot.serializer(), t).encodeToByteArray())
    }
}

private val Context.widgetSnapshotDataStore: DataStore<WidgetSnapshot> by dataStore(
    fileName = "widget_snapshot",
    serializer = WidgetSnapshotSerializer,
)

class DataStoreWidgetSnapshotStore(context: Context) : WidgetSnapshotStore {
    private val dataStore = context.applicationContext.widgetSnapshotDataStore

    override suspend fun read(): WidgetSnapshot = dataStore.data.first()

    override suspend fun write(snapshot: WidgetSnapshot) {
        dataStore.updateData { snapshot }
    }
}

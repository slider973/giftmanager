package ch.jonathanlemaine.giftmanager.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Fichier DataStore unique de l'app (équivalent des `UserDefaults` iOS). */
private val Context.giftDataStore: DataStore<Preferences> by preferencesDataStore(name = "gift_prefs")

fun giftDataStore(context: Context): DataStore<Preferences> = context.applicationContext.giftDataStore

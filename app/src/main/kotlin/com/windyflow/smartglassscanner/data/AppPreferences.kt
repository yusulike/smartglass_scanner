package com.windyflow.smartglassscanner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Single shared Preferences DataStore instance for settings, whitelist, and custom Hex IDs. */
val Context.detectorDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "smartglass_scanner_prefs"
)

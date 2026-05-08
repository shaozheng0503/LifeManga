package com.lifemanga.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("app_settings")

data class AppSettings(
    val style: MangaStyle = MangaStyle.SHONEN_JUMP,
    val isColor: Boolean = true,
    val bubbleMode: BubbleMode = BubbleMode.CHINESE,
    val endpointType: EndpointType = EndpointType.OPENAI,
    val azureEndpoint: String = "",
    val azureDeployment: String = "",
    val azureApiVersion: String = "2024-02-01",
) {
    val endpointConfig: EndpointConfig
        get() = EndpointConfig(endpointType, azureEndpoint, azureDeployment, azureApiVersion)
}

class AppSettingsStore(private val context: Context) {

    private object Keys {
        val STYLE = stringPreferencesKey("style")
        val IS_COLOR = booleanPreferencesKey("is_color")
        val BUBBLE_MODE = stringPreferencesKey("bubble_mode")
        val ENDPOINT_TYPE = stringPreferencesKey("endpoint_type")
        val AZURE_ENDPOINT = stringPreferencesKey("azure_endpoint")
        val AZURE_DEPLOYMENT = stringPreferencesKey("azure_deployment")
        val AZURE_API_VERSION = stringPreferencesKey("azure_api_version")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            style = MangaStyle.fromKey(p[Keys.STYLE]) ?: MangaStyle.SHONEN_JUMP,
            isColor = p[Keys.IS_COLOR] ?: true,
            bubbleMode = BubbleMode.fromKey(p[Keys.BUBBLE_MODE]) ?: BubbleMode.CHINESE,
            endpointType = EndpointType.fromKey(p[Keys.ENDPOINT_TYPE]) ?: EndpointType.OPENAI,
            azureEndpoint = p[Keys.AZURE_ENDPOINT].orEmpty(),
            azureDeployment = p[Keys.AZURE_DEPLOYMENT].orEmpty(),
            azureApiVersion = p[Keys.AZURE_API_VERSION] ?: "2024-02-01",
        )
    }

    suspend fun setStyle(style: MangaStyle) {
        context.dataStore.edit { it[Keys.STYLE] = style.key }
    }

    suspend fun setIsColor(value: Boolean) {
        context.dataStore.edit { it[Keys.IS_COLOR] = value }
    }

    suspend fun setBubbleMode(value: BubbleMode) {
        context.dataStore.edit { it[Keys.BUBBLE_MODE] = value.key }
    }

    suspend fun setEndpointType(value: EndpointType) {
        context.dataStore.edit { it[Keys.ENDPOINT_TYPE] = value.key }
    }

    suspend fun setAzureEndpoint(value: String) {
        context.dataStore.edit { it[Keys.AZURE_ENDPOINT] = value.trim() }
    }

    suspend fun setAzureDeployment(value: String) {
        context.dataStore.edit { it[Keys.AZURE_DEPLOYMENT] = value.trim() }
    }

    suspend fun setAzureApiVersion(value: String) {
        context.dataStore.edit { it[Keys.AZURE_API_VERSION] = value.trim() }
    }
}

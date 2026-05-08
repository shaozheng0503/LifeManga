package com.lifemanga.android.data

enum class EndpointType(val key: String, val displayName: String) {
    OPENAI("openai", "OpenAI 直连"),
    AZURE("azure", "Azure OpenAI");

    companion object {
        fun fromKey(key: String?): EndpointType? = entries.firstOrNull { it.key == key }
    }
}

data class EndpointConfig(
    val type: EndpointType,
    val azureEndpoint: String,
    val azureDeployment: String,
    val azureApiVersion: String,
) {
    val isAzure: Boolean get() = type == EndpointType.AZURE
    val isAzureReady: Boolean get() = isAzure &&
        azureEndpoint.isNotBlank() && azureDeployment.isNotBlank() && azureApiVersion.isNotBlank()
}

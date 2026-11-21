package org.easypaper.ai

import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorAPI
import ai.koog.prompt.executor.ollama.client.OllamaClient

/**
 * Cloud executor (Koog + OpenRouter). Only the Ktor server creates it, with the key from its own
 * environment; clients never get the key (task 8).
 */
fun cloudExecutor(apiKey: String): PromptExecutorAPI {
    require(apiKey.isNotBlank()) { "OpenRouter key is missing (set OPENROUTER_API_KEY on the server)" }
    return MultiLLMPromptExecutor(OpenRouterLLMClient(apiKey))
}

/** Clinic module executor (Koog + local Ollama): documents and photos never leave the clinic, cloud cost 0. */
fun clinicExecutor(baseUrl: String = OllamaClient.DEFAULT_BASE_URL): PromptExecutorAPI =
    MultiLLMPromptExecutor(OllamaClient(baseUrl))

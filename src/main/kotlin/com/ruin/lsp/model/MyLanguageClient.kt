package com.ruin.lsp.model

import com.ruin.lsp.values.DocumentUri
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture

interface MyLanguageClient : LanguageClient {
    @JsonRequest("idea/temporaryDirectory")
    fun temporaryDirectory(): CompletableFuture<TemporaryDirectoryResult>

    @JsonNotification("idea/indexStarted")
    fun notifyIndexStarted()

    @JsonNotification("idea/indexFinished")
    fun notifyIndexFinished()
}

data class TemporaryDirectoryResult(val directory: DocumentUri)

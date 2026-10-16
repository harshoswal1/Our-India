package com.ourindia.app.ui.lawyer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ourindia.app.data.lawyer.ChatMessage
import com.ourindia.app.data.lawyer.DocumentType
import com.ourindia.app.data.lawyer.IpcBnsItem
import com.ourindia.app.data.lawyer.LawyerClient
import com.ourindia.app.data.lawyer.LegalKnowledge
import com.ourindia.app.data.lawyer.tidyLegalText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LawyerTab(val label: String) { ASK("Ask"), DRAFT("Draft"), SECTIONS("Sections"), RIGHTS("Rights") }

data class DraftForm(
    val type: DocumentType = DocumentType.LEGAL_NOTICE,
    val senderName: String = "",
    val senderAddress: String = "",
    val recipientName: String = "",
    val recipientAddress: String = "",
    val facts: String = "",
    val amount: String = ""
) {
    val missing: List<String>
        get() = buildList {
            if (senderName.isBlank()) add("your name")
            if (recipientName.isBlank()) add("who it's addressed to")
            if (facts.isBlank()) add("what happened")
        }
}

data class LawyerUiState(
    val tab: LawyerTab = LawyerTab.ASK,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val asking: Boolean = false,
    val askError: String? = null,
    val draft: DraftForm = DraftForm(),
    val drafting: Boolean = false,
    val draftResult: String? = null,
    val draftError: String? = null,
    val sectionQuery: String = "",
    val sections: List<IpcBnsItem> = LegalKnowledge.ipcBnsList
)

class LawyerViewModel : ViewModel() {
    private val _state = MutableStateFlow(LawyerUiState())
    val state = _state.asStateFlow()

    fun selectTab(tab: LawyerTab) = _state.update { it.copy(tab = tab) }
    fun setInput(s: String) = _state.update { it.copy(input = s) }

    fun ask(text: String = _state.value.input) {
        val q = text.trim()
        if (q.isEmpty() || _state.value.asking) return
        val history = _state.value.messages
        _state.update { it.copy(tab = LawyerTab.ASK, messages = history + ChatMessage("user", q), input = "", asking = true, askError = null) }
        viewModelScope.launch {
            LawyerClient.ask(q, history)
                .onSuccess { a -> _state.update { it.copy(asking = false, messages = it.messages + ChatMessage("assistant", tidyLegalText(a))) } }
                .onFailure { e -> _state.update { it.copy(asking = false, askError = e.message) } }
        }
    }

    /** Re-sends the last question after an error, without duplicating it in the thread. */
    fun retry() {
        val s = _state.value
        val last = s.messages.lastOrNull()?.takeIf { it.role == "user" } ?: return
        _state.update { it.copy(messages = it.messages.dropLast(1)) }
        ask(last.content)
    }

    fun newConversation() = _state.update { it.copy(messages = emptyList(), askError = null, input = "") }

    fun updateDraft(f: (DraftForm) -> DraftForm) = _state.update { it.copy(draft = f(it.draft), draftError = null) }

    fun generateDraft() {
        val d = _state.value.draft
        if (d.missing.isNotEmpty() || _state.value.drafting) return
        _state.update { it.copy(drafting = true, draftError = null, draftResult = null) }
        val prompt = """
Draft a ${d.type.title} (${d.type.description}).

From: ${d.senderName}, ${d.senderAddress.ifBlank { "[sender's address]" }}
To: ${d.recipientName}, ${d.recipientAddress.ifBlank { "[recipient's address]" }}
Amount involved: ${d.amount.ifBlank { "not applicable" }.let { if (it.first().isDigit()) "₹ $it" else it }}

Facts:
${d.facts}

Include a heading, date and place placeholders, the mode of sending (Registered Post A.D. where usual) and a subject line.
Number the paragraphs: the parties, the facts in order, the law breached (cite BNS/BNSS sections with the old IPC/CrPC numbers, or the NI Act, Consumer Protection Act, RTI Act as relevant), and the demand with the time allowed.
Leave [square-bracket] placeholders for anything not given. End with a signature block.
"""
        viewModelScope.launch {
            LawyerClient.ask(prompt, system = LawyerClient.DRAFTER_PROMPT)
                .onSuccess { r -> _state.update { it.copy(drafting = false, draftResult = tidyLegalText(r).replace("**", "")) } }
                .onFailure { e -> _state.update { it.copy(drafting = false, draftError = e.message) } }
        }
    }

    fun searchSections(q: String) {
        val t = q.trim()
        val hits = if (t.isEmpty()) LegalKnowledge.ipcBnsList else LegalKnowledge.ipcBnsList.filter {
            listOf(it.ipcSection, it.bnsSection, it.offence, it.punishment, it.keyElements).any { f -> f.contains(t, ignoreCase = true) }
        }
        _state.update { it.copy(sectionQuery = q, sections = hits) }
    }
}

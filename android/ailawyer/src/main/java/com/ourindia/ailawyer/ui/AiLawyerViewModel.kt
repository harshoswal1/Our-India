package com.ourindia.ailawyer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ourindia.ailawyer.data.ChatMessage
import com.ourindia.ailawyer.data.DocumentType
import com.ourindia.ailawyer.data.GroqApiClient
import com.ourindia.ailawyer.data.IpcBnsItem
import com.ourindia.ailawyer.data.LegalKnowledge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiLawyerUiState(
    val activeTab: Int = 0, // 0: Chat, 1: Drafter, 2: IPC->BNS, 3: Rights & SOS

    // Chat
    val chatMessages: List<ChatMessage> = listOf(
        ChatMessage(
            role = "assistant",
            content = "Namaste! I am your AI Lawyer (न्याय साथी). I can help you understand your legal rights under Indian Law, explain sections of the new criminal codes (BNS, BNSS, BSA), assist with consumer, property, and workplace disputes, and draft formal legal notices. How can I assist you today?"
        )
    ),
    val chatInput: String = "",
    val isChatLoading: Boolean = false,
    val chatError: String? = null,

    // Document Drafting
    val selectedDocType: DocumentType = DocumentType.LEGAL_NOTICE,
    val senderName: String = "",
    val senderAddress: String = "",
    val recipientName: String = "",
    val recipientAddress: String = "",
    val incidentDetails: String = "",
    val claimAmount: String = "",
    val generatedDraft: String? = null,
    val isDrafting: Boolean = false,
    val draftError: String? = null,

    // IPC to BNS
    val ipcSearchQuery: String = "",
    val ipcBnsResults: List<IpcBnsItem> = LegalKnowledge.ipcBnsList,

    // Toast/Snackbar message
    val userNotice: String? = null
)

class AiLawyerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AiLawyerUiState())
    val uiState: StateFlow<AiLawyerUiState> = _uiState.asStateFlow()

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(activeTab = tabIndex) }
    }

    fun updateChatInput(input: String) {
        _uiState.update { it.copy(chatInput = input) }
    }

    fun sendChatMessage(customQuery: String? = null) {
        val query = (customQuery ?: _uiState.value.chatInput).trim()
        if (query.isBlank() || _uiState.value.isChatLoading) return

        val userMsg = ChatMessage(role = "user", content = query)
        val updatedHistory = _uiState.value.chatMessages + userMsg

        _uiState.update {
            it.copy(
                chatMessages = updatedHistory,
                chatInput = "",
                isChatLoading = true,
                chatError = null
            )
        }

        viewModelScope.launch {
            val result = GroqApiClient.sendQuery(
                userQuery = query,
                conversationHistory = updatedHistory
            )

            result.onSuccess { responseText ->
                _uiState.update {
                    it.copy(
                        chatMessages = it.chatMessages + ChatMessage(role = "assistant", content = responseText),
                        isChatLoading = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isChatLoading = false,
                        chatError = "Error communicating with AI legal engine: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    // Document Drafter Methods
    fun setDocumentType(type: DocumentType) {
        _uiState.update { it.copy(selectedDocType = type) }
    }

    fun updateDrafterField(
        senderName: String? = null,
        senderAddress: String? = null,
        recipientName: String? = null,
        recipientAddress: String? = null,
        incidentDetails: String? = null,
        claimAmount: String? = null
    ) {
        _uiState.update {
            it.copy(
                senderName = senderName ?: it.senderName,
                senderAddress = senderAddress ?: it.senderAddress,
                recipientName = recipientName ?: it.recipientName,
                recipientAddress = recipientAddress ?: it.recipientAddress,
                incidentDetails = incidentDetails ?: it.incidentDetails,
                claimAmount = claimAmount ?: it.claimAmount
            )
        }
    }

    fun generateLegalDocument() {
        val state = _uiState.value
        if (state.senderName.isBlank() || state.recipientName.isBlank() || state.incidentDetails.isBlank()) {
            _uiState.update { it.copy(draftError = "Please fill in Sender Name, Recipient Name, and Facts.") }
            return
        }

        _uiState.update { it.copy(isDrafting = true, draftError = null, generatedDraft = null) }

        val draftingPrompt = """
Draft a formal, professional Indian Legal Document with standard Indian court and advocate drafting conventions.

DOCUMENT TYPE: ${state.selectedDocType.title} (${state.selectedDocType.description})
SENDER (Client / Complainant / Applicant):
Name: ${state.senderName}
Address: ${state.senderAddress.ifBlank { "[Address of Sender]" }}

RECIPIENT (Opposite Party / Respondent / Station House Officer / PIO):
Name: ${state.recipientName}
Address: ${state.recipientAddress.ifBlank { "[Address of Recipient]" }}

CLAIM / DISPUTE AMOUNT (if applicable): ${if (state.claimAmount.isNotBlank()) "₹ " + state.claimAmount else "N/A"}

FACTS AND CIRCUMSTANCES OF THE MATTER:
${state.incidentDetails}

INSTRUCTIONS:
1. Include proper legal heading, Registered Post with A.D. indicator, Subject Line.
2. Structure into numbered paragraphs:
   - Para 1: Description of parties.
   - Para 2-4: Chronological facts and breach/grievance.
   - Para 5: Specific Indian law provisions breached (cite BNS/IPC, BNSS, Consumer Protection Act, NI Act 138, etc.).
   - Final Para: Requisite relief or demand (e.g. 15-day statutory compliance period) and cost warning.
3. Keep the language formal, precise, and court-ready.
"""

        viewModelScope.launch {
            val result = GroqApiClient.sendQuery(
                userQuery = draftingPrompt,
                customSystemPrompt = "You are an expert Advocate & Legal Drafter practicing in the High Courts of India and the Supreme Court of India. Draft comprehensive, authoritative, and professionally formatted legal notices and petitions."
            )

            result.onSuccess { draft ->
                _uiState.update { it.copy(generatedDraft = draft, isDrafting = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isDrafting = false, draftError = "Failed to draft document: ${err.localizedMessage}") }
            }
        }
    }

    // IPC Search
    fun searchIpcBns(query: String) {
        val q = query.trim().lowercase()
        val results = if (q.isBlank()) {
            LegalKnowledge.ipcBnsList
        } else {
            LegalKnowledge.ipcBnsList.filter {
                it.ipcSection.lowercase().contains(q) ||
                it.bnsSection.lowercase().contains(q) ||
                it.offence.lowercase().contains(q) ||
                it.punishment.lowercase().contains(q) ||
                it.keyElements.lowercase().contains(q)
            }
        }
        _uiState.update { it.copy(ipcSearchQuery = query, ipcBnsResults = results) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(userNotice = null) }
    }
}

# AI Lawyer India (न्याय साथी / NyayaAI)

AI Lawyer India is an intelligent legal assistant tailored for Indian Jurisprudence, empowering Indian citizens with accessible legal literacy, automated legal document drafting, and rapid navigation across the newly enacted criminal laws of India:
- **Bharatiya Nyaya Sanhita (BNS) 2023** (replaced Indian Penal Code 1860)
- **Bharatiya Nagarik Suraksha Sanhita (BNSS) 2023** (replaced Code of Criminal Procedure 1973)
- **Bharatiya Sakshya Adhiniyam (BSA) 2023** (replaced Indian Evidence Act 1872)
- **The Constitution of India** (Fundamental Rights, Writs under Art 32 & 226)
- **Consumer Protection Act 2019**, **Negotiable Instruments Act (Sec 138)**, **Motor Vehicles Act 2019**, **RTI Act 2005**, **Domestic Violence Act 2005**, and **Tenancy Regulations**.

---

## Key Modules

### 1. Real-time Legal Consultation (Groq Powered)
- Powered by Groq's ultra-low-latency legal AI inference engine (Configured via `local.properties` / `BuildConfig.GROQ_API_KEY`).
- Specialized legal system prompt instructing the model to provide actionable legal advice, cite specific Indian legal sections, mention limitation periods, specify court jurisdictions, and clarify procedural steps.

### 2. Legal Document Studio
Generate legally formatted drafts in seconds:
- **Legal Notice** (General, Breach of Contract, Defamation, Property Dispute)
- **Cheque Bounce Notice** (Section 138 of Negotiable Instruments Act with 15-day statutory payment demand)
- **Police Complaint / FIR Draft** (Formal complaint to Station House Officer under Section 173 BNSS)
- **RTI Application** (Right to Information Act 2005 Section 6(1) with Public Information Officer addressing)
- **Consumer Forum Complaint** (Consumer Protection Act 2019 for deficient service or unfair trade practice)

### 3. IPC to BNS Converter
Search and convert legacy Indian Penal Code (IPC) sections to the newly active Bharatiya Nyaya Sanhita (BNS) 2023:
- Old IPC 302 (Murder) -> New BNS 103(1)
- Old IPC 420 (Cheating) -> New BNS 318(4)
- Old IPC 378/379 (Theft) -> New BNS 303(2)
- Old IPC 498A (Cruelty by Husband/Relatives) -> New BNS 85 / 86
- Old IPC 304A (Death by Negligence) -> New BNS 106(1)
- Old IPC 500 (Defamation) -> New BNS 356

### 4. Know Your Rights (नागरिक अधिकार) & Emergency Legal SOS
- **Arrest Guidelines**: Supreme Court D.K. Basu guidelines codified under Section 41 BNSS (Right to inform family, right to free legal counsel under Art 39A, mandatory medical check-up).
- **Zero FIR Right**: Any police station must register an FIR irrespective of territorial jurisdiction.
- **Traffic Challan Rights**: Mandatory presence of Sub-Inspector for confiscating driving license; e-challan dispute rules.
- **Direct Emergency SOS Lines**:
  - **112**: All-in-One Emergency National Helpline
  - **1930**: National Cyber Crime Reporting Portal
  - **15100**: NALSA (National Legal Services Authority) Free Legal Aid
  - **1091 / 181**: Women Helpline
  - **1915**: National Consumer Helpline

---

## In the Our India app
The AI Lawyer is part of the main app (the standalone `:ailawyer` module was merged in on 2026-10-17; it is in git history before that). Open it from the violet scales button on any tab.

- Code: `android/app/src/main/java/com/ourindia/app/data/lawyer/` (Groq client, legal data) and `.../ui/lawyer/` (screens).
- Key: put `GROQ_API_KEY=...` in `android/local.properties`. Without it, Ask and Draft explain that AI isn't set up; Sections (IPC → BNS) and Rights work offline.
- Build: `./gradlew :app:assembleDebug`.

**Before a public release:** the key is compiled into the APK and can be extracted. Route requests through a small server that holds the key.

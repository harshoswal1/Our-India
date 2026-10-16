package com.ourindia.app.data.lawyer

data class IpcBnsItem(
    val ipcSection: String,
    val bnsSection: String,
    val offence: String,
    val punishment: String,
    val bailable: String,
    val triableBy: String,
    val keyElements: String
)

data class CitizenRight(
    val title: String,
    val category: String,
    val legalProvision: String,
    val summary: String,
    val practicalTip: String
)

data class EmergencyContact(
    val title: String,
    val number: String,
    val description: String,
    val authority: String
)

enum class DocumentType(val title: String, val description: String) {
    LEGAL_NOTICE(
        "Legal notice",
        "Demand payment, compliance or settlement before going to court."
    ),
    CHEQUE_BOUNCE(
        "Cheque bounce notice",
        "Section 138, Negotiable Instruments Act: demand payment within 15 days of the notice."
    ),
    POLICE_COMPLAINT(
        "Police complaint",
        "Written complaint to the Station House Officer, for an FIR under Section 173 BNSS."
    ),
    RTI_APPLICATION(
        "RTI application",
        "Request to a Public Information Officer under Section 6(1), RTI Act 2005."
    ),
    CONSUMER_COMPLAINT(
        "Consumer complaint",
        "Complaint under the Consumer Protection Act 2019 for a defective product or service."
    )
}

object LegalKnowledge {

    val ipcBnsList = listOf(
        IpcBnsItem(
            ipcSection = "302",
            bnsSection = "103(1)",
            offence = "Murder",
            punishment = "Death or life imprisonment, and fine",
            bailable = "Non-bailable",
            triableBy = "Court of Session",
            keyElements = "Intentional causing of death or bodily injury sufficient in ordinary course to cause death."
        ),
        IpcBnsItem(
            ipcSection = "304A",
            bnsSection = "106(1)",
            offence = "Death by Negligence (Rash Driving / Industrial)",
            punishment = "Imprisonment up to 5 years, and fine",
            bailable = "Bailable",
            triableBy = "Magistrate First Class",
            keyElements = "Causing death by rash or negligent act not amounting to culpable homicide."
        ),
        IpcBnsItem(
            ipcSection = "307",
            bnsSection = "109",
            offence = "Attempt to Murder",
            punishment = "Imprisonment up to 10 years and fine; if hurt caused, life imprisonment",
            bailable = "Non-bailable",
            triableBy = "Court of Session",
            keyElements = "Act done with intention or knowledge that it would cause death if completed."
        ),
        IpcBnsItem(
            ipcSection = "378 / 379",
            bnsSection = "303(2)",
            offence = "Theft",
            punishment = "Imprisonment up to 3 years, or fine, or both (Community service if stolen value < ₹5,000)",
            bailable = "Non-bailable",
            triableBy = "Any Magistrate",
            keyElements = "Dishonest moving of movable property out of possession without consent."
        ),
        IpcBnsItem(
            ipcSection = "420",
            bnsSection = "318(4)",
            offence = "Cheating & Dishonest Inducement of Property",
            punishment = "Imprisonment up to 7 years, and fine",
            bailable = "Non-bailable",
            triableBy = "Magistrate First Class",
            keyElements = "Deception leading to delivery of property or alteration of valuable security."
        ),
        IpcBnsItem(
            ipcSection = "406",
            bnsSection = "316(2)",
            offence = "Criminal Breach of Trust",
            punishment = "Imprisonment up to 5 years, or fine, or both",
            bailable = "Non-bailable",
            triableBy = "Magistrate First Class",
            keyElements = "Dishonest misappropriation or conversion of entrusted property."
        ),
        IpcBnsItem(
            ipcSection = "498A",
            bnsSection = "85 / 86",
            offence = "Cruelty by Husband or In-laws (Domestic / Dowry)",
            punishment = "Imprisonment up to 3 years, and fine",
            bailable = "Non-bailable",
            triableBy = "Magistrate First Class",
            keyElements = "Willful conduct causing grave injury, mental harassment, or unlawful demand for property."
        ),
        IpcBnsItem(
            ipcSection = "500",
            bnsSection = "356(2)",
            offence = "Defamation",
            punishment = "Simple imprisonment up to 2 years, or fine, or both, or community service",
            bailable = "Bailable",
            triableBy = "Court of Session / Magistrate",
            keyElements = "Making or publishing imputation intending to harm the reputation of a person."
        ),
        IpcBnsItem(
            ipcSection = "354",
            bnsSection = "74",
            offence = "Assault / Force on Woman with intent to Outrage Modesty",
            punishment = "Imprisonment 1 to 5 years, and fine",
            bailable = "Non-bailable",
            triableBy = "Any Magistrate",
            keyElements = "Assaulting or using criminal force knowing it will outrage her modesty."
        ),
        IpcBnsItem(
            ipcSection = "506",
            bnsSection = "351(2)",
            offence = "Criminal Intimidation",
            punishment = "Imprisonment up to 2 years, or fine, or both",
            bailable = "Bailable",
            triableBy = "Any Magistrate",
            keyElements = "Threatening another person with injury to person, reputation, or property."
        ),
        IpcBnsItem(
            ipcSection = "323",
            bnsSection = "115(2)",
            offence = "Voluntarily Causing Hurt",
            punishment = "Imprisonment up to 1 year, or fine up to ₹10,000, or both",
            bailable = "Bailable",
            triableBy = "Any Magistrate",
            keyElements = "Doing an act with the intention of causing bodily pain, disease, or infirmity."
        ),
        IpcBnsItem(
            ipcSection = "325",
            bnsSection = "117(2)",
            offence = "Voluntarily Causing Grievous Hurt",
            punishment = "Imprisonment up to 7 years, and fine",
            bailable = "Bailable",
            triableBy = "Any Magistrate",
            keyElements = "Fracture, dislocation of bone/tooth, permanent privation of sight/hearing, etc."
        ),
        IpcBnsItem(
            ipcSection = "392",
            bnsSection = "309(4)",
            offence = "Robbery",
            punishment = "Rigorous imprisonment up to 10 years, and fine",
            bailable = "Non-bailable",
            triableBy = "Magistrate First Class",
            keyElements = "Theft or extortion with wrongful restraint or causing fear of death/hurt."
        ),
        IpcBnsItem(
            ipcSection = "120B",
            bnsSection = "61(2)",
            offence = "Criminal Conspiracy",
            punishment = "Punished in same manner as abetment of the committed offence",
            bailable = "Depends on substantive offence",
            triableBy = "Court by which offence is triable",
            keyElements = "Agreement between two or more persons to do an illegal act."
        )
    )

    val citizenRights = listOf(
        CitizenRight(
            title = "Rights Upon Arrest (गिरफ्तारी के अधिकार)",
            category = "Criminal Law",
            // CrPC 41-41D became BNSS 35-38; "41 BNSS" in the first draft was the old CrPC number.
            legalProvision = "Sections 35–38 BNSS, Article 22 of the Constitution, D.K. Basu guidelines",
            summary = "The police officer must show clear identification, state the exact grounds of arrest, and prepare an Arrest Memo signed by a witness.",
            practicalTip = "You have the fundamental right to inform a family member or advocate immediately, and right to free medical examination every 48 hours."
        ),
        CitizenRight(
            title = "Zero FIR Right (ज़ीरो एफआईआर)",
            category = "Police Procedures",
            legalProvision = "Section 173(1) BNSS / Supreme Court Directions",
            summary = "A police station cannot refuse to register an FIR on the grounds of lack of territorial jurisdiction. They must register a 'Zero FIR' and transfer it.",
            practicalTip = "If an SHO refuses to register an FIR, you can send the complaint in writing to the Superintendent of Police (SP) under Sec 173(4) BNSS."
        ),
        CitizenRight(
            title = "Traffic Police & Vehicle Search Rules",
            category = "Motor Vehicles",
            legalProvision = "Motor Vehicles Act 1988 (Sections 130, 206) & Central Motor Vehicles Rules, Rule 139",
            summary = "You must show your licence, RC, insurance and PUC on demand. Who may seize a licence or impound a vehicle is set by state notification; in many states it is a Sub-Inspector or above, not a constable.",
            practicalTip = "Digital copies of your licence and RC in DigiLocker or mParivahan are accepted as valid documents under Rule 139."
        ),
        CitizenRight(
            title = "Special Protections for Women in Custody",
            category = "Women's Rights",
            legalProvision = "Sections 43(5) and 49(2) BNSS",
            summary = "No woman can be arrested after sunset and before sunrise except in exceptional circumstances with prior permission of a Judicial Magistrate.",
            practicalTip = "Search of a woman can only be made by another female officer with strict regard to decency."
        ),
        CitizenRight(
            title = "Tenant Eviction Protections",
            category = "Civil & Property",
            legalProvision = "State Rent Control Acts & Transfer of Property Act 1882",
            summary = "A landlord cannot forcibly throw out a tenant, disconnect water/electricity, or lock premises without a valid court eviction order.",
            practicalTip = "A written notice to quit comes first: usually 15 days for a monthly tenancy under Section 106 of the Transfer of Property Act, or as your rent agreement or state rent law says."
        ),
        CitizenRight(
            title = "Consumer Rights Against Defective Goods / Services",
            category = "Consumer Law",
            legalProvision = "Consumer Protection Act 2019",
            summary = "Right to replacement, refund, and compensation for damages caused by defective products or deficient services within 2 years of cause of action.",
            practicalTip = "You can file an e-daakhil complaint online at edaakhil.nic.in without physical court visits for claims up to ₹50 Lakhs at District Commission."
        )
    )

    val emergencyContacts = listOf(
        EmergencyContact(
            title = "All-in-One National Emergency",
            number = "112",
            description = "Single emergency number across India for Police, Fire, and Ambulance.",
            authority = "Ministry of Home Affairs"
        ),
        EmergencyContact(
            title = "National Cyber Crime Helpline",
            number = "1930",
            description = "Immediate financial fraud freeze helpline (Citizen Financial Cyber Fraud Reporting).",
            authority = "Indian Cybercrime Coordination Centre (I4C)"
        ),
        EmergencyContact(
            title = "NALSA Free Legal Aid Helpline",
            number = "15100",
            description = "Toll-free tele-law consultation and free advocate assignment for eligible citizens.",
            authority = "National Legal Services Authority"
        ),
        EmergencyContact(
            title = "Women Helpline",
            number = "1091",
            description = "24x7 police helpline for women facing distress, violence, or harassment. 181 is the national women's helpline for counselling and referral.",
            authority = "State police"
        ),
        EmergencyContact(
            title = "National Consumer Helpline",
            number = "1915",
            description = "Grievance registration against e-commerce sellers, banks, airlines, and service providers.",
            authority = "Department of Consumer Affairs"
        ),
        EmergencyContact(
            title = "Childline Emergency Support",
            number = "1098",
            description = "24-hour nationwide emergency phone service for children in need of aid and protection.",
            authority = "Ministry of Women & Child Development"
        )
    )
}

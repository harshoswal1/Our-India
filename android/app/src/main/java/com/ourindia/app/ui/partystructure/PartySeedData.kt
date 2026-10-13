package com.ourindia.app.ui.partystructure

import com.ourindia.app.data.local.entity.PartyNodeEntity

/**
 * Bundled party structures, shown until the ingestion pipeline syncs real records.
 *
 * Rule for every entry: a named holder must carry a published source, checked on
 * [SEED_DATE]. A post we know exists but could not confirm the holder of stays
 * "Not yet fetched" and renders as an open seat. The earlier seed invented ward and
 * booth office-bearers and kept holders who had since changed office or party;
 * none of that survives here.
 *
 * Bumping [SEED_DATE] replaces older seed rows on devices that already have them.
 */
object PartySeedData {
    const val SEED_DATE = "2026-10-13"
    private const val NYF = "Not yet fetched"

    fun nodesFor(party: String): List<PartyNodeEntity> = seeds[party]?.nodes.orEmpty()

    fun sourceFor(nodeId: String): VerifiedSource? = sources[nodeId]

    val parties: Set<String> get() = seeds.keys

    private class Seed(val nodes: List<PartyNodeEntity>, val sources: Map<String, VerifiedSource>)

    private class Builder(private val party: String) {
        val nodes = mutableListOf<PartyNodeEntity>()
        val sources = mutableMapOf<String, VerifiedSource>()

        /** A post whose holder is confirmed by [source]. */
        fun held(
            id: String, level: String, parent: String?, role: String, holder: String,
            source: VerifiedSource, state: String? = null, district: String? = null
        ): String {
            nodes += PartyNodeEntity(id, party, level, parent, role, holder, state, district, SEED_DATE)
            sources[id] = source
            return id
        }

        /** A post that exists in the party's organisation; holder not confirmed. */
        fun open(id: String, level: String, parent: String?, role: String, state: String? = null, district: String? = null): String {
            nodes += PartyNodeEntity(id, party, level, parent, role, NYF, state, district, SEED_DATE)
            return id
        }
    }

    private fun seed(party: String, block: Builder.() -> Unit): Pair<String, Seed> {
        val b = Builder(party).apply(block)
        return party to Seed(b.nodes, b.sources)
    }

    private fun src(title: String, authority: String, date: String, url: String) = VerifiedSource(title, authority, date, url)

    // ── Sources ──────────────────────────────────────────────────────
    private val bjpPresident = src("Nitin Nabin becomes National President of BJP", "All India Radio News", "2026-01-20",
        "https://www.newsonair.gov.in/nitin-nabin-becomes-national-president-of-bharatiya-janata-party")
    private val bjpTeam = src("BJP names new national team under Nitin Nabin", "Deccan Herald", "2026-08-17",
        "https://www.deccanherald.com/india/nitin-nabin-gets-new-team-bjp-gets-13-vice-presidents-including-vasundhara-raje-scindia-ram-madhav-piyush-goyal-becomes-national-treasurer-4113926")
    private val bjpSecretaries = src("BJP names new team; K Surendran, Anil Antony appointed national secretaries", "Onmanorama", "2026-08-17",
        "https://www.onmanorama.com/news/india/2026/08/17/bjp-announces-new-national-office-bearers-under-nitin-nabin-piyush-goyal-named-treasurer.html")
    private val bjpPathak = src("Former AAP leader Sandeep Pathak appointed BJP National Secretary", "Babushahi", "2026-08-17",
        "https://www.babushahi.com/view-news.php?id=225482")
    private val pmModi = src("BJP's 'one person, one post' rule and the Modi cabinet", "The Week", "2026-08-17",
        "https://www.theweek.in/news/india/2026/08/17/bjp-one-person-one-post-piyush-goyal-cabinet.html")
    private val bjpStatePresidents = src("State Presidents", "bjp.org (official)", "2026-10-13",
        "https://www.bjp.org/state-president")
    private val bjpKarnataka = src("State Office Bearers", "BJP Karnataka (official)", "2026-10-13",
        "https://karnataka.bjp.org/state-national-office-bearer")

    private val incPresident = src("List of presidents of the Indian National Congress", "Wikipedia", "2026-10-13",
        "https://en.wikipedia.org/wiki/List_of_presidents_of_the_Indian_National_Congress")
    private val incVenugopal = src("K.C. Venugopal, General Secretary (Organisation), and Jairam Ramesh address the media", "INC press release", "2026-10-13",
        "https://inc.in/media/press-releases/shri-k-c-venugopal-shri-jairam-ramesh-addressed-the-media-at-indira-bhawan-aicc-hdqrs")
    private val incTreasurer = src("Congress appoints Ajay Maken as AICC Treasurer", "The Hans India", "2023-10",
        "https://www.thehansindia.com/news/national/congress-has-appointed-ajay-maken-as-the-new-treasurer-of-aicc-826754")
    private val incLop = src("Rahul Gandhi appointed Leader of Opposition in Lok Sabha", "All India Radio News", "2024-06-25",
        "https://www.newsonair.gov.in/congress-mp-rahul-gandhi-appointed-leader-of-opposition-in-lok-sabha")
    private val incMaharashtra = src("Harshwardhan Sapkal — MPCC President since 13 Feb 2025", "Wikipedia", "2026-10-13",
        "https://en.wikipedia.org/wiki/Harshwardhan_Vasantrao_Sapkal")
    private val incDelhi = src("Delhi PCC revamped under Devender Yadav's leadership", "The Statesman", "2026-05",
        "https://www.thestatesman.com/cities/infused-with-new-energy-pcc-revamped-after-14-years-under-yadavs-leadership-1503594053.html")
    private val incKarnataka = src("B. K. Hariprasad — KPCC President since 3 Jun 2026", "Wikipedia", "2026-10-13",
        "https://en.wikipedia.org/wiki/B._K._Hariprasad")
    private val incUp = src("Ajay Rai replaced as UP Congress chief", "Social News XYZ (IANS)", "2026-10-03",
        "https://www.socialnews.xyz/2026/10/03/we-will-stand-firmly-strengthen-congress-in-up-ajay-rai-after-being-replaced-as-state-chief/")
    private val incMumbai = src("Varsha Gaikwad — Mumbai Regional Congress President since 9 Jun 2023", "Wikipedia", "2026-10-13",
        "https://en.wikipedia.org/wiki/Varsha_Gaikwad")

    private val aapConvenor = src("CPI(M)'s M A Baby meets AAP national convenor Arvind Kejriwal", "UNI", "2026-09-27",
        "https://www.uniindia.com/national/politics-cpim-aap/194922")
    private val aapStates = src("AAP expands national executive, adds 20 state conveners", "The Hans India", "2026-09",
        "https://www.thehansindia.com/news/cities/new-delhi/aap-expands-national-executive-adds-20-state-conveners-1120933")

    private val ncpPawar = src("NCP (SP) chief Sharad Pawar chairs review meeting to revive party", "Deccan Herald", "2026",
        "https://www.deccanherald.com/india/maharashtra/ncp-sp-chief-sharad-pawar-chairs-review-meeting-to-assess-political-situation-revive-party-4010003")
    private val ncpSule = src("Supriya Sule — National Working President, NCP (SP), since 1 Jun 2024", "Wikipedia", "2026-10-13",
        "https://en.wikipedia.org/wiki/Supriya_Sule")
    private val ncpShinde = src("Shashikant Shinde — NCP (SP) Maharashtra President since 15 Jul 2025", "Wikipedia", "2026-10-13",
        "https://en.wikipedia.org/wiki/Shashikant_Shinde")

    private val tmcMamata = src("Mamata Banerjee assumes charge as West Bengal TMC president", "The Sentinel", "2026",
        "https://www.sentinelassam.com/more-news/national-news/trinamool-congress-tmc-chief-mamata-banerjee-assumes-charge-as-west-bengal-tmc-president")
    private val tmcAbhishek = src("Abhishek Banerjee — National General Secretary and Lok Sabha leader since 18 Sep 2026", "Wikipedia", "2026-10-13",
        "https://en.wikipedia.org/wiki/Abhishek_Banerjee_(politician)")

    private val dmkStalin = src("M. K. Stalin — President, DMK", "Wikipedia", "2026-10-13", "https://en.wikipedia.org/wiki/M._K._Stalin")
    private val dmkDurai = src("Durai Murugan — General Secretary, DMK, since 9 Sep 2020", "Wikipedia", "2026-10-13", "https://en.wikipedia.org/wiki/Durai_Murugan")
    private val dmkBaalu = src("T. R. Baalu — Treasurer, DMK, since 9 Sep 2020", "Wikipedia", "2026-10-13", "https://en.wikipedia.org/wiki/T._R._Baalu")
    private val dmkUdhay = src("Udhayanidhi Stalin — Leader of the Opposition, Tamil Nadu Assembly, since 10 May 2026", "Wikipedia", "2026-10-13",
        "https://en.wikipedia.org/wiki/Udhayanidhi_Stalin")

    private val bsp = src("Akash Anand reappointed BSP national coordinator", "Punjab Kesari", "2026-10-05",
        "https://english.punjabkesari.com/india/akash-anand-reappointed-bsp-national-coordinator-mayawati-restores-organisational-responsibilities")
    private val cpim = src("M. A. Baby — CPI(M) General Secretary since 6 Apr 2025", "Wikipedia", "2026-10-13", "https://en.wikipedia.org/wiki/M._A._Baby")
    private val npp = src("Conrad Sangma re-elected NPP president for 2025–28", "The Sentinel", "2025-03",
        "https://www.sentinelassam.com/north-east-india-news/meghalaya-news/meghalaya-conrad-sangma-re-elected-as-npp-president-for-2025-28")
    private val ssubt = src("Uddhav Thackeray — President, Shiv Sena (UBT), since 11 Oct 2022", "Wikipedia", "2026-10-13", "https://en.wikipedia.org/wiki/Uddhav_Thackeray")
    private val shs = src("Eknath Shinde — Chief Leader of Shiv Sena since Feb 2023", "Wikipedia", "2026-10-13", "https://en.wikipedia.org/wiki/Eknath_Shinde")
    private val mns = src("MNS chief Raj Thackeray meets Deputy CM Eknath Shinde in Thane", "Lokmat Times", "2026-02",
        "https://www.lokmattimes.com/thane/mns-chief-raj-thackeray-meets-maharashtra-deputy-cm-eknath-shinde-at-his-residence-in-thane-a517/")
    private val aiadmk = src("Edappadi K Palaniswami — AIADMK General Secretary", "Oneindia", "2026",
        "https://www.oneindia.com/politicians/edappadi-k-palaniswami-992.html")
    private val tdp = src("Nara Lokesh elevated as TDP National Working President", "Outlook", "2026-04",
        "https://www.outlookindia.com/national/chandrababu-naidus-son-nara-lokesh-elevated-as-tdp-working-president")
    private val ysrcp = src("YSRCP president Jagan Mohan Reddy to visit jailed former minister", "The News Mill", "2026-07",
        "https://thenewsmill.com/2026/07/ysrcp-president-jagan-mohan-reddy-to-visit-jailed-former-minister-seediri-appalaraju/")
    private val jsp = src("Janasena Party — founded and led by Pawan Kalyan", "Wikipedia", "2026-10-13", "https://en.wikipedia.org/wiki/Janasena_Party")
    private val brs = src("K. T. Rama Rao — BRS Working President since 17 Dec 2018", "Wikipedia", "2026-10-13", "https://en.wikipedia.org/wiki/K._T._Rama_Rao")
    private val jdu = src("Nitish Kumar files for second term as JD(U) national president", "Patna Press", "2026-03",
        "https://patnapress.com/nitish-kumar-jdu-president-nomination-2026/")
    private val jduJha = src("JD(U) appoints Sanjay Jha as national executive president", "All India Radio News", "2024",
        "https://www.newsonair.gov.in/jdu-appoints-its-rs-mp-sanjay-jha-as-the-party-national-executive-president")
    private val rjdLalu = src("Lalu re-elected RJD president", "The Tribune", "2025-07",
        "https://www.tribuneindia.com/news/india/lalu-re-elected-rjd-president-at-78-says-survey-under-way-for-identifying-candidates-for-bihar-polls")
    private val rjdTejashwi = src("Tejashwi Yadav named RJD national working president", "India TV", "2026-01-25",
        "https://www.indiatvnews.com/bihar/news-tejashwi-yadav-named-rjd-national-working-president-amid-explosive-family-feud-with-sister-rohini-puppet-attack-party-meeting-patna-lalu-rabri-misa-2026-01-25-1027400")
    private val sp = src("Akhilesh Yadav to be Samajwadi Party's leader in Lok Sabha", "The Tribune", "2024-06",
        "https://www.tribuneindia.com/news/india/akhilesh-yadav-to-be-samajwadi-partys-leader-in-lok-sabha-628971")
    private val bjd = src("BJD president Naveen Patnaik leads Jana Sampark Padyatra", "Kalinga TV", "2026-10-09",
        "https://kalingatv.com/odisha/bjds-jana-sampark-padyatra-double-engine-government-has-tarnished-odishas-image-says-naveen-patnaik-watch")
    private val jmm = src("Hemant Soren elected JMM President, Shibu Soren Founder Patron", "ANI", "2025-04-15",
        "https://aninews.in/news/national/politics/hemant-soren-elected-as-new-president-of-jmm-shibu-soren-as-founder-patron20250415192448/")
    private val sad = src("Sukhbir Badal returns as Shiromani Akali Dal chief", "ThePrint", "2025-04",
        "https://theprint.in/politics/sukhbir-badal-returns-as-shiromani-akali-dal-chief-calls-party-the-real-waaris-of-punjab/2587727/")
    private val jds = src("H D Deve Gowda to continue as national president of JD(S)", "Deccan Herald", "2025-11",
        "https://www.deccanherald.com/india/karnataka/h-d-deve-gowda-to-continue-as-national-president-of-jds-3806503")
    private val jdsState = src("Deve Gowda, Kumaraswamy re-elected JD(S) national and Karnataka presidents", "NewKerala (IANS)", "2025-11",
        "https://www.newkerala.com/news/o/karnataka-deve-gowda-kumaraswamy-re-elected-jd-chief-state-president-465")
    private val jkncFarooq = src("Farooq Abdullah — President, J&K National Conference", "Wikipedia", "2026-10-13", "https://en.wikipedia.org/wiki/Farooq_Abdullah")
    private val jkncOmar = src("Omar Abdullah — Chief Minister of Jammu and Kashmir", "Wikipedia", "2026-10-13", "https://en.wikipedia.org/wiki/Omar_Abdullah")

    // ── Structures ───────────────────────────────────────────────────
    private val seeds: Map<String, Seed> = mapOf(
        seed("BJP") {
            val n = held("BJP_NAT_PRES", "NATIONAL", null, "National President", "Nitin Nabin", bjpPresident)
            held("BJP_NAT_PM", "NATIONAL", n, "Prime Minister & Parliamentary Leader", "Narendra Modi", pmModi)
            held("BJP_NAT_GS_ORG", "NATIONAL_GS_ORG", n, "National General Secretary (Organisation)", "B. L. Santhosh", bjpTeam)
            held("BJP_NAT_VP1", "NATIONAL_VP", n, "National Vice President", "Vasundhara Raje Scindia", bjpTeam)
            held("BJP_NAT_VP2", "NATIONAL_VP", n, "National Vice President", "Ram Madhav", bjpTeam)
            held("BJP_NAT_VP3", "NATIONAL_VP", n, "National Vice President", "Tirath Singh Rawat", bjpTeam)
            held("BJP_NAT_GS1", "NATIONAL_GS", n, "National General Secretary", "Vinod Tawde", bjpTeam)
            held("BJP_NAT_GS2", "NATIONAL_GS", n, "National General Secretary", "Smriti Irani", bjpTeam)
            held("BJP_NAT_SEC1", "NATIONAL_SECRETARY", n, "National Secretary", "Anil Antony", bjpSecretaries)
            held("BJP_NAT_SEC2", "NATIONAL_SECRETARY", n, "National Secretary", "Sandeep Pathak", bjpPathak)
            held("BJP_NAT_TREAS", "NATIONAL_TREASURER", n, "National Treasurer", "Piyush Goyal", bjpTeam)

            val mh = held("BJP_STATE_MH", "STATE", n, "State President (Maharashtra)", "Ravindra Chavan", bjpStatePresidents, "Maharashtra")
            open("BJP_STATE_MH_GS_ORG", "STATE_GS_ORG", mh, "State General Secretary (Organisation)", "Maharashtra")
            open("BJP_STATE_MH_TREAS", "STATE_TREASURER", mh, "State Treasurer", "Maharashtra")
            open("BJP_DIST_MUM", "DISTRICT", mh, "Mumbai President", "Maharashtra", "Mumbai")
            open("BJP_DIST_PUNE", "DISTRICT", mh, "Pune City President", "Maharashtra", "Pune")
            open("BJP_DIST_NAGPUR", "DISTRICT", mh, "Nagpur City President", "Maharashtra", "Nagpur")

            val dl = held("BJP_STATE_DL", "STATE", n, "State President (Delhi)", "Harsh Malhotra", bjpStatePresidents, "Delhi")
            open("BJP_STATE_DL_GS_ORG", "STATE_GS_ORG", dl, "State General Secretary (Organisation)", "Delhi")
            open("BJP_DIST_ND", "DISTRICT", dl, "New Delhi District President", "Delhi", "New Delhi")

            val ka = held("BJP_STATE_KA", "STATE", n, "State President (Karnataka)", "B. Y. Vijayendra", bjpKarnataka, "Karnataka")
            open("BJP_STATE_KA_GS_ORG", "STATE_GS_ORG", ka, "State General Secretary (Organisation)", "Karnataka")
            open("BJP_DIST_BLR", "DISTRICT", ka, "Bengaluru Urban District President", "Karnataka", "Bangalore")

            val up = held("BJP_STATE_UP", "STATE", n, "State President (Uttar Pradesh)", "Pankaj Chaudhary", bjpStatePresidents, "Uttar Pradesh")
            open("BJP_STATE_UP_GS_ORG", "STATE_GS_ORG", up, "State General Secretary (Organisation)", "Uttar Pradesh")
            open("BJP_DIST_LKO", "DISTRICT", up, "Lucknow District President", "Uttar Pradesh", "Lucknow")

            val gj = held("BJP_STATE_GJ", "STATE", n, "State President (Gujarat)", "Jagdish Vishwakarma", bjpStatePresidents, "Gujarat")
            open("BJP_STATE_GJ_GS_ORG", "STATE_GS_ORG", gj, "State General Secretary (Organisation)", "Gujarat")
            open("BJP_DIST_AHM", "DISTRICT", gj, "Ahmedabad City President", "Gujarat", "Ahmadabad")
        },

        seed("INC") {
            val n = held("INC_NAT_PRES", "NATIONAL", null, "Congress President", "Mallikarjun Kharge", incPresident)
            held("INC_NAT_LOP", "NATIONAL", n, "Leader of Opposition (Lok Sabha)", "Rahul Gandhi", incLop)
            held("INC_NAT_GS_ORG", "NATIONAL_GS_ORG", n, "General Secretary (Organisation)", "K. C. Venugopal", incVenugopal)
            held("INC_NAT_GS1", "NATIONAL_GS", n, "General Secretary (Communications)", "Jairam Ramesh", incVenugopal)
            held("INC_NAT_TREAS", "NATIONAL_TREASURER", n, "AICC Treasurer", "Ajay Maken", incTreasurer)

            val mh = held("INC_STATE_MH", "STATE", n, "PCC President (Maharashtra)", "Harshwardhan Sapkal", incMaharashtra, "Maharashtra")
            held("INC_DIST_MUM", "DISTRICT", mh, "Mumbai Regional Congress President", "Varsha Gaikwad", incMumbai, "Maharashtra", "Mumbai")
            open("INC_DIST_PUNE", "DISTRICT", mh, "Pune City Congress President", "Maharashtra", "Pune")

            val dl = held("INC_STATE_DL", "STATE", n, "PCC President (Delhi)", "Devender Yadav", incDelhi, "Delhi")
            open("INC_DIST_ND", "DISTRICT", dl, "New Delhi DCC President", "Delhi", "New Delhi")

            val ka = held("INC_STATE_KA", "STATE", n, "PCC President (Karnataka)", "B. K. Hariprasad", incKarnataka, "Karnataka")
            open("INC_DIST_BLR", "DISTRICT", ka, "Bengaluru DCC President", "Karnataka", "Bangalore")

            val up = held("INC_STATE_UP", "STATE", n, "PCC President (Uttar Pradesh)", "Aradhana Misra", incUp, "Uttar Pradesh")
            open("INC_DIST_LKO", "DISTRICT", up, "Lucknow DCC President", "Uttar Pradesh", "Lucknow")
        },

        seed("AAP") {
            val n = held("AAP_NAT_CONV", "NATIONAL", null, "National Convenor", "Arvind Kejriwal", aapConvenor)
            // Vacant on record since Sandeep Pathak left for the BJP in April 2026.
            open("AAP_NAT_GS_ORG", "NATIONAL_GS_ORG", n, "National General Secretary (Organisation)")
            open("AAP_NAT_TREAS", "NATIONAL_TREASURER", n, "National Treasurer")
            held("AAP_STATE_DL", "STATE", n, "State Convenor (Delhi)", "Saurabh Bharadwaj", aapStates, "Delhi")
            held("AAP_STATE_PB", "STATE", n, "State President (Punjab)", "Aman Arora", aapStates, "Punjab")
            open("AAP_STATE_MH", "STATE", n, "State Convenor (Maharashtra)", "Maharashtra")
        },

        seed("NCP(SP)") {
            val n = held("NCP_NAT_PRES", "NATIONAL", null, "National President", "Sharad Pawar", ncpPawar)
            held("NCP_NAT_WP", "NATIONAL_WORKING", n, "National Working President", "Supriya Sule", ncpSule)
            open("NCP_NAT_GS_ORG", "NATIONAL_GS_ORG", n, "General Secretary (Organisation)")
            open("NCP_NAT_TREAS", "NATIONAL_TREASURER", n, "National Treasurer")
            val mh = held("NCP_STATE_MH", "STATE", n, "State President (Maharashtra)", "Shashikant Shinde", ncpShinde, "Maharashtra")
            open("NCP_DIST_PUNE", "DISTRICT", mh, "Pune City President", "Maharashtra", "Pune")
            open("NCP_DIST_MUM", "DISTRICT", mh, "Mumbai President", "Maharashtra", "Mumbai")
            open("NCP_DIST_SATARA", "DISTRICT", mh, "Satara District President", "Maharashtra", "Satara")
        },

        seed("TMC") {
            val n = held("TMC_NAT_CHAIR", "NATIONAL", null, "Chairperson", "Mamata Banerjee", tmcMamata)
            held("TMC_NAT_GS", "NATIONAL_GS", n, "National General Secretary", "Abhishek Banerjee", tmcAbhishek)
            held("TMC_NAT_PARL", "NATIONAL", n, "Leader in Lok Sabha", "Abhishek Banerjee", tmcAbhishek)
            val wb = held("TMC_STATE_WB", "STATE", n, "State President (West Bengal)", "Mamata Banerjee", tmcMamata, "West Bengal")
            open("TMC_DIST_KOL", "DISTRICT", wb, "Kolkata District President", "West Bengal", "Kolkata")
            open("TMC_STATE_TR", "STATE", n, "State President (Tripura)", "Tripura")
        },

        seed("DMK") {
            // DMK's headquarters runs district secretaries directly; there is no state tier.
            val n = held("DMK_NAT_PRES", "NATIONAL", null, "Party President", "M. K. Stalin", dmkStalin)
            held("DMK_NAT_GS", "NATIONAL_GS", n, "General Secretary", "Durai Murugan", dmkDurai)
            held("DMK_NAT_TREAS", "NATIONAL_TREASURER", n, "Treasurer", "T. R. Baalu", dmkBaalu)
            held("DMK_NAT_LOP", "NATIONAL", n, "Leader of the Opposition (Tamil Nadu Assembly)", "Udhayanidhi Stalin", dmkUdhay)
            open("DMK_NAT_DGS", "NATIONAL_GS", n, "Deputy General Secretary")
            open("DMK_DIST_CHN", "DISTRICT", n, "District Secretary (Chennai)", "Tamil Nadu", "Chennai")
            open("DMK_DIST_CBE", "DISTRICT", n, "District Secretary (Coimbatore)", "Tamil Nadu", "Coimbatore")
            open("DMK_DIST_MDU", "DISTRICT", n, "District Secretary (Madurai)", "Tamil Nadu", "Madurai")
        },

        seed("BSP") {
            val n = held("BSP_NAT_PRES", "NATIONAL", null, "National President", "Mayawati", bsp)
            held("BSP_NAT_COORD", "NATIONAL_GS", n, "National Coordinator", "Akash Anand", bsp)
        },
        seed("CPI(M)") {
            held("CPIM_NAT_GS", "NATIONAL", null, "General Secretary", "M. A. Baby", cpim)
        },
        seed("NPP") {
            held("NPP_NAT_PRES", "NATIONAL", null, "National President", "Conrad Sangma", npp)
        },
        seed("SS(UBT)") {
            held("SSUBT_PRES", "NATIONAL", null, "Party President", "Uddhav Thackeray", ssubt)
        },
        seed("SHS") {
            held("SHS_CHIEF", "NATIONAL", null, "Chief Leader (Mukhya Neta)", "Eknath Shinde", shs)
        },
        seed("MNS") {
            held("MNS_PRES", "NATIONAL", null, "President", "Raj Thackeray", mns)
        },
        seed("AIADMK") {
            held("AIADMK_GS", "NATIONAL", null, "General Secretary", "Edappadi K. Palaniswami", aiadmk)
        },
        seed("TDP") {
            val n = held("TDP_NAT_PRES", "NATIONAL", null, "National President", "N. Chandrababu Naidu", tdp)
            held("TDP_NAT_WP", "NATIONAL_WORKING", n, "National Working President", "Nara Lokesh", tdp)
        },
        seed("YSRCP") {
            held("YSRCP_PRES", "NATIONAL", null, "President", "Y. S. Jagan Mohan Reddy", ysrcp)
        },
        seed("JSP") {
            held("JSP_PRES", "NATIONAL", null, "President", "Pawan Kalyan", jsp)
        },
        seed("BRS") {
            val n = held("BRS_PRES", "NATIONAL", null, "President", "K. Chandrashekar Rao", brs)
            held("BRS_WP", "NATIONAL_WORKING", n, "Working President", "K. T. Rama Rao", brs)
        },
        seed("JD(U)") {
            val n = held("JDU_NAT_PRES", "NATIONAL", null, "National President", "Nitish Kumar", jdu)
            held("JDU_NAT_WP", "NATIONAL_WORKING", n, "National Working President", "Sanjay Jha", jduJha)
        },
        seed("RJD") {
            val n = held("RJD_NAT_PRES", "NATIONAL", null, "National President", "Lalu Prasad Yadav", rjdLalu)
            held("RJD_NAT_WP", "NATIONAL_WORKING", n, "National Working President", "Tejashwi Yadav", rjdTejashwi)
        },
        seed("SP") {
            val n = held("SP_NAT_PRES", "NATIONAL", null, "National President", "Akhilesh Yadav", sp)
            held("SP_NAT_LS", "NATIONAL", n, "Leader in Lok Sabha", "Akhilesh Yadav", sp)
        },
        seed("BJD") {
            held("BJD_PRES", "NATIONAL", null, "President", "Naveen Patnaik", bjd)
        },
        seed("JMM") {
            held("JMM_PRES", "NATIONAL", null, "President", "Hemant Soren", jmm)
        },
        seed("SAD") {
            held("SAD_PRES", "NATIONAL", null, "President", "Sukhbir Singh Badal", sad)
        },
        seed("JD(S)") {
            val n = held("JDS_NAT_PRES", "NATIONAL", null, "National President", "H. D. Deve Gowda", jds)
            held("JDS_STATE_KA", "STATE", n, "State President (Karnataka)", "H. D. Kumaraswamy", jdsState, "Karnataka")
        },
        seed("JKNC") {
            val n = held("JKNC_PRES", "NATIONAL", null, "President", "Farooq Abdullah", jkncFarooq)
            held("JKNC_CM", "NATIONAL", n, "Chief Minister of Jammu & Kashmir", "Omar Abdullah", jkncOmar, "Jammu & Kashmir")
        }
    )

    private val sources: Map<String, VerifiedSource> = seeds.values.flatMap { it.sources.entries }.associate { it.key to it.value }
}

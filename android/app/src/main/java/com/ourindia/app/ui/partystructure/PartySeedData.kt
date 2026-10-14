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
    // Compared as a string against stored rows, so a same-day revision takes a suffix:
    // "2026-10-14.2" sorts after "2026-10-14" and before any later date.
    const val SEED_DATE = "2026-10-14.2"
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

        /** State-unit heads from one roster source. Rows: id suffix, state (boundary-file spelling), holder. */
        fun stateHeads(prefix: String, parent: String, role: String, source: VerifiedSource, vararg rows: Triple<String, String, String>) {
            rows.forEach { (code, state, holder) -> held("${prefix}_$code", "STATE", parent, "$role ($state)", holder, source, state) }
        }

        /**
         * District units from one roster. Rows: post title, holder, and the boundary-file
         * district the unit sits in (null when a unit spans several, e.g. Delhi's
         * assembly-based districts), which is what the map tints.
         */
        fun districtHeads(prefix: String, parent: String, state: String, source: VerifiedSource, vararg rows: Triple<String, String, String?>) {
            rows.forEach { (role, holder, mapDistrict) ->
                val id = prefix + "_" + role.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')
                held(id, "DISTRICT", parent, role, holder, source, state, mapDistrict)
            }
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

    // Added 2026-10-14
    private val bjpStateRoster = src("List of state presidents of the Bharatiya Janata Party", "Wikipedia", "2026-10-14",
        "https://en.wikipedia.org/wiki/List_of_state_presidents_of_the_Bharatiya_Janata_Party")
    private val bjpDelhi = src("Union Minister Harsh Malhotra appointed Delhi BJP president", "The Tribune", "2026-05-28",
        "https://www.tribuneindia.com/news/delhi/union-minister-harsh-malhotra-appointed-delhi-bjp-president/")
    private val bjpOrgSecretaries = src("Nitin Nabin appoints party's organisational general secretaries in states", "ThePrint (PTI)", "2026-09-24",
        "https://theprint.in/india/bjp-chief-nitin-nabin-appoints-partys-organisational-general-secretaries-in-states/3052298/")
    private val bjpMumbai = src("Who is Ameet Satam, the BJP Mumbai chief behind the BMC show", "The Week", "2026-01-16",
        "https://www.theweek.in/news/india/2026/01/16/who-is-ameet-satam-the-former-tata-group-employee-responsible-for-bjps-impressive-bmc-show.html")
    private val bjpNagpur = src("Dayashankar Tiwari - President, BJP Nagpur City, since 14 May 2025", "Wikipedia", "2026-10-14",
        "https://en.wikipedia.org/wiki/Dayashankar_Tiwari")
    private val bjpBengaluru = src("BJP Karnataka press events - C. K. Ramamurthy, Bengaluru South district president", "BJP Karnataka (official)", "2026-09-25",
        "https://karnataka.bjp.org/state-bjp-live")
    private val incStateRoster = src("List of state presidents of the Indian National Congress", "Wikipedia", "2026-10-14",
        "https://en.wikipedia.org/wiki/List_of_state_presidents_of_the_Indian_National_Congress")
    private val incPune = src("Congress names Prashant Jagtap and Deepti Chaudhari as Pune city presidents", "Punekar News", "2026-05",
        "https://www.punekarnews.in/congress-names-prashant-jagtap-and-deepti-chaudhari-as-pune-city-presidents-in-major-organisational-overhaul/")
    private val aapCouncil = src("AAP National Council: Kejriwal re-elected convenor; N. D. Gupta treasurer; Pankaj Gupta national secretary", "aamaadmiparty.org (official)", "2026-09-09",
        "https://aamaadmiparty.org/aap-news/aap-national-council-meeting-arvind-kejriwal-unanimously-re-elected-as-national-convenor/")
    private val ncpPune = src("NCP (SP) splits Pune unit, appoints two city presidents", "Pune Pulse", "2026-08-17",
        "https://www.mypunepulse.com/ncp-sharadchandra-pawar-splits-pune-unit-appoints-two-city-presidents/")
    private val dmkKanimozhi = src("Kanimozhi - Deputy General Secretary, DMK, since 9 Oct 2022", "Wikipedia", "2026-10-14",
        "https://en.wikipedia.org/wiki/Kanimozhi")
    private val cpimParty = src("Communist Party of India (Marxist) - Lok Sabha leader K. Radhakrishnan", "Wikipedia", "2026-10-14",
        "https://en.wikipedia.org/wiki/Communist_Party_of_India_(Marxist)")
    private val cpimBengal = src("Pratik Ur Rahaman resignation reviewed by CPI(M) West Bengal state secretary Md Salim", "Wikipedia", "2026-02",
        "https://en.wikipedia.org/wiki/Pratik_Ur_Rahaman")
    private val ssubtSawant = src("Arvind Sawant, Shiv Sena (UBT) leader in Lok Sabha, writes to Speaker", "ANI", "2026-06-24",
        "https://aninews.in/news/national/politics/urged-lok-sabha-speaker-not-to-make-any-decision-on-rebels-joining-another-party-without-hearing-us-shiv-sena-ubt-leader-arvind-sawant20260624235918/")
    private val ssubtRaut = src("Sanjay Raut - Shiv Sena (UBT) leader in Rajya Sabha", "Wikipedia", "2026-10-14", "https://en.wikipedia.org/wiki/Sanjay_Raut")
    private val ssubtAaditya = src("Aaditya Thackeray - President, Yuva Sena", "Wikipedia", "2026-10-14", "https://en.wikipedia.org/wiki/Aaditya_Thackeray")
    private val shsShrikant = src("Shiv Sena MP Shrikant Shinde to tour 22 Maharashtra districts", "ANI", "2026-05-11",
        "https://aninews.in/news/national/politics/shiv-sena-mp-shrikant-shinde-to-tour-22-maharashtra-districts-ahead-of-local-body-polls20260511084948/")
    private val mnsAmit = src("Amit Thackeray - President, Maharashtra Navnirman Vidyarthi Sena", "Wikipedia", "2026-10-14", "https://en.wikipedia.org/wiki/Amit_thackeray")
    private val aiadmkTreasurer = src("Dindigul C. Sreenivasan - AIADMK Treasurer since 11 Jul 2022", "Wikipedia", "2026-10-14",
        "https://en.wikipedia.org/wiki/Dindigul_C._Sreenivasan")
    private val aiadmkDeputies = src("K. P. Munusamy - AIADMK Deputy General Secretary since 11 Jul 2022, with Natham R. Viswanathan", "Wikipedia", "2026-10-14",
        "https://en.wikipedia.org/wiki/K._P._Munusamy")
    private val tdpState = src("Palla Srinivasa Rao - TDP Andhra Pradesh President since 14 Jun 2024", "Wikipedia", "2026-10-14",
        "https://en.wikipedia.org/wiki/Palla_Srinivasa_Rao")
    private val tdpParty = src("Telugu Desam Party - Lok Sabha leader Lavu Sri Krishna Devarayalu", "Wikipedia", "2026-10-14",
        "https://en.wikipedia.org/wiki/Telugu_Desam_Party")
    private val ysrcpMidhun = src("P V Midhun Reddy - Rajampet MP, YSRCP floor leader", "PRS Legislative Research", "2026-10-14",
        "https://prsindia.org/mptrack/18-lok-sabha/p-v-midhun-reddy")
    private val jspNadendla = src("Nadendla Manohar - Chairman, Janasena Political Affairs Committee, since 12 Oct 2018", "Wikipedia", "2026-10-14",
        "https://en.wikipedia.org/wiki/Nadendla_Manohar")
    private val brsKcr = src("List of leaders of the opposition in the Telangana Legislative Assembly", "Wikipedia", "2026-10-14",
        "https://en.wikipedia.org/wiki/List_of_leaders_of_the_opposition_in_the_Telangana_Legislative_Assembly")
    private val bjdLop = src("Odisha LoP and BJD president Naveen Patnaik accepts Sasmit Patra's resignation as RS leader", "Kalinga TV", "2026-04-09",
        "https://kalingatv.com/odisha/naveen-patnaik-accepts-sasmit-patras-resignation-as-bjd-rajya-sabha-leader/")
    private val jmmGs = src("JMM announces new central executive committee; Pandey and Bhattacharya re-appointed general secretaries", "Avenue Mail", "2025-04",
        "https://avenuemail.in/jmm-announces-new-central-executive-committee-with-63-members/")
    private val sadParty = src("Shiromani Akali Dal - Lok Sabha leader Harsimrat Kaur Badal", "Wikipedia", "2026-10-14",
        "https://en.wikipedia.org/wiki/Shiromani_Akali_Dal")
    private val nppMeghalaya = src("NPP state president Prestone Tynsong: Meghalaya government strong and stable", "Northeast News", "2026-09",
        "https://nenews.in/meghalaya/meghalaya-government-strong-and-stable-npp-has-33-mlas-prestone-tynsong/62943/")
    private val jkncSagar = src("NC General Secretary Ali Muhammad Sagar: propaganda to weaken NC nothing new", "Kashmir Reader", "2026-09-13",
        "https://kashmirreader.com/2026/09/13/propaganda-conspiracies-to-weaken-nc-nothing-new-sagar/")
    private val bspParty = src("Bahujan Samaj Party - Rajya Sabha leader Ramji Gautam", "Wikipedia", "2026-10-14",
        "https://en.wikipedia.org/wiki/Bahujan_Samaj_Party")
    private val spRamGopal = src("Ram Gopal Yadav - Secretary-General, Samajwadi Party", "Wikipedia", "2026-10-14", "https://en.wikipedia.org/wiki/Ram_Gopal_Yadav")
    private val spState = src("Samajwadi Party appoints Shyam Lal Pal as Uttar Pradesh president", "Outlook", "2024-05",
        "https://www.outlookindia.com/national/samajwadi-party-appoints-shyam-lal-pal-as-uttar-pradesh-president")
    private val rjdState = src("RJD state president Mangani Lal Mandal sets age rule for district presidents", "Deshaj Times", "2026-09",
        "https://deshajtimes.com/news/bihar/patna/bihar-rjd-district-president-age-limit-resignation-rule/312887/")
    private val rjdLop = src("Bihar Legislative Assembly - Leader of the Opposition Tejashwi Yadav since 17 Nov 2025", "Wikipedia", "2026-10-14",
        "https://en.wikipedia.org/wiki/Bihar_Legislative_Assembly")
    private val jduState = src("JD(U) unveils new Bihar state committee; Nitish Kumar elected national president in March", "Social News XYZ (IANS)", "2026-07-08",
        "https://www.socialnews.xyz/2026/07/08/jdu-unveils-new-bihar-state-committee-with-12-vice-presidents")

    // Added 2026-10-14 (district tier and remaining open posts)
    private val bjpMhOffice = src("With an eye on 2029 Assembly polls, Maharashtra BJP appoints new office-bearers", "The Statesman", "2026-02-24",
        "https://www.thestatesman.com/india/with-an-eye-on-2029-assembly-polls-maharashtra-bjp-appoints-new-office-bearers-1503561870.html")
    private val bjpMhDistricts = src("BJP appoints new district presidents in 58 Maharashtra districts", "Maharashtra Desha", "2025-05-14",
        "https://maharashtradesha.com/bjp-new-district-presidents-maharashtra-2025/")
    private val bjpMhDistricts2 = src("BJP finalises remaining 22 Maharashtra district presidents", "Free Press Journal", "2025-06-01",
        "https://www.freepressjournal.in/mumbai/maharashtra-news-bjp-finalises-remaining-22-district-presidents-ahead-of-local-body-polls")
    private val bjpUpDistricts = src("UP BJP Jila Adhyaksh list: district and metropolitan presidents", "India TV", "2025-03-16",
        "https://www.indiatvnews.com/uttar-pradesh/up-bjp-jila-adhyaksh-list-out-bjp-announces-district-presidents-names-in-uttar-pradesh-check-full-list-here-2025-03-16-980842")
    private val bjpLucknowCity = src("Lucknow Mahanagar BJP executive announced by city president Anand Dwivedi", "ETV Bharat", "2026-03",
        "https://www.etvbharat.com/hi/state/lucknow-bjp-mahanagar-executive-committee-announced-uttar-pradesh-news-ups26032204344")
    private val bjpGjDistricts = src("Gujarat BJP district and city presidents announced", "DeshGujarat", "2025-03-06",
        "https://deshgujarat.com/2025/03/06/gujarat-bjp-district-and-city-presidents-announced-here-is-the-list/")
    private val bjpAhmedabadCity = src("Jagdish Vishwakarma takes charge, with Ahmedabad city BJP president Prerak Shah", "Gujarat Samachar", "2025-10",
        "https://english.gujaratsamachar.com/news/gujarat/jagdish-vishwakarma-appointed-bjps-state-president-first-leader-from-ahmedabad-to-hold-the-post")
    private val bjpDlDistricts = src("Delhi BJP announces names of presidents for 14 district units", "The News Mill (ANI)", "2025-05-26",
        "https://thenewsmill.com/2025/05/delhi-bjp-announces-names-of-presidents-for-14-district-units/")
    private val incDlDistricts = src("Congress appoints 14 new district presidents in Delhi", "The Tribune", "2026-05-15",
        "https://www.tribuneindia.com/news/delhi/congress-appoints-14-new-district-presidents-in-delhi-under-organisational-revamp/")
    private val incKaDistricts = src("Karnataka Congress new district presidents: full list (AICC)", "Oneindia Kannada", "2026-09-10",
        "https://kannada.oneindia.com/news/karnataka/karnataka-congress-appoints-new-district-presidents-full-list-of-district-congress-committee-chief-467419.html")
    private val tmcNorthKolkata = src("Saugata Roy unveils TMC appointments; Kunal Ghosh takes over North Kolkata unit", "ANI", "2026-06-13",
        "https://aninews.in/news/national/general-news/saugata-roy-unveils-new-tmc-appointments-arnab-banerjee-for-youth-congress-president-kunal-ghosh-takes-over-north-kolkata-unit20260613233824/")
    private val cpimKerala = src("CPI(M) Kerala rejig: no change in state leadership, M V Govindan continues", "The Week", "2026-09-15",
        "https://www.theweek.in/news/india/2026/09/15/cpi-m-kerala-meeting-reforms.html")
    private val cpimTripura = src("Tripura CPI(M), led by state secretary Jitendra Chaudhury, prepares for ULB polls", "Borok Times", "2026-10",
        "https://boroktimes.com/tripura-cpim-begins-preparations-for-urban-local-body-elections/")
    private val bjdRs = src("BJD names Manas Ranjan Mangaraj its Rajya Sabha leader after Sasmit Patra's exit", "The Hindu (PressReader)", "2026-04-14",
        "https://www.pressreader.com/india/the-hindu-international-9bn2/20260414/281603837037423")

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
            held("BJP_STATE_MH_GS_ORG", "STATE_GS_ORG", mh, "State General Secretary (Organisation)", "Ajay Jamwal", bjpOrgSecretaries, "Maharashtra")
            held("BJP_STATE_MH_TREAS", "STATE_TREASURER", mh, "State Treasurer", "Mihir Kotecha", bjpMhOffice, "Maharashtra")
            held("BJP_DIST_MUM", "DISTRICT", mh, "Mumbai President", "Ameet Satam", bjpMumbai, "Maharashtra", "Mumbai")
            held("BJP_DIST_PUNE", "DISTRICT", mh, "Pune City President", "Dhiraj Ghate", bjpMhDistricts, "Maharashtra", "Pune")
            held("BJP_DIST_NAGPUR", "DISTRICT", mh, "Nagpur City President", "Dayashankar Tiwari", bjpNagpur, "Maharashtra", "Nagpur")
            districtHeads(
                "BJP_DIST_MH", mh, "Maharashtra", bjpMhDistricts,
                Triple("Sindhudurg District President", "Prabhakar Sawant", "Sindhudurg"),
                Triple("Ratnagiri North District President", "Satish More", "Ratnagiri"),
                Triple("Ratnagiri South District President", "Rajesh Sawant", "Ratnagiri"),
                Triple("Raigad North District President", "Avinash Koli", "Raigarh"),
                Triple("Raigad South District President", "Dhairyasheel Patil", "Raigarh"),
                Triple("Thane City President", "Sandeep Lele", "Thane"),
                Triple("Thane Rural District President", "Jitendra Daki", "Thane"),
                Triple("Bhiwandi District President", "Ravikant Sawant", "Thane"),
                Triple("Mira Bhayandar District President", "Dilip Jain", "Thane"),
                Triple("Navi Mumbai District President", "Rajesh Patil", "Thane"),
                Triple("Kalyan District President", "Nandu Parab", "Thane"),
                Triple("Ulhasnagar District President", "Rajesh Vadhariya", "Thane"),
                Triple("Pune North (Maval) District President", "Pradeep Kand", "Pune"),
                Triple("Pimpri Chinchwad City President", "Shatrughna Kate", "Pune"),
                Triple("Solapur City President", "Rohini Tadavalkar", "Solapur"),
                Triple("Solapur East District President", "Shashikant Chavan", "Solapur"),
                Triple("Solapur West District President", "Chetansingh Kedar", "Solapur"),
                Triple("Satara District President", "Atul Bhosale", "Satara"),
                Triple("Kolhapur East (Hatkanangle) District President", "Rajvardhan Nimbalkar", "Kolhapur"),
                Triple("Kolhapur West (Karveer) District President", "Nathaji Patil", "Kolhapur"),
                Triple("Sangli City President", "Prakash Dhang", "Sangli"),
                Triple("Sangli Rural District President", "Samrat Mahadik", "Sangli"),
                Triple("Nandurbar District President", "Nilesh Mali", "Nandurbar"),
                Triple("Dhule City President", "Gajendra Ampalkar", "Dhule"),
                Triple("Dhule Rural District President", "Bapu Khalane", "Dhule"),
                Triple("Malegaon District President", "Nilesh Kachave", "Nashik"),
                Triple("Jalgaon City President", "Deepak Suryavanshi", "Jalgaon"),
                Triple("Jalgaon East District President", "Chandrakant Baviskar", "Jalgaon"),
                Triple("Jalgaon West District President", "Radheshyam Chaudhari", "Jalgaon"),
                Triple("Ahilyanagar North District President", "Nitin Dinkar", "Ahmadnagar"),
                Triple("Ahilyanagar South District President", "Dilip Bhalsing", "Ahmadnagar"),
                Triple("Nanded Metro President", "Amar Rajurkar", "Nanded"),
                Triple("Parbhani Metro President", "Shivaji Bharose", "Parbhani"),
                Triple("Hingoli District President", "Gajanan Ghuge", "Hingoli"),
                Triple("Jalna Metro President", "Bhaskarrao Danave", "Jalna"),
                Triple("Jalna Rural District President", "Narayan Kuche", "Jalna"),
                Triple("Chhatrapati Sambhajinagar North District President", "Subhash Shirsath", "Aurangabad"),
                Triple("Chhatrapati Sambhajinagar West District President", "Sanjay Khambayate", "Aurangabad"),
                Triple("Dharashiv District President", "Datta Kulkarni", "Osmanabad"),
                Triple("Buldhana District President", "Vijayraj Shinde", "Buldana"),
                Triple("Khamgaon District President", "Sachin Deshmukh", "Buldana"),
                Triple("Akola Metro President", "Jayvantrao Masane", "Akola"),
                Triple("Akola Rural District President", "Santosh Shivarkar", "Akola"),
                Triple("Washim District President", "Purushottam Chitalange", "Washim"),
                Triple("Amravati City President", "Nitin Dhande", "Amravati"),
                Triple("Amravati Rural District President", "Raviraj Deshmukh", "Amravati"),
                Triple("Melghat District President", "Prabhudas Bhilavekar", "Amravati"),
                Triple("Yavatmal District President", "Prafull Chavan", "Yavatmal"),
                Triple("Pusad District President", "Aarti Phuphate", "Yavatmal"),
                Triple("Nagpur Rural (Ramtek) District President", "Anantrao Raut", "Nagpur"),
                Triple("Nagpur Rural (Katol) District President", "Manohar Kumbhare", "Nagpur"),
                Triple("Bhandara District President", "Ashu Gondane", "Bhandara"),
                Triple("Gondia District President", "Sita Rahangdale", "Gondiya"),
                Triple("North Mumbai District President", "Deepak Bala Tawde", "Mumbai Suburban"),
                Triple("North East Mumbai District President", "Deepak Dalvi", "Mumbai Suburban"),
                Triple("North Central Mumbai District President", "Virendra Mhatre", "Mumbai Suburban")
            )
            districtHeads(
                "BJP_DIST_MH", mh, "Maharashtra", bjpMhDistricts2,
                Triple("North West Mumbai District President", "Gyanmurti Sharma", "Mumbai Suburban"),
                Triple("South Central Mumbai District President", "Neeraj Ubhare", "Mumbai"),
                Triple("South Mumbai District President", "Shalaka Salvi", "Mumbai")
            )

            val dl = held("BJP_STATE_DL", "STATE", n, "State President (Delhi)", "Harsh Malhotra", bjpDelhi, "Delhi")
            held("BJP_STATE_DL_GS_ORG", "STATE_GS_ORG", dl, "State General Secretary (Organisation)", "Pawan Rana", bjpOrgSecretaries, "Delhi")
            held("BJP_DIST_ND", "DISTRICT", dl, "New Delhi District President", "Ravindra Chaudhary", bjpDlDistricts, "Delhi", "New Delhi")
            districtHeads(
                "BJP_DIST_DL", dl, "Delhi", bjpDlDistricts,
                Triple("Keshavpuram District President", "Ajay Khatana", null),
                Triple("Chandni Chowk District President", "Arvind Garg", "Central"),
                Triple("North East Delhi District President", "U. K. Chaudhary", "North East"),
                Triple("Naveen Shahdara District President", "Master Binod Kumar", "Shahdara"),
                Triple("Mayur Vihar District President", "Vijendra Dhama", "East"),
                Triple("Shahdara District President", "Deepak Gaba", "Shahdara"),
                Triple("Karol Bagh District President", "Virendra Babbar", "Central"),
                Triple("Outer Delhi District President", "Ramchandra Chawariya", null),
                Triple("West Delhi District President", "Chandrapal Bakshi", "West"),
                Triple("Najafgarh District President", "Raj Sharma Gautam", "South West"),
                Triple("Mehrauli District President", "Ravindra Solanki", "South"),
                Triple("South Delhi District President", "Maya Bisht", "South")
            )

            val ka = held("BJP_STATE_KA", "STATE", n, "State President (Karnataka)", "B. Y. Vijayendra", bjpKarnataka, "Karnataka")
            // Vacant: no full-time organisation secretary since G. V. Rajesh was recalled in May 2024 (ThePrint).
            open("BJP_STATE_KA_GS_ORG", "STATE_GS_ORG", ka, "State General Secretary (Organisation)", "Karnataka")
            held("BJP_DIST_BLR", "DISTRICT", ka, "Bengaluru South District President", "C. K. Ramamurthy", bjpBengaluru, "Karnataka", "Bangalore")

            val up = held("BJP_STATE_UP", "STATE", n, "State President (Uttar Pradesh)", "Pankaj Chaudhary", bjpStatePresidents, "Uttar Pradesh")
            held("BJP_STATE_UP_GS_ORG", "STATE_GS_ORG", up, "State General Secretary (Organisation)", "Dharampal", bjpOrgSecretaries, "Uttar Pradesh")
            held("BJP_DIST_LKO", "DISTRICT", up, "Lucknow Mahanagar (City) President", "Anand Dwivedi", bjpLucknowCity, "Uttar Pradesh", "Lucknow")
            districtHeads(
                "BJP_DIST_UP", up, "Uttar Pradesh", bjpUpDistricts,
                Triple("Lucknow District President", "Vijay Maurya", "Lucknow"),
                Triple("Agra Mahanagar President", "Rajkumar Gupta", "Agra"),
                Triple("Agra District President", "Prashant Poonia", "Agra"),
                Triple("Moradabad Mahanagar President", "Girish Bhandula", "Moradabad"),
                Triple("Moradabad District President", "Akash Pal", "Moradabad"),
                Triple("Ghaziabad Mahanagar President", "Mayank Goyal", "Ghaziabad"),
                Triple("Noida Mahanagar President", "Mahesh Chauhan", "Gautam Buddha Nagar"),
                Triple("Gautam Buddha Nagar District President", "Abhishek Sharma", "Gautam Buddha Nagar"),
                Triple("Meerut Mahanagar President", "Vivek Rastogi", "Meerut"),
                Triple("Gorakhpur Mahanagar President", "Devesh Srivastava", "Gorakhpur"),
                Triple("Gorakhpur District President", "Janardan Tiwari", "Gorakhpur"),
                Triple("Kanpur North Mahanagar President", "Anil Dixit", "Kanpur Nagar"),
                Triple("Kanpur South District President", "Shivram Singh", "Kanpur Nagar"),
                Triple("Kanpur Rural District President", "Upendra Nath Paswan", "Kanpur Nagar"),
                Triple("Mathura Mahanagar President", "Raju Yadav", "Mathura"),
                Triple("Mathura District President", "Nirbhay Pandey", "Mathura"),
                Triple("Prayagraj Mahanagar President", "Sanjay Gupta", "Prayagraj"),
                Triple("Prayagraj Gangapar District President", "Nirmala Paswan", "Prayagraj"),
                Triple("Prayagraj Yamunapar District President", "Rajesh Shukla", "Prayagraj"),
                Triple("Bulandshahr District President", "Vikas Chauhan", "Bulandshahr"),
                Triple("Etawah District President", "Annu Gupta", "Etawah"),
                Triple("Ghazipur District President", "Om Prakash Rai", "Ghazipur"),
                Triple("Lalitpur District President", "Harishchandra Rawat", "Lalitpur"),
                Triple("Amethi District President", "Sudhanshu Shukla", "Amethi"),
                Triple("Kasganj District President", "Neeraj Sharma", "Kasganj"),
                Triple("Maharajganj District President", "Sanjay Pandey", "Mahrajganj"),
                Triple("Raebareli District President", "Buddhi Lal", "Rae Bareli"),
                Triple("Sonbhadra District President", "Nandlal Gupta", "Sonbhadra"),
                Triple("Azamgarh District President", "Dhruv Singh", "Azamgarh"),
                Triple("Lalganj District President", "Vinod Rajbhar", "Azamgarh"),
                Triple("Basti District President", "Vivekanand Mishra", "Basti"),
                Triple("Bhadohi District President", "Deepak Mishra", "Bhadohi"),
                Triple("Pratapgarh District President", "Ashish Srivastava", "Pratapgarh"),
                Triple("Rampur District President", "Harish Gangwar", "Rampur"),
                Triple("Mainpuri District President", "Mamta Rajput", "Mainpuri"),
                Triple("Farrukhabad District President", "Fateh Chandra Verma", "Farrukhabad"),
                Triple("Bahraich District President", "Brajesh Pandey", "Bahraich"),
                Triple("Sultanpur District President", "Sushil Tripathi", "Sultanpur"),
                Triple("Auraiya District President", "Sarvesh Katheria", "Auraiya"),
                Triple("Balrampur District President", "Ravi Mishra", "Balrampur"),
                Triple("Gonda District President", "Amar Kishore Kashyap", "Gonda"),
                Triple("Shravasti District President", "Mishri Lal Verma", "Shrawasti"),
                Triple("Kannauj District President", "Veer Kumar Singh Bhadauria", "Kannauj"),
                Triple("Sant Kabir Nagar District President", "Neetu Singh", "Sant Kabir Nagar"),
                Triple("Mau District President", "Ramashray Maurya", "Mau"),
                Triple("Ballia District President", "Sanjay Mishra", "Ballia"),
                Triple("Machhlishahr District President", "Ajay Kumar Singh", "Jaunpur"),
                Triple("Mahoba District President", "Mohanlal Kushwaha", "Mahoba"),
                Triple("Chitrakoot District President", "Mahendra Kotarya", "Chitrakoot")
            )

            val gj = held("BJP_STATE_GJ", "STATE", n, "State President (Gujarat)", "Jagdish Vishwakarma", bjpStatePresidents, "Gujarat")
            held("BJP_STATE_GJ_GS_ORG", "STATE_GS_ORG", gj, "State General Secretary (Organisation)", "Ratnakar", bjpOrgSecretaries, "Gujarat")
            held("BJP_DIST_AHM", "DISTRICT", gj, "Ahmedabad City President", "Prerak Shah", bjpAhmedabadCity, "Gujarat", "Ahmadabad")
            districtHeads(
                "BJP_DIST_GJ", gj, "Gujarat", bjpGjDistricts,
                Triple("Ahmedabad District President", "Shailesh Davda", "Ahmadabad"),
                Triple("Surat City President", "Paresh Kumar Patel", "Surat"),
                Triple("Surat District President", "Bharat Rathod", "Surat"),
                Triple("Rajkot City President", "Madhav K. Dave", "Rajkot"),
                Triple("Rajkot District President", "Alpesh Dholaria", "Rajkot"),
                Triple("Vadodara City President", "Jayaprakash Soni", "Vadodara"),
                Triple("Jamnagar City President", "Binaben Kothari", "Jamnagar"),
                Triple("Jamnagar District President", "Vinod Bhanderi", "Jamnagar"),
                Triple("Junagadh City President", "Gauravbhai Rupareliya", "Junagadh"),
                Triple("Junagadh District President", "Chandubhai Makwana", "Junagadh"),
                Triple("Bhavnagar City President", "Kumarbhai Shah", "Bhavnagar"),
                Triple("Bhavnagar District President", "Digvijay Singh Gohil", "Bhavnagar"),
                Triple("Gandhinagar District President", "Anil Patel", "Gandhinagar"),
                Triple("Mehsana District President", "Girish Rajgor", "Mahesana"),
                Triple("Banaskantha District President", "Kirtisinh Vaghela", "Banas Kantha"),
                Triple("Patan District President", "Ramesh Sindhav", "Patan"),
                Triple("Navsari District President", "Bhuralal Shah", "Navsari"),
                Triple("Mahisagar District President", "Dashrath Baria", "Mahisagar"),
                Triple("Amreli District President", "Atulbhai Kanani", "Amreli"),
                Triple("Dang District President", "Kishorebhai Gavit", "The Dangs"),
                Triple("Tapi District President", "Suraj Vasava", "Tapi"),
                Triple("Valsad District President", "Hemant Kansara", "Valsad"),
                Triple("Bharuch District President", "Prakash Modi", "Bharuch"),
                Triple("Narmada District President", "Neel Rao", "Narmada"),
                Triple("Chhota Udaipur District President", "Umesh Rathwa", "Chota Udaipur"),
                Triple("Anand District President", "Sanjay Patel", "Anand"),
                Triple("Dahod District President", "Snehal Dharia", "Dohad"),
                Triple("Kutch District President", "Devji Varchand", "Kachchh"),
                Triple("Sabarkantha District President", "Kanubhai Patel", "Sabar Kantha"),
                Triple("Aravalli District President", "Bhikhaji Thakor", "Aravalli"),
                Triple("Devbhoomi Dwarka District President", "Mayur Gadhvi", "Devbhumi Dwarka"),
                Triple("Morbi District President", "Jayanti Rajkotia", "Morbi"),
                Triple("Gir Somnath District President", "Sanjay Parmar", "Gir Somnath"),
                Triple("Botad District President", "Mayur Patel", "Botad"),
                Triple("Surendranagar District President", "Hitendra Singh Chauhan", "Surendranagar")
            )

            stateHeads(
                "BJP_STATE", n, "State President", bjpStateRoster,
                Triple("AP", "Andhra Pradesh", "P. V. N. Madhav"),
                Triple("AR", "Arunachal Pradesh", "Kaling Moyong"),
                Triple("AS", "Assam", "Dilip Saikia"),
                Triple("BR", "Bihar", "Sanjay Saraogi"),
                Triple("CG", "Chhattisgarh", "Kiran Singh Deo"),
                Triple("GA", "Goa", "Damodar Naik"),
                Triple("HR", "Haryana", "Archana Gupta"),
                Triple("HP", "Himachal Pradesh", "Rajeev Bindal"),
                Triple("JH", "Jharkhand", "Aditya Sahu"),
                Triple("KL", "Kerala", "Rajeev Chandrasekhar"),
                Triple("MP", "Madhya Pradesh", "Hemant Khandelwal"),
                Triple("MN", "Manipur", "Adhikarimayum Sharda Devi"),
                Triple("ML", "Meghalaya", "Rikman Momin"),
                Triple("MZ", "Mizoram", "K. Beichhua"),
                Triple("NL", "Nagaland", "Benjamin Yepthomi"),
                Triple("OD", "Odisha", "Manmohan Samal"),
                Triple("PB", "Punjab", "Kewal Singh Dhillon"),
                Triple("RJ", "Rajasthan", "Madan Rathore"),
                Triple("SK", "Sikkim", "Dilli Ram Thapa"),
                Triple("TN", "Tamil Nadu", "Nainar Nagendran"),
                Triple("TG", "Telangana", "N. Ramchander Rao"),
                Triple("TR", "Tripura", "Abhishek Debroy"),
                Triple("UK", "Uttarakhand", "Mahendra Bhatt"),
                Triple("WB", "West Bengal", "Samik Bhattacharya"),
                Triple("AN", "Andaman & Nicobar", "Anil Kumar Tiwari"),
                Triple("CH", "Chandigarh", "Jatinder Pal Malhotra"),
                Triple("JK", "Jammu & Kashmir", "Sat Paul Sharma"),
                Triple("LA", "Ladakh", "Tashi Gyalson Khachu"),
                Triple("LD", "Lakshadweep", "K. N. Kasmikoya"),
                Triple("PY", "Puducherry", "V. P. Ramalingam")
            )
        },

        seed("INC") {
            val n = held("INC_NAT_PRES", "NATIONAL", null, "Congress President", "Mallikarjun Kharge", incPresident)
            held("INC_NAT_LOP", "NATIONAL", n, "Leader of Opposition (Lok Sabha)", "Rahul Gandhi", incLop)
            held("INC_NAT_GS_ORG", "NATIONAL_GS_ORG", n, "General Secretary (Organisation)", "K. C. Venugopal", incVenugopal)
            held("INC_NAT_GS1", "NATIONAL_GS", n, "General Secretary (Communications)", "Jairam Ramesh", incVenugopal)
            held("INC_NAT_TREAS", "NATIONAL_TREASURER", n, "AICC Treasurer", "Ajay Maken", incTreasurer)

            val mh = held("INC_STATE_MH", "STATE", n, "PCC President (Maharashtra)", "Harshwardhan Sapkal", incMaharashtra, "Maharashtra")
            held("INC_DIST_MUM", "DISTRICT", mh, "Mumbai Regional Congress President", "Varsha Gaikwad", incMumbai, "Maharashtra", "Mumbai")
            // Pune city was split into two units in May 2026.
            held("INC_DIST_PUNE_E", "DISTRICT", mh, "Pune East City President", "Prashant Jagtap", incPune, "Maharashtra", "Pune")
            held("INC_DIST_PUNE_W", "DISTRICT", mh, "Pune West City President", "Deepti Chaudhari", incPune, "Maharashtra", "Pune")

            val dl = held("INC_STATE_DL", "STATE", n, "PCC President (Delhi)", "Devender Yadav", incDelhi, "Delhi")
            held("INC_DIST_ND", "DISTRICT", dl, "New Delhi DCC President", "Mohinder Mangla", incDlDistricts, "Delhi", "New Delhi")
            districtHeads(
                "INC_DIST_DL", dl, "Delhi", incDlDistricts,
                Triple("Adarsh Nagar DCC President", "Siddharth Rao", "North West"),
                Triple("Chandni Chowk DCC President", "Mohammed Usman", "Central"),
                Triple("Karawal Nagar DCC President", "Mangesh Tyagi", "North East"),
                Triple("Babarpur DCC President", "Raj Kumar Jain", "North East"),
                Triple("Patparganj DCC President", "Vijender Choudary", "East"),
                Triple("Krishna Nagar DCC President", "Sumit Sharma", "Shahdara"),
                Triple("Karol Bagh DCC President", "Mahender Bhaskar", "Central"),
                Triple("Rohini DCC President", "Indrajeet Singh", "North West"),
                Triple("Kirari DCC President", "Varun Dhaka", "North West"),
                Triple("Tilak Nagar DCC President", "Amandeep Singh Sudan", "West"),
                Triple("Najafgarh DCC President", "Rajesh Yadav", "South West"),
                Triple("Mehrauli DCC President", "Virender Sharma", "South"),
                Triple("Badarpur DCC President", "Harsh Choudhary", "South East")
            )

            val ka = held("INC_STATE_KA", "STATE", n, "PCC President (Karnataka)", "B. K. Hariprasad", incKarnataka, "Karnataka")
            districtHeads(
                "INC_DIST_KA", ka, "Karnataka", incKaDistricts,
                Triple("Bengaluru North DCC President", "Abdul Wajid", "Bangalore"),
                Triple("Bengaluru South DCC President", "O. Manjunath", "Bangalore"),
                Triple("Bengaluru East DCC President", "D. K. Mohan Babu", "Bangalore"),
                Triple("Bengaluru West DCC President", "Hanumantharayappa", "Bangalore"),
                Triple("Bengaluru Central DCC President", "J. Saravanan", "Bangalore"),
                Triple("Bengaluru Rural DCC President", "C. Prasanna Kumar", "Bengaluru Rural"),
                Triple("Bagalkot DCC President", "Dayanand S. Patil", "Bagalkote"),
                Triple("Chikkodi DCC President", "Lakshmanrao Chingale", "Belagavi"),
                Triple("Belagavi City DCC President", "Rajasaleem Khateeb", "Belagavi"),
                Triple("Belagavi Rural DCC President", "Basavaraj Shegavi", "Belagavi"),
                Triple("Ballari City DCC President", "Allam Prashanth", "Ballari"),
                Triple("Ballari Rural DCC President", "K. Mallikarjun", "Ballari"),
                Triple("Bidar DCC President", "Abdul Mannan Seth", "Bidar"),
                Triple("Chamarajanagar DCC President", "J. Yogesh", "Chamarajanagara"),
                Triple("Chikkamagaluru DCC President", "H. P. Manjegowda", "Chikkamagaluru"),
                Triple("Dakshina Kannada DCC President", "Padmaraj R. Poojary", "Dakshina Kannada"),
                Triple("Davanagere DCC President", "Ibrahim Khalilullah", "Davanagere"),
                Triple("Dharwad Rural DCC President", "Chandrashekhar Juttal", "Dharwad"),
                Triple("Hubballi-Dharwad City DCC President", "Sadanand V. Danganavar", "Dharwad"),
                Triple("Gadag DCC President", "Vasanna Kurdagi", "Gadag"),
                Triple("Kalaburagi DCC President", "Kiran A. Deshmukh", "Kalaburagi"),
                Triple("Hassan DCC President", "Shashidhar G. B.", "Hassan"),
                Triple("Haveri DCC President", "Sanjeev Kumar Niralagi", "Haveri"),
                Triple("Kodagu DCC President", "Dharmaja Uthappa", "Kodagu"),
                Triple("Kolar DCC President", "N. Narayanaswamy", "Kolar"),
                Triple("Koppal DCC President", "M. Mallikarjuna Nagappa", "Koppal"),
                Triple("Mandya DCC President", "Kumar Koppa", "Mandya"),
                Triple("Mysuru City DCC President", "R. Murthy", "Mysuru"),
                Triple("Mysuru Rural DCC President", "Biri Hundi Basavanna", "Mysuru"),
                Triple("Raichur DCC President", "K. Shantappa", "Raichur"),
                Triple("Ramanagara DCC President", "C. N. Venkatesh", "Ramanagara"),
                Triple("Shivamogga DCC President", "H. C. Yogesh", "Shivamogga"),
                Triple("Udupi DCC President", "Kishan Kumar Hegde", "Udupi"),
                Triple("Uttara Kannada DCC President", "Gajanana Timma Naik", "Uttara Kannada"),
                Triple("Yadgir DCC President", "Basareddy M. Patil", "Yadgir")
            )

            val up = held("INC_STATE_UP", "STATE", n, "PCC President (Uttar Pradesh)", "Aradhana Misra", incUp, "Uttar Pradesh")
            open("INC_DIST_LKO", "DISTRICT", up, "Lucknow DCC President", "Uttar Pradesh", "Lucknow")

            stateHeads(
                "INC_STATE", n, "PCC President", incStateRoster,
                Triple("AP", "Andhra Pradesh", "Y. S. Sharmila"),
                Triple("AR", "Arunachal Pradesh", "Bosiram Siram"),
                Triple("AS", "Assam", "Gaurav Gogoi"),
                Triple("BR", "Bihar", "Rajesh Kumar"),
                Triple("CG", "Chhattisgarh", "Deepak Baij"),
                Triple("GA", "Goa", "Girish Chodankar"),
                Triple("GJ", "Gujarat", "Amit Chavda"),
                Triple("HR", "Haryana", "Rao Narendra Singh"),
                Triple("HP", "Himachal Pradesh", "Vinay Kumar"),
                Triple("JH", "Jharkhand", "Keshav Mahto Kamlesh"),
                Triple("KL", "Kerala", "Sunny Joseph"),
                Triple("MP", "Madhya Pradesh", "Jitu Patwari"),
                Triple("MN", "Manipur", "Okram Ibobi Singh"),
                Triple("ML", "Meghalaya", "Vincent Pala"),
                Triple("MZ", "Mizoram", "Lal Thanzara"),
                Triple("NL", "Nagaland", "S. Supongmeren Jamir"),
                Triple("OD", "Odisha", "Bhakta Charan Das"),
                Triple("PB", "Punjab", "Pargat Singh"),
                Triple("RJ", "Rajasthan", "Govind Singh Dotasra"),
                Triple("SK", "Sikkim", "Gopal Chettri"),
                Triple("TN", "Tamil Nadu", "Manickam Tagore"),
                Triple("TG", "Telangana", "Bomma Mahesh Kumar Goud"),
                Triple("TR", "Tripura", "Ashish Kumar Saha"),
                Triple("UK", "Uttarakhand", "Ganesh Godiyal"),
                Triple("WB", "West Bengal", "Subhankar Sarkar"),
                Triple("AN", "Andaman & Nicobar", "Ranglal Halder"),
                Triple("CH", "Chandigarh", "Harmohinder Singh"),
                Triple("JK", "Jammu & Kashmir", "Tariq Hameed Karra"),
                Triple("LA", "Ladakh", "Nawang Rigzin Jora"),
                Triple("LD", "Lakshadweep", "Muhammed Hamdulla Sayeed"),
                Triple("PY", "Puducherry", "V. Vaithilingam")
            )
        },

        seed("AAP") {
            val n = held("AAP_NAT_CONV", "NATIONAL", null, "National Convenor", "Arvind Kejriwal", aapCouncil)
            // No successor named since Sandeep Pathak left for the BJP in April 2026.
            open("AAP_NAT_GS_ORG", "NATIONAL_GS_ORG", n, "National General Secretary (Organisation)")
            held("AAP_NAT_SEC", "NATIONAL_SECRETARY", n, "National Secretary", "Pankaj Gupta", aapCouncil)
            held("AAP_NAT_TREAS", "NATIONAL_TREASURER", n, "National Treasurer", "N. D. Gupta", aapCouncil)
            held("AAP_STATE_DL", "STATE", n, "State Convenor (Delhi)", "Saurabh Bharadwaj", aapStates, "Delhi")
            held("AAP_STATE_PB", "STATE", n, "State Convenor (Punjab)", "Aman Arora", aapStates, "Punjab")
            // Not among the 21 conveners named in September 2026.
            open("AAP_STATE_MH", "STATE", n, "State Convenor (Maharashtra)", "Maharashtra")
            stateHeads(
                "AAP_STATE", n, "State Convenor", aapStates,
                Triple("GA", "Goa", "Valmiki Naik"),
                Triple("HR", "Haryana", "Sushil Gupta"),
                Triple("GJ", "Gujarat", "Isudan Gadhvi"),
                Triple("UP", "Uttar Pradesh", "Sabhajeet Singh"),
                Triple("JK", "Jammu & Kashmir", "Mehraj Malik"),
                Triple("KA", "Karnataka", "Mukhyamantri Chandru"),
                Triple("KL", "Kerala", "Vinod Wilson Mathew"),
                Triple("OD", "Odisha", "Nishikant Mahapatra"),
                Triple("TN", "Tamil Nadu", "Vasigaran"),
                Triple("TG", "Telangana", "Hema"),
                Triple("MZ", "Mizoram", "Andrew Lalremkima"),
                Triple("AR", "Arunachal Pradesh", "Yamra Taya"),
                Triple("NL", "Nagaland", "Asukeho"),
                Triple("BR", "Bihar", "Rakesh Kumar"),
                Triple("AP", "Andhra Pradesh", "Ramesh Sira"),
                Triple("AS", "Assam", "Bhoben Choudhury"),
                Triple("UK", "Uttarakhand", "Uma Gaur Sisodia"),
                Triple("RJ", "Rajasthan", "Kirti Pathak"),
                Triple("AN", "Andaman & Nicobar", "Arun K. Mallik")
            )
        },

        seed("NCP(SP)") {
            val n = held("NCP_NAT_PRES", "NATIONAL", null, "National President", "Sharad Pawar", ncpPawar)
            held("NCP_NAT_WP", "NATIONAL_WORKING", n, "National Working President", "Supriya Sule", ncpSule)
            val mh = held("NCP_STATE_MH", "STATE", n, "State President (Maharashtra)", "Shashikant Shinde", ncpShinde, "Maharashtra")
            // Pune city was split into two units in August 2026.
            held("NCP_DIST_PUNE_W", "DISTRICT", mh, "Pune West City President", "Vishal Tambe", ncpPune, "Maharashtra", "Pune")
            held("NCP_DIST_PUNE_E", "DISTRICT", mh, "Pune East City President", "Shrikant Patil", ncpPune, "Maharashtra", "Pune")
            open("NCP_DIST_MUM", "DISTRICT", mh, "Mumbai President", "Maharashtra", "Mumbai")
            open("NCP_DIST_SATARA", "DISTRICT", mh, "Satara District President", "Maharashtra", "Satara")
        },

        seed("TMC") {
            val n = held("TMC_NAT_CHAIR", "NATIONAL", null, "Chairperson", "Mamata Banerjee", tmcMamata)
            held("TMC_NAT_GS", "NATIONAL_GS", n, "National General Secretary", "Abhishek Banerjee", tmcAbhishek)
            held("TMC_NAT_PARL", "NATIONAL", n, "Leader in Lok Sabha", "Abhishek Banerjee", tmcAbhishek)
            val wb = held("TMC_STATE_WB", "STATE", n, "State President (West Bengal)", "Mamata Banerjee", tmcMamata, "West Bengal")
            held("TMC_DIST_KOL", "DISTRICT", wb, "North Kolkata Organisational District Chief", "Kunal Ghosh", tmcNorthKolkata, "West Bengal", "Kolkata")
            open("TMC_STATE_TR", "STATE", n, "State President (Tripura)", "Tripura")
        },

        seed("DMK") {
            // DMK's headquarters runs district secretaries directly; there is no state tier.
            val n = held("DMK_NAT_PRES", "NATIONAL", null, "Party President", "M. K. Stalin", dmkStalin)
            held("DMK_NAT_GS", "NATIONAL_GS", n, "General Secretary", "Durai Murugan", dmkDurai)
            held("DMK_NAT_TREAS", "NATIONAL_TREASURER", n, "Treasurer", "T. R. Baalu", dmkBaalu)
            held("DMK_NAT_LOP", "NATIONAL", n, "Leader of the Opposition (Tamil Nadu Assembly)", "Udhayanidhi Stalin", dmkUdhay)
            held("DMK_NAT_DGS", "NATIONAL_GS", n, "Deputy General Secretary", "Kanimozhi", dmkKanimozhi)
            open("DMK_DIST_CHN", "DISTRICT", n, "District Secretary (Chennai)", "Tamil Nadu", "Chennai")
            open("DMK_DIST_CBE", "DISTRICT", n, "District Secretary (Coimbatore)", "Tamil Nadu", "Coimbatore")
            open("DMK_DIST_MDU", "DISTRICT", n, "District Secretary (Madurai)", "Tamil Nadu", "Madurai")
        },

        seed("BSP") {
            val n = held("BSP_NAT_PRES", "NATIONAL", null, "National President", "Mayawati", bsp)
            held("BSP_NAT_COORD", "NATIONAL_GS", n, "National Coordinator", "Akash Anand", bsp)
            held("BSP_NAT_RS", "NATIONAL", n, "Leader in Rajya Sabha", "Ramji Gautam", bspParty)
        },
        seed("CPI(M)") {
            val n = held("CPIM_NAT_GS", "NATIONAL", null, "General Secretary", "M. A. Baby", cpim)
            held("CPIM_NAT_LS", "NATIONAL", n, "Leader in Lok Sabha", "K. Radhakrishnan", cpimParty)
            held("CPIM_STATE_WB", "STATE", n, "State Secretary (West Bengal)", "Md Salim", cpimBengal, "West Bengal")
            held("CPIM_STATE_KL", "STATE", n, "State Secretary (Kerala)", "M. V. Govindan", cpimKerala, "Kerala")
            held("CPIM_STATE_TR", "STATE", n, "State Secretary (Tripura)", "Jitendra Chaudhury", cpimTripura, "Tripura")
        },
        seed("NPP") {
            val n = held("NPP_NAT_PRES", "NATIONAL", null, "National President", "Conrad Sangma", npp)
            held("NPP_STATE_ML", "STATE", n, "State President (Meghalaya)", "Prestone Tynsong", nppMeghalaya, "Meghalaya")
        },
        seed("SS(UBT)") {
            val n = held("SSUBT_PRES", "NATIONAL", null, "Party President", "Uddhav Thackeray", ssubt)
            held("SSUBT_LS", "NATIONAL", n, "Leader in Lok Sabha", "Arvind Sawant", ssubtSawant)
            held("SSUBT_RS", "NATIONAL", n, "Leader in Rajya Sabha", "Sanjay Raut", ssubtRaut)
            held("SSUBT_YUVA", "NATIONAL_GS", n, "President, Yuva Sena (youth wing)", "Aaditya Thackeray", ssubtAaditya)
        },
        seed("SHS") {
            val n = held("SHS_CHIEF", "NATIONAL", null, "Chief Leader (Mukhya Neta)", "Eknath Shinde", shs)
            held("SHS_LS", "NATIONAL", n, "Leader in Lok Sabha", "Shrikant Shinde", shsShrikant)
        },
        seed("MNS") {
            val n = held("MNS_PRES", "NATIONAL", null, "President", "Raj Thackeray", mns)
            held("MNS_MNVS", "NATIONAL_GS", n, "President, Vidyarthi Sena (student wing)", "Amit Thackeray", mnsAmit)
        },
        seed("AIADMK") {
            val n = held("AIADMK_GS", "NATIONAL", null, "General Secretary", "Edappadi K. Palaniswami", aiadmk)
            held("AIADMK_DGS1", "NATIONAL_GS", n, "Deputy General Secretary", "K. P. Munusamy", aiadmkDeputies)
            held("AIADMK_DGS2", "NATIONAL_GS", n, "Deputy General Secretary", "Natham R. Viswanathan", aiadmkDeputies)
            held("AIADMK_TREAS", "NATIONAL_TREASURER", n, "Treasurer", "Dindigul C. Sreenivasan", aiadmkTreasurer)
        },
        seed("TDP") {
            val n = held("TDP_NAT_PRES", "NATIONAL", null, "National President", "N. Chandrababu Naidu", tdp)
            held("TDP_NAT_WP", "NATIONAL_WORKING", n, "National Working President", "Nara Lokesh", tdp)
            held("TDP_NAT_LS", "NATIONAL", n, "Leader in Lok Sabha", "Lavu Sri Krishna Devarayalu", tdpParty)
            held("TDP_STATE_AP", "STATE", n, "State President (Andhra Pradesh)", "Palla Srinivasa Rao", tdpState, "Andhra Pradesh")
        },
        seed("YSRCP") {
            val n = held("YSRCP_PRES", "NATIONAL", null, "President", "Y. S. Jagan Mohan Reddy", ysrcp)
            held("YSRCP_LS", "NATIONAL", n, "Leader in Lok Sabha", "P. V. Midhun Reddy", ysrcpMidhun)
        },
        seed("JSP") {
            val n = held("JSP_PRES", "NATIONAL", null, "President", "Pawan Kalyan", jsp)
            held("JSP_PAC", "NATIONAL_GS", n, "Chairman, Political Affairs Committee", "Nadendla Manohar", jspNadendla)
        },
        seed("BRS") {
            val n = held("BRS_PRES", "NATIONAL", null, "President", "K. Chandrashekar Rao", brs)
            held("BRS_WP", "NATIONAL_WORKING", n, "Working President", "K. T. Rama Rao", brs)
            held("BRS_LOP", "NATIONAL", n, "Leader of the Opposition (Telangana Assembly)", "K. Chandrashekar Rao", brsKcr, "Telangana")
        },
        seed("JD(U)") {
            val n = held("JDU_NAT_PRES", "NATIONAL", null, "National President", "Nitish Kumar", jduState)
            held("JDU_NAT_WP", "NATIONAL_WORKING", n, "National Working President", "Sanjay Jha", jduJha)
            held("JDU_STATE_BR", "STATE", n, "State President (Bihar)", "Umesh Singh Kushwaha", jduState, "Bihar")
        },
        seed("RJD") {
            val n = held("RJD_NAT_PRES", "NATIONAL", null, "National President", "Lalu Prasad Yadav", rjdLalu)
            held("RJD_NAT_WP", "NATIONAL_WORKING", n, "National Working President", "Tejashwi Yadav", rjdTejashwi)
            held("RJD_LOP", "NATIONAL", n, "Leader of the Opposition (Bihar Assembly)", "Tejashwi Yadav", rjdLop, "Bihar")
            held("RJD_STATE_BR", "STATE", n, "State President (Bihar)", "Mangani Lal Mandal", rjdState, "Bihar")
        },
        seed("SP") {
            val n = held("SP_NAT_PRES", "NATIONAL", null, "National President", "Akhilesh Yadav", sp)
            held("SP_NAT_LS", "NATIONAL", n, "Leader in Lok Sabha", "Akhilesh Yadav", sp)
            held("SP_NAT_SG", "NATIONAL_GS", n, "Secretary-General", "Ram Gopal Yadav", spRamGopal)
            held("SP_STATE_UP", "STATE", n, "State President (Uttar Pradesh)", "Shyam Lal Pal", spState, "Uttar Pradesh")
        },
        seed("BJD") {
            val n = held("BJD_PRES", "NATIONAL", null, "President", "Naveen Patnaik", bjd)
            held("BJD_LOP", "NATIONAL", n, "Leader of the Opposition (Odisha Assembly)", "Naveen Patnaik", bjdLop, "Odisha")
            held("BJD_RS", "NATIONAL", n, "Leader in Rajya Sabha", "Manas Ranjan Mangaraj", bjdRs)
        },
        seed("JMM") {
            val n = held("JMM_PRES", "NATIONAL", null, "President", "Hemant Soren", jmm)
            held("JMM_GS1", "NATIONAL_GS", n, "General Secretary", "Vinod Kumar Pandey", jmmGs)
            held("JMM_GS2", "NATIONAL_GS", n, "General Secretary", "Supriyo Bhattacharya", jmmGs)
        },
        seed("SAD") {
            val n = held("SAD_PRES", "NATIONAL", null, "President", "Sukhbir Singh Badal", sad)
            held("SAD_LS", "NATIONAL", n, "Leader in Lok Sabha", "Harsimrat Kaur Badal", sadParty)
        },
        seed("JD(S)") {
            val n = held("JDS_NAT_PRES", "NATIONAL", null, "National President", "H. D. Deve Gowda", jds)
            held("JDS_STATE_KA", "STATE", n, "State President (Karnataka)", "H. D. Kumaraswamy", jdsState, "Karnataka")
        },
        seed("JKNC") {
            val n = held("JKNC_PRES", "NATIONAL", null, "President", "Farooq Abdullah", jkncFarooq)
            held("JKNC_CM", "NATIONAL", n, "Chief Minister of Jammu & Kashmir", "Omar Abdullah", jkncOmar, "Jammu & Kashmir")
            held("JKNC_GS", "NATIONAL_GS", n, "General Secretary", "Ali Mohammad Sagar", jkncSagar)
        }
    )

    private val sources: Map<String, VerifiedSource> = seeds.values.flatMap { it.sources.entries }.associate { it.key to it.value }
}

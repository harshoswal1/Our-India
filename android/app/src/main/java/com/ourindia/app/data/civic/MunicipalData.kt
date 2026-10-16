package com.ourindia.app.data.civic

/**
 * Real municipal corporation data for Maharashtra cities.
 * Source: Official government directory (Excel dataset).
 */
object MunicipalData {

    data class DepartmentContact(
        val designation: String,
        val phone: String? = null,
        val email: String? = null
    )

    data class Department(
        val name: String,
        val contacts: List<DepartmentContact>
    )

    data class MunicipalCorporation(
        val city: String,
        val authorityName: String,
        val governanceType: String,
        val district: String,
        val departments: List<Department>
    )

    val corporations: List<MunicipalCorporation> = listOf(
        MunicipalCorporation(
            city = "Ahilyanagar (Ahmednagar)",
            authorityName = "Ahilyanagar Municipal Corporation",
            governanceType = "Municipal Corporation",
            district = "Ahilyanagar",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "0241-2345001", null),
                    DepartmentContact("DEOC", "0241-2323844", null),
                    DepartmentContact("RDC", "0241-2345004", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "0241-2430218", "seahmednagar@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhoahmednagar@gmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "0241-2345001", null),
                    DepartmentContact("Property Tax Contact", null, "ahmednagarcorp@gmail.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "0241-2430018", "ahmednagar.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "ahmednagarcorp@gmail.com"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Akola",
            authorityName = "Akola Municipal Corporation (AMC)",
            governanceType = "Municipal Corporation",
            district = "Akola",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "0724-2424442", null),
                    DepartmentContact("DEOC", "0724-2424444", null),
                    DepartmentContact("RDC", "0724-2435193", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "0724-2434418", "seakola@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhoako@rediffmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "0724-2434011", null),
                    DepartmentContact("Property Tax Contact", null, "mcakola@rediffmail.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "0724-2434415", "akola.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "mcakola@rediffmail.com"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Amravati",
            authorityName = "Amravati Municipal Corporation (AMC)",
            governanceType = "Municipal Corporation",
            district = "Amravati",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "0721-2662493", null),
                    DepartmentContact("DEOC", "0721-2662025", null),
                    DepartmentContact("RDC", "0721-2662025", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "0721-2662118", "seamravati@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhoamravati@rediffmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "0721-2562357", null),
                    DepartmentContact("Property Tax Contact", null, "amravaticorporation@gmail.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "0721-2662116", "amravati.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "amravaticorporation@gmail.com"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Baramati",
            authorityName = "Baramati Municipal Council",
            governanceType = "Class-A Municipal Council",
            district = "Pune",
            departments = listOf(
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "02112-243415", "sebaramati@mahadiscom.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Beed",
            authorityName = "Beed Municipal Council",
            governanceType = "Class-A Municipal Council",
            district = "Beed",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "02442-222201", null),
                    DepartmentContact("DEOC", "02442-299299", null),
                    DepartmentContact("RDC", "02442-222604", null),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "beeddho@gmail.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "02442-222415", "beed.se@mahapwd.gov.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Bhayandar",
            authorityName = "Mira-Bhayandar Municipal Corporation (MBMC)",
            governanceType = "Municipal Corporation (Cluster)",
            district = "Thane",
            departments = listOf(
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "022-28192828", null),
                    DepartmentContact("Property Tax Contact", null, "tax@mbmc.gov.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Bhiwandi",
            authorityName = "Bhiwandi-Nizampur Municipal Corporation (BNMC)",
            governanceType = "Municipal Corporation (Cluster)",
            district = "Thane",
            departments = listOf(
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "02522-250001", null),
                    DepartmentContact("Property Tax Contact", null, "tax.bnmc@gmail.com"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "bhnmcorp@gmail.com"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Chandrapur",
            authorityName = "Chandrapur Municipal Corporation (CMC)",
            governanceType = "Municipal Corporation",
            district = "Chandrapur",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "07172-255300", null),
                    DepartmentContact("DEOC", "07172-225077", null),
                    DepartmentContact("RDC", "07172-255400", null),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhocha@gmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", null, "cmcchandrapur@gmail.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "07172-255301", "chandrapur.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "cmcchandrapur@gmail.com"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Chhatrapati Sambhajinagar",
            authorityName = "Chhatrapati Sambhajinagar Municipal Corporation (CSMC)",
            governanceType = "Municipal Corporation",
            district = "Chhatrapati Sambhajinagar",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "0240-2331200", null),
                    DepartmentContact("DEOC", "0240-2331077", null),
                    DepartmentContact("RDC", "0240-2334127", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "0240-2331451", "seaudi@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhoaurangabad@rediffmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "0240-2333536", null),
                    DepartmentContact("Property Tax Contact", null, "tax@aurangabadmahapalika.org"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "0240-2331459", "aurangabad.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "est1@aurangabadmahapalika.org"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Dhule",
            authorityName = "Dhule Municipal Corporation (DMC)",
            governanceType = "Municipal Corporation",
            district = "Dhule",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "02562-288701", null),
                    DepartmentContact("DEOC", "02562-288066", null),
                    DepartmentContact("RDC", "02562-288705", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "02562-247741", "sedhule@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhodhulezp@gmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "02562-237102", null),
                    DepartmentContact("Property Tax Contact", null, "dhulecorporation@gmail.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "02562-237302", "dhule.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "dhulecorporation@gmail.com"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Gondia",
            authorityName = "Gondia Municipal Council",
            governanceType = "Class-A Municipal Council",
            district = "Gondia",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "07182-236149", null),
                    DepartmentContact("DEOC", "07182-230196", null),
                    DepartmentContact("RDC", "07182-236147", null),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhozp_gondia@rediffmail.com"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Jalgaon",
            authorityName = "Jalgaon Municipal Corporation (JMC)",
            governanceType = "Municipal Corporation",
            district = "Jalgaon",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "0257-2242100", null),
                    DepartmentContact("DEOC", "0257-2217193", null),
                    DepartmentContact("RDC", "0257-2220800", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "0257-2223616", "sejalgaon@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhojalgaon@rediffmail.com , dpmnrhmjalgaon1@gmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", null, "jmcjalgaon@yahoo.co.in"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "0257-2223612", "jalgaon.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "jmcjalgaon@yahoo.co.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Jalna",
            authorityName = "Jalna Municipal Corporation (JMC)",
            governanceType = "Municipal Corporation",
            district = "Jalna",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "02482-224700", null),
                    DepartmentContact("DEOC", "02482-223132", null),
                    DepartmentContact("RDC", "02482-225502", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "02482-225412", "sejalna@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhojalna@rediffmail.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "02482-225302", "jalna.se@mahapwd.gov.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Kalyan",
            authorityName = "Kalyan-Dombivli Municipal Corporation (KDMC)",
            governanceType = "Municipal Corporation (Cluster)",
            district = "Thane",
            departments = listOf(
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "0251-2315808", "sekalyan@mahadiscom.in"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "0251-2206206", null),
                    DepartmentContact("Property Tax Contact", null, "propertytax@kdmc.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "commissioner@kdmc.gov.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Kolhapur",
            authorityName = "Kolhapur Municipal Corporation (KMC)",
            governanceType = "Municipal Corporation",
            district = "Kolhapur",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "0231-2654811", null),
                    DepartmentContact("DEOC", "0231-2652954", null),
                    DepartmentContact("RDC", "0231-2654812", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "0231-2661815", "sekolhapur@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhokolhapur1@gmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "8087773368", null),
                    DepartmentContact("Property Tax Contact", null, "kmchelpdesk@ascentechindia.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "0231-2661559", "kolhapur.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "kmc@maharashtra.gov.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Latur",
            authorityName = "Latur Municipal Corporation (LMC)",
            governanceType = "Municipal Corporation",
            district = "Latur",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "02382-224001", null),
                    DepartmentContact("DEOC", "02382-220204", null),
                    DepartmentContact("RDC", "02382-223234", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "02382-245329", "selatur@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dholatur@rediffmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "02382-242853", null),
                    DepartmentContact("Property Tax Contact", null, "laturcorporation@gmail.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "02382-221048", "latur.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "laturcorporation@gmail.com"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Malegaon",
            authorityName = "Malegaon Municipal Corporation (MMC)",
            governanceType = "Municipal Corporation",
            district = "Nashik",
            departments = listOf(
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "02554-251218", "semalegaon@mahadiscom.in"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "02554-242007", null),
                    DepartmentContact("Property Tax Contact", null, "malegaoncorporation@gmail.com"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Mumbai",
            authorityName = "Brihanmumbai Municipal Corporation (BMC)",
            governanceType = "Municipal Corporation",
            district = "Mumbai City / Suburban",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "022-22662440", null),
                    DepartmentContact("DEOC", "022-22664232", null),
                    DepartmentContact("RDC", "022-22670656", null),
                    DepartmentContact("Collector", "022-69403311", null),
                    DepartmentContact("DEOC", "022-69403344", null),
                    DepartmentContact("RDC", "022-69403333", null),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "1800-222-828", null),
                    DepartmentContact("Property Tax Contact", null, "propertyportal@mcgm.gov.in"),
                    DepartmentContact("Property Tax Contact", "8291920504", null),
                    DepartmentContact("Property Tax Contact", null, "propertytax@nmmconline.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "022-22072510", "mumbai.ce@mahapwd.gov.in"),
                    DepartmentContact("Office / Circle", "022-22072512", "mumbai.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Engineering Desk", null, "dmc.engg@mcgm.gov.in"),
                    DepartmentContact("Engineering Desk", null, "mc@mcgm.gov.in"),
                    DepartmentContact("Water & Infrastructure", null, "cityengineer@nmmc.gov.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Nanded",
            authorityName = "Nanded-Waghala Municipal Corporation (NWMC)",
            governanceType = "Municipal Corporation (Cluster)",
            district = "Nanded",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "02462-237101", null),
                    DepartmentContact("DEOC", "02462-235077", null),
                    DepartmentContact("RDC", "02462-248418", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "02462-2252115", "senanded@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhonanded4@gmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "02462-234645", null),
                    DepartmentContact("Property Tax Contact", null, "nwcmcnd@yahoo.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "02462-2252112", "nanded.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "nwcmcnd@yahoo.com"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Nashik",
            authorityName = "Nashik Municipal Corporation (NMC)",
            governanceType = "Municipal Corporation",
            district = "Nashik",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "0253-2578500", null),
                    DepartmentContact("DEOC", "0253-2317151", null),
                    DepartmentContact("RDC", "0253-2578501", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "0253-2575638", "senashik@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhonashik@gmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "0253-2575631", null),
                    DepartmentContact("Property Tax Contact", null, "taxdept@nmc.gov.in"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "0253-2575631", "nashik.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Regional Water Head", null, "cem.nashikwrd@maharashtra.gov.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Panvel",
            authorityName = "Panvel Municipal Corporation (PMC)",
            governanceType = "Municipal Corporation",
            district = "Raigad",
            departments = listOf(
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "1800-227701", null),
                    DepartmentContact("Property Tax Contact", null, "panvelcorporation@gmail.com"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "panvelcorporation@gmail.com"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Parbhani",
            authorityName = "Parbhani Municipal Corporation (PMC)",
            governanceType = "Municipal Corporation",
            district = "Parbhani",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "02452-223555", null),
                    DepartmentContact("DEOC", "02452-226400", null),
                    DepartmentContact("RDC", "02452-223702", null),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhopar@rediffmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "02452-223456", null),
                    DepartmentContact("Property Tax Contact", null, "mcparbhani@gmail.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "02452-223612", "parbhani.se@mahapwd.gov.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Pimpri",
            authorityName = "Pimpri-Chinchwad Municipal Corporation (PCMC)",
            governanceType = "Municipal Corporation (Cluster)",
            district = "Pune",
            departments = listOf(
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "020-67331111", null),
                    DepartmentContact("Property Tax Contact", null, "propertytax@pcmcindia.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Central Citizens Help Desk", null, "sarathi@pcmcindia.gov.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Pune",
            authorityName = "Pune Municipal Corporation (PMC)",
            governanceType = "Municipal Corporation",
            district = "Pune",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "020-26114949", null),
                    DepartmentContact("DEOC", "020-26123371", null),
                    DepartmentContact("RDC", "020-26122114", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "020-25654512", "seganeshkhind@mahadiscom.in"),
                    DepartmentContact("Circle Office", "020-26131341", "sepune@mahadiscom.in"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "020-25501157", null),
                    DepartmentContact("Property Tax Contact", null, "propertytax@punecorporation.org"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "020-26122421", "pune.se@mahapwd.gov.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Sangli",
            authorityName = "Sangli-Miraj-Kupwad Municipal Corporation (SMKMC)",
            governanceType = "Municipal Corporation (Cluster)",
            district = "Sangli",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "0233-2602001", null),
                    DepartmentContact("DEOC", "0233-2600500", null),
                    DepartmentContact("RDC", "0233-2600059", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "0233-2670312", "sesangli@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "zpdhosan@yahoo.co.in , zpdhosan@gmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "0233-2373721", null),
                    DepartmentContact("Property Tax Contact", null, "smkcorporation@gmail.com"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Satara",
            authorityName = "Satara Municipal Council",
            governanceType = "Class-A Municipal Council",
            district = "Satara",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "02162-232750", null),
                    DepartmentContact("DEOC", "02162-232349", null),
                    DepartmentContact("RDC", "02162-236133", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "02162-234563", "sesatara@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhozpsatara@gmail.com , dhosatara@gmail.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "02162-234208", "satara.se@mahapwd.gov.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Solapur",
            authorityName = "Solapur Municipal Corporation (SMC)",
            governanceType = "Municipal Corporation",
            district = "Solapur",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "0217-2731000", null),
                    DepartmentContact("DEOC", "0217-2731012", null),
                    DepartmentContact("RDC", "0217-2731002", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "0217-2310165", "sesolapur@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhosolapur@gmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", null, "smcproperty@yahoo.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "0217-2312668", "solapur.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "smccommissioner@yahoo.co.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Thane",
            authorityName = "Thane Municipal Corporation (TMC)",
            governanceType = "Municipal Corporation",
            district = "Thane",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "022-25344041", null),
                    DepartmentContact("DEOC", "022-25301740", null),
                    DepartmentContact("RDC", "022-25345130", null),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhozpthane@rediffmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "1800-222-108", null),
                    DepartmentContact("Property Tax Contact", null, "taxproperty@thanecity.gov.in"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "022-25363217", "thane.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Municipal Commissioner", null, "tc@thanecity.gov.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Ulhasnagar",
            authorityName = "Ulhasnagar Municipal Corporation (UMC)",
            governanceType = "Municipal Corporation",
            district = "Thane",
            departments = listOf(
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", null, "umcpropertytax@gmail.com"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("Water Department Contact", null, "umccommissioner@gmail.com"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Vasai",
            authorityName = "Vasai-Virar City Municipal Corporation (VVCMC)",
            governanceType = "Municipal Corporation (Cluster)",
            district = "Palghar",
            departments = listOf(
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "0250-2334415", "sevasai@mahadiscom.in"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", null, "tax.vvcmc@yahoo.com"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Wardha",
            authorityName = "Wardha Municipal Council",
            governanceType = "Class-A Municipal Council",
            district = "Wardha",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "07152-240102", null),
                    DepartmentContact("DEOC", "07152-243446", null),
                    DepartmentContact("RDC", "07152-240872", null),
                )),
                Department("Electricity Department (MSEDCL)", listOf(
                    DepartmentContact("Circle Office", "07152-240212", "sewardha@mahadiscom.in"),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhowardha@rediffmail.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "07152-240218", "wardha.se@mahapwd.gov.in"),
                )),
            )
        ),
        MunicipalCorporation(
            city = "Yavatmal",
            authorityName = "Yavatmal Municipal Council",
            governanceType = "Class-A Municipal Council",
            district = "Yavatmal",
            departments = listOf(
                Department("Disaster Management", listOf(
                    DepartmentContact("Collector", "07232-242501", null),
                    DepartmentContact("DEOC", "07232-240720", null),
                    DepartmentContact("RDC", "07232-242488", null),
                )),
                Department("Healthcare / District Health Office", listOf(
                    DepartmentContact("District Health Officer (email listed)", null, "dhoyeo@rediffmail.com"),
                )),
                Department("Property Tax Department", listOf(
                    DepartmentContact("Property Tax Contact", "07232-244243", null),
                    DepartmentContact("Property Tax Contact", null, "yavatmal.dpo@gmail.com"),
                )),
                Department("Public Works Department (PWD)", listOf(
                    DepartmentContact("Office / Circle", "07232-244218", "yavatmal.se@mahapwd.gov.in"),
                )),
                Department("Water Department", listOf(
                    DepartmentContact("District Nodal Office", null, "yavatmal.dpo@gmail.com"),
                )),
            )
        ),
    )

    /** All unique city names for the municipal corporation picker. */
    val cityNames: List<String> = corporations.map { it.city }

    /** Look up corporation by city name. */
    fun forCity(city: String): MunicipalCorporation? = corporations.firstOrNull {
        it.city.equals(city, ignoreCase = true) || it.authorityName.contains(city, ignoreCase = true)
    }

    /** Get the department that maps to an IssueCategory code. */
    fun departmentForCategory(corp: MunicipalCorporation, categoryCode: String): Department? {
        val deptName = when (categoryCode) {
            "DISASTER_MANAGEMENT" -> "Disaster Management"
            "ELECTRICITY_MSEDCL" -> "Electricity Department (MSEDCL)"
            "HEALTHCARE_DISTRICT_HEALTH" -> "Healthcare / District Health Office"
            "PROPERTY_TAX" -> "Property Tax Department"
            "PUBLIC_WORKS_PWD" -> "Public Works Department (PWD)"
            "WATER_DEPARTMENT" -> "Water Department"
            else -> null
        }
        return deptName?.let { name -> corp.departments.firstOrNull { it.name == name } }
    }
}


package com.obdreader.app.obd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A fake adapter on a car that only answers on one protocol, like a 2003 Honda on ISO 9141-2. */
private class OneProtocolCar(private val answersOn: String) : ObdTransport {
    override val name = "fake"
    var current = "0"
    val tried = mutableListOf<String>()

    override fun open() {}
    override fun close() {}

    override fun exchange(command: String, timeoutMs: Long): String {
        val cmd = command.uppercase()
        val body = when {
            cmd.startsWith("ATSP") -> { current = cmd.removePrefix("ATSP").removePrefix("A"); tried += current; "OK" }
            cmd == "ATDPN" -> answersOn
            cmd == "ATDP" -> "ISO 9141-2"
            cmd == "ATI" -> "ELM327 v1.5"
            cmd.startsWith("AT") -> "OK"
            // A clone whose auto-search ("0") misses this car, as often happens on K-line.
            cmd == "0100" -> if (current == answersOn) "4100BE3EB811" else "UNABLE TO CONNECT"
            else -> "NO DATA"
        }
        return "$body\r\r"
    }
}

class ProtocolSearchTest {

    @Test
    fun findsIso9141WhenAutoSearchFails() = runBlocking {
        val car = OneProtocolCar("3")
        val info = ElmClient(car).initialize()
        assertEquals("3", info.protocolCode)
        assertTrue(info.summary.contains("ISO 9141-2"))
        assertEquals(listOf("0", "6", "1", "3"), car.tried)
    }

    @Test
    fun lastWorkingProtocolIsTriedFirst() = runBlocking {
        val car = OneProtocolCar("1")
        val client = ElmClient(car)
        val info = client.initialize(lastWorking = "1")
        assertEquals("1", info.protocolCode)
        assertEquals(listOf("1"), car.tried)
        assertFalse(client.isCan)
    }

    @Test
    fun forcedProtocolFailsWithAClearMessage() = runBlocking {
        val car = OneProtocolCar("3")
        val error = runCatching { ElmClient(car).initialize(protocol = "1") }.exceptionOrNull()
        assertTrue(error is ObdException)
        assertTrue(error!!.message!!.contains("SAE J1850 PWM"))
    }
}

class CarPickerTest {

    @Test
    fun chosenCarsProtocolIsTriedBeforeAutoSearch() = runBlocking {
        val car = OneProtocolCar("3")
        val order = VehicleCatalog.recommend(VehicleCatalog.Car("Honda", 2003, "Civic")).order
        val info = ElmClient(car).initialize(carOrder = order)
        assertEquals("3", info.protocolCode)
        assertEquals(listOf("3"), car.tried)
    }

    @Test
    fun wrongGuessStillFallsBackToEveryProtocol() = runBlocking {
        val car = OneProtocolCar("9") // the catalogue would never guess this first
        val order = VehicleCatalog.recommend(VehicleCatalog.Car("Ford", 2003, "Focus")).order
        val info = ElmClient(car).initialize(carOrder = order)
        assertEquals("9", info.protocolCode)
    }

    @Test
    fun recommendationsPerMakeAndYear() {
        fun likely(make: String, year: Int, model: String?) =
            VehicleCatalog.recommend(VehicleCatalog.Car(make, year, model)).order.first()
        assertEquals("1", likely("Ford", 2002, "Focus"))
        assertEquals("6", likely("Ford", 2004, "Falcon"))
        assertEquals("6", likely("Ford", 2012, "Ranger"))
        assertEquals("3", likely("Honda", 2003, "Civic"))
        assertEquals("1", likely("Mazda", 2003, "Tribute"))
        assertEquals("3", likely("Mazda", 2003, "323"))
        assertEquals("6", likely("Mazda", 2015, "CX-5"))
        // Every recommendation still covers all nine protocols.
        assertEquals(9, VehicleCatalog.recommend(VehicleCatalog.Car("Other make", 2001, null)).order.size)
    }

    @Test
    fun modelSearchFiltersByYearAndText() {
        assertEquals(listOf("Falcon"), VehicleCatalog.models("Ford", 2010, "fal").map { it.name })
        assertTrue(VehicleCatalog.models("Ford", 2002, "").none { it.name == "Territory" })
        assertTrue(VehicleCatalog.models("Mazda", 2016, "cx").map { it.name }.containsAll(listOf("CX-3", "CX-5", "CX-9")))
    }
}

class EngineTest {

    @Test
    fun tributeEnginesDependOnYear() {
        fun names(year: Int) = VehicleCatalog.engines("Mazda", year, "Tribute").map { it.name }
        assertEquals(listOf("2.0 I4", "3.0 V6"), names(2002))
        assertEquals(listOf("2.3 I4", "3.0 V6"), names(2006))
    }

    @Test
    fun engineSetsRedlineAndDieselNote() {
        val diesel = VehicleCatalog.recommend(VehicleCatalog.Car("Ford", 2015, "Ranger", "3.2 diesel"))
        assertEquals(4500, diesel.redline)
        assertTrue(diesel.note!!.contains("Diesels"))
        assertEquals(9000, VehicleCatalog.recommend(VehicleCatalog.Car("Mazda", 2008, "RX-8", "1.3 rotary")).redline)
        // An engine typed by hand: matched by its size ("2.4" is the 2.4 I4).
        assertEquals(6800, VehicleCatalog.recommend(VehicleCatalog.Car("Honda", 2010, "Odyssey", "2.4")).redline)
    }

    @Test
    fun theRevDialFollowsTheCar() {
        fun rec(make: String, year: Int, model: String?, engine: String?) = VehicleCatalog.recommend(VehicleCatalog.Car(make, year, model, engine))
        // George's Tribute: the 2.3 I4 revs to 6,500, so the dial ends at 7k, not 8k.
        val tribute = rec("Mazda", 2006, "Tribute", "2.3 I4")
        assertEquals(6500, tribute.redline); assertTrue(tribute.redlineExact); assertEquals(7000, tribute.dialMax)
        assertEquals(7000, rec("Mazda", 2006, "Tribute", "2.3").dialMax)            // typed by hand
        // No engine chosen: the model's highest redline that year, marked as not exact.
        val noEngine = rec("Mazda", 2006, "Tribute", null)
        assertEquals(6500, noEngine.redline); assertEquals(false, noEngine.redlineExact)
        // High-revving and low-revving engines get dials to match.
        assertEquals(10000, rec("Mazda", 2008, "RX-8", "1.3 rotary").dialMax)
        assertEquals(10000, rec("Honda", 2002, "S2000", "2.0").dialMax)
        assertTrue(rec("Ram", 2015, "2500 / 3500", "6.7 diesel").dialMax <= 5000)
        // Nothing known: the default dial.
        assertEquals(8000, rec("Other", 2010, null, null).dialMax)
        assertEquals(8000, VehicleCatalog.dialMax(null))
        assertEquals(7000, VehicleCatalog.dialMax(6000)); assertEquals(8000, VehicleCatalog.dialMax(7200)); assertEquals(9000, VehicleCatalog.dialMax(8300))
        // Across the catalogue, the dial always ends above the redline, with room to show it.
        for (make in VehicleCatalog.MAKES) for (m in make.models) for (e in m.engines) {
            val d = VehicleCatalog.dialMax(e.redline)
            assertTrue("${make.name} ${m.name} ${e.name}", d >= e.redline + 500 && d <= e.redline + 1500)
        }
    }

    @Test
    fun engineDoesNotChangeProtocolOrder() {
        val a = VehicleCatalog.recommend(VehicleCatalog.Car("Mazda", 2003, "Tribute", "2.0 I4")).order
        val b = VehicleCatalog.recommend(VehicleCatalog.Car("Mazda", 2003, "Tribute", "3.0 V6")).order
        assertEquals(a, b)
    }

    @Test
    fun labelIncludesEngine() {
        assertEquals("2004 Mazda Tribute 3.0 V6", VehicleCatalog.Car("Mazda", 2004, "Tribute", "3.0 V6").label)
    }
}

class ToyotaBmwTest {

    private fun likely(make: String, year: Int, model: String?) =
        VehicleCatalog.recommend(VehicleCatalog.Car(make, year, model)).order.first()

    @Test
    fun toyotaProtocolsByYear() {
        assertEquals("3", likely("Toyota", 2003, "Corolla"))
        assertEquals("6", likely("Toyota", 2006, "Camry"))
        assertEquals("6", likely("Toyota", 2016, "HiLux"))
        assertEquals("6", likely("Toyota", 2020, "Supra"))
    }

    @Test
    fun bmwProtocolsByYearAndNote() {
        assertEquals("3", likely("BMW", 2002, "3 Series"))
        assertEquals("5", likely("BMW", 2006, "3 Series"))
        assertEquals("6", likely("BMW", 2015, "X5"))
        assertTrue(VehicleCatalog.recommend(VehicleCatalog.Car("BMW", 2015, "X5")).note!!.contains("BMW-specific"))
    }

    @Test
    fun modelsAndEngines() {
        assertTrue(VehicleCatalog.models("Toyota", 2010, "land").map { it.name }.containsAll(listOf("LandCruiser", "LandCruiser Prado")))
        assertEquals(listOf("2.5 diesel", "3.0 diesel"), VehicleCatalog.engines("Toyota", 2010, "HiLux").filter { it.fuel == VehicleCatalog.Fuel.DIESEL }.map { it.name })
        assertEquals(8300, VehicleCatalog.recommend(VehicleCatalog.Car("BMW", 2010, "M3", "4.0 V8")).redline)
    }

    @Test
    fun vinMakes() {
        assertEquals("Toyota", VinDecoder.decode("JTDBR32E720123456").make)
        assertEquals("Toyota", VinDecoder.decode("6T1BF3EK5CX123456").make)
        assertEquals("Lexus (Toyota)", VinDecoder.decode("JTHBA30G465123456").make)
        assertEquals("BMW", VinDecoder.decode("WBAVA31070N123456").make)
        assertEquals(2016, VinDecoder.decode("5UXKR0C50G0P12345").modelYear)
    }
}

class MercedesTest {

    private fun rec(year: Int, model: String?, engine: String? = null) =
        VehicleCatalog.recommend(VehicleCatalog.Car("Mercedes-Benz", year, model, engine))

    @Test
    fun protocolsByYear() {
        assertEquals("3", rec(2003, "C-Class").order.first())
        assertEquals("5", rec(2006, "E-Class").order.first())
        assertEquals("6", rec(2014, "GLE / M-Class").order.first())
    }

    @Test
    fun notes() {
        assertTrue(rec(2014, "C-Class").note!!.contains("dealer tools"))
        assertTrue(rec(2018, "X-Class").note!!.contains("Nissan"))
        assertTrue(rec(2010, "Sprinter", "3.0 V6 diesel").note!!.contains("Diesels"))
    }

    @Test
    fun enginesAndVin() {
        assertEquals(listOf("2.6 V6", "3.2 V6", "1.8 supercharged", "2.1 diesel"), VehicleCatalog.engines("Mercedes-Benz", 2003, "C-Class").map { it.name })
        assertEquals(7200, rec(2010, "C-Class", "6.2 V8 (AMG)").redline)
        assertEquals("Mercedes-Benz", VinDecoder.decode("WDD2040482A123456").make)
        assertEquals("Mercedes-Benz", VinDecoder.decode("4JGBB86E37A123456").make)
        assertEquals(2007, VinDecoder.decode("4JGBB86E37A123456").modelYear)
    }
}

class GmAndVwGroupTest {

    private fun rec(make: String, year: Int, model: String?, engine: String? = null) =
        VehicleCatalog.recommend(VehicleCatalog.Car(make, year, model, engine))

    @Test
    fun holdenCommodoreByGeneration() {
        assertEquals("2", rec("Holden", 2002, "Commodore").order.first())
        assertTrue(rec("Holden", 2002, "Commodore").note!!.contains("VX and VY"))
        assertEquals("6", rec("Holden", 2005, "Commodore").order.first())
        assertEquals("6", rec("Holden", 2010, "Ute").order.first())
        assertEquals(listOf("3.6 V6", "6.0 V8", "3.0 V6"), VehicleCatalog.engines("Holden", 2010, "Commodore").map { it.name })
    }

    @Test
    fun gmNorthAmericaUsesVpwBefore2008() {
        assertEquals("2", rec("Chevrolet", 2003, "Silverado").order.first())
        assertTrue(rec("Chevrolet", 2003, "Silverado").note!!.contains("J1850 VPW"))
        assertEquals("6", rec("GMC", 2012, "Sierra").order.first())
        assertEquals(3600, rec("Chevrolet", 2015, "Silverado", "6.6 diesel").redline)
    }

    @Test
    fun badgeEngineeredModels() {
        assertTrue(rec("Pontiac", 2008, "G8").note!!.contains("Holden"))
        assertEquals("3", rec("Pontiac", 2004, "Vibe").order.first())
        assertTrue(rec("Saab", 2005, "9-2X").note!!.contains("Subaru"))
    }

    @Test
    fun vwGroup() {
        assertEquals("3", rec("Volkswagen", 2002, "Golf").order.first())
        assertEquals("6", rec("Audi", 2006, "A3").order.first())
        assertTrue(rec("Skoda", 2015, "Octavia").note!!.contains("VCDS"))
        assertTrue(rec("Porsche", 2015, "911").note!!.contains("Porsche's dealer tools"))
        assertEquals(9000, rec("Porsche", 2018, "911", "4.0 (GT3)").redline)
    }

    @Test
    fun makesAreAlphabeticalAndVinsDecode() {
        val names = VehicleCatalog.makeNames.dropLast(1)
        assertEquals(names.sortedBy { it.lowercase() }, names)
        assertEquals(VehicleCatalog.OTHER_MAKE, VehicleCatalog.makeNames.last())
        assertEquals("Holden", VinDecoder.decode("6G1ZK52F67L123456").make)
        assertEquals("Volkswagen", VinDecoder.decode("WVWZZZ1KZ6W123456").make)
        assertEquals("Chevrolet", VinDecoder.decode("1GCEK19T63E123456").make)
        assertEquals(2003, VinDecoder.decode("1GCEK19T63E123456").modelYear)
    }
}

class StellantisTest {

    private fun rec(make: String, year: Int, model: String?, engine: String? = null) =
        VehicleCatalog.recommend(VehicleCatalog.Car(make, year, model, engine))

    @Test
    fun chryslerDodgeJeepUseVpwBefore2008() {
        assertEquals("2", rec("Jeep", 2003, "Grand Cherokee").order.first())
        assertTrue(rec("Dodge", 2004, "Durango").note!!.contains("J1850 VPW"))
        assertEquals("6", rec("Chrysler", 2006, "300 / 300C").order.first())
        assertEquals(3200, rec("Ram", 2015, "2500 / 3500", "6.7 diesel").redline)
    }

    @Test
    fun europeanBrands() {
        assertEquals("3", rec("Peugeot", 2003, "206").order.first())
        assertEquals("6", rec("Alfa Romeo", 2018, "Giulia").order.first())
        assertTrue(rec("Citroën", 2015, "C4").note!!.contains("DiagBox"))
        assertTrue(rec("Fiat", 2012, "500").note!!.contains("AlfaOBD"))
        assertTrue(rec("Maserati", 2018, "Levante").note!!.contains("Maserati's dealer tools"))
    }

    @Test
    fun sharedPlatformModels() {
        assertTrue(rec("Chrysler", 2005, "Crossfire").note!!.contains("Mercedes"))
        assertTrue(rec("Fiat", 2017, "124 Spider").note!!.contains("Mazda"))
        assertTrue(rec("Citroën", 2010, "C1").note!!.contains("Toyota"))
        assertTrue(rec("Fiat", 2017, "Fullback").note!!.contains("Triton"))
    }

    @Test
    fun vins() {
        assertEquals("Jeep", VinDecoder.decode("1J4GW58N84C123456").make)
        assertEquals(2004, VinDecoder.decode("1J4GW58N84C123456").modelYear)
        assertEquals("Peugeot", VinDecoder.decode("VF33C9HYB84123456").make)
        assertEquals("Fiat", VinDecoder.decode("ZFA31200000123456").make)
    }
}

class RemainingBrandsTest {

    private fun rec(make: String, year: Int, model: String?, engine: String? = null) =
        VehicleCatalog.recommend(VehicleCatalog.Car(make, year, model, engine))

    @Test
    fun japaneseBrands() {
        assertEquals("3", rec("Nissan", 2003, "Pulsar / Sentra").order.first())
        assertEquals("6", rec("Subaru", 2015, "WRX / STI").order.first())
        assertEquals(7000, rec("Mitsubishi", 2008, "Lancer Evolution", "2.0 turbo").redline)
        assertEquals("6", rec("Isuzu", 2010, "D-Max").order.first())
        assertEquals("3", rec("Lexus", 2003, "IS").order.first())
        assertEquals("3", rec("Acura", 2004, "RSX").order.first())
        assertTrue(rec("Nissan", 2004, "Patrol", "4.2 diesel").note!!.contains("Diesels"))
    }

    @Test
    fun americanBrands() {
        assertEquals("1", rec("Lincoln", 2002, "Navigator").order.first())
        assertEquals("2", rec("Oldsmobile", 2003, "Alero").order.first())
        assertEquals("2", rec("Plymouth", 2001, "Neon").order.first())
        assertFalse(rec("Mercury", 2005, "Mariner").note?.contains("Australia") == true)
        assertTrue(rec("Tesla", 2022, "Model 3").note!!.contains("no standard OBD2 port"))
        assertTrue(rec("Rivian", 2023, "R1T").note!!.contains("Electric"))
    }

    @Test
    fun europeanBrands() {
        assertEquals("6", rec("Volvo", 2006, "XC90").order.first())
        assertTrue(rec("Volvo", 2006, "XC90").note!!.contains("Volvo"))
        assertTrue(rec("Land Rover", 2012, "Discovery").note!!.contains("Land Rover"))
        assertTrue(rec("Renault", 2014, "Mégane").note!!.contains("Renault"))
        assertTrue(rec("Ferrari", 2015, "458").note!!.contains("Ferrari's dealer tools"))
        assertTrue(rec("Mini", 2010, "Hatch / Cooper").note!!.contains("BMW"))
        assertTrue(rec("Polestar", 2022, "Polestar 2").note!!.contains("Electric"))
        assertTrue(rec("Nissan", 2015, "Leaf").note!!.contains("Electric"))
    }

    @Test
    fun sharedPlatformModels() {
        assertTrue(rec("Infiniti", 2017, "Q30").note!!.contains("Mercedes"))
        assertTrue(rec("Isuzu", 2005, "Ascender").note!!.contains("GM"))
        assertEquals("2", rec("Isuzu", 2005, "Ascender").order.first())
        assertTrue(rec("Scion", 2016, "iA").note!!.contains("Mazda"))
        assertTrue(rec("Smart", 2005, "ForFour").note!!.contains("Mitsubishi"))
    }

    @Test
    fun everyMakeTriesEveryProtocol() {
        val all = ObdProtocols.ALL.map { it.code }.toSet()
        VehicleCatalog.MAKES.forEach { make ->
            make.models.forEach { model ->
                val r = rec(make.name, model.from, model.name)
                assertEquals("${make.name} ${model.name}", all, r.order.toSet())
            }
        }
    }

    @Test
    fun vins() {
        assertEquals("Nissan", VinDecoder.decode("JN1TANT31U0123456").make)
        assertEquals("Subaru", VinDecoder.decode("JF1GE7E68BG123456").make)
        assertEquals("Tesla", VinDecoder.decode("5YJ3E1EA6KF123456").make)
        assertEquals(2019, VinDecoder.decode("5YJ3E1EA6KF123456").modelYear)
        assertEquals("Volvo", VinDecoder.decode("YV1CZ91H641123456").make)
        assertEquals("Land Rover", VinDecoder.decode("SALLAAA146A123456").make)
    }
}

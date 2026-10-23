package rtl.geo

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Path

class PlaceIndexTest extends AnyFlatSpec with Matchers:
  private lazy val places = PlaceIndex.load(Path.of("data/geo")) // the real Natural Earth data, downloaded once

  // expected names cross-checked with an independent point-in-polygon run over the same data
  "PlaceIndex" should "name points on land after their country" in {
    places.locate(41.39, 2.17) shouldBe Place("Spain", PlaceKind.Country)
    places.locate(-25.0, 135.0) shouldBe Place("Australia", PlaceKind.Country)
  }

  it should "name points at sea after the sea or ocean" in {
    places.locate(41.0, 2.8) shouldBe Place("Balearic Sea", PlaceKind.Sea)
    places.locate(25.0, -90.0) shouldBe Place("Gulf of Mexico", PlaceKind.Sea)
    places.locate(30.0, -40.0) shouldBe Place("North Atlantic Ocean", PlaceKind.Sea)
    places.locate(15.0, 88.0) shouldBe Place("Bay of Bengal", PlaceKind.Sea)
  }

  it should "fall back to the nearest shape where the sea polygons leave gaps" in {
    places.locate(5.0, 5.0) shouldBe Place("Gulf of Guinea", PlaceKind.Sea) // 0.2° from that polygon
    places.locate(-65.0, 100.0) shouldBe Place("Antarctica", PlaceKind.Country) // 0.5° off its coast
  }

  it should "use full English country names, not abbreviated map labels" in {
    places.locate(-2.0, 23.0).name shouldBe "Democratic Republic of the Congo" // "Dem. Rep. Congo" as a label
    places.locate(43.86, 18.41).name shouldBe "Bosnia and Herzegovina" // "Bosnia and Herz."
    places.locate(18.7, -70.2).name shouldBe "Dominican Republic" // "Dominican Rep."
  }

  it should "use readable names for upper-case source names" in {
    places.locate(-62.0, 100.0).name shouldBe "Southern Ocean" // "SOUTHERN OCEAN" in the source data
  }

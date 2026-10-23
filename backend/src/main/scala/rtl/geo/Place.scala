package rtl.geo

enum PlaceKind:
  case Country, Sea

/** A named area on the globe: a country, or a sea/ocean (Natural Earth names). */
case class Place(name: String, kind: PlaceKind)

object Place:
  val OpenOcean: Place = Place("Open ocean", PlaceKind.Sea)

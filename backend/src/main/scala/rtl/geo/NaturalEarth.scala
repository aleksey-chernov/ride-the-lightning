package rtl.geo

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import ox.resilience.{retry, RetryConfig}
import ox.scheduling.Schedule
import rtl.Logging
import sttp.client4.*

import java.nio.file.{Files, Path, StandardCopyOption}
import scala.concurrent.duration.*

/** Natural Earth (public domain) 1:50m country and sea shapes: downloaded on the first start, then read from disk. */
object NaturalEarth extends Logging:
  // pinned natural-earth-vector commit (master on 2026-10-23, after v5.1.2), so the shapes never change underneath us
  private val BaseUrl =
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/ca96624a56bd078437bca8184e78163e5039ad19/geojson"

  /** A place and its polygons; each polygon is rings of `[lon, lat]` points, outer ring first. */
  case class Shape(place: Place, polygons: Array[Array[Array[Array[Double]]]])

  private case class Layer(file: String, kind: PlaceKind, name: Properties => Option[String])
  private val Layers = List(
    Layer("ne_50m_admin_0_countries.geojson", PlaceKind.Country, _.NAME_EN), // full names, not map labels
    Layer("ne_50m_geography_marine_polys.geojson", PlaceKind.Sea, _.name)
  )

  def load(cacheDir: Path): List[Shape] =
    Layers.flatMap { layer =>
      readFromArray[FeatureCollection](cached(cacheDir, layer.file)).features.flatMap { f =>
        for name <- layer.name(f.properties); geometry <- f.geometry
        yield Shape(Place(readable(name), layer.kind), geometry.coordinates.polygons)
      }
    }

  // some sea names are upper-case ("SOUTHERN OCEAN"); mixed-case names stay as they are
  private def readable(name: String): String =
    if name != name.toUpperCase then name else name.toLowerCase.split(' ').map(_.capitalize).mkString(" ")

  private def cached(cacheDir: Path, file: String): Array[Byte] =
    val path = cacheDir.resolve(file)
    if !Files.exists(path) then
      logger.info(s"Downloading $file into $cacheDir (first start only)")
      val bytes = download(s"$BaseUrl/$file")
      Files.createDirectories(cacheDir)
      val part = Files.write(cacheDir.resolve(s"$file.part"), bytes)
      Files.move(part, path, StandardCopyOption.ATOMIC_MOVE) // an interrupted download never looks cached
    Files.readAllBytes(path)

  private def download(url: String): Array[Byte] =
    val backend = DefaultSyncBackend()
    try
      retry(RetryConfig[Throwable, Array[Byte]](Schedule.exponentialBackoff(1.second).maxRetries(3))) {
        basicRequest.get(uri"$url").response(asByteArray).send(backend).body match
          case Right(bytes) => bytes
          case Left(error)  => throw IllegalStateException(s"GET $url failed: $error")
      }
    finally backend.close()

  private case class Properties(NAME_EN: Option[String] = None, name: Option[String] = None)
  private case class Geometry(coordinates: Coordinates)
  private case class Feature(properties: Properties, geometry: Option[Geometry])
  private case class FeatureCollection(features: List[Feature])

  // GeoJSON Polygon coordinates are rings[points], a MultiPolygon's one level deeper; both become a polygon list
  private case class Coordinates(polygons: Array[Array[Array[Array[Double]]]])
  private given JsonValueCodec[Coordinates] = new JsonValueCodec[Coordinates]:
    private val polygon: JsonValueCodec[Array[Array[Array[Double]]]] = JsonCodecMaker.make
    private val multiPolygon: JsonValueCodec[Array[Array[Array[Array[Double]]]]] = JsonCodecMaker.make
    def nullValue: Coordinates = Coordinates(Array.empty)
    def encodeValue(x: Coordinates, out: JsonWriter): Unit = multiPolygon.encodeValue(x.polygons, out)
    def decodeValue(in: JsonReader, default: Coordinates): Coordinates =
      in.setMark()
      val depth = Iterator.continually(in.isNextToken('[')).takeWhile(identity).size
      in.rollbackToMark()
      if depth == 3 then Coordinates(Array(polygon.decodeValue(in, polygon.nullValue)))
      else Coordinates(multiPolygon.decodeValue(in, multiPolygon.nullValue))
  private given JsonValueCodec[FeatureCollection] = JsonCodecMaker.make

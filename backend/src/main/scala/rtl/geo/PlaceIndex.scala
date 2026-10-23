package rtl.geo

import java.nio.file.Path
import scala.collection.mutable

/** Finds the country or sea containing a point. Countries win over seas; points in no shape get the nearest shape
  * within [[PlaceIndex.NearestWithinDegrees]], else [[Place.OpenOcean]]. Immutable after construction, so thread-safe.
  */
final class PlaceIndex private (shapes: Array[PlaceIndex.Shape], grid: Array[Array[Int]]):
  import PlaceIndex.*

  def locate(lat: Double, lon: Double): Place =
    val candidates = grid(cellOf(lat, lon)).map(shapes)
    def containing(kind: PlaceKind) = candidates.find(s => s.place.kind == kind && s.contains(lon, lat))
    containing(PlaceKind.Country).orElse(containing(PlaceKind.Sea)).map(_.place).getOrElse(nearest(lat, lon))

  private def nearest(lat: Double, lon: Double): Place =
    val r = NearestWithinDegrees.toInt
    val nearby = (for
      dLat <- -r to r
      dLon <- -r to r
      id <- grid(cellOf(lat + dLat, lon + dLon))
    yield id).distinct.map(shapes)
    val scale = math.cos(math.toRadians(lat)) // a degree of longitude shrinks towards the poles
    val closest = nearby.map(s => s.place -> s.distanceTo(lon, lat, scale)).minByOption(_._2)
    closest.collect { case (place, d) if d <= NearestWithinDegrees => place }.getOrElse(Place.OpenOcean)

object PlaceIndex:
  val NearestWithinDegrees = 3.0

  private final case class Ring(xs: Array[Double], ys: Array[Double])

  private final case class Polygon(
      outer: Ring,
      holes: Array[Ring],
      minX: Double,
      maxX: Double,
      minY: Double,
      maxY: Double
  ):
    def contains(x: Double, y: Double): Boolean =
      x >= minX && x <= maxX && y >= minY && y <= maxY && inRing(outer, x, y) && !holes.exists(inRing(_, x, y))

  private final case class Shape(place: Place, polygons: Array[Polygon]):
    def contains(x: Double, y: Double): Boolean = polygons.exists(_.contains(x, y))
    def distanceTo(x: Double, y: Double, xScale: Double): Double =
      polygons.iterator.flatMap(p => Iterator(p.outer) ++ p.holes).map(ringDistance(_, x, y, xScale)).min

  /** Builds the index from Natural Earth shapes, downloading them into `cacheDir` on the first start. */
  def load(cacheDir: Path): PlaceIndex =
    val shapes = NaturalEarth.load(cacheDir).map(s => Shape(s.place, s.polygons.map(toPolygon))).toArray
    val cells = Array.fill(180 * 360)(mutable.ArrayBuilder.make[Int])
    for
      (shape, id) <- shapes.zipWithIndex
      cellIds = shape.polygons.flatMap(p => cellsCovering(p.minX, p.maxX, p.minY, p.maxY)).distinct
      cell <- cellIds
    do cells(cell) += id
    PlaceIndex(shapes, cells.map(_.result()))

  private def toPolygon(rings: Array[Array[Array[Double]]]): Polygon =
    val all = rings.map(r => Ring(r.map(_(0)), r.map(_(1))))
    val outer = all.head
    Polygon(outer, all.tail, outer.xs.min, outer.xs.max, outer.ys.min, outer.ys.max)

  // 1° cells: row = latitude band (0 = 90°S), column = longitude band (0 = 180°W)
  private def cellOf(lat: Double, lon: Double): Int =
    val row = math.floor(lat + 90).toInt.max(0).min(179)
    val col = Math.floorMod(math.floor(lon + 180).toInt, 360)
    row * 360 + col

  private def cellsCovering(minX: Double, maxX: Double, minY: Double, maxY: Double): Seq[Int] =
    for
      row <- math.floor(minY + 90).toInt.max(0) to math.floor(maxY + 90).toInt.min(179)
      col <- math.floor(minX + 180).toInt.max(0) to math.floor(maxX + 180).toInt.min(359)
    yield row * 360 + col

  // ray casting: count edge crossings of a ray going right from (x, y)
  private def inRing(r: Ring, x: Double, y: Double): Boolean =
    var inside = false
    var i = 0
    var j = r.xs.length - 1
    while i < r.xs.length do
      if (r.ys(i) > y) != (r.ys(j) > y) && x < (r.xs(j) - r.xs(i)) * (y - r.ys(i)) / (r.ys(j) - r.ys(i)) + r.xs(i) then
        inside = !inside
      j = i
      i += 1
    inside

  private def ringDistance(r: Ring, x: Double, y: Double, xScale: Double): Double =
    var best = Double.MaxValue
    var i = 0
    while i < r.xs.length - 1 do
      best = best.min(segmentDistance(x, y, r.xs(i), r.ys(i), r.xs(i + 1), r.ys(i + 1), xScale))
      i += 1
    best

  private def segmentDistance(
      px: Double,
      py: Double,
      ax: Double,
      ay: Double,
      bx: Double,
      by: Double,
      xScale: Double
  ): Double =
    val (dx, dy) = ((bx - ax) * xScale, by - ay)
    val (qx, qy) = ((px - ax) * xScale, py - ay)
    val lengthSq = dx * dx + dy * dy
    val t = if lengthSq == 0 then 0.0 else ((qx * dx + qy * dy) / lengthSq).max(0).min(1)
    math.hypot(qx - t * dx, qy - t * dy)

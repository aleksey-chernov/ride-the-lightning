package rtl.ingest

import ox.flow.Flow
import rtl.strikes.Strike

trait StrikeSource:
  /** Lazy, infinite stream of strikes; nothing happens until it's run. */
  def strikes: Flow[Strike]

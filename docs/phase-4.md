# Phase 4: live stream over Server-Sent Events

## Run it
```bash
INGEST_SOURCE=replay sbt ~backend/reStart          # or IntelliJ "Backend (replay)"
curl -N localhost:8080/api/v1/strikes/stream        # Ctrl+C to disconnect
```
Output, one event per strike plus a heartbeat every 15 s:
```
data: {"time":"2026-10-17T22:00:05.319973509Z","lat":24.559874,"lon":-50.506327,"region":3,...}
event: strike

data:
event: heartbeat
```
The log shows each client: `Stream client connected (1 active)` / `Stream client disconnected, 0 dropped (0 active)`.

## Files
```
strikes/StrikeBroadcaster.scala   fan-out to subscribers; bounded queue per client, drop when full
strikes/StrikesApi.scala          + GET /strikes/stream (SSE); restEndpoints / streamEndpoints
ingest/Ingest.scala               publishes each new (deduplicated) strike to the broadcaster
http/HttpApi.scala                HttpApi[R]: generic over endpoint capabilities (OxStreams for SSE)
test: StrikeBroadcasterTest (unit), StrikeStreamTest (real server on a random port)
```

## How data flows
```
Ingest fork ──publish──> StrikeBroadcaster ──offer──> [queue client A] ──take──> Flow ──> SSE A
                                           └─offer──> [queue client B] ──take──> Flow ──> SSE B
                          queue full? offer returns false → strike dropped for that client only
```

## Concepts
- **SSE**: a plain HTTP response that never ends (`text/event-stream`), each event is `data:` + `event:` lines and a blank line. Browsers read it with `EventSource`, which reconnects automatically. One-way only, which is all we need.
- **Fan-out with bounded queues**: each subscriber gets an `ArrayBlockingQueue(1000)`. `offer` never blocks, so one slow browser can't stall ingest or the other clients; it just misses strikes. C# twin: `Channel.CreateBounded<T>(new BoundedChannelOptions(1000) { FullMode = DropWrite })` per subscriber.
- **Subscription = flow lifetime**: `broadcaster.flow` subscribes when the stream starts and unsubscribes in `finally`. When a client disconnects, the server interrupts the flow's virtual thread (blocked in `queue.take()`), `finally` runs, and the subscriber is gone. No manual cleanup, no leaks.
- **Flow composition**: `strikes.merge(heartbeats)` interleaves two infinite flows; `Flow.tick(15.seconds)` emits immediately, then every 15 s. Heartbeats keep proxies from closing idle connections and make dead connections fail on write.
- **Capabilities in types**: a streaming endpoint is a `ServerEndpoint[OxStreams, Identity]`; plain ones are `ServerEndpoint[Any, Identity]`. `HttpApi[R >: OxStreams & WebSockets]` accepts either. Stub tests use `HttpApi[Any]` because the stub interpreter can't stream (Scala infers the lower bound unless told, so the type is written explicitly).
- **Real-server test**: `HttpConfig("localhost", 0)` binds a free port; the test reads the stream with sttp's `asInputStreamAlways` and parses events the way a browser would.

## Try
1. Open two `curl -N` terminals, then kill one and watch the active count in the log.
2. Change `HeartbeatInterval` to `3.seconds` and watch heartbeats arrive.
3. Simulate a slow client: `curl -N --limit-rate 100 localhost:8080/api/v1/strikes/stream` at `REPLAY_SPEED=50`, disconnect, and read the `dropped` count.

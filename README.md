# fs2-pipeline #

A collection of order-relaxing concurrent operations on
[fs2](https://github.com/functional-streams-for-scala/fs2) streams.

## Installation ##

### Requirements ###

* [SBT](https://www.scala-sbt.org) 0.13.16+
* [fs2-cats-effect](https://github.com/jameshales/fs2-cats-effect)

### Installing ###

`$ sbt publishLocal`

## Usage ##

```scala
import fs2._
import org.jameshales.fs2.pipeline._

val fruits: Stream[IO, Event] = ???
val writeToDatabase: Sink[IO, Event] = ???
val writeToIndex: Sink[IO, Event] = ???
val writeToWorkQueue: Sink[IO, Event] = ???

// Pass a Stream through multiple Sinks sequentially
fruits
  .through(writeToDatabase.passthrough)
  .through(writeToIndex.passthrough)
  .through(writeToWorkQueue.passthrough)
  .run
  .unsafeRunSync

// Pass a Stream through multiple Sinks sequentially with pipelining
fruits
  .pipeline(writeToDatabase.passthrough)
  .pipeline(writeToIndex.passthrough)
  .pipeline(writeToWorkQueue.passthrough)
  .run
  .unsafeRunSync

// Partition a Stream by key, processing each partition concurrently
fruits.joinPartition(4)(_.key)(
    _.pipeline(writeToDatabase.passthrough)
     .pipeline(writeToIndex.passthrough)
     .pipeline(writeToWorkQueue.passthrough)
  )
  .run
  .unsafeRunSync
```

See [PipelineExample.scala](src/test/scala/org/jameshales/fs2/pipeline/PipelineExample.scala)
for the full example.

## License

[MIT](https://choosealicense.com/licenses/mit/)

package org.jameshales.fs2.pipeline

import cats.effect.IO
import fs2.{ Scheduler, Sink, Stream }
import fs2.interop.cats.effect._
import fs2.time

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.math.abs
import scala.util.Random

object PipelineExample extends App {
  implicit val scheduler = Scheduler.fromFixedDaemonPool(3)

  final case class Event(key: Int, value: String)

  val fruits =
    Stream.emits[IO, Event](Seq(
      Event(0, "Apple"),
      Event(0, "Banana"),
      Event(0, "Cherry"),
      Event(0, "Durian"),
      Event(0, "Eggplant"),
      Event(0, "Fig"),
      Event(0, "Guava"),
      Event(0, "Honeydew"),
      Event(0, "Ita Palm"),
      Event(0, "Jack Fruit")
    ))

  val animals =
    Stream.emits[IO, Event](Seq(
      Event(1, "Ant"),
      Event(1, "Bee"),
      Event(1, "Cat"),
      Event(1, "Dog"),
      Event(1, "Elephant"),
      Event(1, "Fox"),
      Event(1, "Giraffe"),
      Event(1, "Hippo"),
      Event(1, "Iguana"),
      Event(1, "Jaguar")
    ))

  val sports =
    Stream.emits[IO, Event](Seq(
      Event(2, "Archery"),
      Event(2, "Bowling"),
      Event(2, "Cycling"),
      Event(2, "Darts"),
      Event(2, "Equestrian"),
      Event(2, "Football"),
      Event(2, "Golf"),
      Event(2, "Hockey"),
      Event(2, "Ice Skating"),
      Event(2, "Javelin")
    ))

  val languages =
    Stream.emits[IO, Event](Seq(
      Event(3, "Ada"),
      Event(3, "Brainfuck"),
      Event(3, "C"),
      Event(3, "Dart"),
      Event(3, "Erlang"),
      Event(3, "Fortran"),
      Event(3, "Go"),
      Event(3, "Haskell"),
      Event(3, "Idris"),
      Event(3, "Java")
    ))

  def slowWrite(message: String): Stream[IO, Unit] =
    Stream.eval(IO(abs(Random.nextGaussian()) * 3))
      .flatMap(n => time.sleep(n.seconds))
      .evalMap(_ => IO(println(message)))

  val writeToDatabase: Sink[IO, Event] =
    (_: Stream[IO, Event]).flatMap(event =>
      slowWrite(s"Wrote ${event.value} to the database.")
    )
  
  val writeToIndex: Sink[IO, Event] =
    (_: Stream[IO, Event]).flatMap(event =>
      slowWrite(s"Wrote ${event.value} to the search index.")
    )
  
  val writeToWorkQueue: Sink[IO, Event] =
    (_: Stream[IO, Event]).flatMap(event =>
      slowWrite(s"Wrote ${event.value} to the work queue.")
    )

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
  (fruits ++ animals ++ sports ++ languages).joinPartition(4)(_.key)(
      _.pipeline(writeToDatabase.passthrough)
       .pipeline(writeToIndex.passthrough)
       .pipeline(writeToWorkQueue.passthrough)
    )
    .run
    .unsafeRunSync
}

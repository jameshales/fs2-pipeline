package org.jameshales.fs2.pipeline

import fs2.{ Pipe, Sink, Stream }
import fs2.async
import fs2.util.Async
import fs2.util.syntax._

object Pipeline {
  def passthrough[F[_], A](
    o: Sink[F, A]
  )(
    implicit F: Async[F]
  ): Pipe[F, A, A] = (s: Stream[F, A]) =>
    fs2.pipe.diamond(s)(identity)(async.boundedQueue(1), o)(fs2.pipe2.zip)
      .map(_._1)

  def pipeline[F[_], A, B](
    f: Pipe[F, A, B]
  )(
    implicit F: Async[F]
  ): Pipe[F, A, B] = (s: Stream[F, A]) =>
    fs2.pipe.diamond(s)(identity)(async.synchronousQueue, f)(
      fs2.pipe2.mergeDrainL
    )

  def partition[F[_], A, B](n: Int)(partitionBy: A => B)(
    implicit F: Async[F]
  ): Pipe[F, A, Stream[F, A]] =
    (s: Stream[F, A]) =>
      Stream.eval(
        List.fill(n)(()).traverse(_ => async.unboundedQueue[F, Option[A]])
      ).flatMap(qs =>
        (
          s.evalMap(a => qs(partitionBy(a).hashCode % n).enqueue1(Some(a))).drain
          ++ Stream.eval(qs.traverse(_.enqueue1(None))).drain
        ).mergeDrainL(
          Stream.emits(qs.map(_.dequeue.unNoneTerminate))
        )
      )
}

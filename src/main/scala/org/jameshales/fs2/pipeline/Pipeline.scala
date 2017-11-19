package org.jameshales.fs2.pipeline

import scala.concurrent.ExecutionContext

import cats.effect.Effect
import cats.implicits._
import fs2.{ Pipe, Sink, Stream }
import fs2.async

object Pipeline {
  def passthrough[F[_], A](
    o: Sink[F, A]
  )(
    implicit
    F:  Effect[F],
    ec: ExecutionContext
  ): Pipe[F, A, A] = (s: Stream[F, A]) =>
    s.diamond(identity)(async.unboundedQueue, o)(_ zip _).map(_._1)
    /*
     *s.diamond(identity)(async.boundedQueue(1), o)((l, r) => l zip r)
     *  .map(_._1)
     */

  def pipeline[F[_], A, B](
    f: Pipe[F, A, B]
  )(
    implicit
    F:  Effect[F],
    ec: ExecutionContext
  ): Pipe[F, A, B] = (s: Stream[F, A]) =>
    s.diamond(identity)(async.synchronousQueue, f)(
      (l, r) => l.drain.merge(r)
    )

  def partition[F[_], A, B](n: Int)(partitionBy: A => B)(
    implicit
    F:  Effect[F],
    ec: ExecutionContext
  ): Pipe[F, A, Stream[F, A]] =
    (s: Stream[F, A]) =>
      Stream.eval(
        List.fill(n)(async.unboundedQueue[F, Option[A]]).sequence
      ).flatMap(qs =>
        Stream.emits(qs.map(_.dequeue.unNoneTerminate)).concurrently(
          s.evalMap(a => qs(partitionBy(a).hashCode % n).enqueue1(Some(a))).drain
          ++ Stream.eval(qs.traverse(_.enqueue1(None))).drain
        )
      )
}

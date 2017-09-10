package org.jameshales.fs2

import fs2.{ Pipe, Sink, Stream }
import fs2.util.Async

package object pipeline {
  implicit final class PipelineStreamOps[F[_], A](
    val stream: Stream[F, A]
  ) extends AnyVal {
    /**
     * Behaves like `self through f`, but pipelines the effects that `f`
     * produces. Successive pipelined calls will overlap the effects between
     * iterations of the Stream whilst preserving the order of the Stream and
     * the order of the pipelined effects.
     *
     * For example, `self through f through g through h` will lead to effects
     * in a purely serial order:
     *
     *   F(1)
     *   G(1)
     *   H(1)
     *   F(2)
     *   G(2)
     *   H(2)
     *   F(3)
     *   G(3)
     *   H(3)
     *   ...
     *
     * Here all effects F, G, and H for each iteration are completed in order
     * before the next iteration begins.
     *
     * Whereas `self pipeline f pipeline g pipeline h` may lead to effects
     * running in parallel:
     *
     *   F(1)
     *   F(2) G(1)
     *   F(3) G(2) H(1)
     *   F(4) G(3) H(2)
     *   F(5) G(4) H(3)
     *   ...
     *   F(n) G(n-1) H(n-2)
     *        G(n)   H(n-1)
     *               H(n)
     *
     * Here the effects F, G, and H for each iteration may overlap with the
     * effects for the following iteration. Pipelining preserves the order of
     * the Stream iterations, and the order of the effects within a Stream
     * iteration.
     */
    def pipeline[B](f: Pipe[F, A, B])(implicit F: Async[F]): Stream[F, B] =
      stream through Pipeline.pipeline(f)

    /**
     * Partitions a Stream into `n` Streams by hashing the key produced by the
     * `partitionBy` function. Partitioning preserves the order of the original
     * Stream for Stream elements with the same key.
     */
    def partition[B](n: Int)(partitionBy: A => B)(
      implicit F: Async[F]
    ): Stream[F, Stream[F, A]] =
      stream through Pipeline.partition(n)(partitionBy)

    /**
     * Partitions a Stream into `n` Streams and runs them in parallel.
     * Partitioning preserves the order of the original Stream for Stream
     * elements with the same key. However Stream elements with different keys
     * may be re-ordered.
     */
    def joinPartition[B, C](n: Int)(partitionBy: A => B)(p: Pipe[F, A, C])(
      implicit F: Async[F]
    ):  Stream[F, C] =
      fs2.concurrent.join(n)(stream.partition(n)(partitionBy).map(p))
  }

  implicit final class PipelineSinkOps[F[_], A](
    val sink: Sink[F, A]
  ) extends AnyVal {
    /**
     * Converts a `Sink[F, A]` into a `Pipe[F, A, A]` that yields an `A` after
     * the sink has consumed the `A`.
     */
    def passthrough(implicit F: Async[F]): Pipe[F, A, A] =
      Pipeline.passthrough(sink)
  }
}

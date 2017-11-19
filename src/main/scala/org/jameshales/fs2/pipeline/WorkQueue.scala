package org.jameshales.fs2.pipeline

import cats._
import cats.implicits._
import fs2.{ Chunk, Pipe, Stream }
import fs2.async.immutable

trait WorkQueue[F[_], A, B] { self =>
  def available: immutable.Signal[F, Int]

  def cancellableDequeue1: F[(F[B], F[Unit])]

  def cancellableDequeueBatch1(
    batchSize: Int
  ): F[(F[Chunk[B]], F[Unit])]

  def dequeue1: F[B]

  def dequeueBatch1(batchSize: Int): F[Chunk[B]]

  def enqueue1(a: A): F[B]

  def full: immutable.Signal[F, Boolean]

  def offer1(a: A): F[Option[B]]

  def size: immutable.Signal[F, Int]

  def upperBound: Option[Int]

  def dequeue: Stream[F, B] =
    Stream.bracket(cancellableDequeue1)(
      d => Stream.eval(d._1),
      d => d._2
    ).repeat

  def dequeueBatch: Pipe[F, Int, B] =
    _.flatMap { batchSize =>
      Stream.bracket(cancellableDequeueBatch1(batchSize))(
        d => Stream.eval(d._1).flatMap(Stream.chunk(_).covary),
        d => d._2
      )
    }

  def dequeueAvailable: Stream[F, B] =
    Stream.constant(Int.MaxValue).covary.through(dequeueBatch)

  def enqueue: Pipe[F, A, B] =
    _.evalMap(enqueue1)

  def map[C](f: B => C)(implicit F: Functor[F]): WorkQueue[F, A, C] =
    new WorkQueue[F, A, C] {
      override def available: immutable.Signal[F, Int] =
        self.available

      override def cancellableDequeue1: F[(F[C], F[Unit])] =
        self.cancellableDequeue1.map { case (fb, fu) =>
          (fb.map(f), fu)
        }

      override def cancellableDequeueBatch1(
        batchSize: Int
      ): F[(F[Chunk[C]], F[Unit])] =
        self.cancellableDequeueBatch1(batchSize).map { case (fcb, fu) =>
          (fcb.map(_.map(f).toChunk), fu)
        }

      override def dequeue1: F[C] =
        self.dequeue1.map(f)

      override def dequeueBatch1(batchSize: Int): F[Chunk[C]] =
        self.dequeueBatch1(batchSize).map(_.map(f).toChunk)

      override def enqueue1(a: A): F[C] =
        self.enqueue1(a).map(f)

      override def full: immutable.Signal[F, Boolean] =
        self.full

      override def offer1(a: A): F[Option[C]] =
        self.offer1(a).map(_.map(f))

      override def size: immutable.Signal[F, Int] =
        self.size

      override def upperBound: Option[Int] =
        self.upperBound
    }

  def contramap[C](f: C => A): WorkQueue[F, C, B] =
    new WorkQueue[F, C, B] {
      override def available: immutable.Signal[F, Int] =
        self.available

      override def cancellableDequeue1: F[(F[B], F[Unit])] =
        self.cancellableDequeue1

      override def cancellableDequeueBatch1(
        batchSize: Int
      ): F[(F[Chunk[B]], F[Unit])] =
        self.cancellableDequeueBatch1(batchSize)

      override def dequeue1: F[B] =
        self.dequeue1

      override def dequeueBatch1(batchSize: Int): F[Chunk[B]] =
        self.dequeueBatch1(batchSize)

      override def enqueue1(c: C): F[B] =
        self.enqueue1(f(c))

      override def full: immutable.Signal[F, Boolean] =
        self.full

      override def offer1(c: C): F[Option[B]] =
        self.offer1(f(c))

      override def size: immutable.Signal[F, Int] =
        self.size

      override def upperBound: Option[Int] =
        self.upperBound
    }
}

/*
 *object WorkQueue {
 *  implicit val TraverseChunk: Traverse[Chunk] =
 *    new Traverse[NonEmptyChunk] {
 *      override def map[A, B](
 *        a: NonEmptyChunk[A]
 *      )(f: A => B): NonEmptyChunk[B] =
 *        a.map(f)
 *
 *      override def traverse[G[_], A, B](fa: NonEmptyChunk[A])(f: A => G[B])(
 *        implicit G: Applicative[G]
 *      ): G[NonEmptyChunk[B]] = {
 *        val (h, t) = fa.unconsNonEmpty
 *        f(h).ap(
 *          t.toList
 *           .traverse(f)
 *           .map(bs => b => NonEmptyChunk(b, Chunk.seq(bs)))
 *        )
 *      }
 *    }
 *
 *  def fromQueue[F[_], A, B](
 *    f: A => F[B]
 *  )(
 *    fq: F[Queue[F, (A, mutable.Signal[F, Option[B]])]]
 *  )(
 *    implicit F: Async[F]
 *  ): F[WorkQueue[F, A, B]] =
 *    fq.map { q =>
 *      new WorkQueue[F, A, B] {
 *        private def onDequeue: ((A, mutable.Signal[F, Option[B]])) => F[B] = {
 *          case (a, s) =>
 *            for {
 *              b <- f(a)
 *              _ <- s.set(Some(b))
 *            } yield b
 *        }
 *
 *        override def available: immutable.Signal[F, Int] =
 *          q.available
 *
 *        override def cancellableDequeue1: F[(F[B], F[Unit])] =
 *          for {
 *            t <- q.cancellableDequeue1
 *            (fa, fu) = t
 *            fb = fa.flatMap(onDequeue)
 *          } yield (fb, fu)
 *
 *        override def cancellableDequeueBatch1(
 *          batchSize: Int
 *        ): F[(F[NonEmptyChunk[B]], F[Unit])] =
 *          for {
 *            t <- q.cancellableDequeueBatch1(batchSize)
 *            (fca, fu) = t
 *            fcb = fca.flatMap(_.traverse(onDequeue))
 *          } yield (fcb, fu)
 *
 *        override def dequeue1: F[B] =
 *          q.dequeue1.flatMap(onDequeue)
 *
 *        override def dequeueBatch1(batchSize: Int): F[NonEmptyChunk[B]] =
 *          for {
 *            fa <- q.dequeueBatch1(batchSize)
 *            fb <- fa.traverse(onDequeue)
 *          } yield fb
 *
 *        override def enqueue1(a: A): F[B] =
 *          for {
 *            s <- fs2.async.signalOf[F, Option[B]](None)
 *            _ <- q.enqueue1((a, s))
 *            b <-
 *              s.discrete
 *                .collect({ case Some(b) => b })
 *                .head
 *                .runFold[Option[B]](None)((_, b) => Some(b))
 *          } yield b.get
 *
 *        override def full: immutable.Signal[F, Boolean] =
 *          q.full
 *
 *        override def offer1(a: A): F[Option[B]] =
 *          for {
 *            s <- fs2.async.signalOf[F, Option[B]](None)
 *            r <- q.offer1((a, s))
 *            b <-
 *              if (r) {
 *                s.discrete
 *                  .collect({ case Some(b) => b })
 *                  .head
 *                  .runFold[Option[B]](None)((_, b) => Some(b))
 *              } else {
 *                F.pure(None)
 *              }
 *          } yield b
 *
 *        override def size: immutable.Signal[F, Int] =
 *          q.size
 *
 *        override def upperBound: Option[Int] =
 *          q.upperBound
 *      }
 *    }
 *
 *  def bounded[F[_]: Async, A, B](
 *    maxSize: Int
 *  )(
 *    f: A => F[B]
 *  ): F[WorkQueue[F, A, B]] =
 *    fromQueue(f)(Queue.bounded(maxSize))
 *
 *  def synchronous[F[_]: Async, A, B](f: A => F[B]): F[WorkQueue[F, A, B]] =
 *    fromQueue(f)(Queue.synchronous)
 *
 *  def unbounded[F[_]: Async, A, B](f: A => F[B]): F[WorkQueue[F, A, B]] =
 *    fromQueue(f)(Queue.unbounded)
 *}
 */

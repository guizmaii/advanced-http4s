package com.github.gvolpe.fs2

import cats.effect._
import cats.effect.concurrent.Deferred
import fs2.Stream

/**
  * Demonstrate the use of [[cats.effect.concurrent.Deferred]]
  *
  * Two processes will try to complete the promise at the same time but only one will succeed,
  * completing the promise exactly once.
  * The loser one will raise an error when trying to complete a promise already completed,
  * that's why we call `attempt` on the evaluation.
  *
  * Notice that the loser process will remain running in the background and the program will
  * end on completion of all of the inner streams.
  *
  * So it's a "race" in the sense that both processes will try to complete the promise at the
  * same time but conceptually is different from "race". So for example, if you schedule one
  * of the processes to run in 10 seconds from now, then the entire program will finish after
  * 10 seconds and you can know for sure that the process completing the promise is going to
  * be the first one.
  * */
object OnceApp extends IOApp {

  def stream[F[_]: ConcurrentEffect]: fs2.Stream[F, Unit] =
    for {
      p <- Stream.eval(Deferred[F, Int])
      e <- new ConcurrentCompletion[F](p).start
    } yield e

  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.drain.as(ExitCode.Success)
}

class ConcurrentCompletion[F[_]](p: Deferred[F, Int])(implicit F: Concurrent[F]) {

  private def attemptPromiseCompletion(n: Int): Stream[F, Unit] =
    Stream.eval(p.complete(n)).attempt.drain

  def start: Stream[F, ExitCode] =
    Stream(
      attemptPromiseCompletion(1),
      attemptPromiseCompletion(2),
      Stream.eval(p.get).evalMap(n => F.delay(println(s"Result: $n")))
    ).parJoin(3).drain ++ Stream.emit(ExitCode.Success)

}

import io.chrisdavenport.fuuid._
import java.util.concurrent.atomic.AtomicBoolean
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import scala.concurrent._
import java.util.concurrent.Executors

import util.Random.nextInt
import util.Random.nextDouble
import scala.concurrent.duration._
import cats.syntax.apply._
import cats.effect.{ContextShift, IO, Timer}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.concurrent.{ExecutionContext, Future}
import cats.effect.Clock

import scala.concurrent.duration.{FiniteDuration, TimeUnit}
import scala.sys.process.Process

import cats.effect.Sync
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

object Hyperparameters {

  //implicit val timer = IO.timer(ExecutionContext.global)

  val info = (msg: String) => println("OUT " + msg)
  val err = (msg: String) => println("ERR " + msg)

  def putStrlLn(value: String) = IO(println(value))
  val readLn = IO(scala.io.StdIn.readLine)

  def cmd(cmd: String, outputFile: Option[String] = None)(runId: String): IO[Int] = {

    val logFile =
      outputFile match {
        case Some(x: String) => x
        case None => runId + ".txt"
      } // TODO incorporate some cats effects logging library

    IO.cancelable(
      (cb: (Either[Throwable, Int] => Unit)) => {
        val isCancelled = new AtomicBoolean(false)

        var process: Option[Process] = None

        val asyncResult = Future {
          import sys.process._
          info(s"${runId} Running `${cmd}`:")

          val log = ProcessLogger(
            (msg) => info(s"${runId}   ${msg}"),
            (msg) => err(s"${runId}   ${msg}")
          )

          val proc = Process(cmd)
          process = Some(proc.run(log))

          process.get.exitValue()
        }

        asyncResult.onComplete {
          case Success(value) => cb(Right(value))
          case Failure(e) => cb(Left(e))
        }

        IO {
          isCancelled.set(true)

          process match {
            case Some(process) => process.destroy()
            case None => {
              info("No process to cancel")
            }
          }

          info("# # # set isCancelled = true")
        }
      }
    )
  }

  def docker(id: String, params: String)(runId: String) = cmd(s"docker run --rm -i ${params} ${id}")(runId)

  def portAvailable(i: Int) = IO({

  })

  implicit val executionContext: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  implicit lazy val contextShift: ContextShift[IO] =
    IO.contextShift(executionContext) // ceremony 1

  implicit lazy val timer: Timer[IO] =
    IO.timer(executionContext) // ceremony 2

  def main(args: Array[String]): Unit = {
    val contextShift = IO.contextShift(global)

    case class Experiment(
       dimensions: Int,
       epochs: Int,
       neg: Int,
       lr: Double,
       loss: String,
       minCount: Int,
       index: Int
    )

    val FASTTEXT_PATH = "/projects/fastText"
    val DATA_PATH = "/projects/wikipedia-categorization/"
    val MODEL_PATH = "/projects/wikipedia-categorization/models/"
     
    def getSteps(logger: SelfAwareStructuredLogger[IO]): 
      Stream[List[(String) => IO[_]]] = { 
      Stream.continually(1).zipWithIndex.map(
      (idx: (Int, Int)) => Experiment(
        (1 + nextInt(8) ) * 50,
        5 + nextInt(100),
        nextInt(20),
        nextDouble(),
        List("ns", "hs", "softmax")(nextInt(3)),
        nextInt(10),
        idx._2
      )
    ).take(1000).map(
      (e) => List(
           // TODO save the experiment parameters off to json
           (runId: String) => IO( { println(runId + ": " + e.asJson.noSpaces) } ),
           (runId: String) => IO( { 
             import java.io._

             val canonicalFilename = MODEL_PATH + e.hashCode() + ".json"

             val file = new File(canonicalFilename)
             val bw = new BufferedWriter(new FileWriter(file))
             bw.write(e.asJson.noSpaces)
             bw.close()
           } ),
           cmd(
             s"${FASTTEXT_PATH}/fasttext supervised " + 
             s"-input ${DATA_PATH}train.txt -output ${MODEL_PATH}model${e.hashCode()} " +
             s"-dim ${e.dimensions} " +
             s"-epoch ${e.epochs} " +
             s"-lr ${e.lr} " + 
             s"-thread 1 " + 
             s"-loss ${e.loss} " +
             s"-neg ${e.neg} " +
             s"-minCount ${e.minCount}"
           )(_),
           cmd(s"${FASTTEXT_PATH}/fasttext test ${MODEL_PATH}model${e.hashCode()}.bin ${DATA_PATH}test.txt 1", Some(s"${MODEL_PATH}perf_${e.hashCode()}_1.txt"))(_),
           cmd(s"${FASTTEXT_PATH}/fasttext test ${MODEL_PATH}model${e.hashCode()}.bin ${DATA_PATH}test.txt 5", Some(s"${MODEL_PATH}perf_${e.hashCode()}_5.txt"))(_)
        )
    ).force
    }

    // TODO one of the logging libraries (log4cats, console4cats)
    // TODO cats retry
    import cats._, cats.data._, cats.syntax.all._, cats.effect.IO
    import cats.syntax.traverse._
    import cats.instances.list._
    import cats.instances.option._

    implicit val Main = ExecutionContext.global
    implicit val cs = IO.contextShift(ExecutionContext.global)

    implicit def unsafeLogger[F[_]: Sync] = Slf4jLogger.unsafeCreate[F]

    val program =
      for (
        logger <- Slf4jLogger.create[IO];
        tuning <- NonEmptyList(
          logger.info("Starting hyperparameter tuning"),
          getSteps(logger).map(
            step => 
              for (
                runId <- FUUID.randomFUUID[IO];
                script <- IO.race(
                  step
                    .map( _(runId.toString) )
                    .reduce(
                      (a, b) => a *> b
                    ),
                  IO.sleep(3 hours) 
                )(contextShift)
              ) yield script
          ).toList).parSequence *>
           logger.info("parSequence complete");
        completion <- logger.debug("done")
      ) yield completion

    program.unsafeRunSync()
  }
}

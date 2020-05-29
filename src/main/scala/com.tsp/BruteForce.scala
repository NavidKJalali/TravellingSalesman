package com.tsp

import java.util.NoSuchElementException

import Timer.Timer.TimerActor
import Timer.Timer.TimerActor.{Start, Stop, TimerMessage}
import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter._
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object BruteForce {
  def allTours(costMatrix: CostMatrix, source: Int): Iterator[Path] = ((0 until costMatrix.size)
    .toSet - source).toVector
    .permutations.map(p => Path((source +: p) :+ source))

  def bruteForce(costMatrix: CostMatrix, source: Int): Unit = {
    val possibleTours = allTours(costMatrix, source).map(tour => (tour, tour.cost(costMatrix))).collect {
      case (tour, Some(cost)) =>
        (tour, cost)
    }.toList

    possibleTours.minBy {
      case (_, cost) => cost
    } match {
      case (path, cost)  => println(s"best tour: ${path.prettyString}, $cost")
    }
  }

  def bruteForceWithStreams(costMatrix: CostMatrix, source: Int): Unit = {

    implicit val system: ActorSystem = ActorSystem("BruteForce")
    implicit val ctx: ExecutionContext = system.dispatcher

    val timer: ActorRef[TimerMessage] = system.spawnAnonymous(new TimerActor().start)

    timer ! Start

    val decider: Supervision.Decider = {
      case _: NoSuchElementException => Supervision.Resume
      case _ => Supervision.Stop
    }

    val reducer = (l: (Path, Int), r: (Path, Int)) => if (l._2 <= r._2) l else r

    val tourPermutations = allTours(costMatrix, source)
    val s = Source.fromIterator(() => tourPermutations)
      .grouped(Config.concurrency.batching)
      .mapAsyncUnordered(Config.concurrency.parallelism)(
        paths => {
          Future {
            paths
              .map(path => (path, path.cost(costMatrix)))
              .collect { case (path, Some(cost)) => (path, cost) } match {
              case batch if batch.nonEmpty => batch.minBy(_._2)
              case _ => throw new NoSuchElementException
            }
          }
        }
      ).withAttributes(ActorAttributes.supervisionStrategy(decider))
      .runWith(Sink.reduce(reducer))

    s onComplete {
      case Success(value) =>
        timer ! Stop
        println(s"best tour: ${value._1.prettyString}, ${value._2}")
        system.terminate().onComplete {
          case Success(_) => println(s"actor system terminated: ${system.name}")
          case Failure(exception) =>
            println(s"failed to terminate actor system ${system.name}: $exception")
        }
      case Failure(exception) =>
        println(s"brute force failed: $exception")

    }
  }

  def routesByExistence(costMatrix: CostMatrix, source: Int): Unit = {
    val bestPath = costMatrix.getExistingRoutes(0, costMatrix.size).toVector
      .map { case (path, cost) => (Path(path), cost) }
      .sortBy(_._2)
      .headOption
    println(bestPath)
  }

}

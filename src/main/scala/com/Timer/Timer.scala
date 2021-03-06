package com.Timer

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior

object Timer {
  def synchronousTimer[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block
    val t1 = System.nanoTime()
    println(s"Elapsed time: ${(t1 - t0) / 1000000f} ms")
    result
  }

  object TimerActor {

    sealed trait TimerMessage

    case object Start extends TimerMessage

    case object Stop extends TimerMessage

    case class State(startTime: Long)

    object State {
      val initial: State = State(0)
    }

  }

  class TimerActor {

    import TimerActor._

    def start: Behavior[TimerMessage] =
      running(State.initial)

    def running(state: State): Behavior[TimerMessage] =
      Behaviors.receive((ctx, msg) =>
        msg match {
          case Start =>
            running(State(System.nanoTime()))
          case Stop =>
            ctx.log.debug(s"Elapsed time: ${(System.nanoTime() - state.startTime) / 1000000f} ms")
            Behaviors.stopped
        }
      )
  }
}

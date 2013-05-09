/*
 *  ____    ____    _____    ____    ___     ____ 
 * |  _ \  |  _ \  | ____|  / ___|  / _/    / ___|        Precog (R)
 * | |_) | | |_) | |  _|   | |     | |  /| | |  _         Advanced Analytics Engine for NoSQL Data
 * |  __/  |  _ <  | |___  | |___  |/ _| | | |_| |        Copyright (C) 2010 - 2013 SlamData, Inc.
 * |_|     |_| \_\ |_____|  \____|   /__/   \____|        All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the 
 * GNU Affero General Public License as published by the Free Software Foundation, either version 
 * 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See 
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this 
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.precog.shard
package scheduling

import akka.actor.ActorRef
import akka.dispatch.Future
import akka.pattern.ask
import akka.util.Timeout

import com.precog.util.PrecogUnit

import java.util.UUID

import scalaz._

trait Scheduler[M[_]] {
  def enabled: Boolean

  def addTask(task: ScheduledTask): M[Validation[String, PrecogUnit]]

  def deleteTask(id: UUID): M[Validation[String, PrecogUnit]]

  def statusForTask(id: UUID, limit: Option[Int]): M[Validation[String, Option[(ScheduledTask, Seq[ScheduledRunReport])]]]
}

class ActorScheduler(scheduler: ActorRef)(implicit timeout: Timeout) extends Scheduler[Future] {
  val enabled = true

  def addTask(task: ScheduledTask) =
    (scheduler ? AddTask(task)).mapTo[Validation[String, PrecogUnit]]

  def deleteTask(id: UUID) =
    (scheduler ? DeleteTask(id)).mapTo[Validation[String, PrecogUnit]]

  def statusForTask(id: UUID, limit: Option[Int]) =
    (scheduler ? StatusForTask(id, limit)).mapTo[Validation[String, Option[(ScheduledTask, Seq[ScheduledRunReport])]]]
}

object NoopScheduler extends Scheduler[Future] {
  val enabled = false

  def addTask(task: ScheduledTask) = sys.error("No scheduling available")

  def deleteTask(id: UUID) = sys.error("No scheduling available")

  def statusForTask(id: UUID, limit: Option[Int]) = sys.error("No scheduling available")
}

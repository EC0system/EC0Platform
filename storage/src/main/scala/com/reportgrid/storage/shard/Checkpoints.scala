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
package com.reportgrid.storage.shard

import scala.collection.mutable.{Map => MMap}
import scalaz.{Validation, Success, Failure}

class CheckpointMetadata {
 
  private val checkpoints = MMap[Int, Int]()
  private val pending = MMap[(Int, Int), Int]()

  def load(map: Map[Int, Int]) {
    reset()

    checkpoints ++= map 
  }
  
  def reset() {
    checkpoints.clear
    pending.clear
  }

  def toMap: Map[Int, Int] = checkpoints.toMap
 
  def expect(producerId: Int, eventId: Int, count: Int): Validation[String, Unit] = {
    if(count <= 0) 
      Failure("Checkpoint expectations must be positive. Found[%d]".format(count))
    else if(pending.contains((producerId, eventId)))
      Failure("Redundant checkpoint expectation rejected. (%d,%d,%d)".format(producerId, eventId, count))
    else
      Success(pending.put((producerId, eventId), count))
  }

  def event(producerId: Int, eventId: Int): Validation[String, Int] = {

    def updateCheckpoint(producerId: Int, eventId: Int)(remaining: Int) {
      if(remaining > 0) ()
      else {
        val cur = checkpoints.get(producerId)
        val newValue = cur.map(c => if(c < eventId) eventId else c).getOrElse( eventId )
        checkpoints.put(producerId, newValue)
      }
    }
    
    decrementPending((producerId, eventId)) map sideeffect(updateCheckpoint(producerId, eventId))
  }

  private def decrementPending(key: (Int, Int)): Validation[String,Int] = {
    
    def updatePending(i: Int) = i match {
      case x if x <= 0 => pending -= key
      case x           => pending += (key -> x)
    }

    pending get(key) map { _ - 1 } map sideeffect(updatePending) match {
      case Some(x) => Success(x)
      case None    => Failure("No matching expectation for this event. (%d,%d)".format(key._1, key._2))
    }
  }

  private def sideeffect[A, B](f: A => B)(a: A): A = { f(a); a }

//
// General event actions
//
// - update affected metadata
// -- open questions
// -- where do the value updates for the metadata come from?
// -- where does the projection metadata come from? (this seems like it must be encoded/created during routing)
// - update checkpoint state
// -- if no pending state
// --- error state log it
// -- else
// --- decrement pending state
// --- if pending state is zero
// ---- remove pending state
// ---- update max checkpoint
// --- else
// ---- save decremented value
}
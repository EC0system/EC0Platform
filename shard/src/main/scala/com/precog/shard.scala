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
package com.precog

import com.precog.daze._
import com.precog.common.security._
import com.precog.common.jobs._
import com.precog.muspelheim._

import akka.dispatch.Future
import scalaz.StreamT
import blueeyes.json.JValue
import java.nio.CharBuffer

package object shard {
  type QueryResult = Either[JValue, StreamT[Future, CharBuffer]]

  type JobQueryT[M[+_], +A] = QueryT[JobQueryState, M, A]

  type ShardQuery[+A] = JobQueryT[Future, A]

  type BasicPlatform[M[+_]] = Platform[M, StreamT[Future, CharBuffer]]
  type AsyncPlatform[M[+_]] = Platform[M, JobId]
  type SyncPlatform[M[+_]] = Platform[M, (Option[JobId], StreamT[Future, CharBuffer])]
}

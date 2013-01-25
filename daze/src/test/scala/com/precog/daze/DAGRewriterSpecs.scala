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
package com.precog.daze

import org.specs2.mutable._

import com.precog.common.Path
import com.precog.yggdrasil._
import org.joda.time.DateTime

trait DAGRewriterSpecs[M[+_]] extends Specification with EvaluatorTestSupport[M] {

  import dag._
  import instructions._

  "DAG rewriting" should {
    "compute identities given a relative path" in {
      val line = Line(1, 1, "")

      val input = dag.LoadLocal(Const(CString("/numbers"))(line))(line)

      val ctx = EvaluationContext("testAPIKey", Path.Root, new DateTime())
      val result = rewriteDAG(true, ctx)(input)

      result.identities mustEqual Identities.Specs(Vector(LoadIds("/numbers")))
    }
  }
}

object DAGRewriterSpecs extends DAGRewriterSpecs[test.YId] with test.YIdInstances

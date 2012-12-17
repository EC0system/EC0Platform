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
package daze

import common.{ Path, VectorCase }
import common.security._

import yggdrasil._
import yggdrasil.table._

import akka.dispatch.Await
import akka.util.Duration

import org.joda.time.DateTime

import scalaz._
import scalaz.Validation
import scalaz.effect.IO
import scalaz.syntax.monad._
import scalaz.syntax.copointed._
import scalaz.std.set._
import Validation._

import blueeyes.json._

// TODO decouple this from the evaluator specifics
trait MemoryDatasetConsumer[M[+_]] extends Evaluator[M] with TableModule[M] {
  type X = Throwable
  type SEvent = (Vector[Long], SValue)

  implicit def M: Monad[M] with Copointed[M]
  
  def consumeEval(apiKey: APIKey, graph: DepGraph, prefix: Path, optimize: Boolean = true): Validation[X, Set[SEvent]] = {
    val ctx = EvaluationContext(apiKey, prefix, new DateTime())
    Validation.fromTryCatch {
      val result = eval(graph, ctx, optimize)
      val json = result.flatMap(_.toJson).copoint filterNot { jvalue => {
        (jvalue \ "value") == JUndefined
      }}

      val events = json map { jvalue =>
        (Vector(((jvalue \ "key") --> classOf[JArray]).elements collect { case JNum(i) => i.toLong }: _*), jvalueToSValue(jvalue \ "value"))
      }
      
      events.toSet
    }
  }
  
  private def jvalueToSValue(value: JValue): SValue = value match {
    case JUndefined => sys.error("don't use jnothing; doh!")
    case JNull => SNull
    case JBool(value) => SBoolean(value)
    case JNum(bi) => SDecimal(bi)
    case JString(str) => SString(str)
    
    case JObject(fields) => {
      val map: Map[String, SValue] = fields.map({
        case JField(name, value) => (name, jvalueToSValue(value))
      })(collection.breakOut)
      
      SObject(map)
    }
    
    case JArray(values) =>
      SArray(Vector(values map jvalueToSValue: _*))
  }
}


// vim: set ts=4 sw=4 et:

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
package com.precog.common
package accounts

import com.precog.common.Path
import com.precog.common.cache.Cache
import com.precog.common.security._
import com.precog.util._

import akka.dispatch.{ ExecutionContext, Future, Promise }

import blueeyes.bkka._
import blueeyes.core.data.DefaultBijections._
import blueeyes.core.data.ByteChunk
import blueeyes.core.http._
import blueeyes.core.http.MimeTypes._
import blueeyes.core.http.HttpStatusCodes._
import blueeyes.core.service._
import blueeyes.core.service.engines.HttpClientXLightWeb
import blueeyes.json._
import blueeyes.json.serialization.{ Extractor, Decomposer }
import blueeyes.json.serialization.DefaultSerialization.{ DateTimeDecomposer => _, DateTimeExtractor => _, _ }
import blueeyes.json.serialization.Extractor._

import org.apache.commons.codec.binary.Base64
import org.joda.time.DateTime
import org.streum.configrity.Configuration
import com.weiglewilczek.slf4s.Logging

import scalaz._
import scalaz.{ NonEmptyList => NEL }
import scalaz.Validation._
import scalaz.syntax.monad._
import scalaz.syntax.validation._
import scalaz.syntax.std.option._

object WebAccountFinder {
  def apply(config: Configuration)(implicit executor: ExecutionContext): Validation[NonEmptyList[String], AccountFinder[Future]] = {
    val serviceConfig = config.detach("service")
    serviceConfig.get[String]("hardcoded_account") map { accountId =>
      success(new ConstantAccountFinder(accountId))
    } getOrElse {
      (serviceConfig.get[String]("protocol").toSuccess(NEL("Configuration property service.protocol is required")) |@|
       serviceConfig.get[String]("host").toSuccess(NEL("Configuration property service.host is required")) |@|
       serviceConfig.get[Int]("port").toSuccess(NEL("Configuration property service.port is required")) |@|
       serviceConfig.get[String]("path").toSuccess(NEL("Configuration property service.path is required")) |@|
       serviceConfig.get[String]("user").toSuccess(NEL("Configuration property service.user is required")) |@|
       serviceConfig.get[String]("password").toSuccess(NEL("Configuration property service.password is required"))) {
        (protocol, host, port, path, user, password) => 
          val cacheSize = serviceConfig[Int]("cache_size", 1000)
          new WebAccountFinder(protocol, host, port, path, user, password, cacheSize)
      }
    }
  }
}

class ConstantAccountFinder(accountId: AccountId)(implicit executor: ExecutionContext) extends AccountFinder[Future] {
  implicit val M: Monad[Future] = new FutureMonad(executor)

  def findAccountByAPIKey(apiKey: APIKey) : Future[Option[AccountId]] = Promise.successful(Some(accountId))
  def findAccountById(accountId: AccountId): Future[Option[Account]] = Promise.successful(None)
}

class WebAccountFinder(protocol: String, host: String, port: Int, path: String, user: String, password: String, cacheSize: Int)(implicit executor: ExecutionContext) extends AccountFinder[Future] with Logging {
  implicit val M: Monad[Future] = new FutureMonad(executor)

  private[this] val apiKeyToAccountCache = Cache.simple[APIKey, AccountId](Cache.MaxSize(cacheSize))

  def findAccountByAPIKey(apiKey: APIKey) : Future[Option[AccountId]] = {
    apiKeyToAccountCache.get(apiKey).map(id => Promise.successful(Some(id))).getOrElse {
      invoke { client =>
        client.query("apiKey", apiKey).contentType(application/MimeTypes.json).get[JValue]("") map {
          case HttpResponse(HttpStatus(OK, _), _, Some(jaccounts), _) =>
            jaccounts.validated[Set[WrappedAccountId]] match {
              case Success(accountIds) => 
                if (accountIds.size > 1) {
                  // FIXME: The underlying apparatus should now be modified such that
                  // this case can be interpreted as a fatal error
                  logger.error("Found more than one account for API key: " + apiKey + 
                               "; proceeding with random account from those returned.")
                } 

                accountIds.headOption.map(_.accountId) tap {
                  _ foreach { apiKeyToAccountCache.put(apiKey, _) }
                }
              
              case Failure(err) =>
                logger.error("Unexpected response to account list request: " + err)
                throw HttpException(BadGateway, "Unexpected response to account list request: " + err)
            }

          case HttpResponse(HttpStatus(failure: HttpFailure, reason), _, content, _) =>
            logger.error("Fatal error attempting to list accounts: " + failure + ": " + content)
            throw HttpException(failure, reason)

          case other =>
            logger.error("Unexpected response from accounts service: " + other)
            throw HttpException(BadGateway, "Unexpected response from accounts service: " + other)
        }
      }
    }
  }

  def findAccountById(accountId: AccountId): Future[Option[Account]] = {
    import Account.Serialization._
    invoke { client =>
      client.contentType(application/MimeTypes.json).get[JValue](accountId) map {
        case HttpResponse(HttpStatus(OK, _), _, Some(jaccount), _) =>
         jaccount.validated[Option[Account]] match {
           case Success(accounts) => accounts
           case Failure(err) =>
            logger.error("Unexpected response to find account request: " + err)
            throw HttpException(BadGateway, "Unexpected response to find account request: " + err)
         }

        case HttpResponse(HttpStatus(failure: HttpFailure, reason), _, content, _) =>
          logger.error("Fatal error attempting to find account: " + failure + ": " + content)
          throw HttpException(failure, reason)

        case other =>
          logger.error("Unexpected response from accounts service: " + other)
          throw HttpException(BadGateway, "Unexpected response from accounts service: " + other)
      }
    }
  }

  def invoke[A](f: HttpClient[ByteChunk] => A): A = {
    val client = new HttpClientXLightWeb
    val auth = HttpHeaders.Authorization("Basic "+new String(Base64.encodeBase64((user+":"+password).getBytes("UTF-8")), "UTF-8"))
    f(client.protocol(protocol).host(host).port(port).path(path).header(auth))
  }
}

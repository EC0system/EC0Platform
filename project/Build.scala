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
import sbt._
import Keys._
import sbtassembly.Plugin.AssemblyKeys._
import sbt.NameFilter._

object PlatformBuild extends Build {
  val scalaz = com.samskivert.condep.Depends(
    ("scalaz", "iteratee", "org.scalaz"                  %% "scalaz-iteratee"        % "7.0-SNAPSHOT")
  )

  val blueeyesDeps = com.samskivert.condep.Depends( 
    ("blueeyes",         null, "com.reportgrid"                  %% "blueeyes"         % "0.5.0-SNAPSHOT")
  )

  val clientLibDeps = com.samskivert.condep.Depends(
    ("client-libraries", null, "com.reportgrid"                  %% "scala-client" % "0.3.1")
  )

  val nexusSettings : Seq[Project.Setting[_]] = Seq(
    resolvers ++= Seq("ReportGrid repo"          at   "http://nexus.reportgrid.com/content/repositories/releases",
                      "ReportGrid repo (public)" at   "http://nexus.reportgrid.com/content/repositories/public-releases",
                      "ReportGrid snapshot repo"          at   "http://nexus.reportgrid.com/content/repositories/snapshots",
                      "ReportGrid snapshot repo (public)" at   "http://nexus.reportgrid.com/content/repositories/public-snapshots"),
    credentials += Credentials(Path.userHome / ".ivy2" / ".rgcredentials")
  )

  lazy val platform = Project(id = "platform", base = file(".")) aggregate(quirrel, storage, bytecode, daze, ingest)
  
  lazy val blueeyes = RootProject(uri("../blueeyes"))
  lazy val clientLibraries = RootProject(uri("../client-libraries/scala"))
  
  lazy val bytecode = Project(id = "bytecode", base = file("bytecode")).settings(nexusSettings : _*)
  lazy val quirrel = Project(id = "quirrel", base = file("quirrel")).settings(nexusSettings : _*) dependsOn bytecode
  
  val storageSettings = nexusSettings ++ Seq(libraryDependencies ++= scalaz.libDeps ++ blueeyesDeps.libDeps)
  lazy val storage = scalaz.addDeps(Project(id = "storage", base = file("storage")).settings(storageSettings : _*))
  
  lazy val daze = Project(id = "daze", base = file("daze")).settings(nexusSettings : _*) dependsOn bytecode // (bytecode, storage)
  

  val commonSettings = nexusSettings ++ Seq(libraryDependencies ++= blueeyesDeps.libDeps)
  lazy val common = blueeyesDeps.addDeps(Project(id = "common", base = file("common")).settings(commonSettings: _*))

  val ingestSettings = sbtassembly.Plugin.assemblySettings ++ nexusSettings ++ Seq(libraryDependencies ++= clientLibDeps.libDeps)
  lazy val ingest = clientLibDeps.addDeps(Project(id = "ingest", base = file("ingest")).settings(ingestSettings: _*) dependsOn(common))
}


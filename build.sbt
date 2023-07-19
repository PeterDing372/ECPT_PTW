// See README.md for license details.

ThisBuild / scalaVersion     := "2.13.8"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "com.github.peterding372"

val chiselVersion = "3.5.4"

lazy val root = (project in file("."))
  .settings(
    name := "my_template",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest" % "0.5.4" % "test"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-P:chiselplugin:genBundleElements",
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full),
  )

  // libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "5.0-SNAPSHOT"

//   def scalacOptionsVersion(scalaVersion: String): Seq[String] = {
//   Seq() ++ {
//     // If we're building with Scala > 2.11, enable the compile option
//     //  switch to support our anonymous Bundle definitions:
//     //  https://github.com/scala/bug/issues/10047
//     CrossVersion.partialVersion(scalaVersion) match {
//       case Some((2, scalaMajor: Long)) if scalaMajor < 12 => Seq()
//       case _ => Seq("-Xsource:2.11")
//     }
//   }
// }


// See README.md for license details.

// ThisBuild / scalaVersion     := "2.13.8"
// ThisBuild / version          := "0.1.0"
// ThisBuild / organization     := "com.github.peterding372"

// val chiselVersion = "3.5.4"

// lazy val root = (project in file("."))
//   .settings(
//     name := "my_template",
//     libraryDependencies ++= Seq(
//       "edu.berkeley.cs" %% "chisel3" % chiselVersion,
//       "edu.berkeley.cs" %% "chiseltest" % "0.5.4" % "test"
//     ),
//     scalacOptions ++= Seq(
//       "-language:reflectiveCalls",
//       "-deprecation",
//       "-feature",
//       "-Xcheckinit",
//       "-P:chiselplugin:genBundleElements",
//     ),
//     addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full),
//   )


val chiselVersion = "3.5.4"

lazy val commonSettings = Seq(
  scalaVersion := "2.12.10",
  scalacOptions ++= Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-unchecked",
    "-feature",
    "-Xsource:2.11"
  ),
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "org.json4s" %% "json4s-jackson" % "3.6.12",
    "org.scalatest" %% "scalatest" % "3.2.12" % "test"
  ),
  addCompilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full))
)

lazy val chiselSettings = Seq(
  libraryDependencies ++= Seq(
    "edu.berkeley.cs" %% "chisel3" % chiselVersion,
    "edu.berkeley.cs" %% "chiseltest" % "0.5.4"
  ),
  addCompilerPlugin(("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion).cross(CrossVersion.full))
)

/* Switchable */
lazy val `api-config-chipsalliance` = (project in file("rocket-chip/api-config-chipsalliance/build-rules/sbt"))
  .settings(commonSettings)

lazy val hardfloat = (project in file("rocket-chip/hardfloat"))
  .settings(commonSettings, chiselSettings)

lazy val rocketMacros = (project in file("rocket-chip/macros"))
  .settings(commonSettings)

lazy val rocketchip = (Project("rocket-chip", file("rocket-chip/src")))
  .settings(commonSettings, chiselSettings)
  .settings(
    Compile / scalaSource := baseDirectory.value / "main" / "scala",
    Compile / resourceDirectory := baseDirectory.value / "main" / "resources"
  )
  .dependsOn(`api-config-chipsalliance`)
  .dependsOn(hardfloat)
  .dependsOn(rocketMacros)
lazy val ECPT = (project in file("."))
  .settings(commonSettings, chiselSettings)
  .dependsOn(rocketchip)
/* Switchable */








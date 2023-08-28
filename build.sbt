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


val chiselVersion = "3.5.6"

lazy val commonSettings = Seq(
  scalaVersion := "2.13.10",
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked"
  ),
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "org.json4s" %% "json4s-jackson" % "4.0.6",
    "org.scalatest" %% "scalatest" % "3.2.14" % "test"
  ),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { x => false },
  pomExtra := <url>https://github.com/chipsalliance/rocket-chip</url>
  <licenses>
    <license>
      <name>Apache 2</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
    <license>
      <name>BSD-style</name>
        <url>http://www.opensource.org/licenses/bsd-license.php</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>https://github.com/chipsalliance/rocketchip.git</url>
      <connection>scm:git:github.com/chipsalliance/rocketchip.git</connection>
    </scm>
)

lazy val chiselSettings = Seq(
  libraryDependencies ++= Seq(
    "edu.berkeley.cs" %% "chisel3" % chiselVersion,
    "edu.berkeley.cs" %% "chiseltest" % "0.5.5"
  ),
  addCompilerPlugin(("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion).cross(CrossVersion.full))
)

lazy val cde = (project in file("rocket-chip/cde"))
  .settings(commonSettings)
  .settings(
    Compile / scalaSource := baseDirectory.value / "cde/src/chipsalliance/rocketchip"
  )

lazy val hardfloat = (project in file("rocket-chip/hardfloat"))
  .settings(commonSettings, chiselSettings)

lazy val rocketMacros = (project in file("rocket-chip/macros"))
  .settings(commonSettings)

lazy val rocketchip = (Project("rocket-chip", file("rocket-chip/src")))
  .settings(commonSettings, chiselSettings)
  .settings(
    Compile / scalaSource       := baseDirectory.value / "main" / "scala",
    Compile / resourceDirectory := baseDirectory.value / "main" / "resources"
  )
  .dependsOn(cde)
  .dependsOn(hardfloat)
  .dependsOn(rocketMacros)

lazy val boom = (Project("riscv-boom", file("riscv-boom/src")))
  .settings(commonSettings, chiselSettings)
  .settings(
    Compile / scalaSource       := baseDirectory.value / "main" / "scala",
    Compile / resourceDirectory := baseDirectory.value / "main" / "resources"
  )
lazy val `api-config-chipsalliance` = (project in file("api-config-chipsalliance/build-rules/sbt"))
  .settings(commonSettings)
  .settings(publishArtifact := false)

lazy val ECPT = (project in file("."))
  .settings(commonSettings, chiselSettings)
  .dependsOn(rocketchip)


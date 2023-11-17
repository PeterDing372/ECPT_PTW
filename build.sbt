// val chiselVersion = "3.5.4"

// lazy val commonSettings = Seq(
//   scalaVersion := "2.12.10",
//   scalacOptions ++= Seq(
//     "-language:reflectiveCalls",
//     "-deprecation",
//     "-unchecked",
//     "-feature",
//     "-Xsource:2.11"
//   ),
//   libraryDependencies ++= Seq(
//     "org.scala-lang" % "scala-reflect" % scalaVersion.value,
//     "org.json4s" %% "json4s-jackson" % "3.6.12",
//     "org.scalatest" %% "scalatest" % "3.2.12" % "test"
//   ),
//   addCompilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full))
// )

// lazy val chiselSettings = Seq(
//   libraryDependencies ++= Seq(
//     "edu.berkeley.cs" %% "chisel3" % chiselVersion,
//     "edu.berkeley.cs" %% "chiseltest" % "0.5.4"
//   ),
//   addCompilerPlugin(("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion).cross(CrossVersion.full))
// )

/* Switchable */
// lazy val `api-config-chipsalliance` = (project in file("rocket-chip/api-config-chipsalliance/build-rules/sbt"))
//   .settings(commonSettings)

// lazy val hardfloat = (project in file("rocket-chip/hardfloat"))
//   .settings(commonSettings, chiselSettings)

// lazy val rocketMacros = (project in file("rocket-chip/macros"))
//   .settings(commonSettings)

// lazy val rocketchip = (Project("rocket-chip", file("rocket-chip/src")))
//   .settings(commonSettings, chiselSettings)
//   .settings(
//     Compile / scalaSource := baseDirectory.value / "main" / "scala",
//     Compile / resourceDirectory := baseDirectory.value / "main" / "resources"
//   )
//   .dependsOn(`api-config-chipsalliance`)
//   .dependsOn(hardfloat)
//   .dependsOn(rocketMacros)
// lazy val ECPT = (project in file("."))
//   .settings(commonSettings, chiselSettings)
//   .dependsOn(rocketchip)


/* Switchable */
val chiselVersion = "3.5.5"

lazy val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version      := "1.6.0",
  scalaVersion := "2.13.10",
  parallelExecution in Global := false,
  traceLevel   := 15,
  scalacOptions ++= Seq("-deprecation","-unchecked"),
  libraryDependencies ++= Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value),
  libraryDependencies ++= Seq("org.json4s" %% "json4s-jackson" % "3.6.6"),
  libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % "3.2.0" % "test"),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal
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
    </scm>,
  publishTo := {
    val v = version.value
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT")) {
      Some("snapshots" at nexus + "content/repositories/snapshots")
    }
    else {
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
  }
)

lazy val chiselSettings = Seq(
  libraryDependencies ++= Seq("edu.berkeley.cs" %% "chisel3" % chiselVersion, 
                              "edu.berkeley.cs" %% "chiseltest" % "0.5.4"),
    

  addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full)
)
lazy val rocketchip = (project in file("rocket-chip"))
lazy val rocketLibDeps = (rocketchip / Keys.libraryDependencies)
lazy val boom = (project in file("riscv-boom"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val root = (project in file("."))
  .settings(commonSettings, chiselSettings)
  // .aggregate(rocketchip)
  .dependsOn(rocketchip)






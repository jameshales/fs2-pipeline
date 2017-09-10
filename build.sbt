organization := "org.jameshales"
name := "fs2-pipeline"

Settings.settings

libraryDependencies ++= Seq(
  "co.fs2"         %% "fs2-core"        % "0.9.7",
  "co.fs2"         %% "fs2-cats"        % "0.3.0"        % "test",
  "org.typelevel"  %% "cats-effect"     % "0.3"          % "test",
  "org.jameshales" %% "fs2-cats-effect" % "0.1-SNAPSHOT" % "test",
  "org.scalacheck" %% "scalacheck"      % "1.13.5"       % "test",
  "org.scalatest"  %% "scalatest"       % "3.0.1"        % "test"
)

organization := "org.jameshales"
name := "fs2-pipeline"

Settings.settings

libraryDependencies ++= Seq(
  "co.fs2"         %% "fs2-core"        % "0.10.0-M6",
  "org.typelevel"  %% "cats-effect"     % "0.4"    % "test",
  "org.scalacheck" %% "scalacheck"      % "1.13.5" % "test",
  "org.scalatest"  %% "scalatest"       % "3.0.1"  % "test"
)

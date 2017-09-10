import sbt._
import sbt.Keys._
import sbt.Resolver

object Settings {
  lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

  private val standardScalacOptions = Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked",
    "-Xfuture",
    "-target:jvm-1.8"
  )

  private val strictScalacOptions = Seq(
    //"-Xfatal-warnings",
    "-Xlint",
    "-Yno-adapted-args",
    "-Yrangepos",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused",
    "-Ywarn-unused-import",
    "-Ywarn-value-discard"
  )

  val settings = Seq(
    scalaVersion := "2.12.3",

    scalacOptions ++= standardScalacOptions ++ strictScalacOptions,

    // Disable warnings in console
    scalacOptions in (Compile, console) := standardScalacOptions,
    scalacOptions in (Test, console)    := standardScalacOptions,

    resolvers ++= Seq(
      Resolver.typesafeRepo("releases")
    ),

    //Stops the auto creation of java / scala-2.x directories
    unmanagedSourceDirectories in Compile ~= { _.filter(_.exists) },
    unmanagedSourceDirectories in Test ~= { _.filter(_.exists) },

    // Auto run scalastyle on compile
    compileScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Compile).toTask("").value,
    (compile in Compile) := ((compile in Compile) dependsOn compileScalastyle ).value
  )
}

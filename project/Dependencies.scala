import sbt.Keys._
import sbt.{Def, _}

object Dependencies extends AutoPlugin {

  object V {
    val circe = "0.13.0"
    val http4s = "0.21.1"
    val cats = "2.1.1"
    val catsTagless = "0.11"
    val log4catsMain = "1.0.1"
  }

  private val dependencies = Seq(
    "io.circe" %% "circe-core" % V.circe,
    "io.circe" %% "circe-generic" % V.circe,
    "io.circe" %% "circe-refined" % V.circe,
    "io.circe" %% "circe-parser" % V.circe % Test,
    "io.circe" %% "circe-literal" % V.circe % Test,
    "org.http4s" %% "http4s-circe" % V.http4s,
    "org.http4s" %% "http4s-client" % V.http4s,
    "org.http4s" %% "http4s-core" % V.http4s,
    "org.http4s" %% "http4s-dsl" % V.http4s,
    "org.http4s" %% "http4s-async-http-client" % V.http4s,
    "org.typelevel" %% "cats-core" % V.cats,
    "org.typelevel" %% "cats-effect" % V.cats,
    "org.typelevel" %% "cats-free" % V.cats,
    "org.typelevel" %% "cats-mtl-core" % "0.7.0",
    "org.typelevel" %% "cats-tagless-core" % V.catsTagless,
    "org.typelevel" %% "cats-tagless-macros" % V.catsTagless,
    "org.scalatest" %% "scalatest" % "3.1.1" % Test,
    "io.chrisdavenport" %% "log4cats-core" % V.log4catsMain,
    "io.chrisdavenport" %% "log4cats-slf4j" % V.log4catsMain,
    "io.chrisdavenport" %% "log4cats-mtl" % "0.1.0",
    "org.http4s" %% "http4s-blaze-server" % V.http4s,
    "org.http4s" %% "http4s-server" % V.http4s,
    "org.slf4j" % "slf4j-api" % "1.7.27",
    "com.github.tomakehurst" % "wiremock-standalone" % "2.24.1" % Test,
  )

  object autoImport {

    implicit final class DependenciesProject(val project: Project) extends AnyVal {

      def withDependencies: Project =
        project
          .settings(libraryDependencies ++= dependencies)
          .settings(addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.10"))
          .settings(addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"))
    }
  }

}

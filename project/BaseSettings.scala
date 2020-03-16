import sbt.Keys._
import sbt.{Def, _}

object BaseSettings {

  def default(module: Option[String] = None): Seq[Def.Setting[_]] =
    Seq(
      organization := "org.caoilte",
      name := "Credit Scoring Service",
      moduleName := s"credit-scoring-service${module.fold("")("-" + _)}",
      scalaVersion := "2.12.10",
      shellPrompt := { s =>
        "[" + scala.Console.BLUE + Project.extract(s).currentProject.id + scala.Console.RESET + "] $ "
      }
    )
}

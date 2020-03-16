lazy val root =
  (project in file("."))
    .settings(BaseSettings.default())
    .withDependencies
    .withTestConfig()

mainClass in (Compile, run) := Some("server.Main")

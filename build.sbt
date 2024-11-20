val sourceMode = setSourceMode(false)

lazy val protoPackageRegistrySettings = Seq(
  resolvers += "Tomshley Proto Registry" at
    "https://gitlab.com/api/v4/projects/60384332/packages/maven",
  credentials += Credentials(file("./.secure_files/.credentials.gitlab"))
)

lazy val hexagonalSettings = Seq(
  resolvers += "Tomshley Hexagonal Registry" at
    "https://gitlab.com/api/v4/projects/61841284/packages/maven",
)
lazy val dockerSettings = Seq(
  dockerExposedPorts ++= Seq(9900, 8080),
  dockerBaseImage := "eclipse-temurin:21-jre-jammy",
  dockerUsername := Some("www-tomshley-com-contact-service"),
  dockerRepository := Some(
    "registry.gitlab.com/tomshley/brands/usa/tomshleyllc/tech"
  )
)

val contactService =
  publishableProject("www-tomshley-com-contact-service", Some(file(".")))
    .enablePlugins(ValueAddProjectPlugin, ForkJVMRunConfigPlugin, VersionFilePlugin, SecureFilesPlugin)
    .sourceDependency(
      ProjectRef(file("../www-tomshley-com-proto"), "www-tomshley-com-proto"),
      "com.tomshley.www" % "www-tomshley-com-proto_3" % "0.0.2"
    )
    .sourceDependency(
      ProjectRef(
        file(
          "../../../../global/tware/tech/products/hexagonal/hexagonal-lib-jvm"
        ),
        "hexagonal-lib"
      ),
      "com.tomshley.hexagonal" % "hexagonal-lib_3" % "0.0.20"
    )
    .settings(
      Compile / run / mainClass := Some("com.tomshley.www.contact.main"),
    )
    .settings(protoPackageRegistrySettings *)
    .settings(hexagonalSettings *)
    .settings(dockerSettings *)

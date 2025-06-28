val zioSbtVersion = "0.4.0-alpha.30"

addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % zioSbtVersion)
addSbtPlugin("dev.zio" % "zio-sbt-website"   % zioSbtVersion)
addSbtPlugin("dev.zio" % "zio-sbt-ci"        % zioSbtVersion)

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.1")
addSbtPlugin("io.spray"       % "sbt-revolver"        % "0.10.0")
addSbtPlugin("ch.epfl.scala"  % "sbt-scalafix"        % "0.14.0")

addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")

libraryDependencies += "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % "0.6.2"

// addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.13.0")
// addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.21.1") 

resolvers ++= Resolver.sonatypeOssRepos("public")
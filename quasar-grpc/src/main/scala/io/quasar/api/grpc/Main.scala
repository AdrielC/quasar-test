package io.quasar.api.grpc

import zio.*

object Main extends ZIOAppDefault:
    
  val server: ZIO[Any, Throwable, Unit] =
    for
      _ <- ZIO.logInfo("Starting server")
    yield ()

  override def run = server
end Main

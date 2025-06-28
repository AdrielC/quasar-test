package io.quasar.blobstore.v1.server

import io.quasar.blobstore.v1.blobstore.ZioBlobstore
import scalapb.zio_grpc.ServerMain
import scalapb.zio_grpc.ServiceList
import zio.*
import zio.logging.*
import zio.logging.backend.SLF4J
import io.grpc.ServerBuilder

/**
 * gRPC Server for the BlobStore service.
 * 
 * This server runs the BlobStore service on a configurable port and provides
 * comprehensive logging and error handling.
 */
object BlobStoreServer extends ZIOAppDefault {

  // Configuration for the server
  case class ServerConfig(
    port: Int = 9000,
    host: String = "0.0.0.0"
  )

  val serverConfig = ServerConfig()

  // Create the BlobStore service implementation
  val blobStoreService = new BlobStoreService()

  // Server configuration
  val serverLayer: ZLayer[Any, Throwable, io.grpc.Server] =
    ZLayer.scoped {
      for {
        _ <- ZIO.logInfo(s"Starting BlobStore gRPC server on ${serverConfig.host}:${serverConfig.port}")
        
        // Build the gRPC server using the correct binding method
        serviceDefinition <- ZioBlobstore.BlobStore.genericBindable.bind(blobStoreService)
        server <- ZIO.attempt {
          ServerBuilder
            .forPort(serverConfig.port)
            .addService(serviceDefinition)
            .build()
        }
        
        // Start the server
        _ <- ZIO.attempt(server.start())
        _ <- ZIO.logInfo(s"BlobStore gRPC server started successfully on port ${serverConfig.port}")
        
        // Add shutdown hook
        _ <- ZIO.addFinalizer {
          ZIO.attempt(server.shutdown()).orDie *>
          ZIO.logInfo("BlobStore gRPC server stopped")
        }
        
      } yield server
    }

  // Main application
  override def run: ZIO[ZIOAppArgs, Any, Any] = {
    val program = for {
      server <- ZIO.service[io.grpc.Server]
      _ <- ZIO.logInfo("BlobStore server is ready to accept connections")
      _ <- ZIO.logInfo("Available services:")
      _ <- ZIO.logInfo("- io.quasar.blobstore.v1.BlobStore")
      _ <- ZIO.logInfo("Press Ctrl+C to stop the server")
      
      // Keep the server running
      _ <- ZIO.never
    } yield ()

    program
      .provide(
        serverLayer,
        // Logging configuration
        Runtime.removeDefaultLoggers >>> SLF4J.slf4j
      )
      .exitCode
  }
}

/**
 * Alternative server implementation using ServerMain for more advanced scenarios.
 */
object BlobStoreServerMain extends ServerMain {
  
  override def port: Int = 9000

  override def services: ServiceList[Any] = ServiceList.add(new BlobStoreService())

  // Custom server configuration
  override def serverBuilder: ZIO[Any, Throwable, ServerBuilder[_]] = {
    ZIO.attempt(
      ServerBuilder
        .forPort(port)
        .maxInboundMessageSize(4 * 1024 * 1024) // 4MB max message size
        .maxInboundMetadataSize(8 * 1024) // 8KB max metadata size
    )
  }

  // Add custom logging and lifecycle hooks
  def serverWillStart: ZIO[Any, Throwable, Unit] = {
    ZIO.logInfo(s"BlobStore gRPC server starting on port $port") *>
    ZIO.logInfo("Service endpoints:") *>
    ZIO.logInfo("- CreateUploadSession: Creates a new upload session") *>
    ZIO.logInfo("- ValidateSession: Validates an existing session") *>
    ZIO.logInfo("- UploadBlob: Uploads blob data in chunks") *>
    ZIO.logInfo("- DownloadBlob: Downloads blob data as chunks") *>
    ZIO.logInfo("- GetBlobInfo: Retrieves blob information and metadata") *>
    ZIO.logInfo("- DeleteBlob: Deletes a blob and its metadata") *>
    ZIO.logInfo("- UpdateMetadata: Updates blob metadata") *>
    ZIO.logInfo("- GetMetadata: Retrieves blob metadata")
  }

  def serverDidStart: ZIO[Any, Throwable, Unit] = {
    ZIO.logInfo(s"BlobStore gRPC server started successfully on port $port") *>
    ZIO.logInfo("Server is ready to accept connections")
  }
} 
package io.quasar.blobstore.v1.client

import io.quasar.blobstore.v1.blobstore.*
import io.quasar.blobstore.v1.examples.BlobStoreExample
import scalapb.zio_grpc.ZManagedChannel
import zio.*
import zio.stream.*
import zio.logging.*
import zio.logging.backend.SLF4J
import io.grpc.ManagedChannelBuilder

/**
 * Example client for the BlobStore gRPC service.
 * 
 * This client demonstrates all the BlobStore operations including:
 * - Creating upload sessions
 * - Uploading blobs in chunks
 * - Downloading blobs
 * - Managing metadata
 */
object BlobStoreClientExample extends ZIOAppDefault {

  // Client configuration
  case class ClientConfig(
    host: String = "localhost",
    port: Int = 9000
  )

  val clientConfig = ClientConfig()

  // Create a managed gRPC channel
  val channelLayer: ZLayer[Any, Throwable, ZioBlobstore.BlobStoreClient] = {
    val managedChannel = ZManagedChannel(
      ManagedChannelBuilder
        .forAddress(clientConfig.host, clientConfig.port)
        .usePlaintext()
    )
    
    ZioBlobstore.BlobStoreClient.live(managedChannel)
  }

  // Helper: Create a timestamp from current time
  private def createTimestamp(): Timestamp = {
    val currentTimeMillis = java.lang.System.currentTimeMillis()
    Timestamp(
      seconds = currentTimeMillis / 1000,
      nanos = ((currentTimeMillis % 1000) * 1000000).toInt
    )
  }

  // Example: Upload a text file
  def uploadTextFile(
    client: ZioBlobstore.BlobStoreClient,
    fileName: String,
    content: String,
    userId: String
  ): ZIO[Any, Throwable, BlobInfo] = {
    for {
      _ <- ZIO.logInfo(s"Uploading file: $fileName")
      
      // 1. Create upload session
      sessionRequest = BlobStoreExample.createUploadSessionRequest(
        userId = userId,
        fileName = fileName,
        content = content,
        metadata = Map(
          "application" -> Map(
            "source" -> "client-example",
            "version" -> "1.0.0"
          ),
          "user" -> Map(
            "uploaded_by" -> userId,
            "upload_time" -> createTimestamp().toString
          )
        )
      )
      
      sessionResponse <- client.createUploadSession(sessionRequest)
      session <- sessionResponse.result match {
        case CreateUploadSessionResponse.Result.Session(s) => ZIO.succeed(s)
        case CreateUploadSessionResponse.Result.Error(e) => ZIO.fail(new RuntimeException(s"Failed to create session: ${e.message}"))
        case CreateUploadSessionResponse.Result.Empty => ZIO.fail(new RuntimeException("Empty session response"))
      }
      
      _ <- ZIO.logInfo(s"Created upload session: ${session.sessionId}")
      
      // 2. Upload blob chunks
      chunks = BlobStoreExample.createBlobChunks(session.sessionId, content, chunkSize = 1024)
      chunkStream = ZStream.fromIterable(chunks)
      
      uploadResponse <- client.uploadBlob(chunkStream)
      blobInfo <- uploadResponse.result match {
        case UploadBlobResponse.Result.Info(info) => ZIO.succeed(info)
        case UploadBlobResponse.Result.Error(e) => ZIO.fail(new RuntimeException(s"Failed to upload blob: ${e.message}"))
        case UploadBlobResponse.Result.Empty => ZIO.fail(new RuntimeException("Empty upload response"))
      }
      
      _ <- ZIO.logInfo(s"Successfully uploaded blob with hash: ${blobInfo.descriptor.map(_.canonicalAddress.map(_.hash)).flatten.getOrElse("unknown")}")
      
    } yield blobInfo
  }

  // Example: Download a blob
  def downloadBlob(
    client: ZioBlobstore.BlobStoreClient,
    address: BlobAddress,
    userId: String
  ): ZIO[Any, Throwable, String] = {
    for {
      _ <- ZIO.logInfo(s"Downloading blob: ${address.hash}")
      
      downloadRequest = BlobStoreExample.createDownloadRequest(address, userId)
      chunkStream = client.downloadBlob(downloadRequest)
      
      chunks <- chunkStream.runCollect
      combinedData = chunks.flatMap(_.data.toByteArray).toArray
      content = new String(combinedData, "UTF-8")
      
      _ <- ZIO.logInfo(s"Downloaded ${combinedData.length} bytes")
      
    } yield content
  }

  // Example: Get blob information
  def getBlobInfo(
    client: ZioBlobstore.BlobStoreClient,
    address: BlobAddress,
    userId: String
  ): ZIO[Any, Throwable, BlobInfo] = {
    for {
      _ <- ZIO.logInfo(s"Getting blob info for: ${address.hash}")
      
      request = GetBlobInfoRequest(
        address = Some(address),
        principal = Some(BlobStoreExample.createUserPrincipal(userId))
      )
      
      blobInfo <- client.getBlobInfo(request)
      
      _ <- ZIO.logInfo(s"Blob info retrieved - Size: ${blobInfo.descriptor.map(_.sizeBytes).getOrElse(0)} bytes")
      _ <- ZIO.logInfo(s"Metadata namespaces: ${blobInfo.metadata.keys.mkString(", ")}")
      
    } yield blobInfo
  }

  // Example: Update metadata
  def updateMetadata(
    client: ZioBlobstore.BlobStoreClient,
    address: BlobAddress,
    userId: String,
    newMetadata: Map[String, Map[String, Any]]
  ): ZIO[Any, Throwable, Unit] = {
    for {
      _ <- ZIO.logInfo(s"Updating metadata for blob: ${address.hash}")
      
      metadataMap = newMetadata.map { case (ns, data) => 
        ns -> BlobStoreExample.createMetadata(ns, data) 
      }
      
      request = UpdateMetadataRequest(
        address = Some(address),
        principal = Some(BlobStoreExample.createUserPrincipal(userId)),
        metadata = metadataMap,
        mergeMode = true
      )
      
      response <- client.updateMetadata(request)
      
      _ <- response.result match {
        case UpdateMetadataResponse.Result.Metadata(meta) =>
          ZIO.logInfo(s"Metadata updated successfully. Namespaces: ${meta.metadata.keys.mkString(", ")}")
        case UpdateMetadataResponse.Result.Error(e) =>
          ZIO.fail(new RuntimeException(s"Failed to update metadata: ${e.message}"))
        case UpdateMetadataResponse.Result.Empty =>
          ZIO.fail(new RuntimeException("Empty metadata response"))
      }
      
    } yield ()
  }

  // Main client application
  override def run: ZIO[ZIOAppArgs, Any, Any] = {
    val program = for {
      client <- ZIO.service[ZioBlobstore.BlobStoreClient]
      
      _ <- ZIO.logInfo("=== BlobStore Client Example ===")
      
      // Test data
      fileName = "example.txt"
      content = "Hello, BlobStore! This is a test file content."
      userId = "test-user-123"
      
      // 1. Upload a file
      _ <- ZIO.logInfo("\n1. Uploading file...")
      blobInfo <- uploadTextFile(client, fileName, content, userId)
      address = blobInfo.descriptor.flatMap(_.canonicalAddress).get
      
      // 2. Get blob information
      _ <- ZIO.logInfo("\n2. Getting blob information...")
      _ <- getBlobInfo(client, address, userId)
      
      // 3. Download the file
      _ <- ZIO.logInfo("\n3. Downloading file...")
      downloadedContent <- downloadBlob(client, address, userId)
      _ <- ZIO.logInfo(s"Downloaded content: $downloadedContent")
      
      // 4. Update metadata
      _ <- ZIO.logInfo("\n4. Updating metadata...")
      _ <- updateMetadata(client, address, userId, Map(
        "processing" -> Map(
          "status" -> "processed",
          "processed_at" -> java.lang.System.currentTimeMillis(),
          "processor" -> "client-example"
        )
      ))
      
      // 5. Get updated blob information
      _ <- ZIO.logInfo("\n5. Getting updated blob information...")
      updatedInfo <- getBlobInfo(client, address, userId)
      _ <- ZIO.logInfo(s"Updated metadata namespaces: ${updatedInfo.metadata.keys.mkString(", ")}")
      
      // 6. Verify content integrity
      _ <- ZIO.logInfo("\n6. Verifying content integrity...")
      _ <- if (content == downloadedContent) {
        ZIO.logInfo("✅ Content integrity verified - upload and download successful!")
      } else {
        ZIO.logError("❌ Content integrity check failed!")
      }
      
      _ <- ZIO.logInfo("\n=== Client example completed successfully! ===")
      
    } yield ()

    program
      .provide(
        channelLayer,
        Runtime.removeDefaultLoggers >>> SLF4J.slf4j
      )
      .catchAll { error =>
        ZIO.logError(s"Client error: ${error.getMessage}") *>
        ZIO.logError("Make sure the BlobStore server is running on localhost:9000")
      }
      .exitCode
  }
} 
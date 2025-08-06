package io.quasar.blobstore.v1.server

import io.quasar.blobstore.v1.*

import io.quasar.api.v1.blobstore.examples.BlobStoreExample
import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.stream.*


object BlobStoreServiceSpec extends ZIOSpecDefault {

  def spec = suite("BlobStore gRPC Service")(
    suite("Session Management")(
      test("should create upload session successfully") {
        for {
          service <- ZIO.succeed(new BlobStoreService())
          
          request = BlobStoreExample.createUploadSessionRequest(
            userId = "test-user",
            fileName = "test.txt",
            content = "Hello, World!"
          )
          
          response <- service.createUploadSession(request)
          
        } yield assertTrue(
          response.result.isSession,
          response.result.session.exists(_.sessionId.nonEmpty),
          response.result.session.exists(_.state == SessionState.SESSION_STATE_ACTIVE)
        )
      },
      
      test("should validate existing session") {
        for {
          service <- ZIO.succeed(new BlobStoreService())
          
          // First create a session
          createRequest = BlobStoreExample.createUploadSessionRequest(
            userId = "test-user",
            fileName = "test.txt",
            content = "Hello, World!"
          )
          createResponse <- service.createUploadSession(createRequest)
          sessionId = createResponse.result.session.get.sessionId
          
          // Then validate it
          validateRequest = ValidateSessionRequest(sessionId = sessionId)
          validateResponse <- service.validateSession(validateRequest)
          
        } yield assertTrue(
          validateResponse.result.isSession,
          validateResponse.result.session.exists(_.sessionId == sessionId)
        )
      },
      
      test("should return error for non-existent session") {
        for {
          service <- ZIO.succeed(new BlobStoreService())
          
          request = ValidateSessionRequest(sessionId = "non-existent-session")
          response <- service.validateSession(request)
          
        } yield assertTrue(
          response.result.isError,
          response.result.error.exists(_.code == ErrorCode.ERROR_CODE_NOT_FOUND)
        )
      }
    ),
    
    suite("Blob Operations")(
      test("should upload and retrieve blob successfully") {
        for {
          service <- ZIO.succeed(new BlobStoreService())
          
          // Create session
          sessionRequest = BlobStoreExample.createUploadSessionRequest(
            userId = "test-user",
            fileName = "test.txt",
            content = "Hello, World!"
          )
          sessionResponse <- service.createUploadSession(sessionRequest)
          session = sessionResponse.result.session.get
          
          // Upload blob
          chunks = BlobStoreExample.createBlobChunks(session.sessionId, "Hello, World!", chunkSize = 5)
          chunkStream = ZStream.fromIterable(chunks)
          uploadResponse <- service.uploadBlob(chunkStream)
          
          // Get blob info
          blobAddress = session.expectedBlob.flatMap(_.canonicalAddress).get
          infoRequest = GetBlobInfoRequest(
            address = Some(blobAddress),
            principal = Some(BlobStoreExample.createUserPrincipal("test-user"))
          )
          blobInfo <- service.getBlobInfo(infoRequest)
          
        } yield assertTrue(
          uploadResponse.result.isInfo,
          blobInfo.descriptor.exists(_.name == "test.txt"),
          blobInfo.descriptor.exists(_.sizeBytes == 13L) // "Hello, World!" length
        )
      },
      
      test("should download blob correctly") {
        for {
          service <- ZIO.succeed(new BlobStoreService())
          content = "Hello, BlobStore!"
          
          // Create session and upload
          sessionRequest = BlobStoreExample.createUploadSessionRequest(
            userId = "test-user",
            fileName = "test.txt",
            content = content
          )
          sessionResponse <- service.createUploadSession(sessionRequest)
          session = sessionResponse.result.session.get
          
          chunks = BlobStoreExample.createBlobChunks(session.sessionId, content)
          chunkStream = ZStream.fromIterable(chunks)
          _ <- service.uploadBlob(chunkStream)
          
          // Download blob
          blobAddress = session.expectedBlob.flatMap(_.canonicalAddress).get
          downloadRequest = DownloadBlobRequest(
            address = Some(blobAddress),
            principal = Some(BlobStoreExample.createUserPrincipal("test-user")),
            includeMetadata = false
          )
          
          downloadStream = service.downloadBlob(downloadRequest)
          downloadedChunks <- downloadStream.runCollect
          downloadedContent = new String(downloadedChunks.flatMap(_.data.toByteArray).toArray, "UTF-8")
          
        } yield assertTrue(
          downloadedContent == content
        )
      },
      
      test("should delete blob successfully") {
        for {
          service <- ZIO.succeed(new BlobStoreService())
          
          // Create and upload blob
          sessionRequest = BlobStoreExample.createUploadSessionRequest(
            userId = "test-user",
            fileName = "test.txt",
            content = "Hello, World!"
          )
          sessionResponse <- service.createUploadSession(sessionRequest)
          session = sessionResponse.result.session.get
          
          chunks = BlobStoreExample.createBlobChunks(session.sessionId, "Hello, World!")
          chunkStream = ZStream.fromIterable(chunks)
          _ <- service.uploadBlob(chunkStream)
          
          // Delete blob
          blobAddress = session.expectedBlob.flatMap(_.canonicalAddress).get
          deleteRequest = DeleteBlobRequest(
            address = Some(blobAddress),
            principal = Some(BlobStoreExample.createUserPrincipal("test-user")),
            force = false
          )
          deleteResponse <- service.deleteBlob(deleteRequest)
          
        } yield assertTrue(
          deleteResponse.result.isDeleteResult,
          deleteResponse.result.deleteResult.exists(_.deleted)
        )
      }
    ),
    
    suite("Metadata Operations")(
      test("should update and retrieve metadata") {
        for {
          service <- ZIO.succeed(new BlobStoreService())
          
          // Create and upload blob
          sessionRequest = BlobStoreExample.createUploadSessionRequest(
            userId = "test-user",
            fileName = "test.txt",
            content = "Hello, World!",
            metadata = Map(
              "initial" -> Map("key" -> "value")
            )
          )
          sessionResponse <- service.createUploadSession(sessionRequest)
          session = sessionResponse.result.session.get
          
          chunks = BlobStoreExample.createBlobChunks(session.sessionId, "Hello, World!")
          chunkStream = ZStream.fromIterable(chunks)
          _ <- service.uploadBlob(chunkStream)
          
          // Update metadata
          blobAddress = session.expectedBlob.flatMap(_.canonicalAddress).get
          newMetadata = Map(
            "updated" -> BlobStoreExample.createMetadata("updated", Map("new_key" -> "new_value"))
          )
          updateRequest = UpdateMetadataRequest(
            address = Some(blobAddress),
            principal = Some(BlobStoreExample.createUserPrincipal("test-user")),
            metadata = newMetadata,
            mergeMode = true
          )
          updateResponse <- service.updateMetadata(updateRequest)
          
          // Get metadata
          getRequest = GetMetadataRequest(
            address = Some(blobAddress),
            principal = Some(BlobStoreExample.createUserPrincipal("test-user")),
            namespaces = Seq.empty
          )
          metadataResponse <- service.getMetadata(getRequest)
          
        } yield assertTrue(
          updateResponse.result.isMetadata,
          metadataResponse.metadata.contains("initial"),
          metadataResponse.metadata.contains("updated"),
          metadataResponse.metadata.size >= 2
        )
      },
      
      test("should filter metadata by namespace") {
        for {
          service <- ZIO.succeed(new BlobStoreService())
          
          // Create and upload blob with multiple metadata namespaces
          sessionRequest = BlobStoreExample.createUploadSessionRequest(
            userId = "test-user",
            fileName = "test.txt",
            content = "Hello, World!",
            metadata = Map(
              "namespace1" -> Map("key1" -> "value1"),
              "namespace2" -> Map("key2" -> "value2"),
              "namespace3" -> Map("key3" -> "value3")
            )
          )
          sessionResponse <- service.createUploadSession(sessionRequest)
          session = sessionResponse.result.session.get
          
          chunks = BlobStoreExample.createBlobChunks(session.sessionId, "Hello, World!")
          chunkStream = ZStream.fromIterable(chunks)
          _ <- service.uploadBlob(chunkStream)
          
          // Get filtered metadata
          blobAddress = session.expectedBlob.flatMap(_.canonicalAddress).get
          getRequest = GetMetadataRequest(
            address = Some(blobAddress),
            principal = Some(BlobStoreExample.createUserPrincipal("test-user")),
            namespaces = Seq("namespace1", "namespace3")
          )
          metadataResponse <- service.getMetadata(getRequest)
          
        } yield assertTrue(
          metadataResponse.metadata.contains("namespace1"),
          metadataResponse.metadata.contains("namespace3"),
          !metadataResponse.metadata.contains("namespace2"),
          metadataResponse.metadata.size == 2
        )
      }
    ),
    
    suite("Error Handling")(
      test("should return error for invalid chunk sequence") {
        for {
          service <- ZIO.succeed(new BlobStoreService())
          
          // Create session
          sessionRequest = BlobStoreExample.createUploadSessionRequest(
            userId = "test-user",
            fileName = "test.txt",
            content = "Hello, World!"
          )
          sessionResponse <- service.createUploadSession(sessionRequest)
          session = sessionResponse.result.session.get
          
          // Create chunks with invalid sequence
          invalidChunks = List(
            BlobChunk(
              sessionId = session.sessionId,
              sequenceNumber = 0L,
              offsetBytes = 0L,
              data = com.google.protobuf.ByteString.copyFromUtf8("Hello"),
              flags = Some(ChunkFlags(isFinal = false, isCompressed = false, requiresValidation = false))
            ),
            BlobChunk(
              sessionId = session.sessionId,
              sequenceNumber = 2L, // Skip sequence 1
              offsetBytes = 5L,
              data = com.google.protobuf.ByteString.copyFromUtf8(", World!"),
              flags = Some(ChunkFlags(isFinal = true, isCompressed = false, requiresValidation = false))
            )
          )
          
          chunkStream = ZStream.fromIterable(invalidChunks)
          uploadResponse <- service.uploadBlob(chunkStream)
          
        } yield assertTrue(
          uploadResponse.result.isError,
          uploadResponse.result.error.exists(_.code == ErrorCode.ERROR_CODE_INVALID_REQUEST)
        )
      },
      
      test("should return error for non-existent blob") {
        for {
          service <- ZIO.succeed(new BlobStoreService())
          
          nonExistentAddress = BlobAddress(
            algorithm = HashAlgorithm.HASH_ALGORITHM_SHA256,
            hash = "nonexistent"
          )
          
          request = GetBlobInfoRequest(
            address = Some(nonExistentAddress),
            principal = Some(BlobStoreExample.createUserPrincipal("test-user"))
          )
          
          result <- service.getBlobInfo(request).either
          
        } yield assertTrue(
          result.isLeft
        )
      }
    )
  )
} 
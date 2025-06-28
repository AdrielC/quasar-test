package io.quasar.blobstore.v1.server

import io.quasar.blobstore.v1.blobstore.*
import io.grpc.Status
import zio.*
import zio.stream.*
import java.security.MessageDigest
import scala.collection.concurrent.TrieMap

/**
 * In-memory implementation of the BlobStore gRPC service.
 * 
 * This is a reference implementation that demonstrates all the BlobStore operations.
 * In production, you would replace the in-memory storage with persistent storage
 * like S3, database, filesystem, etc.
 */
class BlobStoreService extends ZioBlobstore.BlobStore {

  // In-memory storage - replace with persistent storage in production
  private val sessions = TrieMap[String, UploadSession]()
  private val blobs = TrieMap[String, StoredBlob]() // key = hash
  private val blobMetadata = TrieMap[String, Map[String, Metadata]]() // key = hash

  case class StoredBlob(
    descriptor: BlobDescriptor,
    data: Array[Byte],
    chunks: List[BlobChunk] = List.empty
  )

  // Helper: Create a timestamp from current time
  private def createTimestamp(): Timestamp = {
    val currentTimeMillis = java.lang.System.currentTimeMillis()
    Timestamp(
      seconds = currentTimeMillis / 1000,
      nanos = ((currentTimeMillis % 1000) * 1000000).toInt
    )
  }

  override def createUploadSession(
    request: CreateUploadSessionRequest
  ): IO[io.grpc.StatusException, CreateUploadSessionResponse] = {
    (for {
      sessionId <- Random.nextUUID.map(_.toString)
      now = createTimestamp()
      expiresAt = Timestamp(now.seconds + 3600, now.nanos) // 1 hour from now
      
      session = UploadSession(
        sessionId = sessionId,
        principal = request.principal,
        expectedBlob = request.blob,
        config = Some(request.config.getOrElse(defaultSessionConfig)),
        expiresAt = Some(expiresAt),
        state = SessionState.SESSION_STATE_ACTIVE
      )
      
      _ <- ZIO.succeed(sessions.put(sessionId, session))
      _ <- ZIO.succeed(blobMetadata.put(getBlobKey(request.blob.flatMap(_.canonicalAddress)), request.metadata))
      
    } yield CreateUploadSessionResponse(
      CreateUploadSessionResponse.Result.Session(session)
    ))
    // .catchAll(error => 
    //   ZIO.succeed(CreateUploadSessionResponse(
    //     CreateUploadSessionResponse.Result.Error(createInternalError(s"Session creation failed: ${error.toString}"))
    //   ))
    // )
  }

  override def validateSession(
    request: ValidateSessionRequest
  ): IO[io.grpc.StatusException, ValidateSessionResponse] = {
    ZIO.attempt {
      sessions.get(request.sessionId) match {
        case Some(session) if isSessionValid(session) =>
          ValidateSessionResponse(
            ValidateSessionResponse.Result.Session(session)
          )
        case Some(_) =>
          ValidateSessionResponse(
            ValidateSessionResponse.Result.Error(createError(
              ErrorCode.ERROR_CODE_SESSION_EXPIRED,
              "Session has expired"
            ))
          )
        case None =>
          ValidateSessionResponse(
            ValidateSessionResponse.Result.Error(createError(
              ErrorCode.ERROR_CODE_NOT_FOUND,
              "Session not found"
            ))
          )
      }
    }.refineToOrDie[io.grpc.StatusException]
  }

  override def uploadBlob(
    request: ZStream[Any, io.grpc.StatusException, BlobChunk]
  ): IO[io.grpc.StatusException, UploadBlobResponse] = {
    request.runCollect.flatMap { chunks =>
      if (chunks.isEmpty) {
        ZIO.succeed(UploadBlobResponse(
          UploadBlobResponse.Result.Error(createError(
            ErrorCode.ERROR_CODE_INVALID_REQUEST,
            "No chunks provided"
          ))
        ))
      } else {
        val sessionId = chunks.head.sessionId
        sessions.get(sessionId) match {
          case Some(session) if isSessionValid(session) =>
            processUpload(session, chunks.toList)
          case Some(_) =>
            ZIO.succeed(UploadBlobResponse(
              UploadBlobResponse.Result.Error(createError(
                ErrorCode.ERROR_CODE_SESSION_EXPIRED,
                "Session has expired"
              ))
            ))
          case None =>
            ZIO.succeed(UploadBlobResponse(
              UploadBlobResponse.Result.Error(createError(
                ErrorCode.ERROR_CODE_NOT_FOUND,
                "Session not found"
              ))
            ))
        }
      }
    }.catchAll(error =>
      ZIO.succeed(UploadBlobResponse(
        UploadBlobResponse.Result.Error(createInternalError(s"Upload failed: ${error.toString}"))
      ))
    )
  }

  override def downloadBlob(
    request: DownloadBlobRequest
  ): ZStream[Any, io.grpc.StatusException, BlobChunk] = {
    val blobKey = getBlobKey(request.address)
    
    ZStream.fromZIO {
      blobs.get(blobKey) match {
        case Some(storedBlob) =>
          val data = request.range match {
            case Some(range) =>
              val start = range.start.toInt
              val end = range.end.map(_.toInt).getOrElse(storedBlob.data.length)
              storedBlob.data.slice(start, end)
            case None =>
              storedBlob.data
          }
          
          ZIO.succeed(createDownloadChunks(data, "download-session"))
        case None =>
          ZIO.fail(io.grpc.StatusException(Status.NOT_FOUND.withDescription("Blob not found")))
      }
    }.flatMap(ZStream.fromIterable(_))
  }

  override def getBlobInfo(
    request: GetBlobInfoRequest
  ): IO[io.grpc.StatusException, BlobInfo] = {
    val blobKey = getBlobKey(request.address)
    
    ZIO.attempt {
      blobs.get(blobKey) match {
        case Some(storedBlob) =>
          val metadata = blobMetadata.getOrElse(blobKey, Map.empty)
          val accessInfo = AccessInfo(
            lastAccessed = Some(createTimestamp()),
            accessCount = 1L,
            accessedBy = Seq(request.principal.map(_.id).getOrElse("unknown"))
          )
          
          BlobInfo(
            descriptor = Some(storedBlob.descriptor),
            metadata = metadata,
            access = Some(accessInfo)
          )
        case None =>
          throw io.grpc.StatusException(Status.NOT_FOUND.withDescription("Blob not found"))
      }
    }
    .catchAll(error =>
      ZIO.fail(io.grpc.StatusException(Status.INTERNAL.withDescription(s"Get blob info failed: ${error.toString}")))
    )
  }

  override def deleteBlob(
    request: DeleteBlobRequest
  ): IO[io.grpc.StatusException, DeleteBlobResponse] = {
    ZIO.attempt {
      val blobKey = getBlobKey(request.address)
      
      blobs.get(blobKey) match {
        case Some(_) =>
          blobs.remove(blobKey)
          blobMetadata.remove(blobKey)
          DeleteBlobResponse(
            DeleteBlobResponse.Result.DeleteResult(DeleteResult(
              deleted = true,
              message = "Blob deleted successfully"
            ))
          )
        case None =>
          DeleteBlobResponse(
            DeleteBlobResponse.Result.Error(createError(
              ErrorCode.ERROR_CODE_NOT_FOUND,
              "Blob not found"
            ))
          )
      }
    }
    .catchAll(error =>
      ZIO.succeed(DeleteBlobResponse(
        DeleteBlobResponse.Result.Error(createInternalError(s"Delete failed: ${error.toString}"))
      ))
    )
  }

  override def updateMetadata(
    request: UpdateMetadataRequest
  ): IO[io.grpc.StatusException, UpdateMetadataResponse] = {
    ZIO.attempt {
      val blobKey = getBlobKey(request.address)
      
      blobs.get(blobKey) match {
        case Some(_) =>
          val currentMetadata = blobMetadata.getOrElse(blobKey, Map.empty)
          val newMetadata = if (request.mergeMode) {
            currentMetadata ++ request.metadata
          } else {
            request.metadata
          }
          
          blobMetadata.put(blobKey, newMetadata)
          UpdateMetadataResponse(
            UpdateMetadataResponse.Result.Metadata(MetadataResponse(newMetadata))
          )
        case None =>
          UpdateMetadataResponse(
            UpdateMetadataResponse.Result.Error(createError(
              ErrorCode.ERROR_CODE_NOT_FOUND,
              "Blob not found"
            ))
          )
      }
    }
    .refineToOrDie[io.grpc.StatusException]
    .catchAll(error =>
      ZIO.succeed(UpdateMetadataResponse(
        UpdateMetadataResponse.Result.Error(createInternalError(s"Update metadata failed: ${error.toString}"))
      ))
    )
  }

  override def getMetadata(
    request: GetMetadataRequest
  ): IO[io.grpc.StatusException, MetadataResponse] = {
    ZIO.attempt {
      val blobKey = getBlobKey(request.address)
      
      blobs.get(blobKey) match {
        case Some(_) =>
          val allMetadata = blobMetadata.getOrElse(blobKey, Map.empty)
          val filteredMetadata = if (request.namespaces.nonEmpty) {
            allMetadata.filter { case (namespace, _) => request.namespaces.contains(namespace) }
          } else {
            allMetadata
          }
          
          MetadataResponse(filteredMetadata)
        case None =>
          throw io.grpc.StatusException(Status.NOT_FOUND.withDescription("Blob not found"))
      }
    }
    .refineToOrDie[io.grpc.StatusException]
    .catchAll(error =>
      ZIO.fail(io.grpc.StatusException(Status.INTERNAL.withDescription(s"Get metadata failed: ${error.toString}")))
    )
  }

  // Helper methods

  private def defaultSessionConfig: SessionConfig = SessionConfig(
    chunkSizeBytes = 64 * 1024L, // 64KB
    acceptedEncodings = Seq(
      TransferEncoding.TRANSFER_ENCODING_IDENTITY,
      TransferEncoding.TRANSFER_ENCODING_BASE64
    ),
    validateChecksums = true,
    maxChunks = 1000,
    maxBlobSizeBytes = 100 * 1024 * 1024L // 100MB
  )

  private def isSessionValid(session: UploadSession): Boolean = {
    session.state == SessionState.SESSION_STATE_ACTIVE &&
    session.expiresAt.exists { expires =>
      val now = java.lang.System.currentTimeMillis() / 1000
      expires.seconds > now
    }
  }

  private def getBlobKey(address: Option[BlobAddress]): String = {
    address.map(addr => s"${addr.algorithm.name}:${addr.hash}").getOrElse("")
  }

  private def processUpload(session: UploadSession, chunks: List[BlobChunk]): UIO[UploadBlobResponse] = {
    ZIO.succeed {
      // Validate chunks
      val sortedChunks = chunks.sortBy(_.sequenceNumber)
      val expectedSequence = sortedChunks.indices.map(_.toLong)
      val actualSequence = sortedChunks.map(_.sequenceNumber)
      
      if (actualSequence != expectedSequence) {
        UploadBlobResponse(
          UploadBlobResponse.Result.Error(createError(
            ErrorCode.ERROR_CODE_INVALID_REQUEST,
            "Invalid chunk sequence"
          ))
        )
      } else {
        // Combine chunk data
        val combinedData = sortedChunks.flatMap(_.data.toByteArray).toArray
        
        // Validate blob hash
        val actualHash = calculateHash(combinedData, HashAlgorithm.HASH_ALGORITHM_SHA256)
        val expectedHash = session.expectedBlob.flatMap(_.canonicalAddress).map(_.hash).getOrElse("")
        
        if (actualHash == expectedHash) {
          // Store the blob
          val storedBlob = StoredBlob(
            descriptor = session.expectedBlob.get,
            data = combinedData,
            chunks = chunks
          )
          
          val blobKey = getBlobKey(session.expectedBlob.flatMap(_.canonicalAddress))
          blobs.put(blobKey, storedBlob)
          
          // Update session state
          val updatedSession = session.copy(state = SessionState.SESSION_STATE_COMPLETED)
          sessions.put(session.sessionId, updatedSession)
          
          // Create blob info response
          val metadata = blobMetadata.getOrElse(blobKey, Map.empty)
          val accessInfo = AccessInfo(
            lastAccessed = Some(createTimestamp()),
            accessCount = 1L,
            accessedBy = Seq(session.principal.map(_.id).getOrElse("unknown"))
          )
          
          val blobInfo = BlobInfo(
            descriptor = Some(storedBlob.descriptor),
            metadata = metadata,
            access = Some(accessInfo)
          )
          
          UploadBlobResponse(
            UploadBlobResponse.Result.Info(blobInfo)
          )
        } else {
          UploadBlobResponse(
            UploadBlobResponse.Result.Error(createError(
              ErrorCode.ERROR_CODE_INVALID_CHECKSUM,
              s"Hash mismatch: expected $expectedHash, got $actualHash"
            ))
          )
        }
      }
    }
  }

  private def createDownloadChunks(data: Array[Byte], sessionId: String, chunkSize: Int = 64 * 1024): List[BlobChunk] = {
    data.grouped(chunkSize).zipWithIndex.map { case (chunkData, index) =>
      BlobChunk(
        sessionId = sessionId,
        sequenceNumber = index.toLong,
        offsetBytes = (index * chunkSize).toLong,
        data = com.google.protobuf.ByteString.copyFrom(chunkData),
        encoding = Some(BlobEncoding(
          compression = CompressionType.COMPRESSION_TYPE_NONE,
          transfer = TransferEncoding.TRANSFER_ENCODING_IDENTITY
        )),
        checksum = Some(s"sha256:${calculateHash(chunkData, HashAlgorithm.HASH_ALGORITHM_SHA256)}"),
        flags = Some(ChunkFlags(
          isFinal = (index + 1) * chunkSize >= data.length,
          isCompressed = false,
          requiresValidation = true
        ))
      )
    }.toList
  }

  private def calculateHash(data: Array[Byte], algorithm: HashAlgorithm): String = {
    val digest = algorithm match {
      case HashAlgorithm.HASH_ALGORITHM_SHA256 => MessageDigest.getInstance("SHA-256")
      case HashAlgorithm.HASH_ALGORITHM_SHA512 => MessageDigest.getInstance("SHA-512")
      case HashAlgorithm.HASH_ALGORITHM_BLAKE3 => MessageDigest.getInstance("SHA-256") // Fallback for demo
      case _ => MessageDigest.getInstance("SHA-256")
    }
    digest.digest(data).map("%02x".format(_)).mkString
  }

  private def createError(code: ErrorCode, message: String, details: Map[String, String] = Map.empty): Error = {
    Error(
      code = code,
      message = message,
      details = details,
      traceId = Some(java.util.UUID.randomUUID().toString)
    )
  }

  private def createInternalError(message: String): Error = {
    createError(ErrorCode.ERROR_CODE_INTERNAL_ERROR, s"Internal error: $message")
  }
} 
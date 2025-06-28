package io.quasar.blobstore.v1.examples

import io.quasar.blobstore.v1.blobstore.*
import zio.*
import zio.stream.*

/**
 * Example demonstrating how to use the BlobStore service.
 * 
 * This example shows:
 * - Creating upload sessions
 * - Uploading blobs in chunks
 * - Downloading blobs
 * - Managing metadata
 * - Error handling
 */
object BlobStoreExample {

  // Example: Creating a blob address from content
  def createBlobAddress(content: String): BlobAddress = {
    val hash = java.security.MessageDigest.getInstance("SHA-256")
      .digest(content.getBytes("UTF-8"))
      .map("%02x".format(_))
      .mkString
    
    BlobAddress(
      algorithm = HashAlgorithm.HASH_ALGORITHM_SHA256,
      hash = hash
    )
  }

  // Helper: Create a timestamp from current time
  def createTimestamp(): Timestamp = {
    val currentTimeMillis = java.lang.System.currentTimeMillis()
    Timestamp(
      seconds = currentTimeMillis / 1000,
      nanos = ((currentTimeMillis % 1000) * 1000000).toInt
    )
  }

  // Example: Creating a blob descriptor for a text file
  def createTextBlobDescriptor(
    name: String,
    content: String,
    tags: Seq[String] = Seq.empty
  ): BlobDescriptor = {
    val encoding = BlobEncoding(
      compression = CompressionType.COMPRESSION_TYPE_NONE,
      transfer = TransferEncoding.TRANSFER_ENCODING_IDENTITY
    )
    
    BlobDescriptor(
      name = name,
      contentType = "text/plain",
      sizeBytes = content.getBytes("UTF-8").length.toLong,
      encoding = Some(encoding),
      canonicalAddress = Some(createBlobAddress(content)),
      originalAddress = Some(createBlobAddress(content)),
      createdAt = Some(createTimestamp()),
      tags = tags
    )
  }

  // Example: Creating a user principal
  def createUserPrincipal(userId: String): Principal = {
    Principal(
      id = userId,
      `type` = PrincipalType.PRINCIPAL_TYPE_USER,
      permissions = Seq("read", "write", "delete")
    )
  }

  // Helper: Convert Scala values to JsonValue
  def toJsonValue(value: Any): JsonValue = value match {
    case s: String => JsonValue(JsonValue.Kind.StringValue(s))
    case n: Int => JsonValue(JsonValue.Kind.NumberValue(n.toDouble))
    case n: Long => JsonValue(JsonValue.Kind.NumberValue(n.toDouble))
    case n: Double => JsonValue(JsonValue.Kind.NumberValue(n))
    case b: Boolean => JsonValue(JsonValue.Kind.BoolValue(b))
    case m: Map[String, Any] @unchecked => 
      val fields = m.map { case (k, v) => k -> toJsonValue(v) }
      JsonValue(JsonValue.Kind.StructValue(JsonStruct(fields)))
    case l: List[Any] @unchecked =>
      JsonValue(JsonValue.Kind.ListValue(JsonList(l.map(toJsonValue))))
    case null => JsonValue(JsonValue.Kind.NullValue(NullValue.NULL_VALUE))
    case _ => JsonValue(JsonValue.Kind.StringValue(value.toString))
  }

  // Example: Creating metadata with JSON data
  def createMetadata(namespace: String, data: Map[String, Any]): Metadata = {
    val jsonStruct = JsonStruct(data.map { case (k, v) => k -> toJsonValue(v) })
    
    Metadata(
      namespace = namespace,
      data = Some(jsonStruct),
      schema = None,
      updatedAt = Some(createTimestamp()),
      updatedBy = "system"
    )
  }

  // Example: Creating an upload session request
  def createUploadSessionRequest(
    userId: String,
    fileName: String,
    content: String,
    metadata: Map[String, Map[String, Any]] = Map.empty
  ): CreateUploadSessionRequest = {
    val principal = createUserPrincipal(userId)
    val blob = createTextBlobDescriptor(fileName, content)
    val metadataMap = metadata.map { case (ns, data) => 
      ns -> createMetadata(ns, data) 
    }
    
    val config = SessionConfig(
      chunkSizeBytes = 64 * 1024L, // 64KB chunks
      acceptedEncodings = Seq(
        TransferEncoding.TRANSFER_ENCODING_IDENTITY,
        TransferEncoding.TRANSFER_ENCODING_BASE64
      ),
      validateChecksums = true,
      maxChunks = 1000,
      maxBlobSizeBytes = 10 * 1024 * 1024L // 10MB max
    )
    
    CreateUploadSessionRequest(
      principal = Some(principal),
      blob = Some(blob),
      metadata = metadataMap,
      config = Some(config)
    )
  }

  // Example: Creating blob chunks from content
  def createBlobChunks(
    sessionId: String,
    content: String,
    chunkSize: Int = 1024
  ): List[BlobChunk] = {
    val bytes = content.getBytes("UTF-8")
    val chunks = bytes.grouped(chunkSize).zipWithIndex.toList
    
    chunks.map { case (chunkData, index) =>
      val flags = ChunkFlags(
        isFinal = index == chunks.length - 1,
        isCompressed = false,
        requiresValidation = true
      )
      
      BlobChunk(
        sessionId = sessionId,
        sequenceNumber = index.toLong,
        offsetBytes = (index * chunkSize).toLong,
        data = com.google.protobuf.ByteString.copyFrom(chunkData),
        encoding = Some(BlobEncoding(
          compression = CompressionType.COMPRESSION_TYPE_NONE,
          transfer = TransferEncoding.TRANSFER_ENCODING_IDENTITY
        )),
        checksum = Some(s"sha256:${java.security.MessageDigest.getInstance("SHA-256")
          .digest(chunkData)
          .map("%02x".format(_))
          .mkString}"),
        flags = Some(flags)
      )
    }
  }

  // Example: Creating a download request
  def createDownloadRequest(
    address: BlobAddress,
    userId: String,
    includeMetadata: Boolean = true
  ): DownloadBlobRequest = {
    DownloadBlobRequest(
      address = Some(address),
      principal = Some(createUserPrincipal(userId)),
      preferredEncoding = Some(BlobEncoding(
        compression = CompressionType.COMPRESSION_TYPE_NONE,
        transfer = TransferEncoding.TRANSFER_ENCODING_IDENTITY
      )),
      includeMetadata = includeMetadata,
      range = None // Download entire blob
    )
  }

  // Example: Creating a download request with byte range
  def createRangeDownloadRequest(
    address: BlobAddress,
    userId: String,
    start: Long,
    end: Option[Long] = None
  ): DownloadBlobRequest = {
    val range = ByteRange(start = start, end = end)
    
    DownloadBlobRequest(
      address = Some(address),
      principal = Some(createUserPrincipal(userId)),
      preferredEncoding = Some(BlobEncoding(
        compression = CompressionType.COMPRESSION_TYPE_NONE,
        transfer = TransferEncoding.TRANSFER_ENCODING_IDENTITY
      )),
      includeMetadata = false,
      range = Some(range)
    )
  }

  // Example: Creating error responses
  def createError(
    code: ErrorCode,
    message: String,
    details: Map[String, String] = Map.empty,
    traceId: Option[String] = None
  ): Error = {
    Error(
      code = code,
      message = message,
      details = details,
      traceId = traceId
    )
  }

  // Example usage demonstrations
  def demonstrateUsage(): Unit = {
    println("=== BlobStore Service Example ===\n")
    
    // 1. Create an upload session
    val uploadRequest = createUploadSessionRequest(
      userId = "user123",
      fileName = "example.txt",
      content = "Hello, BlobStore!",
      metadata = Map(
        "application" -> Map(
          "source" -> "example",
          "version" -> "1.0.0",
          "environment" -> "development"
        ),
        "user" -> Map(
          "uploaded_by" -> "user123",
          "department" -> "engineering"
        )
      )
    )
    
    println("1. Upload Session Request:")
    println(s"   Principal: ${uploadRequest.principal.map(_.id).getOrElse("unknown")}")
    println(s"   File: ${uploadRequest.blob.map(_.name).getOrElse("unknown")}")
    println(s"   Size: ${uploadRequest.blob.map(_.sizeBytes).getOrElse(0)} bytes")
    println(s"   Metadata namespaces: ${uploadRequest.metadata.keys.mkString(", ")}")
    println()
    
    // 2. Create blob chunks
    val sessionId = "session-12345"
    val chunks = createBlobChunks(sessionId, "Hello, BlobStore!", chunkSize = 8)
    
    println("2. Blob Chunks:")
    chunks.zipWithIndex.foreach { case (chunk, idx) =>
      println(s"   Chunk ${idx}: ${chunk.data.toStringUtf8} (${chunk.data.size} bytes)")
    }
    println()
    
    // 3. Create download request
    val blobAddress = createBlobAddress("Hello, BlobStore!")
    val downloadRequest = createDownloadRequest(blobAddress, "user123")
    
    println("3. Download Request:")
    println(s"   Address: ${blobAddress.algorithm} - ${blobAddress.hash}")
    println(s"   Principal: ${downloadRequest.principal.map(_.id).getOrElse("unknown")}")
    println(s"   Include metadata: ${downloadRequest.includeMetadata}")
    println()
    
    // 4. Create range download request
    val rangeRequest = createRangeDownloadRequest(blobAddress, "user123", 0, Some(5))
    
    println("4. Range Download Request:")
    println(s"   Range: ${rangeRequest.range.map(r => s"${r.start}-${r.end.getOrElse("end")}").getOrElse("full")}")
    println()
    
    // 5. Create error example
    val error = createError(
      ErrorCode.ERROR_CODE_BLOB_TOO_LARGE,
      "Blob exceeds maximum allowed size",
      Map(
        "max_size" -> "10MB",
        "actual_size" -> "15MB",
        "blob_name" -> "large-file.zip"
      ),
      Some("trace-789")
    )
    
    println("5. Error Example:")
    println(s"   Code: ${error.code}")
    println(s"   Message: ${error.message}")
    println(s"   Details: ${error.details}")
    println(s"   Trace ID: ${error.traceId.getOrElse("none")}")
  }
} 
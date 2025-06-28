# Block Storage

Block storage forms the foundation of Quasar's content-addressable storage system. It provides immutable, encrypted, and deduplicated storage of binary data blocks.

## BlockKey Specification

Every block is identified by a `BlockKey` that encodes:
- **Hash Algorithm**: SHA256, SHA512, or BLAKE3
- **Hash Value**: Cryptographic digest of the block content
- **Size Information**: Original and compressed sizes
- **Compression**: Optional compression algorithm (GZIP, ZSTD, Brotli, LZ4)

### Key Properties
- **Immutability**: BlockKeys are deterministic and cannot be modified
- **Integrity**: Hash verification ensures data hasn't been corrupted
- **Deduplication**: Identical content produces identical BlockKeys
- **Efficiency**: Compression reduces storage overhead

## Encryption Modes

Quasar supports multiple encryption schemes for data at rest:

### AES-256-GCM (Recommended)
- **Security**: Authenticated encryption with 256-bit keys
- **Performance**: Hardware acceleration on modern CPUs
- **Nonce**: 96-bit random nonce per block
- **Authentication**: Built-in integrity verification

### ChaCha20-Poly1305
- **Security**: Stream cipher with polynomial authentication
- **Performance**: Excellent on systems without AES hardware acceleration
- **Nonce**: 96-bit random nonce per block
- **Portability**: Pure software implementation

### AES-256-SIV (Synthetic IV)
- **Security**: Nonce-misuse resistant encryption
- **Determinism**: Same plaintext + key produces same ciphertext
- **Use Case**: When deterministic encryption is required
- **Performance**: Slower than GCM but more robust

## Resume Protocol

Large file uploads can be interrupted and resumed efficiently:

### Checkpoint Strategy
- **Block-level Checkpoints**: Track which blocks have been successfully stored
- **Partial Block Handling**: Resume within partially uploaded blocks
- **Session State**: Maintain upload progress across client disconnections
- **Timeout Handling**: Automatic cleanup of stale upload sessions

### Resume Process
1. **Session Recovery**: Client reconnects with session ID
2. **Progress Query**: Server reports which blocks are already stored
3. **Delta Upload**: Client uploads only missing blocks
4. **Verification**: Server validates block integrity before accepting

## Storage Strategies

### Local Storage
- **File System**: Direct storage on local disk
- **Directory Structure**: Content-addressed directory hierarchy
- **Atomic Operations**: Temporary files with atomic rename
- **Cleanup**: Garbage collection of unreferenced blocks

### Distributed Storage
- **Replication**: Configurable redundancy across nodes
- **Consistency**: Eventually consistent with conflict resolution
- **Partitioning**: Consistent hashing for load distribution
- **Failure Handling**: Automatic failover and recovery

### Cloud Storage
- **Object Storage**: Integration with S3, GCS, Azure Blob
- **Lifecycle Management**: Automatic tiering to cheaper storage classes
- **Cross-Region**: Geographic distribution for disaster recovery
- **Cost Optimization**: Intelligent placement based on access patterns

## Performance Considerations

### Caching
- **Block Cache**: In-memory cache for frequently accessed blocks
- **Metadata Cache**: Cache block existence and metadata
- **Negative Cache**: Remember blocks that don't exist
- **Cache Eviction**: LRU with size and time-based limits

### Compression Trade-offs
- **CPU vs Storage**: Balance compression ratio against CPU usage
- **Algorithm Selection**: Choose based on data characteristics
- **Compression Levels**: Configurable trade-offs between speed and ratio
- **Bypass Logic**: Skip compression for already-compressed data

### Network Optimization
- **Parallel Transfers**: Concurrent block uploads/downloads
- **Connection Pooling**: Reuse connections across operations
- **Compression**: Network-level compression for metadata
- **Batching**: Group small operations for efficiency

## Security Model

### Encryption Key Management
- **Key Derivation**: Derive block keys from master secrets
- **Key Rotation**: Support for periodic key updates
- **Key Escrow**: Optional key backup for data recovery
- **Hardware Security**: Integration with HSMs and secure enclaves

### Access Control
- **Principal Authentication**: Verify client identity
- **Permission Checks**: Enforce read/write permissions per block
- **Audit Logging**: Track all access attempts
- **Rate Limiting**: Prevent abuse and DoS attacks

### Data Integrity
- **Hash Verification**: Validate blocks on read and write
- **Corruption Detection**: Periodic integrity checks
- **Repair Mechanisms**: Automatic recovery from corruption
- **Forensics**: Detailed logging for security analysis 
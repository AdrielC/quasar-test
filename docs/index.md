# Quasar: Content-Addressable Storage with Document Semantics

Quasar is a distributed storage system that combines content-addressable storage (CAS) with document-oriented semantics. It provides immutable, deduplicated storage with rich metadata, schema validation, and transformation capabilities. Inspired by remote build cache systems like Bazel and SBT, Quasar implements sophisticated semantic caching for deterministic transformations over immutable content.

## Architecture Overview

Quasar operates on a two-layer architecture:

```
                 ┌─────────────────────┐
                 │     Client/API      │
                 └────────┬────────────┘
                          │
                          ▼
             ┌────────────────────────────┐
             │        Quasar Layer        │
             │ ─────────────────────────  │
             │ DocumentKey, ViewKey       │
             │ Delta manifests            │
             │ Metadata schemas/validation│
             │ Access control (ABAC/RBAC) │
             │ Semantic URNs              │
             └────────┬───────────────────┘
                      │
                      ▼
             ┌────────────────────────────┐
             │       Graviton Layer       │
             │ ─────────────────────────  │
             │ BlobKey, FileLayout        │
             │ BlockIndex, ChunkRef       │
             │ Smart chunking (FastCDC)   │
             │ AES-GCM encryption         │
             │ CAS storage backend        │
             └────────┬───────────────────┘
                      │
                      ▼
             ┌────────────────────────────┐
             │   Blob Store (S3/MinIO)    │
             └────────────────────────────┘
```

### Graviton Layer (Storage Substrate)
- **Blob Storage**: Handles the fundamental storage primitive - content-addressable blobs
- **Content Addressing**: Immutable storage with cryptographic hashing
- **Network Protocol**: Distributed retrieval and replication
- **Encryption**: End-to-end encryption with multiple cipher suites

### Quasar Layer (Orchestration)
- **Document Semantics**: Schema-aware document storage and retrieval
- **Transformations**: View-based data transformations and projections
- **Semantic Caching**: Function-level caching of deterministic transformations
- **Access Control**: Principal-based permissions and quotas
- **Search & Indexing**: Rich querying capabilities over stored documents

## Core Concepts

### Blobs vs Documents

#### Blobs: Content-Addressable Storage Primitives
**Blobs** are the fundamental storage unit in Graviton - a nebulous entity that could be:
- **Block**: A fixed-size storage block (e.g., 64KB chunk)
- **Chunk**: A variable-size content-defined chunk from FastCDC
- **File**: A complete file stored as a single blob
- **Artifact**: Any binary content with a cryptographic address

**Key Properties of Blobs:**
- **Content-Addressed**: Identified by cryptographic hash of content
- **Immutable**: Cannot be modified once stored
- **Deduplication**: Identical content stored only once regardless of context
- **Agnostic**: No semantic meaning - just bytes with a hash

#### Documents: Semantic Entities
**Documents** are semantic constructs in the Quasar layer that give meaning to blobs:
- **User-Defined**: Meaning assigned by applications and users
- **Schema-Aware**: Structured according to defined schemas
- **Relationship-Rich**: Express relationships to other documents
- **Semantically Deduplicated**: Deduplication based on user-defined semantics

**Key Properties of Documents:**
- **Semantic Identity**: Meaning beyond just content hash
- **Metadata-Rich**: Extensive metadata and annotations
- **Transformation Target**: Subject to views and transformations
- **Access-Controlled**: Fine-grained permissions and policies

### Content Addressing
Every blob is addressed by its cryptographic hash, ensuring:
- **Immutability**: Content cannot be modified without changing its address
- **Deduplication**: Identical content is stored only once
- **Integrity**: Content corruption is immediately detectable
- **Verification**: Data authenticity is cryptographically guaranteed

### Semantic Caching
Quasar implements sophisticated caching of transformation functions:
- **Deterministic Operations**: Same inputs always produce same outputs
- **Hermetic Execution**: No dependency on ambient state or environment
- **Explicit Output Declaration**: All side effects explicitly declared
- **Merkle Tree Keys**: Cache keys derived from input content hashes

### Chunking Strategy
Large blobs are split into chunks using FastCDC (Fast Content-Defined Chunking):
- **Variable-size chunks**: More efficient deduplication than fixed-size
- **Alignment-aware**: Respects natural data boundaries
- **Configurable parameters**: Min/max chunk sizes, rolling hash windows

### File Layouts
Files are represented as immutable layouts containing:
- **Block Index**: B-tree structure mapping file ranges to storage blobs
- **Metadata**: File attributes, timestamps, permissions
- **Chunk References**: Pointers to deduplicated blobs
- **Layout Versioning**: Support for multiple representations of the same logical file

### Views and Transformations
Documents can be transformed through views:
- **Schema Evolution**: Handle changing document structures
- **Projections**: Extract subsets of data
- **Aggregations**: Compute derived values
- **Materialization**: Cache computed views for performance

## Key Features

### Storage Features
- **Multiple Hash Algorithms**: SHA256, SHA512, BLAKE3
- **Compression Support**: GZIP, ZSTD, Brotli, LZ4
- **Encryption Modes**: AES-256-GCM, ChaCha20-Poly1305, AES-256-SIV
- **Resume Protocol**: Interrupted transfers can be resumed
- **Range Requests**: Efficient partial blob retrieval

### API Features
- **Session Management**: Stateful upload/download sessions
- **Metadata Namespaces**: Organize metadata by application or domain
- **Schema Validation**: Enforce document structure constraints
- **Access Tracking**: Monitor usage patterns and access frequency
- **Quota Management**: Control storage usage per principal

### Operational Features
- **Distributed Architecture**: Horizontal scaling across multiple nodes
- **Replication**: Configurable redundancy levels
- **Monitoring**: Comprehensive metrics and health checks
- **Backup & Recovery**: Point-in-time recovery capabilities

## Use Cases

### Document Storage
- **Configuration Management**: Version-controlled application configs
- **Content Management**: Immutable document repositories
- **Data Archival**: Long-term storage with integrity guarantees

### Data Pipeline
- **ETL Workflows**: Immutable intermediate results with semantic caching
- **ML Training Data**: Versioned datasets with lineage tracking
- **Analytics**: Time-series data with efficient querying

### Distributed Systems
- **Service Discovery**: Immutable service definitions
- **Deployment Artifacts**: Container images and binaries
- **State Synchronization**: Distributed state management

## Getting Started

The system is accessed through a gRPC API that provides:
- **Blob Operations**: Store, retrieve, and manage binary data
- **Metadata Operations**: Attach and query structured metadata
- **Session Management**: Handle multi-part uploads and downloads
- **Administrative Operations**: Manage principals, quotas, and permissions

For detailed implementation specifics, see the individual documentation files:
- [Architecture](architecture.md) - System architecture and design principles
- [Block Storage](block-storage.md) - Low-level storage primitives
- [Chunking](chunking.md) - Content-defined chunking strategies  
- [File Layouts](file-layouts.md) - File representation and indexing
- [View Specification](view-spec.md) - Document transformations and views
- [Semantic Caching](semantic-caching.md) - Function-level caching system
- [URNs](urns.md) - Uniform Resource Names and addressing
- [Metadata](metadata.md) - Metadata management and schemas
- [Caching](caching.md) - Caching strategies and performance
- [PDF Handling](pdf-handling.md) - Specialized PDF processing
- [Encryption](encryption.md) - Cryptographic security model

## Community and Support

- **GitHub Repository**: [github.com/quasar-project/quasar](https://github.com/quasar-project/quasar)
- **Documentation**: [docs.quasar.dev](https://docs.quasar.dev)
- **Discord Community**: [discord.gg/quasar](https://discord.gg/quasar)
- **Issue Tracker**: [github.com/quasar-project/quasar/issues](https://github.com/quasar-project/quasar/issues) 
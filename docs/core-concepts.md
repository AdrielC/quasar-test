# Block vs Chunk Architecture


## 1. Overview

This document outlines the architectural design for Quasar's storage layer, integrating Torrent's content-addressable block model with higher-level, logical chunking. It supports resumable uploads, efficient deduplication, canonical hashing, and flexible transformation handling.

---

## 2. Key Concepts

### 2.1 Block

* **Fixed-size** (e.g., 256 KiB)
* Optimized for **resumable upload**, **fast seeking**, and **parallel I/O**
* Represented by `BlockKey(hash, algo, size, mediaTypeHint)`
* Stored as compressed/encrypted binary, but **hashed before encoding** for canonical identity
* **Fundamental unit of CAS storage**

### 2.2 Chunk

* **Variable-size**, semantically meaningful segments (e.g., PDF object, XML section)
* Split using content-defined boundaries (e.g., FastCDC)
* Can span one or more blocks
* Used for deduplication and logical file operations (views, deltas)
* **Higher-level logical grouping** — may map to one or more blocks, but **is not a subtype of block**

#### Chunk vs Block Relationship

* **Blocks** and **Chunks** are both atomic units that can comprise a File.
* **Blocks** are fixed-size, offset-aligned physical storage units; they are optimized for resume, seek, and dedup.
* **Chunks** are semantically meaningful logical segments that may or may not align with block boundaries.
* A chunk may span one or more blocks, or a block may serve as a complete chunk if alignment allows.
* Conceptually, neither is a subtype of the other — they are different **views over the binary stream**.
* Chunks may guide the transform/view logic in Quasar, while Blocks drive the storage-level dedup and retrieval in Torrent.

### 2.3 FileKey

* Represents the entire logical file:

  * `FileKey(hash, algo, size, mediaType)`
* Multiple layouts (block sequences) can map to the same FileKey

### 2.4 ViewKey (Quasar)

* Represents a transformation over a FileKey
* Encodes a series of operations (e.g., OCR, redactions) as a delta manifest
* Deterministic and memoizable
* Note: **ViewKeys are part of the Quasar layer**, not Torrent
* Quasar owns the logic for view materialization, caching, and resolution (e.g., OCR, redactions) as a delta manifest
* Deterministic and memoizable

---

## 3. Hashing Strategy

### Canonical Hashing

* Compute hash on **decompressed, unencrypted** byte stream
* Guarantees stable identity across encodings
* Ensures deduplication across transforms and formats

### Compression/Encryption

* Applied **after** hashing
* Stored form may be compressed (e.g., zstd, gzip) and/or encrypted (e.g., KMS)
* Deterministic compression optional (e.g., `gzip --no-name`)

---

## 4. Chunking Strategy

### Block: Fixed-Size

* e.g., 256 KiB
* Benefits:

  * Simple offset math
  * Fast resume and parallelization
  * Compatible with sparse I/O and caching

### Chunk: Content-Aware

* Smart splitting via FastCDC or format parser
* Enforce:

  * **minChunkSize** (e.g., 64 KiB)
  * **maxChunkSize** (e.g., 1 MiB)

### Hybrid Pipeline

```text
Full file stream → Smart chunker → Blocks → Hash → Compress → Store
```

* Allows smart dedup across content boundaries
* Aligns with Git/Restic-style delta stores

---

## 5. Resume Upload Support

### Workflow

1. Client computes or requests `BlockKey`s
2. Server returns known/missing blocks
3. Client resumes upload from first missing block
4. On completion, client submits `FileLayout` and/or `FileKey`

### BlockIndex Structure

```scala
final case class BlockIndex(
  fileSize: Long,
  blockSizes: Chunk[Int],
  cumulativeOffsets: Chunk[Long],
  blockHashes: Chunk[Hash]
)
```

Used to:

* Seek into file by offset
* Resume interrupted streams
* Support range-based download

---

## 6. FileLayout + ChunkIndex

### FileLayout

```scala
final case class FileLayout(
  fileKey: FileKey,
  layoutId: UUID,
  blocks: Chunk[BlockKey],
  chunkingStrategy: Option[String],
  insertedAt: Instant
)
```

* Describes a specific block sequence for a file
* Supports multiple layouts per content

### ChunkIndex

```scala
final case class ChunkRef(
  chunkId: UUID,
  blockKeys: Chunk[BlockKey],
  offsetHint: Option[Long],
  length: Int,
  semanticHint: Option[String]
)
```

* Semantic map from chunks to blocks
* Enables delta creation, view rendering, smart UI previews

---

## 7. ViewKey and Delta Manifest

### ViewKey

```scala
final case class ViewKey(
  base: FileKey,
  opChain: ListMap[UUID, Chunk[DynamicValue]]
)
```

* Represents a transformed view of a file
* Composed of deterministic operations (e.g., OCR page 3, redact region)
* Enables:

  * Memoization
  * Auditability
  * Non-destructive transforms

### Delta Manifest Example

```json
{
  "base": "fileKey:abc123",
  "ops": [
    { "op": "ocr", "page": 3 },
    { "op": "redact", "region": [100, 200, 300, 400] }
  ]
}
```

---

## 8. URN Naming and Layer Model

### URN Strategy Overview

We adopt the contemporary URI/URN model where:

* **URNs are opaque identifiers** not bound to a specific resolution method
* **Quasar** assigns and interprets these URNs
* **Torrent** stores physical data but does not resolve or interpret URNs

### Namespace Prefix

We use the `urn:quasar:` prefix to indicate the system namespace. Alternatives like `urn:ioquasar:` are avoided to keep it short and readable.

### Format

```
urn:quasar:<type>:<subtype>:<id>[?+r-component][?=q-component][#fragment]
```

Where:

* `type`: major category (`block`, `file`, `view`, `document`, `schema`, etc.)
* `subtype`: optional algorithm, layout, or strategy (e.g., `sha256`, `blake3`, `folder`, `uuid`)
* `id`: base64url-encoded or URI-safe hash, UUID, or string identifier
* `?+`: **r-component** for resolver hints (e.g., `?+=format=zip&compression=deflate`)
* `?=`: **q-component** for resource-specific filters (e.g., `?=tags=sealed&recursive=true`)

### Examples

* **Block**:

  ```
  urn:quasar:block:sha256:uDiVw93rzQ0wWtw-Vg7ufkV8BgM
  ```
* **File**:

  ```
  urn:quasar:file:blake3:Cyd8aJkN...aNQ?=mediaType=application/pdf
  ```
* **View**:

  ```
  urn:quasar:view:folder/xyz?+=format=zip&compression=store?=tags=confidential
  ```
* **Document**:

  ```
  urn:quasar:document:uuid:de305d54-75b4-431b-adb2-eb6b9e546014
  ```
* **Schema**:

  ```
  urn:quasar:schema:org.tybera.case/filing-metadata@v1
  ```

### Parser Spec

You can model a strongly typed parser in Scala like:

```scala
final case class QuasarURN(
  nid: String = "quasar",
  resourceType: String,
  subtype: Option[String],
  id: String,
  rComponent: Map[String, String],
  qComponent: Map[String, String],
  fragment: Option[String]
)
```

This enables dynamic interpretation of the URN in both the Quasar resolver and client tooling.

### Rationale

* Enforces **location independence**
* Encodes **semantic identity + resolver options**
* Enables **caching and deduplication based on meaning**, not just raw bytes

This makes Quasar’s URNs more like a cross between DOI and IPFS — globally unique, semantically meaningful, and optionally resolvable.

### URN Layout

```
urn:quasar:<type>:<id>[?+r][?=q][#fragment]
```

Examples:

* `urn:quasar:file:blake3:abc123?=mediaType=application/pdf`
* `urn:quasar:view:folder/xyz?+=format=zip?=tags=sealed`

### Layer Responsibilities

| Layer   | Ownership | Responsibility                                                  |
| ------- | --------- | --------------------------------------------------------------- |
| Torrent | Owns      | Blocks, Chunks, Files, BlockIndex, FileLayouts, CAS Metadata    |
| Quasar  | Owns      | Documents, Views, ViewKeys, Domain Metadata, Schema Enforcement |

---

## 9. Cryptographic and Technology Strategy

### Supported Algorithms

We support **Advanced Encryption Standard (AES)** in **Galois/Counter Mode (GCM)** with the following key sizes:

* AES-GCM-128
* AES-GCM-192
* AES-GCM-256

These are used to encrypt blocks and files after canonical hashing has been computed. Keys are managed through a configurable KMS abstraction.

### Technology Foundations

* **ZIO Schema** is used for describing typed metadata, file manifests, transforms, and view operations.
* All transforms are serialized as **typed, extensible operations** (e.g., OCR, redaction) that allow:

  * Remote execution
  * Semantic hashing
  * Composable diffs and patches

### Metadata System

* **Fully namespaced**, using `urn:quasar:schema:<name>` style
* Supports:

  * JSON Schema-based form generation and validation
  * Patchable structures with rich diff/merge capabilities
  * Custom extensions per org/domain

### Future Smart Caching

We plan to implement **SBT-style smart caching** by:

* Capturing all input hashes to view/render jobs
* Hashing the input set deterministically
* Using this to cache memoized outputs

This enables:

* Deduplication by *intent*, not just byte content
* Efficient reuse of prior view generations

### Documentation Layout Proposal

We will create separate docs for:

* `quasar_block_storage.md` (block structure, resume, indexing)
* `quasar_chunking_strategies.md` (FastCDC, format-aware chunkers)
* `quasar_view_spec.md` (ViewKey, operations, delta manifests)
* `quasar_urn_spec.md` (URN grammar, parser, use cases)
* `quasar_metadata_model.md` (schemas, typing, validation)
* `quasar_caching.md` (memoization, semantic cache keys)

---

## 10. Summary

* **Blocks** = low-level, fixed-size, CAS units
* **Chunks** = logical, semantic units derived from content
* **FileKey** = identity for raw content
* **ViewKey** = identity for transformed content
* **Hashing** is always on decompressed canonical content
* **Upload resumes** by skipping known blocks using `BlockKey`s
* **Transformation & dedup** work via chunk alignment and delta ops
* **Chunks are composed of Blocks**, not the other way around

You are not building legacy CAS — you’re building **a Git for legal records**, with dedup, views, schema enforcement, and seekable block streams.

Let me know if you want to add:

* Full gRPC spec for upload/download
* Client manifest format
* Test vectors for hashing and view operations

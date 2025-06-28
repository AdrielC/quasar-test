# Uniform Resource Names (URNs)

Quasar uses a comprehensive URN (Uniform Resource Name) system to provide persistent, location-independent identifiers for all resources within the system. URNs enable stable references that remain valid across system changes, migrations, and reorganizations.

## URN Structure

### Basic Format
Quasar URNs follow the standard URN syntax defined in RFC 8141:
```
urn:quasar:<namespace>:<specific-string>
```

### Components
- **Scheme**: Always `urn` for URN syntax compliance
- **Namespace Identifier (NID)**: Always `quasar` for Quasar resources
- **Namespace**: Specific resource namespace within Quasar
- **Specific String**: Resource-specific identifier within the namespace

## Namespace Hierarchy

### Core Namespaces

#### Document Namespace (`doc`)
For document-level resources:
```
urn:quasar:doc:<document-id>
urn:quasar:doc:sha256:a1b2c3d4...
urn:quasar:doc:user-docs:meeting-notes-2024-01-15
```

#### View Namespace (`view`)
For computed views and transformations:
```
urn:quasar:view:<view-id>
urn:quasar:view:analytics:user-engagement-monthly
urn:quasar:view:reports:financial-summary-q4
```

#### Block Namespace (`block`)
For storage blocks in the Graviton layer:
```
urn:quasar:block:<block-id>
urn:quasar:block:sha256:e5f6g7h8...
urn:quasar:block:compressed:zstd:a1b2c3d4...
```

#### Schema Namespace (`schema`)
For metadata schemas and validation rules:
```
urn:quasar:schema:<schema-id>
urn:quasar:schema:user-profile:v2
urn:quasar:schema:financial-record:2024
```

### Extended Namespaces

#### Principal Namespace (`principal`)
For user and service identities:
```
urn:quasar:principal:user:<user-id>
urn:quasar:principal:service:<service-name>
urn:quasar:principal:role:<role-name>
```

#### Collection Namespace (`collection`)
For grouped resources and datasets:
```
urn:quasar:collection:<collection-id>
urn:quasar:collection:project:alpha-release
urn:quasar:collection:dataset:ml-training-2024
```

#### Policy Namespace (`policy`)
For access control and governance policies:
```
urn:quasar:policy:<policy-id>
urn:quasar:policy:access:confidential-data
urn:quasar:policy:retention:financial-records
```

## Addressing Schemes

### Content-Addressed URNs
Resources addressed by their cryptographic hash:

#### Hash-Based Addressing
```
urn:quasar:doc:sha256:<hash>
urn:quasar:block:blake3:<hash>
urn:quasar:view:sha512:<hash>
```

#### Algorithm Support
- **SHA256**: Default hash algorithm for most resources
- **SHA512**: Higher security for sensitive resources
- **BLAKE3**: High-performance hashing for large datasets. (Not NIST)
- **Custom**: Support for domain-specific hash functions

### Semantic Addressing
Human-readable identifiers for stable references:

#### Hierarchical Names
```
urn:quasar:doc:org:department:project:document
urn:quasar:collection:2024:q1:financial-reports
urn:quasar:schema:api:v3:user-profile
```

#### Versioned Names
```
urn:quasar:doc:user-manual:v2.1.0
urn:quasar:schema:product-catalog:2024-03-15
urn:quasar:view:dashboard:latest
```

### Composite Addressing
Combine multiple addressing schemes:

#### Multi-Hash References
```
urn:quasar:doc:canonical:sha256:<hash1>:mirror:blake3:<hash2>
```

#### Layered Addressing
```
urn:quasar:view:base:user-analytics:derived:monthly-summary
```

## URN Resolution

### Resolution Process
1. **Parse URN**: Extract namespace and specific string
2. **Namespace Lookup**: Identify appropriate resolver
3. **Resource Location**: Find actual resource location
4. **Access Control**: Verify permissions for requesting principal
5. **Content Retrieval**: Fetch and return resource content

### Resolution Strategies

#### Direct Resolution
For content-addressed resources:
- Hash directly maps to storage location
- No additional lookup required
- Guaranteed uniqueness and integrity

#### Registry Resolution
For semantic names:
- Lookup in centralized registry
- Support for aliases and redirects
- Version resolution and selection

#### Distributed Resolution
For federated deployments:
- DHT-based resource location
- Peer-to-peer resolution protocols
- Caching of resolution results

## URN Management

### Registration
New URNs can be registered through:

#### Automatic Registration
- Content-addressed URNs generated automatically
- Hash-based URNs created during storage
- System-generated identifiers for internal resources

#### Manual Registration
- Semantic names registered by users
- Namespace allocation and management
- Collision detection and resolution

#### Bulk Registration
- Import existing identifier schemes
- Migration from legacy systems
- Batch processing of large datasets

### Lifecycle Management

#### Creation
- Validate URN syntax and uniqueness
- Register in appropriate namespace
- Initialize access control policies
- Create audit trail entry

#### Updates
- Immutable content-addressed URNs cannot be updated
- Semantic URNs can be redirected or aliased
- Version management for evolving resources
- Deprecation and retirement processes

#### Deletion
- Soft deletion with tombstone records
- Grace periods for dependent resources
- Cascade deletion policies
- Archive and recovery procedures

## Advanced Features

### URN Patterns

#### Wildcard Matching
```
urn:quasar:doc:project-alpha:*
urn:quasar:view:analytics:user-*:monthly
urn:quasar:collection:2024:*:reports
```

#### Regular Expressions
```
urn:quasar:doc:user-\d{4}-\d{2}-\d{2}:.*
urn:quasar:schema:api:v\d+\.\d+\.\d+
```

#### Template URNs
```
urn:quasar:doc:{project}:{date}:{document-type}
urn:quasar:view:{department}:{metric}:{period}
```

### URN Transformations

#### Canonicalization
- Convert URNs to canonical form
- Normalize case and encoding
- Resolve aliases and redirects
- Remove redundant components

#### Derivation
- Generate derived URNs from base URNs
- Apply transformation rules
- Maintain relationship metadata
- Support for complex derivation chains

#### Validation
- Syntax validation against URN standards
- Namespace-specific validation rules
- Content validation for hash-based URNs
- Access permission validation

## Integration Patterns

### API Integration
URNs as primary identifiers in APIs:
- REST endpoints using URN paths
- gRPC services with URN parameters
- GraphQL schemas with URN types
- WebSocket subscriptions to URN resources

### Database Integration
URNs in database schemas:
- Foreign key relationships using URNs
- Indexing strategies for URN lookups
- Query optimization for URN patterns
- Cross-database references via URNs

### Caching Integration
URN-based caching strategies:
- Cache keys derived from URNs
- Invalidation based on URN patterns
- Distributed caching with URN routing
- Cache warming using URN prefetching

## Performance Considerations

### Resolution Performance
- Cache frequently resolved URNs
- Optimize namespace lookup structures
- Parallel resolution for multiple URNs
- Batch resolution APIs

### Storage Efficiency
- Compact URN representation in storage
- Compression of repetitive URN patterns
- Deduplication of common URN prefixes
- Efficient encoding for network transmission

### Scalability
- Distributed URN registries
- Sharding strategies for large namespaces
- Load balancing for resolution services
- Horizontal scaling of URN infrastructure

## Security Model

### Access Control
- URN-based permission systems
- Namespace-level access policies
- Resource-specific authorization
- Audit logging for URN access

### Integrity Protection
- Cryptographic verification of hash-based URNs
- Digital signatures for semantic URNs
- Tamper detection for URN metadata
- Secure URN transmission protocols

### Privacy Considerations
- Anonymization of sensitive URNs
- Encrypted URN storage
- Access pattern obfuscation
- Privacy-preserving URN sharing 
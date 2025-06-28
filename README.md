# Quasar - ZIO Content Addressable Storage

A modern Content Addressable Storage (CAS) system built with ZIO, Scala 3, and gRPC.

## 🚀 Quick Start with Dev Container

This project includes a complete development environment using VS Code Dev Containers.

### Prerequisites

- [Docker](https://www.docker.com/get-started)
- [VS Code](https://code.visualstudio.com/)
- [Dev Containers Extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)

### Getting Started

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd quasar
   ```

2. **Open in Dev Container**:
   - Open VS Code
   - Press `Cmd+Shift+P` (Mac) or `Ctrl+Shift+P` (Windows/Linux)
   - Type "Dev Containers: Reopen in Container"
   - Wait for the container to build and start

3. **Start developing**:
   ```bash
   # Compile the project
   sbt compile
   
   # Run tests
   sbt test
   
   # Start the application
   sbt run
   ```

## 🛠️ Development Environment

The dev container includes:

### Core Tools
- **Java 21** (Temurin OpenJDK)
- **Scala 3.7.1**
- **SBT 1.10.6**
- **Metals** (Scala Language Server)
- **Node.js 20** (for frontend development)

### Development Tools
- **Scalafmt** - Code formatting
- **Scalafix** - Code linting and refactoring
- **Coursier** - Scala package manager
- **Ammonite** - Scala REPL
- **Bloop** - Fast compilation server

### Testing & Debugging
- **grpcurl** - gRPC testing tool
- **PostgreSQL client** - Database access
- **Redis tools** - Cache debugging

### Services (via Docker Compose)
- **PostgreSQL 16** - Primary database (port 5432)
- **Redis 7** - Caching layer (port 6379)
- **pgAdmin** - Database admin UI (port 5050)
- **Redis Commander** - Redis admin UI (port 8081)


## 🔧 Development Scripts

The dev container includes several helpful scripts:

```bash
# Full development setup
./scripts/dev-setup.sh

# Run all tests with coverage
./scripts/test-all.sh

# Format and fix code
./scripts/format-code.sh
```

## 🌐 Service URLs

When running in the dev container:

- **Application**: http://localhost:8080
- **pgAdmin**: http://localhost:5050 (admin@quasar.dev / admin)
- **Redis Commander**: http://localhost:8081

## 🗄️ Database

The PostgreSQL database comes pre-configured with:

- **Database**: `quasar`
- **Username**: `quasar`
- **Password**: `quasar`
- **Schemas**: `quasar` (main), `audit` (logging)

Sample tables for CAS system:
- `users` - User management
- `files` - File metadata and CAS storage
- `file_chunks` - Chunked upload tracking
- `upload_sessions` - Upload session management
- `activity_log` - Audit logging

## 🚀 ZIO Features

This project demonstrates:

- **ZIO HTTP** - High-performance HTTP server
- **ZIO gRPC** - Type-safe gRPC services
- **ZIO Config** - Configuration management
- **ZIO Logging** - Structured logging
- **ZIO Test** - Comprehensive testing
- **ZIO Schema** - Serialization and validation

## 📝 Code Style

The project uses:

- **Scalafmt** for consistent formatting
- **Scalafix** for code quality rules
- **Scala 3** syntax and features
- **ZIO 2.x** best practices

Configuration files:
- `.scalafmt.conf` - Formatting rules
- `.scalafix.conf` - Linting rules

## 🧪 Testing

Run tests with:

```bash
# All tests
sbt test

# Specific test
sbt "testOnly io.quasar.MainSpec"

# Tests with coverage
sbt clean coverage test coverageReport
```

## 🐛 Debugging

The dev container supports debugging:

- **JVM Debug Port**: 5005
- **VS Code Debugging**: Pre-configured launch configurations
- **Remote JVM**: Attach debugger to running processes

## 🤝 Contributing

1. Open in dev container
2. Create a feature branch
3. Make changes and test
4. Format code: `./scripts/format-code.sh`
5. Run tests: `./scripts/test-all.sh`
6. Submit pull request

## 📚 Resources

- [ZIO Documentation](https://zio.dev/)
- [Scala 3 Documentation](https://docs.scala-lang.org/scala3/)
- [gRPC Documentation](https://grpc.io/docs/)
- [Metals IDE Features](https://scalameta.org/metals/)

## 🎯 Useful Aliases

The dev container includes helpful aliases:

```bash
# SBT shortcuts
sbt-clean, sbt-compile, sbt-test, sbt-run

# gRPC testing
grpc-list, grpc-describe

# Docker shortcuts
dc (docker-compose), dps (docker ps)

# File operations
ll, la (ls -la)
```

Happy coding with ZIO! 🎉 




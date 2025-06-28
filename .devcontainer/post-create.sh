#!/bin/bash

# Post-create script for ZIO Scala development container
echo "🚀 Setting up ZIO Scala development environment..."

# Create necessary directories
mkdir -p ~/.ivy2 ~/.sbt ~/.cache ~/.metals ~/.bloop

# Set up Git configuration (if not already set)
if [ -z "$(git config --global user.name)" ]; then
    echo "⚙️ Setting up Git configuration..."
    git config --global init.defaultBranch main
    git config --global pull.rebase false
    git config --global core.autocrlf input
fi

# Install additional ZIO-related tools
echo "📦 Installing ZIO-related tools..."
cs install mill
cs install giter8

# Create useful aliases
echo "🔧 Setting up aliases..."
cat >> ~/.zshrc << 'EOL'

# ZIO Development Aliases
alias zio-new="g8 zio/zio-seed.g8"
alias sbt-clean="sbt clean"
alias sbt-compile="sbt compile"
alias sbt-test="sbt test"
alias sbt-run="sbt run"
alias sbt-console="sbt console"
alias metals-import="sbt bloopInstall"

# Useful shortcuts
alias ll="ls -la"
alias la="ls -la"
alias ..="cd .."
alias ...="cd ../.."
alias grep="grep --color=auto"

# Docker shortcuts
alias dc="docker-compose"
alias dps="docker ps"
alias di="docker images"

# gRPC testing shortcuts
alias grpc-list="grpcurl -plaintext"
alias grpc-describe="grpcurl -plaintext -describe"

EOL

# Set up scalafmt configuration if it doesn't exist
if [ ! -f .scalafmt.conf ]; then
    echo "📝 Creating .scalafmt.conf..."
    cat > .scalafmt.conf << 'EOL'
version = "3.8.3"
runner.dialect = scala3

maxColumn = 120
align.preset = most
align.multiline = false

rewrite.rules = [
  RedundantBraces,
  RedundantParens,
  SortModifiers,
  PreferCurlyFors
]

rewrite.redundantBraces.generalExpressions = false
rewrite.redundantBraces.ifElseExpressions = false

newlines.beforeCurlyLambdaParams = false
newlines.afterCurlyLambdaParams = keep

spaces.inImportCurlyBraces = true

includeCurlyBraceInSelectChains = false
includeNoParensInSelectChains = false

trailingCommas = always

project.excludeFilters = [
  "target/"
]
EOL
fi

# Set up .scalafix.conf if it doesn't exist
if [ ! -f .scalafix.conf ]; then
    echo "🔧 Creating .scalafix.conf..."
    cat > .scalafix.conf << 'EOL'
rules = [
  OrganizeImports,
  DisableSyntax,
  LeakingImplicitClassVal,
  NoValInForComprehension,
  ProcedureSyntax,
  RemoveUnused
]

OrganizeImports {
  removeUnused = true
  expandRelative = true
  groupedImports = Merge
}

DisableSyntax.noVars = true
DisableSyntax.noThrows = true
DisableSyntax.noNulls = true
DisableSyntax.noReturns = true
DisableSyntax.noWhileLoops = true
DisableSyntax.noAsInstanceOf = false
DisableSyntax.noIsInstanceOf = false
DisableSyntax.noXml = true
DisableSyntax.noDefaultArgs = false
DisableSyntax.noFinalVal = true
DisableSyntax.noFinalize = true
DisableSyntax.noValPatterns = true

RemoveUnused {
  imports = true
  privates = true
  locals = true
  patternvars = true
}
EOL
fi

# Initialize Metals if there's a build.sbt
if [ -f build.sbt ]; then
    echo "🔨 Initializing Metals and Bloop..."
    sbt bloopInstall
fi

# Set up useful development scripts
mkdir -p scripts

cat > scripts/dev-setup.sh << 'EOL'
#!/bin/bash
# Development setup script

echo "🔄 Running development setup..."

# Clean and compile
sbt clean compile

# Generate Bloop configuration for Metals
sbt bloopInstall

# Format code
sbt scalafmtAll

echo "✅ Development setup complete!"
EOL

chmod +x scripts/dev-setup.sh

cat > scripts/test-all.sh << 'EOL'
#!/bin/bash
# Run all tests with coverage

echo "🧪 Running all tests..."

# Run tests with coverage
sbt clean coverage test coverageReport

echo "📊 Test coverage report generated in target/scala-*/scoverage-report/"
EOL

chmod +x scripts/test-all.sh

cat > scripts/format-code.sh << 'EOL'
#!/bin/bash
# Format all code

echo "🎨 Formatting code..."

# Format Scala code
sbt scalafmtAll

# Fix imports and other issues
sbt "scalafix --rules OrganizeImports"

echo "✅ Code formatting complete!"
EOL

chmod +x scripts/format-code.sh

# Create a sample ZIO application if none exists
if [ ! -f src/main/scala/Main.scala ] && [ ! -d src ]; then
    echo "📝 Creating sample ZIO application..."
    mkdir -p src/main/scala/io/quasar
    
    cat > src/main/scala/io/quasar/Main.scala << 'EOL'
package io.quasar

import zio.*
import zio.http.*

object Main extends ZIOAppDefault:
  
  val app = Routes(
    Method.GET / "health" -> handler(Response.text("OK")),
    Method.GET / "hello" -> handler { (req: Request) =>
      val name = req.queryParamToOrElse("name", "World")
      Response.text(s"Hello, $name!")
    }
  )

  def run =
    for
      _ <- Console.printLine("🚀 Starting ZIO HTTP server on port 8080...")
      _ <- Server.serve(app).provide(Server.default)
    yield ()
EOL

    mkdir -p src/test/scala/io/quasar
    
    cat > src/test/scala/io/quasar/MainSpec.scala << 'EOL'
package io.quasar

import zio.*
import zio.test.*
import zio.http.*

object MainSpec extends ZIOSpecDefault:
  
  def spec = suite("MainSpec")(
    test("health endpoint returns OK") {
      for
        response <- Main.app.runZIO(Request.get(URL.decode("/health").toOption.get))
        body <- response.body.asString
      yield assertTrue(body == "OK")
    },
    
    test("hello endpoint returns greeting") {
      for
        response <- Main.app.runZIO(Request.get(URL.decode("/hello?name=ZIO").toOption.get))
        body <- response.body.asString
      yield assertTrue(body == "Hello, ZIO!")
    }
  )
EOL
fi

echo "✅ ZIO Scala development environment setup complete!"
echo ""
echo "🎯 Quick start commands:"
echo "  - sbt compile          # Compile the project"
echo "  - sbt test             # Run tests"
echo "  - sbt run              # Run the application"
echo "  - ./scripts/dev-setup.sh # Run full development setup"
echo ""
echo "🔧 Useful tools installed:"
echo "  - Scala 3.7.1"
echo "  - SBT 1.10.6"
echo "  - Metals (Scala LSP)"
echo "  - Scalafmt (code formatter)"
echo "  - Scalafix (code linter)"
echo "  - Coursier (package manager)"
echo "  - grpcurl (gRPC testing)"
echo ""
echo "Happy coding with ZIO! 🎉" 
# Jenkins Pipeline Metadata Extractor

A standalone tool to extract GDSL (Groovy DSL) metadata from a running Jenkins instance and convert it into a structured JSON format.

## Usage

### 1. Extract GDSL from Jenkins

```bash
./extract.sh output/
```

This starts a Docker container with Jenkins, installs necessary plugins, and extracts the GDSL file to `output/gdsl-output.groovy`.

### 2. Convert to JSON

```bash
./gradlew run --args="output/gdsl-output.groovy output/json/ 2.440.1 jenkins-core 2.440.1"
```

## Architecture

This tool is a specialized extractor maintained by the [Groovy Language Server](https://github.com/albertocavalcante/groovy-lsp) project.

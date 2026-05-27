# UVerify Backend

<p align="center">
  <a href="https://github.com/UVerify-io/uverify-backend/actions/workflows/build.yaml">
    <img src="https://img.shields.io/github/actions/workflow/status/UVerify-io/uverify-backend/build.yaml" alt="Build Workflow Status" />
  </a>
  <a href="https://UVerify-io.github.io/coverage-report/index.html">
    <img src="https://img.shields.io/github/actions/workflow/status/UVerify-io/uverify-backend/tests.yaml?label=tests" alt="Test Workflow Status" />
  </a>
  <a href="https://UVerify-io.github.io/uverify-backend/coverage-report/index.html"><img alt="Coverage" src="https://uverify-io.github.io/uverify-backend/coverage-report/badges/jacoco.svg" /></a>
  <a href="https://github.com/UVerify-io/uverify-backend/actions/workflows/release.yaml">
    <img src="https://img.shields.io/github/actions/workflow/status/UVerify-io/uverify-backend/release.yaml?label=release" alt="Release Workflow Status">
  </a>
  <a href="https://conventionalcommits.org">
    <img src="https://img.shields.io/badge/Conventional%20Commits-1.0.0-%23FE5196?logo=conventionalcommits&logoColor=white" alt="Conventional Commits">
  </a>
  <a href="https://discord.gg/Dvqkynn6xc">
    <img src="https://img.shields.io/discord/1263737876743589938" alt="Join our Discord">
  </a>
  <a href="https://cla-assistant.io/UVerify-io/uverify-backend"><img src="https://cla-assistant.io/readme/badge/UVerify-io/uverify-backend" alt="CLA assistant" /></a>
</p>

The Spring Boot backend that powers [app.uverify.io](https://app.uverify.io). It issues and verifies certificates on the Cardano blockchain using Plutus V3 validators, indexes chain state with Yaci Store, and exposes a REST API consumed by the UI and the UVerify SDKs.

## 🚀 Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.8+
- Git
- Docker (optional)

## 🐳 Running with Docker

```zsh
cp .env.example .env
# Edit .env with your settings
docker run --env-file .env -p 9090:9090 uverify/uverify-backend:latest
```

The API will be available at [http://localhost:9090](http://localhost:9090). The Swagger UI is at [http://localhost:9090/v1/api-docs](http://localhost:9090/v1/api-docs).

## 🏗️ Running Full Stack with Docker Compose

An example `docker-compose.yml` is available [here](https://github.com/UVerify-io/.github/blob/main/scripts/docker-compose.yml).

1. Download the example compose file:
   ```zsh
   curl -O https://raw.githubusercontent.com/UVerify-io/.github/main/scripts/docker-compose.yml
   ```
2. Copy and configure your environment file as above.

3. Start all services:
   ```zsh
   docker compose up
   ```
   This will start the backend, ui, and database containers.
   By default, the frontend will be available at [http://localhost:3000](http://localhost:3000) and the backend at [http://localhost:9090](http://localhost:9090).

> **Tip:** You can customize the services and ports in the `docker-compose.yml` file as needed.

### Installation

1. Clone the repository:
   ```zsh
   git clone https://github.com/UVerify-io/uverify-backend.git
   cd uverify-backend
    ```

2. Configure environment variables:
    - Copy the .env.example file to .env:
      ```zsh
      cp src/main/resources/.env.example .env
      ```
    - Edit the .env file with your configuration (see [Environment Variables](#-environment-variables) section)

3. Build and run the application:
   ```zsh
   mvn clean package
   java -jar target/uverify-backend-1.18.0.jar
   ```

## 🔧 Environment Variables

Copy `.env.example` to `.env` and place it next to the JAR file (or in `src/main/resources/` when running from IntelliJ).

### Spring Profiles

Set `SPRING_ACTIVE_PROFILES` to a comma-separated combination of one network profile and one database profile:

| Dimension | Values |
|-----------|--------|
| Network | `preprod`, `mainnet` |
| Database | `h2` (in-memory, development), `postgres` (production) |

Examples: `preprod,h2` — `mainnet,postgres`

### Core

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `SPRING_ACTIVE_PROFILES` | Active Spring profiles (see above) | `preprod,h2` | No |
| `PORT` | HTTP server port | `9090` | No |
| `MANAGEMENT_PORT` | Actuator / metrics port | `9091` | No |
| `LOG_LEVEL` | Root log level | `INFO` | No |
| `LOG_FILE` | Log file path (omit to log to console) | — | No |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed origins | `*` | No |
| `RATE_LIMIT_ENABLED` | Enable per-IP rate limiting on write endpoints | `true` | No |
| `RATE_LIMIT_REQUESTS_PER_MINUTE` | Max POST requests per minute per IP | `20` | No |

### Database

Only required when using the `postgres` profile. The `h2` profile uses an in-memory database and needs no additional configuration.

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `DB_URL` | JDBC connection URL | `jdbc:postgresql://localhost:5432/uverify` | postgres only |
| `DB_USERNAME` | Database username | `sa` | postgres only |
| `DB_PASSWORD` | Database password | — | postgres only |

### Cardano — Primary Backend

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `CARDANO_BACKEND_TYPE` | `blockfrost` or `koios` | `blockfrost` | Yes |
| `CARDANO_NETWORK` | `PREPROD` or `MAINNET` | — | Yes |
| `BLOCKFROST_BASE_URL` | Blockfrost API base URL | — | blockfrost only |
| `BLOCKFROST_PROJECT_ID` | Blockfrost project ID | — | blockfrost only |

### Cardano — Secondary Query Backend (optional)

When configured, UTXO queries use this endpoint instead of the primary backend. This is useful when the primary backend lags behind the chain indexer — for example, when you run a Blockfrost-compatible relay such as [Yano](https://github.com/bloxbean/yano) that shares the same data source as Yaci Store.

| Variable | Description | Default |
|----------|-------------|---------|
| `CARDANO_QUERY_BASE_URL` | Blockfrost-compatible base URL | — |
| `CARDANO_QUERY_PROJECT_ID` | Project ID (use `localtest` for local relays) | — |

### Cardano — Wallets

| Variable | Description | Required |
|----------|-------------|----------|
| `FACILITATOR_ACCOUNT_MNEMONIC` | 24-word mnemonic used by the backend to sign authentication messages and build state queries | Yes |
| `SERVICE_USER_ADDRESS` | Override the canonical service wallet address. Defaults to the standard preprod or mainnet address for this deployment. | No |

### Yaci Store — Chain Indexer

Yaci Store syncs directly from a Cardano relay node. The defaults point to public relay nodes and work out of the box for preprod and mainnet. Override them only if you want to connect to your own node.

| Variable | Description | Preprod default |
|----------|-------------|-----------------|
| `REMOTE_NODE_URL` | Cardano relay node hostname | `preprod-node.world.dev.cardano.org` |
| `REMOTE_NODE_PORT` | Node port | `30000` |

### Faucet (testnet / local development only)

The faucet endpoint lets example scripts fund wallets without a separate faucet service. It should never be enabled on mainnet.

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `FAUCET_ENABLED` | Enable `POST /api/v1/transaction/fund` | `false` | No |
| `FAUCET_MNEMONIC` | 24-word mnemonic of a pre-funded testnet wallet | — | if enabled |
| `FAUCET_UTXO_COUNT` | Number of UTxOs distributed per claim | `3` | No |
| `FAUCET_UTXO_AMOUNT_LOVELACE` | Lovelace per UTxO (default: 10 tADA) | `10000000` | No |
| `FAUCET_COOLDOWN_MS` | Minimum time between claims per address (ms) | `120000` | No |

### KERI / vLEI Credential Verification

UVerify supports the [KERI](https://keri.one/) identity protocol for verifiable credential anchoring. When a certificate carries KERI fields (`keri_oobi`, `keri_proof`), the backend can verify them against a live vLEI verifier.

| Variable | Description | Default |
|----------|-------------|---------|
| `VLEI_VERIFIER_URL` | Base URL of the vLEI verifier (KERIA instance) | — |
| `KERIA_TIMEOUT_MS` | Request timeout for KERIA calls (ms) | `3000` |

When `VLEI_VERIFIER_URL` is not set, the backend falls back to the `keri_verified` flag stored in the database (set at issuance time). Verification results are cached in memory for one hour.

## 🧩 Extensions

Extensions add domain-specific endpoints and on-chain logic on top of the core certificate system. Each extension is enabled independently via an environment variable.

### Tokenizable Certificate

Issues redeemable certificates backed by [CIP-68](https://github.com/cardano-foundation/CIPs/tree/master/CIP-0068) NFT pairs. The issuer locks a certificate into a sorted on-chain linked list; the recipient redeems it to receive a user token and a reference token.

| Variable | Default |
|----------|---------|
| `TOKENIZABLE_CERTIFICATE_EXTENSION_ENABLED` | `false` |

### Fractionized Certificate

Splits a certificate into fractional NFT holdings, allowing multiple parties to hold a share of the same credential.

| Variable | Default |
|----------|---------|
| `FRACTIONIZED_CERTIFICATE_EXTENSION_ENABLED` | `false` |

### Connected Goods

Attaches encrypted identity data to physical products via an on-chain social hub. Uses PBKDF2 key derivation and AES encryption.

| Variable | Description | Required |
|----------|-------------|----------|
| `CONNECTED_GOODS_EXTENSION_ENABLED` | Enable the extension | No (default `false`) |
| `CONNECTED_GOODS_SERVICE_WALLET_ADDRESS` | Service wallet address | if enabled |
| `CONNECTED_GOODS_ENCRYPTION_SALT` | Salt for the double-encryption layer | if enabled |

### Tadamon

Manages community service organisation data via Google Sheets and writes attestations on-chain.

| Variable | Description | Required |
|----------|-------------|----------|
| `TADAMON_EXTENSION_ENABLED` | Enable the extension | No (default `false`) |
| `TADAMON_ALLOWED_ADDRESSES` | Comma-separated list of permitted addresses | No |
| `TADAMON_GOOGLE_SHEETS_ID` | Google Sheets spreadsheet ID | if enabled |
| `TADAMON_GOOGLE_SHEETS_PRIVATE_KEY` | PEM-format private key for the service account | if enabled |
| `TADAMON_GOOGLE_SHEETS_SERVICE_ACCOUNT` | Service account email | if enabled |

## 💙 Contributing

We welcome all contributions. Please read our [Contributing Guidelines](CONTRIBUTING.md) before getting started.

- Use **semantic commits** for all contributions.
- Sign the **Contributor License Agreement (CLA)** before committing. You can review the CLA [here](./CLA.md) and sign it via [CLA Assistant](https://cla-assistant.io/). The CLA bot guides you through the process when you open a pull request.
- For feature requests or tasks, open an issue first to align with the project goals.

## 📚 Additional Documents

- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Security](SECURITY.md)
- [Contributor License Agreement (CLA)](./CLA.md)
- [Contributing Guidelines](CONTRIBUTING.md)

## 📜 License

This project is licensed under the **AGPL**. If this license does not match your use case, feel free to reach out at **[hello@uverify.io](mailto:hello@uverify.io)** to discuss alternative licensing options.

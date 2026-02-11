# 💎 Welcome to UVerify: Your Gateway to Blockchain Simplicity

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

UVerify makes blockchain technology accessible to everyone, regardless of prior experience. Effortlessly secure your file or text hashes on the Cardano blockchain. Want to try it out before diving into the code? Visit [app.uverify.io](https://app.uverify.io) to explore the app.

## 🚀 Getting Started

### Prerequisites
- Java 17 or higher
- Maven 3.8+
- Git
- Docker (optional, for containerized setup)

## 🐳 Running with Docker

You can run the backend as a Docker container without installing Java or Maven locally.

1. Copy and configure your environment file:
   ```zsh
   cp src/main/resources/.env.example .env
   ```
   Edit `.env` as needed.

2. Run the backend container:
   ```zsh
   docker run --env-file .env -p 9090:9090 uverify/uverify-backend:latest
   ```
   The backend will be available at [http://localhost:9090](http://localhost:9090).

## 🏗️ Running Full Stack with Docker Compose

To run the backend, ui, and database together, you can use Docker Compose.  
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
   java -jar target/uverify-backend-1.7.0.jar
   ```

## 🔧 Environment Variables

The application requires a `.env` file either in the same folder as the JAR file or in the `src/main/resources` folder when running with IntelliJ.

| Variable                               | Description                                                       | Example                                       | Required |
|----------------------------------------|-------------------------------------------------------------------|-----------------------------------------------|----------|
| SPRING_ACTIVE_PROFILES                 | Active Spring profiles (h2/postgres, mainnet/preprod, https)      | preprod,h2                                    | Yes      |
| LOG_LEVEL                              | Logging level for the application                                 | INFO                                          | No       |
| LOG_FILE                               | Log file path (if not set, logs to console)                       | ./logs/uverify.log                            | No       |
| CORS_ALLOWED_ORIGINS                   | Allowed origins for CORS                                          | http://localhost:3000                         | No       |
| KEY_STORE_PASSWORD                     | Password for the keystore (required when https profile is active) | password123                                   | No\*     |
| DB_URL                                 | Database connection URL when using H2                             | jdbc:h2:./data/db                             | No       |
| SERVICE_USER_ADDRESS                   | The wallet address for the service                                | addr_test1qqgmew8y57fsfc3...                  | Yes      |
| SERVICE_ACCOUNT_MNEMONIC               | The testnet account mnemonic (for testing purposes)               | word1 word2 ... word24                        | No       |
| FACILITATOR_ACCOUNT_MNEMONIC           | Mnemonic to sign authentication messages for state queries        | word1 word2 ... word24                        | No       |
| BLOCKFROST_PROJECT_ID                  | Your Blockfrost project ID                                        | preprod123abc                                 | Yes\**   |
| BLOCKFROST_BASE_URL                    | Blockfrost API base URL                                           | https://cardano-preprod.blockfrost.io/api/v0/ | Yes\**   |
| CARDANO_BACKEND_TYPE                   | Backend type (koios or blockfrost)                                | blockfrost                                    | Yes      |
| CARDANO_NETWORK                        | Cardano network to use (PREPROD or MAINNET)                       | PREPROD                                       | Yes      |
| CONNECTED_GOODS_EXTENSION_ENABLED      | Enable Connected Goods extension                                  | false                                         | No       |
| CONNECTED_GOODS_SERVICE_WALLET_ADDRESS | Address for testing Connected Goods extension                     | addr_test1...                                 | No       |
| CONNECTED_GOODS_ENCRYPTION_SALT        | Salt for double encryption of user data                           | randomsalt123                                 | No       |
| TADAMON_EXTENSION_ENABLED              | Enable Tadamon extension                                          | false                                         | No       |
| TADAMON_ALLOWED_ADDRESSES              | Addresses allowed to use Tadamon extension                        | addr1,addr2                                   | No       |
| TADAMON_GOOGLE_SHEETS_PRIVATE_KEY      | Google Sheets API private key                                     | -----BEGIN PRIVATE KEY-----\\n...             | No       |
| TADAMON_GOOGLE_SHEETS_SERVICE_ACCOUNT  | Google Sheets service account email                               | account@project.iam.gserviceaccount.com       | No       |
| TADAMON_GOOGLE_SHEETS_ID               | Google Sheets ID                                                  | 1FZZA0N...AaaAVe                              | No       |

\* Only required if you use the `https` profile.
\** Only required if you use the `blockfrost` backend type.

## 💙 Contributing

We welcome all contributions! Please read our [Contributing Guidelines](CONTRIBUTING.md) before getting started.

### Important Notes:

- Use **semantic commits** for all contributions.
- Sign the **Contributor License Agreement (CLA)** before committing. You can review the CLA [here](./CLA.md) and sign it via **[CLA Assistant](https://cla-assistant.io/)**. The CLA bot will guide you through the process when you open a pull request.
- For feature requests or tasks, please open an issue first to align with the project goals.

## 📚 Additional Documents

- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Security](SECURITY.md)
- [Contributor License Agreement (CLA)](./CLA.md)
- [Contributing Guidelines](CONTRIBUTING.md)

## 📜 License

This project is licensed under the **AGPL**. If this license does not match your use case, feel free to reach out to us at **[hello@uverify.io](mailto:hello@uverify.io)** to discuss alternative licensing options.
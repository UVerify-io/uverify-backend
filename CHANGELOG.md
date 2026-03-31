# Changelog

## [1.13.0](https://github.com/UVerify-io/uverify-backend/compare/v1.12.1...v1.13.0) (2026-03-31)


### Features

* add fractionized and tokenizable certificates ([992f7d6](https://github.com/UVerify-io/uverify-backend/commit/992f7d6d97907e753797498ad9e6abc381c113fe))
* certificates can now be tokenized and fractionized ([bb58f4f](https://github.com/UVerify-io/uverify-backend/commit/bb58f4f074e05809925c86418b42503f682b083c))


### Bug Fixes

* remove unused cert token ([7646e7a](https://github.com/UVerify-io/uverify-backend/commit/7646e7a0f4af7edda04d33eaf19a9632cc84e4ea))

## [1.12.1](https://github.com/UVerify-io/uverify-backend/compare/v1.12.0...v1.12.1) (2026-03-13)


### Bug Fixes

* add logging to endpoints to avoid 500er without internal messages ([80f0004](https://github.com/UVerify-io/uverify-backend/commit/80f00049f9a9b0c9e0e8dc6f3b15cd4b906e2a60))

## [1.12.0](https://github.com/UVerify-io/uverify-backend/compare/v1.11.3...v1.12.0) (2026-03-11)


### Features

* enhance api docs and apply best practices ([0909451](https://github.com/UVerify-io/uverify-backend/commit/090945111788e9ebf26fef7d0c3adc4bf97e9d3e))
* return boostrap information as part of the certificate data ([f039953](https://github.com/UVerify-io/uverify-backend/commit/f0399531487ae792cfdba9eba5a3b60106c5c16f))

## [1.11.3](https://github.com/UVerify-io/uverify-backend/compare/v1.11.2...v1.11.3) (2026-03-05)


### Bug Fixes

* repair broken import ([6822729](https://github.com/UVerify-io/uverify-backend/commit/68227298aeea993f9a554355f531ba230f1e6d3e))

## [1.11.2](https://github.com/UVerify-io/uverify-backend/compare/v1.11.1...v1.11.2) (2026-03-05)


### Bug Fixes

* repair broken import ([2b49004](https://github.com/UVerify-io/uverify-backend/commit/2b49004037213a2c0c57b02b543a9b6484f09c6a))

## [1.11.1](https://github.com/UVerify-io/uverify-backend/compare/v1.11.0...v1.11.1) (2026-03-05)


### Bug Fixes

* use latest utxo set in transaction building ([8419310](https://github.com/UVerify-io/uverify-backend/commit/8419310e9dd9d0d390ee7e212dc4c03aa4467b81))

## [1.11.0](https://github.com/UVerify-io/uverify-backend/compare/v1.10.1...v1.11.0) (2026-03-05)


### Features

* add transaction verification endpoint ([5cccada](https://github.com/UVerify-io/uverify-backend/commit/5cccadaae752aad7a9cb5c475d2cf413eb346d15))

## [1.10.1](https://github.com/UVerify-io/uverify-backend/compare/v1.10.0...v1.10.1) (2026-03-05)


### Bug Fixes

* split the faucet utxos correctly ([633f717](https://github.com/UVerify-io/uverify-backend/commit/633f7173c4fa118545744392b4814afc8dbca67b))

## [1.10.0](https://github.com/UVerify-io/uverify-backend/compare/v1.9.0...v1.10.0) (2026-03-04)


### Features

* introduce a testnet faucet for UVerify to make the onboarding more frictionless ([dcaac1c](https://github.com/UVerify-io/uverify-backend/commit/dcaac1c2a1bde91617f09912332e8e6863570892))

## [1.9.0](https://github.com/UVerify-io/uverify-backend/compare/v1.8.0...v1.9.0) (2026-03-01)


### Features

* make network depended constants optional environment variables ([18eb555](https://github.com/UVerify-io/uverify-backend/commit/18eb5553bdb21139729f2875621d254bdbb04668))

## [1.8.0](https://github.com/UVerify-io/uverify-backend/compare/v1.7.0...v1.8.0) (2026-02-18)


### Features

* introduce a library service and fetching deployments automatically from the chain. Closes [#25](https://github.com/UVerify-io/uverify-backend/issues/25) ([dd300a4](https://github.com/UVerify-io/uverify-backend/commit/dd300a4720beddd883616a4158ce08d0f8363a78))

## [1.7.0](https://github.com/UVerify-io/uverify-backend/compare/v1.6.0...v1.7.0) (2026-02-11)


### Features

* add new contracts and minting logic ([345a217](https://github.com/UVerify-io/uverify-backend/commit/345a217993e4f639af959f4dff239f24064b55b1))
* rewrite transaction controller to use UVerify v2 contracts ([c10030c](https://github.com/UVerify-io/uverify-backend/commit/c10030cefadafa68e9fb87f8b58c710187039829))


### Bug Fixes

* apply fee in the correct state update ([3615851](https://github.com/UVerify-io/uverify-backend/commit/3615851e92eb571dd9deb67fababa699f86f6467))

## [1.6.0](https://github.com/UVerify-io/uverify-backend/compare/v1.5.1...v1.6.0) (2025-08-13)


### Features

* Just store the important utxos and transactions. This decreases the space needed from >13GB to ~5MB.

* add statistics endpoint to collect UVerify total fees and certificate use-cases ([1a6234a](https://github.com/UVerify-io/uverify-backend/commit/1a6234ae320545c5d6ebb0d3583fd70861d57160))

## [1.5.1](https://github.com/UVerify-io/uverify-backend/compare/v1.5.0...v1.5.1) (2025-06-03)


### Bug Fixes

* write N/A to spreadsheet if beneficiary signing date is empty ([f944217](https://github.com/UVerify-io/uverify-backend/commit/f944217410838fdc7e9809d670213df24949ef83))

## [1.5.0](https://github.com/UVerify-io/uverify-backend/compare/v1.4.6...v1.5.0) (2025-05-30)


### Features

* make file logging optional ([dd6d442](https://github.com/UVerify-io/uverify-backend/commit/dd6d442bbcbc3ee7d3d894892834ae7ce16d0aa9))

## [1.4.6](https://github.com/UVerify-io/uverify-backend/compare/v1.4.5...v1.4.6) (2025-05-30)


### Bug Fixes

* exclude BeneficiarySigningDate from google sheet ([66459dd](https://github.com/UVerify-io/uverify-backend/commit/66459dd30a9c90defa995a4e1cb527cf8e06e961))
* exclude BeneficiarySigningDate from google sheet ([00e5227](https://github.com/UVerify-io/uverify-backend/commit/00e52275599008b87d7330ab502796ffc55dfb79))
* formatDate process nullable ([fd70b90](https://github.com/UVerify-io/uverify-backend/commit/fd70b90a5867451afaa3768b42743e738ebb1fef))
* set BeneficiarySigningDate as nullable ([b756576](https://github.com/UVerify-io/uverify-backend/commit/b756576c4dd985f70779ee6a071083d7f2d5c63e))
* sql table to have nullable ([49d24f5](https://github.com/UVerify-io/uverify-backend/commit/49d24f52b777e4031db5d907354b996666757e76))

## [1.4.5](https://github.com/UVerify-io/uverify-backend/compare/v1.4.4...v1.4.5) (2025-05-29)


### Bug Fixes

* add log level configuration and log error in tadamon controller ([7054eab](https://github.com/UVerify-io/uverify-backend/commit/7054eabff771f6bdaabbe01b97e57d15c9994333))

## [1.4.4](https://github.com/UVerify-io/uverify-backend/compare/v1.4.3...v1.4.4) (2025-05-26)


### Bug Fixes

* **tadamon:** make beneficiary signing date optional ([820158a](https://github.com/UVerify-io/uverify-backend/commit/820158ac9ae7e02891879e8fbfad33a3caf43503))

## [1.4.3](https://github.com/UVerify-io/uverify-backend/compare/v1.4.2...v1.4.3) (2025-05-24)


### Bug Fixes

* store transaction id from value instead of a message coming from the response ([ae2280b](https://github.com/UVerify-io/uverify-backend/commit/ae2280b0b8f317899ea62d500c3f23a40bea0189))

## [1.4.2](https://github.com/UVerify-io/uverify-backend/compare/v1.4.1...v1.4.2) (2025-05-22)


### Bug Fixes

* **tadamon:** apply blake2b hash function before comparing vkey witness ([880682c](https://github.com/UVerify-io/uverify-backend/commit/880682c82bab2f04af2cea92ececd59a136a6642))

## [1.4.1](https://github.com/UVerify-io/uverify-backend/compare/v1.4.0...v1.4.1) (2025-05-17)


### Bug Fixes

* repair release and build pipeline ([9ac9036](https://github.com/UVerify-io/uverify-backend/commit/9ac9036c3bf189145effd6fbeb1d4e3e81265723))

## [1.4.0](https://github.com/UVerify-io/uverify-backend/compare/v1.3.0...v1.4.0) (2025-05-17)


### Features

* **transaction:** make witness set optional if vkey witness is included in the transaction ([3c80e73](https://github.com/UVerify-io/uverify-backend/commit/3c80e73241f0ed74a51fd9a5e24e2d86fc03969d))

## [1.3.0](https://github.com/UVerify-io/uverify-backend/compare/v1.2.0...v1.3.0) (2025-05-10)


### Features

* create schema for storing transaction data for resubmission ([1105f0c](https://github.com/UVerify-io/uverify-backend/commit/1105f0c16c495994379bbfd55f249e37995fd5c9))
* implement google sheets service for writing certificate changes to a google spreadsheet ([f7c5055](https://github.com/UVerify-io/uverify-backend/commit/f7c5055d5f3a9ebcd219ae6820f7d3a0c762bb19))
* update google sheet on rollback ([ea765b9](https://github.com/UVerify-io/uverify-backend/commit/ea765b91b8cae3865449dffab6a28069b1ebc021))


### Bug Fixes

* add new variables to generated env file ([88c4178](https://github.com/UVerify-io/uverify-backend/commit/88c41787399788dfc889ef820e10a5c232b3be33))
* update test pipeline and exclude tadamon extension ([ceff8c9](https://github.com/UVerify-io/uverify-backend/commit/ceff8c9e142d9908a50346e3040e32c51c08fe0a))

## [1.2.0](https://github.com/UVerify-io/uverify-backend/compare/v1.1.0...v1.2.0) (2025-05-04)


### Features

* add config for postgres database ([13450a3](https://github.com/UVerify-io/uverify-backend/commit/13450a3d88de13d620941339e2f11d6aeb778393))
* make facilitator mnemonic optional ([c2d17ee](https://github.com/UVerify-io/uverify-backend/commit/c2d17eecb28fd0c84371f26b4bcf03656e1a5a85))

## [1.1.0](https://github.com/UVerify-io/uverify-backend/compare/v1.0.0...v1.1.0) (2025-05-03)


### Features

* release first public version of UVerify backend under AGPL license ([49ac60a](https://github.com/UVerify-io/uverify-backend/commit/49ac60a46e46273d7802aa5557398c307d6564ff))


### Bug Fixes

* DockerHub should now receive a correct image ([cf02e8f](https://github.com/UVerify-io/uverify-backend/commit/cf02e8fe1c727535f78743f1db858d399a8486e7))

# Changelog

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

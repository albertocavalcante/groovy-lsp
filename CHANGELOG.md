# Changelog

## [0.2.0](https://github.com/albertocavalcante/groovy-lsp/compare/v0.1.0...v0.2.0) (2025-10-20)


### ⚠ BREAKING CHANGES

* Real Groovy compilation with AST-based language features ([#3](https://github.com/albertocavalcante/groovy-lsp/issues/3))

### Features

* add build configuration for CodeNarc integration ([#40](https://github.com/albertocavalcante/groovy-lsp/issues/40)) ([7d954ca](https://github.com/albertocavalcante/groovy-lsp/commit/7d954caf1e2f593e9cd9fe8e97fc638debdf5dc0))
* add core CodeNarc integration infrastructure ([#44](https://github.com/albertocavalcante/groovy-lsp/issues/44)) ([0d9f1a8](https://github.com/albertocavalcante/groovy-lsp/commit/0d9f1a83f0db30cb3c9d99364da061a285354017))
* add e2e tests / fix workspace compilation and duplicate workspace diagnostics ([#60](https://github.com/albertocavalcante/groovy-lsp/issues/60)) ([94ff474](https://github.com/albertocavalcante/groovy-lsp/commit/94ff4744c01e70350c15c06cd0d61b49bbcb2f8c))
* add gradle-pre-commit-git-hooks with auto-fix capabilities ([#30](https://github.com/albertocavalcante/groovy-lsp/issues/30)) ([a0b40ff](https://github.com/albertocavalcante/groovy-lsp/commit/a0b40ff7fd3fe5b8ee409f51ead01cefb939cb19))
* add lintFix task for auto-correcting code quality issues ([#27](https://github.com/albertocavalcante/groovy-lsp/issues/27)) ([a3f51f8](https://github.com/albertocavalcante/groovy-lsp/commit/a3f51f8e17d7894a144e3ef8734cdf14755a4a01))
* add Makefile with development shortcuts ([#33](https://github.com/albertocavalcante/groovy-lsp/issues/33)) ([0db7744](https://github.com/albertocavalcante/groovy-lsp/commit/0db77442873d7cbc58ed18c88d4319d4e50c5c4b))
* add OpenRewrite formatter and telemetry instrumentation ([#56](https://github.com/albertocavalcante/groovy-lsp/issues/56)) ([a3a11a4](https://github.com/albertocavalcante/groovy-lsp/commit/a3a11a4eb05319a29ea7d5fd855756cc0c6b72b4))
* add type definition support for Groovy LSP ([#28](https://github.com/albertocavalcante/groovy-lsp/issues/28)) ([cefe0c8](https://github.com/albertocavalcante/groovy-lsp/commit/cefe0c8f864e5beda1a6f805fb516b5e3f4c2a42))
* advertise formatting and strengthen lsp coverage ([#59](https://github.com/albertocavalcante/groovy-lsp/issues/59)) ([a5b0f37](https://github.com/albertocavalcante/groovy-lsp/commit/a5b0f37327376c05857394b41170fdee3da6078a))
* **ci:** enable build scans and improve yaml formatting ([#55](https://github.com/albertocavalcante/groovy-lsp/issues/55)) ([d35b7e1](https://github.com/albertocavalcante/groovy-lsp/commit/d35b7e1ae6910e7c66e00d50381a07d1b34beed8))
* extract ast/resolution package from original PR [#35](https://github.com/albertocavalcante/groovy-lsp/issues/35) ([#48](https://github.com/albertocavalcante/groovy-lsp/issues/48)) ([926d22b](https://github.com/albertocavalcante/groovy-lsp/commit/926d22b26fbb9afbd1f77fc6f26fcc45260aa750))
* implement centralized coordinate system for LSP/Groovy conversion ([#43](https://github.com/albertocavalcante/groovy-lsp/issues/43)) ([0d2164f](https://github.com/albertocavalcante/groovy-lsp/commit/0d2164f8e483cb1c17a7ee4e3f249b2821700d71))
* Improve build and test process ([#53](https://github.com/albertocavalcante/groovy-lsp/issues/53)) ([21b7d86](https://github.com/albertocavalcante/groovy-lsp/commit/21b7d865cc8348eb7f0c86e0eaec836266cdb733))
* initial Groovy LSP implementation ([2cb1190](https://github.com/albertocavalcante/groovy-lsp/commit/2cb1190bd2345becf27add226241b34b18eeda34))
* integrate SonarCloud coverage reporting in CI workflow ([#29](https://github.com/albertocavalcante/groovy-lsp/issues/29)) ([7e42933](https://github.com/albertocavalcante/groovy-lsp/commit/7e429330c72675541e2fde6aeb4c82494d8d3a99))
* non-blocking Gradle dependency resolution ([#19](https://github.com/albertocavalcante/groovy-lsp/issues/19)) ([0107e1d](https://github.com/albertocavalcante/groovy-lsp/commit/0107e1d93e85adb04bd13584acd01132516dfbac))
* provider architecture with comprehensive AST utilities and LSP features ([#5](https://github.com/albertocavalcante/groovy-lsp/issues/5)) ([916c6e1](https://github.com/albertocavalcante/groovy-lsp/commit/916c6e1ef7f994478643d97260dc7b943cc6d1d3))
* Real Groovy compilation with AST-based language features ([#3](https://github.com/albertocavalcante/groovy-lsp/issues/3)) ([5d03312](https://github.com/albertocavalcante/groovy-lsp/commit/5d033129dea9fd2950906ccbb33f89868fcbaff0))


### Bug Fixes

* **deps:** update dependency ch.qos.logback:logback-classic to v1.5.18 ([#7](https://github.com/albertocavalcante/groovy-lsp/issues/7)) ([cd9201a](https://github.com/albertocavalcante/groovy-lsp/commit/cd9201ae427b8ea844b50169cf4fc29ac9487669))
* **deps:** update dependency io.mockk:mockk to v1.14.6 ([#41](https://github.com/albertocavalcante/groovy-lsp/issues/41)) ([1e8266a](https://github.com/albertocavalcante/groovy-lsp/commit/1e8266afbb90b88b30e31dd582c671bb8ea4f6ab))
* **deps:** update dependency org.codenarc:codenarc to v3.6.0 ([#45](https://github.com/albertocavalcante/groovy-lsp/issues/45)) ([0879ecf](https://github.com/albertocavalcante/groovy-lsp/commit/0879ecfbcabc86f1ebc44fb9095b3bfaa0257650))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.13.4 ([#16](https://github.com/albertocavalcante/groovy-lsp/issues/16)) ([d06e4af](https://github.com/albertocavalcante/groovy-lsp/commit/d06e4af48b1b405998a71060c9828d8d9f17e73c))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.14.0 ([#36](https://github.com/albertocavalcante/groovy-lsp/issues/36)) ([8a1b8ad](https://github.com/albertocavalcante/groovy-lsp/commit/8a1b8addd312ab7a1a9f58ba67c16a1e5a7a0138))
* **deps:** update dependency org.slf4j:slf4j-api to v2.0.17 ([#12](https://github.com/albertocavalcante/groovy-lsp/issues/12)) ([2dff2cf](https://github.com/albertocavalcante/groovy-lsp/commit/2dff2cf14a1eb1364e2f8ebcbd9738c2b53e9a1f))
* implement CodeNarc diagnostic triplication fix ([#46](https://github.com/albertocavalcante/groovy-lsp/issues/46)) ([9db68e6](https://github.com/albertocavalcante/groovy-lsp/commit/9db68e611a80f19f75fe41b3743bbc9e10723537))
* resolve critical detekt issues and failing hover test ([#14](https://github.com/albertocavalcante/groovy-lsp/issues/14)) ([998343c](https://github.com/albertocavalcante/groovy-lsp/commit/998343cd08bc22c227ea7dc8301470fce938fa98))
* resolve detekt code quality warnings ([#26](https://github.com/albertocavalcante/groovy-lsp/issues/26)) ([d91d452](https://github.com/albertocavalcante/groovy-lsp/commit/d91d45281f46796bc6b9a27fee2e5eef373ecb6a))

#!/bin/sh
# Bootstrap check: the one command that works on a machine with nothing on it.
#
# `bb doctor` is the real toolchain check, but it cannot be the first step:
# it needs babashka, which is one of the things a fresh machine is missing.
# This script closes that gap and nothing else.
#
# Deliberately POSIX sh with no dependencies. A Makefile would not do: on
# macOS /usr/bin/make is a Command Line Tools shim that fails until Xcode CLT
# is installed, so `make` is *less* available than /bin/sh, not more.
#
# It owns exactly one piece of knowledge - how to install babashka - and then
# hands off to `bb doctor` for everything else, so there is no second list of
# prerequisites to drift out of sync with bb.edn.
#
#   exit 0  toolchain complete (bb doctor said so)
#   exit 1  something is missing; the output says what and how to fix it

set -e

if ! command -v bb >/dev/null 2>&1; then
  cat >&2 <<'MSG'
babashka (bb) is not installed, and every task in this repo runs through it.

Install it, then re-run this script:

  brew install borkdude/brew/babashka

  # If brew reports "tap formula is not trusted":
  #   brew trust borkdude/brew

  # Without Homebrew, the official installer:
  #   curl -sSL https://raw.githubusercontent.com/babashka/babashka/master/install | bash

Docs: https://github.com/babashka/babashka#installation
MSG
  exit 1
fi

# babashka is here, so the authoritative check can take over. It reports every
# other tool (JDK 21+, node, yarn, clj-kondo, node_modules) and how to fix it.
exec bb doctor

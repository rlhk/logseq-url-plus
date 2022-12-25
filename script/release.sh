#!/bin/sh
# NOTE: Obsolete script. See `release` task in `bb.edn`
# brew install borkdude/brew/jet # uncomment to install the required jet tool
# `package.json` is the single source of truth of version no.
VERSION=`cat package.json | jet -i json -k -q ':version println'` 
git tag -a $VERSION -m "Release version: $VERSION"
git push origin $VERSION
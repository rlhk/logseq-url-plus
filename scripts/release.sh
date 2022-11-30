#!/bin/sh
# brew install borkdude/brew/jet # uncomment to install the required jet tool
VERSION=`cat package.json | jet -i json --keywordize -q ':version' -f '#(println %)'`
git tag -a $VERSION -m "Release version: $VERSION"
git push origin $VERSION
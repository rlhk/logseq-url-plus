#!/bin/sh

git tag -a $1 -m $1
git push origin $1

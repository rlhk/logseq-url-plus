#!/bin/sh

rlwrap bb -cp `clj -Spath -Sdeps '{:deps {djblue/portal {:mvn/version "0.51.0"}}}'`

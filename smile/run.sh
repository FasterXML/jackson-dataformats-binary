#!/bin/sh

java -Xmx256m -server -cp lib/\*:target/\*:target/test-classes $*


#!/bin/sh

java -Xmx64m -server \
  -cp target/classes:target/test-classes:lib/\* \
$*

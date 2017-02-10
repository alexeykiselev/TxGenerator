#!/bin/sh

java -jar target/scala-2.11/tx-gen-main.jar check -f $1 -h nodes.wavesnodes.com -p 443 -s -l 100 -o $1.report.csv
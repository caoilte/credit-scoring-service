#!/bin/sh

sbt "run $HTTP_PORT $CSCARDS_ENDPOINT $SCOREDCARDS_ENDPOINT"

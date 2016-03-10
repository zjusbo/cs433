#!/bin/bash

SERVER=python.zoo.cs.yale.edu
SERVERNAME=python.zoo.cs.yale.edu 
PORT=6789
FILENAME=../requests
TIME=60

for PARALLEL in 1 2 3 4 5 10 15 20 30 40 50 60 70 
#for PARALLEL in 60 
do
  COMMAND="java client.SHTTPTestClient -server $SERVER -servname $SERVERNAME -port $PORT -parallel $PARALLEL -files $FILENAME -T $TIME"
  echo $COMMAND
  echo $PARALLEL >> result_channel
  $COMMAND >> result_channel
done

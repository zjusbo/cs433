#!/bin/bash

SERVER=hippo.zoo.cs.yale.edu
PORT=6789
FILENAME=requests_big.txt
TIME=60

for PARALLEL in 1 2 3 4 5 10 15 20 30 40 50 60 70 
do
  COMMAND="java SHTTPTestClient -server $SERVER -servname $SERVER -port $PORT -parallel $PARALLEL -files $FILENAME -T $TIME"
  echo $COMMAND
  echo $PARALLEL >> result_me.big
  $COMMAND >> result_me.big
done

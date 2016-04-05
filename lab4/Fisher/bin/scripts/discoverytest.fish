// perl fishnet.pl simulate 5 scripts/discoverytest.fish
edge 0 1
edge 1 2
edge 2 3
edge 3 4
edge 0 4
time + 5000
1 4 Ping before failing node 0
time + 20000
echo ------- Failing node 0 ---------
fail 0
time + 50000
1 4 Ping after failing node 0
time + 20000
echo ------- Restarting node 0 ---------
restart 0
time + 50000
1 4 Ping after restarting node 0
time + 25000
exit

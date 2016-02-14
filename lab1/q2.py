import math
n = 46
l = 16722.0/24/60
u = 1.0/3
sum = 0.0
for x in xrange(0,n+1):
	sum += (l / u) ** x / math.factorial(x)
res = (l / u) ** n / math.factorial(n) / sum
print res

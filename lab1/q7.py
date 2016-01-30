import operator as op
def ncr(n, r):
    r = min(r, n-r)
    if r == 0: return 1
    numer = reduce(op.mul, xrange(n, n-r, -1))
    denom = reduce(op.mul, xrange(1, r+1))
    return numer//denom

sum = 0.0
for x in xrange(0,21):
	sum += ncr(120, x) * (0.1 ** x) * 0.9 ** (120 - x)
sum = 1 - sum
print sum
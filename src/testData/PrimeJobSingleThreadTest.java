package testData;

import java.util.ArrayList;
import java.util.List;


public class PrimeJobSingleThreadTest{
	public static void main(String[] args)
	{
		long start = System.currentTimeMillis();
		System.out.println(new PrimeJobSingleThreadTest(1, 100000000).work().size());
		long end = System.currentTimeMillis();
		System.out.println("Done in " + (end-start) + " ms.");
	}
	int start;
	int end; 
	public PrimeJobSingleThreadTest(int start, int end)
	{
		this.start = start;
		this.end = end;
	}
	public List<Integer> work() {
		List<Integer> res;
		if (end - start < 100)
		{
			res = new ArrayList<Integer>();
			for (int i = start; i <= end; i++)
			{
				if (isPrime(i))
				{
					res.add(i);
				}
			}
			return res;
		}
		else
		{
			int diff = end - start;
			int halfDiff = diff / 2;
			res = new PrimeJobSingleThreadTest(start, end - halfDiff).work();
			res.addAll(new PrimeJobSingleThreadTest(end - halfDiff + 1, end).work());
			return res;
		}
	}
	private boolean isPrime(int n) {
	    //check if n is a multiple of 2
		if (n == 1) return false;
		if (n == 2) return true;
	    if (n%2==0) return false;
	    //if not, then just check the odds
	    for(int i = 3; i * i <= n; i += 2) {
	        if(n%i==0)
	        {
	            return false;
	        }
	    }
	    return true;
	}
}

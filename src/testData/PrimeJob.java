package testData;

import java.util.List;

import model.Job;

public class PrimeJob extends Job<Integer>{
	/**
	 * Remember to increment when developing. 
	 */
	private static final long serialVersionUID = 1L;
	
	int start;
	int end; 
	public PrimeJob(int start, int end)
	{
		this.start = start;
		this.end = end;
	}
	@Override
	public Integer work() {
		if (end - start < 1000)
		{
			int res = 0;
			for (int i = start; i <= end; i++)
			{
				if (isPrime(i))
				{
					res++;
				}
			}
			return res;
		}
		else
		{
			int diff = end - start;
			int halfDiff = diff / 2;
			invoke(new PrimeJob(start, end - halfDiff));
			invoke(new PrimeJob(end - halfDiff + 1, end));
		}
		return null;
	}
	@Override
	public Integer join(List<Integer> list) {
		Integer res = 0;
		for (Integer i : list)
		{
			res += i;
		}
		return res;
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

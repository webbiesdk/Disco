import java.util.concurrent.ExecutionException;

import model.Job;
import testData.PrimeJob;
public class Test {
	public static <E> void main(String[] args) throws InterruptedException, ExecutionException
	{
		
		System.out.println("Start");
		long start = System.currentTimeMillis();
		Job<Integer> job = new PrimeJob(1, 30000000);
		
		DisCo<Integer> dis = new DisCo<Integer>(1, true, false);
		System.out.println(dis.execute(job).get());
		
		long end = System.currentTimeMillis();
		System.out.println("Done in " + (end-start) + " ms.");
		dis.close();
		/* Thread.currentThread().sleep(3000);
		System.out.println(env);
		System.out.println(cluster);
		cluster.close(); */
	}
}

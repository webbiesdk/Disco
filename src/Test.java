import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import model.Job;
import testData.PrimeJob;

public class Test {
    public static <E> void main(String[] args) throws InterruptedException, ExecutionException, TimeoutException {
        System.out.println("Start");
        long start = System.currentTimeMillis();
        Job<Integer> job = new PrimeJob(1, 30000000);

        DisCo<Integer> cluster = new DisCo<Integer>(1, true, false);
        System.out.println(cluster.execute(job).get());

        long end = System.currentTimeMillis();
        System.out.println("Done in " + (end - start) + " ms.");
        cluster.close();
    }
}

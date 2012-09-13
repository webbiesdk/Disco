public class Receiver {
	public static void main(String[] args)
	{

		System.out.println("Recieving");
		@SuppressWarnings({ "rawtypes", "unused" })
		DisCo dis = new DisCo(true, true);
		
		/* new Thread(new Runnable(){
			@Override
			public void run() {
				BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
				while (true)
				{
					try {
						reader.readLine();
					} catch (IOException e) {}
					System.out.println(env);
					System.out.println(cluster);
				}
				
			}
		}).start(); */
	}
}

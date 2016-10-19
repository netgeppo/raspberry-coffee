package readserialport;

import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialFactory;
import util.DumpUtil;

import java.io.IOException;

/**
 * Just reads the GPS data.
 * No parsing, just raw data.
 * <p>
 * Uses the Serial communication packages from PI4J
 */
public class GPSDataReader {
	public static void main(String args[])
					throws InterruptedException, NumberFormatException {
		int br = Integer.parseInt(System.getProperty("baud.rate", "9600"));
		String port = System.getProperty("port.name", Serial.DEFAULT_COM_PORT);
		boolean verbose = "true".equals(System.getProperty("verbose", "false"));

		System.out.println("Serial Communication.");
		System.out.println(" ... connect using settings: " + Integer.toString(br) + ", N, 8, 1.");
		System.out.println(" ... data received on serial port should be displayed below.");

		// create an instance of the serial communications class
		final Serial serial = SerialFactory.createInstance();

		// create and register the serial data listener
		serial.addListener(event -> {
			try {
				// print out the data received to the console
				String data = event.getAsciiString();
				if (verbose)
				{
					System.out.println("Got Data (" + data.length() + " byte(s))");
					System.out.println(data);
				}
				String[] sa = DumpUtil.dualDump(data);
				for (String str : sa)
					System.out.println(str);
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		});

		final Thread t = Thread.currentThread();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("\nShutting down...");
				try {
					if (serial.isOpen()) {
						serial.close();
						System.out.println("Serial port closed");
					}
					synchronized (t) {
						t.notify();
						System.out.println("Thread notified");
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});
		try {
			System.out.println("Opening port [" + port + "]");
			boolean open = false;
			while (!open) {
				serial.open(port, br);
				open = serial.isOpen();
				System.out.println("Port is " + (open ? "" : "NOT ") + "opened.");
				if (!open)
					try {
						Thread.sleep(500L);
					} catch (Exception ex) {
					}
			}
			synchronized (t) {
				t.wait();
			}
			System.out.println("Bye...");
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} catch (InterruptedException ie) {
			ie.printStackTrace();
		}
	}
}


package i2c.samples.motorHAT;

import com.pi4j.io.i2c.I2CFactory;
import i2c.servo.adafruitmotorhat.AdafruitMotorHAT;

import java.io.IOException;

/*
 * See https://learn.adafruit.com/adafruit-dc-and-stepper-motor-hat-for-raspberry-pi/using-stepper-motors
 */
public class StepperDemo {
	private AdafruitMotorHAT mh;
	private AdafruitMotorHAT.AdafruitStepperMotor stepper;

	private boolean keepGoing = true;
	private final static String DEFAULT_RPM = "30";

	private static int nbStepsPerRev = AdafruitMotorHAT.AdafruitStepperMotor.DEFAULT_NB_STEPS; // 200 steps per rev

	public StepperDemo() throws I2CFactory.UnsupportedBusNumberException {

		System.out.println("Starting Stepper Demo");
		int rpm = Integer.parseInt(System.getProperty("rpm", DEFAULT_RPM));
		System.out.println(String.format("RPM set to %d.", rpm));

		this.mh = new AdafruitMotorHAT(nbStepsPerRev); // Default addr 0x60
		this.stepper = mh.getStepper(AdafruitMotorHAT.AdafruitStepperMotor.PORT_M1_M2);
		this.stepper.setSpeed(rpm); // Default 30 RPM
	}

	public void go() {
		keepGoing = true;
		while (keepGoing) {
			try {
				System.out.println("-- Single coil steps --");
				System.out.println("  Forward");
				this.stepper.step(100, AdafruitMotorHAT.ServoCommand.FORWARD, AdafruitMotorHAT.Style.SINGLE);
				System.out.println("  Backward");
				this.stepper.step(100, AdafruitMotorHAT.ServoCommand.BACKWARD, AdafruitMotorHAT.Style.SINGLE);
				System.out.println("-- Double coil steps --");
				System.out.println("  Forward");
				this.stepper.step(100, AdafruitMotorHAT.ServoCommand.FORWARD, AdafruitMotorHAT.Style.DOUBLE);
				System.out.println("  Backward");
				this.stepper.step(100, AdafruitMotorHAT.ServoCommand.BACKWARD, AdafruitMotorHAT.Style.DOUBLE);
				System.out.println("-- Interleaved coil steps --");
				System.out.println("  Forward");
				this.stepper.step(100, AdafruitMotorHAT.ServoCommand.FORWARD, AdafruitMotorHAT.Style.INTERLEAVE);
				System.out.println("  Backward");
				this.stepper.step(100, AdafruitMotorHAT.ServoCommand.BACKWARD, AdafruitMotorHAT.Style.INTERLEAVE);
				System.out.println("-- Microsteps --");
				System.out.println("  Forward");
				this.stepper.step(100, AdafruitMotorHAT.ServoCommand.FORWARD, AdafruitMotorHAT.Style.MICROSTEP);
				System.out.println("  Backward");
				this.stepper.step(100, AdafruitMotorHAT.ServoCommand.BACKWARD, AdafruitMotorHAT.Style.MICROSTEP);
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
			System.out.println("==== Again! ====");
		}
		try { Thread.sleep(1_000); } catch (Exception ex) {} // Wait for the motors to be released.
		System.out.println("... Done with the demo ...");
	}

	public void stop() {
		this.keepGoing = false;
		if (mh != null) {
			try { // Release all
				mh.getMotor(AdafruitMotorHAT.Motor.M1).run(AdafruitMotorHAT.ServoCommand.RELEASE);
				mh.getMotor(AdafruitMotorHAT.Motor.M2).run(AdafruitMotorHAT.ServoCommand.RELEASE);
				mh.getMotor(AdafruitMotorHAT.Motor.M3).run(AdafruitMotorHAT.ServoCommand.RELEASE);
				mh.getMotor(AdafruitMotorHAT.Motor.M4).run(AdafruitMotorHAT.ServoCommand.RELEASE);
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
	}

	/**
	 * System properties:
	 * rpm, default 30
	 * hat.debug, default false
	 *
	 * @param args Not used
	 * @throws Exception
	 */
	public static void main(String... args) throws Exception {
		StepperDemo demo = new StepperDemo();
		System.out.println("Hit Ctrl-C to stop the demo");
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			demo.stop();
			try { Thread.sleep(1_000); } catch (Exception absorbed) {}
		}));

		demo.go();

		System.out.println("Bye.");
	}
}

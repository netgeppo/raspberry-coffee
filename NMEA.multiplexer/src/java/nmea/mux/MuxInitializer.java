package nmea.mux;

import context.ApplicationContext;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import nmea.api.Multiplexer;
import nmea.api.NMEAClient;
import nmea.api.NMEAReader;
import nmea.computers.Computer;
import nmea.computers.ExtraDataComputer;
import nmea.consumers.client.BME280Client;
import nmea.consumers.client.BMP180Client;
import nmea.consumers.client.DataFileClient;
import nmea.consumers.client.HTU21DFClient;
import nmea.consumers.client.LSM303Client;
import nmea.consumers.client.RandomClient;
import nmea.consumers.client.SerialClient;
import nmea.consumers.client.TCPClient;
import nmea.consumers.client.WebSocketClient;
import nmea.consumers.client.ZDAClient;
import nmea.consumers.reader.BME280Reader;
import nmea.consumers.reader.BMP180Reader;
import nmea.consumers.reader.DataFileReader;
import nmea.consumers.reader.HTU21DFReader;
import nmea.consumers.reader.LSM303Reader;
import nmea.consumers.reader.RandomReader;
import nmea.consumers.reader.SerialReader;
import nmea.consumers.reader.TCPReader;
import nmea.consumers.reader.WebSocketReader;
import nmea.consumers.reader.ZDAReader;
import nmea.forwarders.ConsoleWriter;
import nmea.forwarders.DataFileWriter;
import nmea.forwarders.Forwarder;
import nmea.forwarders.GPSdServer;
import nmea.forwarders.SerialWriter;
import nmea.forwarders.TCPServer;
import nmea.forwarders.WebSocketProcessor;
import nmea.forwarders.WebSocketWriter;
import nmea.forwarders.rmi.RMIServer;

/**
 * Initialize the configuration of the Multiplexer, at startup,
 * with the properties read from the file system., through one
 * package-private method named <code>{@link #setup}</code>.
 * <br>
 * Initializes:
 * <ul>
 *   <li>NMEA Channels</li>
 *   <li>NMEA Forwarders</li>
 *   <li>NMEA Computers</li>
 * </ul>
 * All those objects can be also managed later on, through the REST Admin Interface
 * (see {@link RESTImplementation}).
 */
public class MuxInitializer {

	private final static NumberFormat MUX_IDX_FMT = new DecimalFormat("00");

	/**
	 * This is the method to call to initialize the {@link Multiplexer}.
	 * The 3 <code>List</code>s must have been created in it, as they will be populated here.
	 *
	 * @param muxProps The properties to get the data from. See <a href="../../../../README.md">here</a> for more details.
	 * @param nmeaDataClients List of the input channels
	 * @param nmeaDataForwarders List of the output channels
	 * @param nmeaDataComputers List of the data computers
	 * @param mux the Multiplexer instance to initialize
	 */
	static void setup(Properties muxProps,
	                  List<NMEAClient> nmeaDataClients,
	                  List<Forwarder> nmeaDataForwarders,
	                  List<Computer> nmeaDataComputers,
	                  Multiplexer mux) {
		int muxIdx = 1;
		boolean thereIsMore = true;
		// 1 - Input channels
		while (thereIsMore) {
			String classProp = String.format("mux.%s.cls", MUX_IDX_FMT.format(muxIdx));
			String clss = muxProps.getProperty(classProp);
			if (clss != null) { // Dynamic loading
				try {
					// Devices and Sentences filters.
					String deviceFilters = "";
					String sentenceFilters = "";
					deviceFilters = muxProps.getProperty(String.format("mux.%s.device.filters", MUX_IDX_FMT.format(muxIdx)), "");
					sentenceFilters = muxProps.getProperty(String.format("mux.%s.sentence.filters", MUX_IDX_FMT.format(muxIdx)), "");
					Object dynamic = Class.forName(clss)
									.getDeclaredConstructor(String[].class, String[].class, Multiplexer.class)
									.newInstance(
													deviceFilters.trim().length() > 0 ? deviceFilters.split(",") : null,
													sentenceFilters.trim().length() > 0 ? sentenceFilters.split(",") : null,
													mux);
					if (dynamic instanceof NMEAClient) {
						NMEAClient nmeaClient = (NMEAClient)dynamic;
						String propProp = String.format("mux.%s.properties", MUX_IDX_FMT.format(muxIdx));
						String propFileName = muxProps.getProperty(propProp);
						Properties readerProperties = null;
						if (propFileName != null) {
							try {
								readerProperties = new Properties();
								readerProperties.load(new FileReader(propFileName));
								nmeaClient.setProperties(readerProperties);
							} catch (Exception ex) {
								ex.printStackTrace();
							}
						}
						nmeaClient.initClient();
						NMEAReader reader = null;
						try {
							String readerProp = String.format("mux.%s.reader", MUX_IDX_FMT.format(muxIdx));
							String readerClass = muxProps.getProperty(readerProp);
							// Cannot invoke declared constructor with a generic type... :(
							if (readerProperties == null) {
								reader = (NMEAReader) Class.forName(readerClass).getDeclaredConstructor(List.class).newInstance(nmeaClient.getListeners());
							} else {
								reader = (NMEAReader) Class.forName(readerClass).getDeclaredConstructor(List.class, Properties.class).newInstance(nmeaClient.getListeners(), readerProperties);
							}
						} catch (Exception ex) {
							ex.printStackTrace();
						}
						if (reader != null) {
							nmeaClient.setReader(reader);
						}
						nmeaDataClients.add(nmeaClient);
					} else {
						throw new RuntimeException(String.format("Expected an NMEAClient, found a [%s]", dynamic.getClass().getName()));
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			} else {
				String typeProp = String.format("mux.%s.type", MUX_IDX_FMT.format(muxIdx));
				String type = muxProps.getProperty(typeProp);
				if (type == null) {
					thereIsMore = false;
				} else {
					String deviceFilters = "";
					String sentenceFilters = "";
					switch (type) {
						case "serial":
							try {
								String serialPort = muxProps.getProperty(String.format("mux.%s.port", MUX_IDX_FMT.format(muxIdx)));
								String br = muxProps.getProperty(String.format("mux.%s.baudrate", MUX_IDX_FMT.format(muxIdx)));
								deviceFilters = muxProps.getProperty(String.format("mux.%s.device.filters", MUX_IDX_FMT.format(muxIdx)), "");
								sentenceFilters = muxProps.getProperty(String.format("mux.%s.sentence.filters", MUX_IDX_FMT.format(muxIdx)), "");
								NMEAClient serialClient = new SerialClient(
												deviceFilters.trim().length() > 0 ? deviceFilters.split(",") : null,
												sentenceFilters.trim().length() > 0 ? sentenceFilters.split(",") : null,
												mux);
								serialClient.initClient();
								serialClient.setReader(new SerialReader(serialClient.getListeners(), serialPort, Integer.parseInt(br)));
								serialClient.setVerbose("true".equals(muxProps.getProperty(String.format("mux.%s.verbose", MUX_IDX_FMT.format(muxIdx)), "false")));
								nmeaDataClients.add(serialClient);
							} catch (Exception e) {
								e.printStackTrace();
							}
							break;
						case "tcp":
							try {
								String tcpPort = muxProps.getProperty(String.format("mux.%s.port", MUX_IDX_FMT.format(muxIdx)));
								String tcpServer = muxProps.getProperty(String.format("mux.%s.server", MUX_IDX_FMT.format(muxIdx)));
								deviceFilters = muxProps.getProperty(String.format("mux.%s.device.filters", MUX_IDX_FMT.format(muxIdx)), "");
								sentenceFilters = muxProps.getProperty(String.format("mux.%s.sentence.filters", MUX_IDX_FMT.format(muxIdx)), "");
								NMEAClient tcpClient = new TCPClient(
												deviceFilters.trim().length() > 0 ? deviceFilters.split(",") : null,
												sentenceFilters.trim().length() > 0 ? sentenceFilters.split(",") : null,
												mux);
								tcpClient.initClient();
								tcpClient.setReader(new TCPReader(tcpClient.getListeners(), tcpServer, Integer.parseInt(tcpPort)));
								tcpClient.setVerbose("true".equals(muxProps.getProperty(String.format("mux.%s.verbose", MUX_IDX_FMT.format(muxIdx)), "false")));
								nmeaDataClients.add(tcpClient);
							} catch (Exception e) {
								e.printStackTrace();
							}
							break;
						case "file":
							try {
								String filename = muxProps.getProperty(String.format("mux.%s.filename", MUX_IDX_FMT.format(muxIdx)));
								long betweenRec = 500;
								try {
									betweenRec = Long.parseLong(muxProps.getProperty(String.format("mux.%s.between-records", MUX_IDX_FMT.format(muxIdx)), "500"));
								} catch (NumberFormatException nfe) {
									betweenRec = 500; // Default value
								}
								deviceFilters = muxProps.getProperty(String.format("mux.%s.device.filters", MUX_IDX_FMT.format(muxIdx)), "");
								sentenceFilters = muxProps.getProperty(String.format("mux.%s.sentence.filters", MUX_IDX_FMT.format(muxIdx)), "");
								NMEAClient fileClient = new DataFileClient(
												deviceFilters.trim().length() > 0 ? deviceFilters.split(",") : null,
												sentenceFilters.trim().length() > 0 ? sentenceFilters.split(",") : null,
												mux);
								fileClient.initClient();
								fileClient.setReader(new DataFileReader(fileClient.getListeners(), filename, betweenRec));
								fileClient.setVerbose("true".equals(muxProps.getProperty(String.format("mux.%s.verbose", MUX_IDX_FMT.format(muxIdx)), "false")));
								nmeaDataClients.add(fileClient);
							} catch (Exception e) {
								e.printStackTrace();
							}
							break;
						case "ws":
							try {
								String wsUri = muxProps.getProperty(String.format("mux.%s.wsuri", MUX_IDX_FMT.format(muxIdx)));
								deviceFilters = muxProps.getProperty(String.format("mux.%s.device.filters", MUX_IDX_FMT.format(muxIdx)), "");
								sentenceFilters = muxProps.getProperty(String.format("mux.%s.sentence.filters", MUX_IDX_FMT.format(muxIdx)), "");
								NMEAClient wsClient = new WebSocketClient(
												deviceFilters.trim().length() > 0 ? deviceFilters.split(",") : null,
												sentenceFilters.trim().length() > 0 ? sentenceFilters.split(",") : null,
												mux);
								wsClient.initClient();
								wsClient.setReader(new WebSocketReader(wsClient.getListeners(), wsUri));
								wsClient.setVerbose("true".equals(muxProps.getProperty(String.format("mux.%s.verbose", MUX_IDX_FMT.format(muxIdx)), "false")));
								nmeaDataClients.add(wsClient);
							} catch (Exception e) {
								e.printStackTrace();
							}
							break;
						case "htu21df": // Humidity & Temperature sensor
							try {
								deviceFilters = muxProps.getProperty(String.format("mux.%s.device.filters", MUX_IDX_FMT.format(muxIdx)), "");
								sentenceFilters = muxProps.getProperty(String.format("mux.%s.sentence.filters", MUX_IDX_FMT.format(muxIdx)), "");
								String htu21dfDevicePrefix = muxProps.getProperty(String.format("mux.%s.device.prefix", MUX_IDX_FMT.format(muxIdx)), "");
								NMEAClient htu21dfClient = new HTU21DFClient(
												deviceFilters.trim().length() > 0 ? deviceFilters.split(",") : null,
												sentenceFilters.trim().length() > 0 ? sentenceFilters.split(",") : null,
												mux);
								htu21dfClient.initClient();
								htu21dfClient.setReader(new HTU21DFReader(htu21dfClient.getListeners()));
								htu21dfClient.setVerbose("true".equals(muxProps.getProperty(String.format("mux.%s.verbose", MUX_IDX_FMT.format(muxIdx)), "false")));
								// Important: after the setReader
								if (htu21dfDevicePrefix.trim().length() > 0) {
									if (htu21dfDevicePrefix.trim().length() == 2) {
										((HTU21DFClient) htu21dfClient).setSpecificDevicePrefix(htu21dfDevicePrefix.trim());
									} else {
										throw new RuntimeException(String.format("Bad prefix [%s] for HTU21DF. Must be 2 character long, exactly.", htu21dfDevicePrefix.trim()));
									}
								}
								nmeaDataClients.add(htu21dfClient);
							} catch (Exception e) {
								e.printStackTrace();
							} catch (Error err) {
								err.printStackTrace();
							}
							break;
						case "rnd": // Random generator, for debugging
							try {
								deviceFilters = muxProps.getProperty(String.format("mux.%s.device.filters", MUX_IDX_FMT.format(muxIdx)), "");
								sentenceFilters = muxProps.getProperty(String.format("mux.%s.sentence.filters", MUX_IDX_FMT.format(muxIdx)), "");
								NMEAClient rndClient = new RandomClient(
												deviceFilters.trim().length() > 0 ? deviceFilters.split(",") : null,
												sentenceFilters.trim().length() > 0 ? sentenceFilters.split(",") : null,
												mux);
								rndClient.initClient();
								rndClient.setReader(new RandomReader(rndClient.getListeners()));
								rndClient.setVerbose("true".equals(muxProps.getProperty(String.format("mux.%s.verbose", MUX_IDX_FMT.format(muxIdx)), "false")));
								nmeaDataClients.add(rndClient);
							} catch (Exception e) {
								e.printStackTrace();
							} catch (Error err) {
								err.printStackTrace();
							}
							break;
						case "zda": // ZDA generator
							try {
								deviceFilters = muxProps.getProperty(String.format("mux.%s.device.filters", MUX_IDX_FMT.format(muxIdx)), "");
								sentenceFilters = muxProps.getProperty(String.format("mux.%s.sentence.filters", MUX_IDX_FMT.format(muxIdx)), "");
								NMEAClient zdaClient = new ZDAClient(
												deviceFilters.trim().length() > 0 ? deviceFilters.split(",") : null,
												sentenceFilters.trim().length() > 0 ? sentenceFilters.split(",") : null,
												mux);
								zdaClient.initClient();
								zdaClient.setReader(new ZDAReader(zdaClient.getListeners()));
								zdaClient.setVerbose("true".equals(muxProps.getProperty(String.format("mux.%s.verbose", MUX_IDX_FMT.format(muxIdx)), "false")));
								nmeaDataClients.add(zdaClient);
							} catch (Exception e) {
								e.printStackTrace();
							} catch (Error err) {
								err.printStackTrace();
							}
							break;
						case "lsm303": // Pitch & Roll, plus Heading (possibly).
							try {
								deviceFilters = muxProps.getProperty(String.format("mux.%s.device.filters", MUX_IDX_FMT.format(muxIdx)), "");
								sentenceFilters = muxProps.getProperty(String.format("mux.%s.sentence.filters", MUX_IDX_FMT.format(muxIdx)), "");
								String lsm303DevicePrefix = muxProps.getProperty(String.format("mux.%s.device.prefix", MUX_IDX_FMT.format(muxIdx)), "");
								int headingOffset = 0;
								try {
									headingOffset = Integer.parseInt(muxProps.getProperty(String.format("mux.%s.heading.offset", MUX_IDX_FMT.format(muxIdx)), "0"));
									if (headingOffset > 180 || headingOffset < -180) {
										System.err.println(String.format("Warning: Bad range for Heading Offset, must be in [-180..180], found %d. Defaulting to 0", headingOffset));
										headingOffset = 0;
									}
								} catch (NumberFormatException nfe) {
									nfe.printStackTrace();
								}
								Long readFrequency = null;
								try {
									readFrequency = new Long(muxProps.getProperty(String.format("mux.%s.read.frequency", MUX_IDX_FMT.format(muxIdx))));
									if (readFrequency < 0) {
										System.err.println(String.format("Warning: Bad value for Read Frequency, must be positive, found %d.", readFrequency));
										readFrequency = null;
									}
								} catch (NumberFormatException nfe) {
									nfe.printStackTrace();
								}
								Integer dampingSize = null;
								try {
									dampingSize = new Integer(muxProps.getProperty(String.format("mux.%s.damping.size", MUX_IDX_FMT.format(muxIdx))));
									if (dampingSize < 0) {
										System.err.println(String.format("Warning: Bad value for Damping Size, must be positive, found %d.", dampingSize));
										dampingSize = null;
									}
								} catch (NumberFormatException nfe) {
									nfe.printStackTrace();
								}
								NMEAClient lsm303Client = new LSM303Client(
												deviceFilters.trim().length() > 0 ? deviceFilters.split(",") : null,
												sentenceFilters.trim().length() > 0 ? sentenceFilters.split(",") : null,
												mux);
								lsm303Client.initClient();
								lsm303Client.setReader(new LSM303Reader(lsm303Client.getListeners()));
								lsm303Client.setVerbose("true".equals(muxProps.getProperty(String.format("mux.%s.verbose", MUX_IDX_FMT.format(muxIdx)), "false")));
								// Important: after the setReader
								if (headingOffset != 0) {
									((LSM303Client) lsm303Client).setHeadingOffset(headingOffset);
								}
								if (readFrequency != null) {
									((LSM303Client) lsm303Client).setReadFrequency(readFrequency);
								}
								if (dampingSize != null) {
									((LSM303Client) lsm303Client).setDampingSize(dampingSize);
								}
								if (lsm303DevicePrefix.trim().length() > 0) {
									if (lsm303DevicePrefix.trim().length() == 2) {
										((LSM303Client) lsm303Client).setSpecificDevicePrefix(lsm303DevicePrefix.trim());
									} else {
										throw new RuntimeException(String.format("Bad prefix [%s] for LSM303. Must be 2 character long, exactly.", lsm303DevicePrefix.trim()));
									}
								}
								nmeaDataClients.add(lsm303Client);
							} catch (Exception e) {
								e.printStackTrace();
							} catch (Error err) {
								err.printStackTrace();
							}
							break;
						case "bme280": // Humidity, Temperature, Pressure
							try {
								deviceFilters = muxProps.getProperty(String.format("mux.%s.device.filters", MUX_IDX_FMT.format(muxIdx)), "");
								sentenceFilters = muxProps.getProperty(String.format("mux.%s.sentence.filters", MUX_IDX_FMT.format(muxIdx)), "");
								String bme280DevicePrefix = muxProps.getProperty(String.format("mux.%s.device.prefix", MUX_IDX_FMT.format(muxIdx)), "");
								NMEAClient bme280Client = new BME280Client(
												deviceFilters.trim().length() > 0 ? deviceFilters.split(",") : null,
												sentenceFilters.trim().length() > 0 ? sentenceFilters.split(",") : null,
												mux);
								bme280Client.initClient();
								bme280Client.setReader(new BME280Reader(bme280Client.getListeners()));
								bme280Client.setVerbose("true".equals(muxProps.getProperty(String.format("mux.%s.verbose", MUX_IDX_FMT.format(muxIdx)), "false")));
								// Important: after the setReader
								if (bme280DevicePrefix.trim().length() > 0) {
									if (bme280DevicePrefix.trim().length() == 2) {
										((BME280Client) bme280Client).setSpecificDevicePrefix(bme280DevicePrefix.trim());
									} else {
										throw new RuntimeException(String.format("Bad prefix [%s] for BME280. Must be 2 character long, exactly.", bme280DevicePrefix.trim()));
									}
								}
								nmeaDataClients.add(bme280Client);
							} catch (Exception e) {
								e.printStackTrace();
							} catch (Error err) {
								err.printStackTrace();
							}
							break;
						case "bmp180": // Temperature, Pressure
							try {
								deviceFilters = muxProps.getProperty(String.format("mux.%s.device.filters", MUX_IDX_FMT.format(muxIdx)), "");
								sentenceFilters = muxProps.getProperty(String.format("mux.%s.sentence.filters", MUX_IDX_FMT.format(muxIdx)), "");
								String bmp180DevicePrefix = muxProps.getProperty(String.format("mux.%s.device.prefix", MUX_IDX_FMT.format(muxIdx)), "");
								NMEAClient bmp180Client = new BMP180Client(
												deviceFilters.trim().length() > 0 ? deviceFilters.split(",") : null,
												sentenceFilters.trim().length() > 0 ? sentenceFilters.split(",") : null,
												mux);
								bmp180Client.initClient();
								bmp180Client.setReader(new BMP180Reader(bmp180Client.getListeners()));
								bmp180Client.setVerbose("true".equals(muxProps.getProperty(String.format("mux.%s.verbose", MUX_IDX_FMT.format(muxIdx)), "false")));
								// Important: after the setReader
								if (bmp180DevicePrefix.trim().length() > 0) {
									if (bmp180DevicePrefix.trim().length() == 2) {
										((BMP180Client) bmp180Client).setSpecificDevicePrefix(bmp180DevicePrefix.trim());
									} else {
										throw new RuntimeException(String.format("Bad prefix [%s] for BMP180. Must be 2 character long, exactly.", bmp180DevicePrefix.trim()));
									}
								}
								nmeaDataClients.add(bmp180Client);
							} catch (Exception e) {
								e.printStackTrace();
							} catch (Error err) {
								err.printStackTrace();
							}
							break;
						case "batt":   // Battery Voltage, use XDR
						default:
							throw new RuntimeException(String.format("mux type [%s] not supported yet.", type));
					}
				}
			}
			muxIdx++;
		}

		// Data Cache
		if ("true".equals(muxProps.getProperty("init.cache", "false"))) {
			try {
				String deviationFile = muxProps.getProperty("deviation.file.name", "zero-deviation.csv");
				double maxLeeway = Double.parseDouble(muxProps.getProperty("max.leeway", "0"));
				double bspFactor = Double.parseDouble(muxProps.getProperty("bsp.factor", "1"));
				double awsFactor = Double.parseDouble(muxProps.getProperty("aws.factor", "1"));
				double awaOffset = Double.parseDouble(muxProps.getProperty("awa.offset", "0"));
				double hdgOffset = Double.parseDouble(muxProps.getProperty("hdg.offset", "0"));
				double defaultDeclination = Double.parseDouble(muxProps.getProperty("default.declination", "0"));
				int damping = Integer.parseInt(muxProps.getProperty("damping", "1"));
				ApplicationContext.getInstance().initCache(deviationFile, maxLeeway, bspFactor, awsFactor, awaOffset, hdgOffset, defaultDeclination, damping);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		thereIsMore = true;
		int fwdIdx = 1;
		// 2 - Output channels, aka forwarders
		while (thereIsMore) {
			String classProp = String.format("forward.%s.cls", MUX_IDX_FMT.format(fwdIdx));
			String clss = muxProps.getProperty(classProp);
			if (clss != null) { // Dynamic loading
				try {
					Object dynamic = Class.forName(clss).newInstance();
					if (dynamic instanceof Forwarder) {
						Forwarder forwarder = (Forwarder) dynamic;
						String propProp = String.format("forward.%s.properties", MUX_IDX_FMT.format(fwdIdx));
						String propFileName = muxProps.getProperty(propProp);
						if (propFileName != null) {
							try {
								Properties properties = new Properties();
								properties.load(new FileReader(propFileName));
								forwarder.setProperties(properties);
							} catch (Exception ex) {
								ex.printStackTrace();
							}
						}
						nmeaDataForwarders.add(forwarder);
					} else {
						throw new RuntimeException(String.format("Expected a Forwarder, found a [%s]", dynamic.getClass().getName()));
					}
				} catch (Exception ioe) {
					// Some I2C device was not found?
					System.err.println("---------------------------");
					ioe.printStackTrace();
					System.err.println("---------------------------");
				} catch (Throwable ex) {
					System.err.println("===========================");
					System.err.println("Classpath: " + System.getProperty("java.class.path"));
					ex.printStackTrace();
					System.err.println("===========================");
				}
			} else {
				String typeProp = String.format("forward.%s.type", MUX_IDX_FMT.format(fwdIdx));
				String type = muxProps.getProperty(typeProp);
				if (type == null) {
					thereIsMore = false;
				} else {
					switch (type) {
						case "serial":
							String serialPort = muxProps.getProperty(String.format("forward.%s.port", MUX_IDX_FMT.format(fwdIdx)));
							int baudrate = Integer.parseInt(muxProps.getProperty(String.format("forward.%s.baudrate", MUX_IDX_FMT.format(fwdIdx))));
							String propFileSerial = muxProps.getProperty(String.format("forward.%s.properties", MUX_IDX_FMT.format(fwdIdx)));
							String serialSubClass = muxProps.getProperty(String.format("forward.%s.subclass", MUX_IDX_FMT.format(fwdIdx)));
							try {
								Forwarder serialForwarder;
								if (serialSubClass == null) {
									serialForwarder = new SerialWriter(serialPort, baudrate);
								} else {
									serialForwarder = (SerialWriter)Class.forName(serialSubClass.trim()).getConstructor(String.class, Integer.class).newInstance(serialPort, baudrate);
								}
								if (propFileSerial != null) {
									Properties forwarderProps = new Properties();
									forwarderProps.load(new FileReader(propFileSerial));
									serialForwarder.setProperties(forwarderProps);
								}
								nmeaDataForwarders.add(serialForwarder);
							} catch (Exception ex) {
								ex.printStackTrace();
							}
							break;
						case "tcp":
							String tcpPort = muxProps.getProperty(String.format("forward.%s.port", MUX_IDX_FMT.format(fwdIdx)));
							String tcpPropFile = muxProps.getProperty(String.format("forward.%s.properties", MUX_IDX_FMT.format(fwdIdx)));
							String tcpSubClass = muxProps.getProperty(String.format("forward.%s.subclass", MUX_IDX_FMT.format(fwdIdx)));
							try {
								Forwarder tcpForwarder;
								if (tcpSubClass == null) {
									tcpForwarder = new TCPServer(Integer.parseInt(tcpPort));
								} else {
									tcpForwarder = (TCPServer)Class.forName(tcpSubClass.trim()).getConstructor(Integer.class).newInstance(tcpPort);
								}
								if (tcpPropFile != null) {
									Properties forwarderProps = new Properties();
									forwarderProps.load(new FileReader(tcpPropFile));
									tcpForwarder.setProperties(forwarderProps);
								}
								nmeaDataForwarders.add(tcpForwarder);
							} catch (Exception ex) {
								ex.printStackTrace();
							}
							break;
						case "gpsd":
							String gpsdPort = muxProps.getProperty(String.format("forward.%s.port", MUX_IDX_FMT.format(fwdIdx)));
							String gpsdPropFile = muxProps.getProperty(String.format("forward.%s.properties", MUX_IDX_FMT.format(fwdIdx)));
							String gpsdSubClass = muxProps.getProperty(String.format("forward.%s.subclass", MUX_IDX_FMT.format(fwdIdx)));
							try {
								Forwarder gpsdForwarder;
								if (gpsdSubClass == null) {
									gpsdForwarder = new GPSdServer(Integer.parseInt(gpsdPort));
								} else {
									gpsdForwarder = (GPSdServer)Class.forName(gpsdSubClass.trim()).getConstructor(Integer.class).newInstance(gpsdPort);
								}
								if (gpsdPropFile != null) {
									Properties forwarderProps = new Properties();
									forwarderProps.load(new FileReader(gpsdPropFile));
									gpsdForwarder.setProperties(forwarderProps);
								}
								nmeaDataForwarders.add(gpsdForwarder);
							} catch (Exception ex) {
								ex.printStackTrace();
							}
							break;
						case "file":
							String fName = muxProps.getProperty(String.format("forward.%s.filename", MUX_IDX_FMT.format(fwdIdx)), "data.nmea");
							boolean append = "true".equals(muxProps.getProperty(String.format("forward.%s.append", MUX_IDX_FMT.format(fwdIdx)), "false"));
							boolean timeBased = "true".equals(muxProps.getProperty(String.format("forward.%s.timebase.filename", MUX_IDX_FMT.format(fwdIdx)), "false"));
							String propFile = muxProps.getProperty(String.format("forward.%s.properties", MUX_IDX_FMT.format(fwdIdx)));
							String fSubClass = muxProps.getProperty(String.format("forward.%s.subclass", MUX_IDX_FMT.format(fwdIdx)));
							String radix = muxProps.getProperty(String.format("forward.%s.filename.suffix", MUX_IDX_FMT.format(fwdIdx)));
							String logDir = muxProps.getProperty(String.format("forward.%s.log.dir", MUX_IDX_FMT.format(fwdIdx)));
							String split = muxProps.getProperty(String.format("forward.%s.split", MUX_IDX_FMT.format(fwdIdx)));
							try {
								Forwarder fileForwarder;
								if (fSubClass == null) {
									fileForwarder = new DataFileWriter(fName, append, timeBased, radix, logDir, split);
								} else {
									try {
										fileForwarder = (DataFileWriter) Class.forName(fSubClass.trim())
												.getConstructor(String.class, Boolean.class, Boolean.class, String.class, String.class, String.class)
												.newInstance(fName, append, timeBased, radix, logDir, split);
									} catch (NoSuchMethodException nsme) {
										fileForwarder = (DataFileWriter) Class.forName(fSubClass.trim()) // Fallback on previous constructor
												.getConstructor(String.class, Boolean.class)
												.newInstance(fName, append);
									}
								}
								if (propFile != null) {
									Properties forwarderProps = new Properties();
									forwarderProps.load(new FileReader(propFile));
									fileForwarder.setProperties(forwarderProps);
								}
								nmeaDataForwarders.add(fileForwarder);
							} catch (Exception ex) {
								ex.printStackTrace();
							}
							break;
						case "ws":
							String wsUri = muxProps.getProperty(String.format("forward.%s.wsuri", MUX_IDX_FMT.format(fwdIdx)));
							String wsPropFile = muxProps.getProperty(String.format("forward.%s.properties", MUX_IDX_FMT.format(fwdIdx)));
							String wsSubClass = muxProps.getProperty(String.format("forward.%s.subclass", MUX_IDX_FMT.format(fwdIdx)));
							try {
								Forwarder wsForwarder;
								if (wsSubClass == null) {
									wsForwarder = new WebSocketWriter(wsUri);
								} else {
									wsForwarder = (WebSocketWriter)Class.forName(wsSubClass.trim()).getConstructor(String.class).newInstance(wsUri);
								}
								if (wsPropFile != null) {
									Properties forwarderProps = new Properties();
									forwarderProps.load(new FileReader(wsPropFile));
									wsForwarder.setProperties(forwarderProps);
								}
								nmeaDataForwarders.add(wsForwarder);
							} catch (Exception ex) {
								ex.printStackTrace();
							}
							break;
						case "wsp":
							String wspUri = muxProps.getProperty(String.format("forward.%s.wsuri", MUX_IDX_FMT.format(fwdIdx)));
							String wspPropFile = muxProps.getProperty(String.format("forward.%s.properties", MUX_IDX_FMT.format(fwdIdx)));
							String wspSubClass = muxProps.getProperty(String.format("forward.%s.subclass", MUX_IDX_FMT.format(fwdIdx)));
							try {
								Forwarder wspForwarder;
								if (wspSubClass == null) {
									wspForwarder = new WebSocketProcessor(wspUri);
								} else {
									wspForwarder = (WebSocketProcessor)Class.forName(wspSubClass.trim()).getConstructor(String.class).newInstance(wspUri);
								}
								if (wspPropFile != null) {
									Properties forwarderProps = new Properties();
									forwarderProps.load(new FileReader(wspPropFile));
									wspForwarder.setProperties(forwarderProps);
								}
								nmeaDataForwarders.add(wspForwarder);
							} catch (Exception ex) {
								ex.printStackTrace();
							}
							break;
						case "console":
							try {
								String consolePropFile = muxProps.getProperty(String.format("forward.%s.properties", MUX_IDX_FMT.format(fwdIdx)));
								String consoleSubClass = muxProps.getProperty(String.format("forward.%s.subclass", MUX_IDX_FMT.format(fwdIdx)));
								Forwarder consoleForwarder = new ConsoleWriter();
								if (consoleSubClass == null) {
									consoleForwarder = new ConsoleWriter();
								} else {
									consoleForwarder = (ConsoleWriter)Class.forName(consoleSubClass.trim()).getConstructor().newInstance();
								}
								if (consolePropFile != null) {
									Properties forwarderProps = new Properties();
									forwarderProps.load(new FileReader(consolePropFile));
									consoleForwarder.setProperties(forwarderProps);
								}
								nmeaDataForwarders.add(consoleForwarder);
							} catch (Exception ex) {
								ex.printStackTrace();
							}
							break;
						case "rmi":
							String rmiPort = muxProps.getProperty(String.format("forward.%s.port", MUX_IDX_FMT.format(fwdIdx)));
							String rmiName = muxProps.getProperty(String.format("forward.%s.name", MUX_IDX_FMT.format(fwdIdx)));
							String rmiPropFile = muxProps.getProperty(String.format("forward.%s.properties", MUX_IDX_FMT.format(fwdIdx)));
							String subClass = muxProps.getProperty(String.format("forward.%s.subclass", MUX_IDX_FMT.format(fwdIdx))); // TODO Manage that one...
							try {
								Forwarder rmiServerForwarder;
								if (rmiName != null && rmiName.trim().length() > 0) {
									rmiServerForwarder = new RMIServer(Integer.parseInt(rmiPort), rmiName);
								} else {
									rmiServerForwarder = new RMIServer(Integer.parseInt(rmiPort));
								}
								if (rmiPropFile != null) {
									Properties forwarderProps = new Properties();
									forwarderProps.load(new FileReader(rmiPropFile));
									rmiServerForwarder.setProperties(forwarderProps);
								}
								nmeaDataForwarders.add(rmiServerForwarder);
							} catch (Exception ex) {
								ex.printStackTrace();
							}
							break;
						default:
							throw new RuntimeException(String.format("forward type [%s] not supported yet.", type));
					}
				}
			}
			fwdIdx++;
		}
		// Init cache (for Computers).
		if ("true".equals(muxProps.getProperty("init.cache", "false"))) {
			try {
				// If there is a cache, then let's see what computers to start.
				thereIsMore = true;
				int cptrIdx = 1;
				// 3 - Computers
				while (thereIsMore) {
					String classProp = String.format("computer.%s.cls", MUX_IDX_FMT.format(cptrIdx));
					String clss = muxProps.getProperty(classProp);
					if (clss != null) { // Dynamic loading
						try {
							Object dynamic = Class.forName(clss).getDeclaredConstructor(Multiplexer.class).newInstance(mux);
							if (dynamic instanceof Computer) {
								Computer computer = (Computer)dynamic;
								String propProp = String.format("computer.%s.properties", MUX_IDX_FMT.format(cptrIdx));
								String propFileName = muxProps.getProperty(propProp);
								if (propFileName != null) {
									try {
										Properties properties = new Properties();
										properties.load(new FileReader(propFileName));
										computer.setProperties(properties);
									} catch (Exception ex) {
										ex.printStackTrace();
									}
								}
								nmeaDataComputers.add(computer);
							} else {
								throw new RuntimeException(String.format("Expected a Computer, found a [%s]", dynamic.getClass().getName()));
							}
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					} else {
						String typeProp = String.format("computer.%s.type", MUX_IDX_FMT.format(cptrIdx));
						String type = muxProps.getProperty(typeProp);
						if (type == null) {
							thereIsMore = false;
						} else {
							switch (type) {
								case "tw-current":
									String prefix = muxProps.getProperty(String.format("computer.%s.prefix", MUX_IDX_FMT.format(cptrIdx)), "OS");
									String[] timeBuffers = muxProps.getProperty(String.format("computer.%s.time.buffer.length", MUX_IDX_FMT.format(cptrIdx)), "600000").split(",");
									List<Long> timeBufferLengths = Arrays.asList(timeBuffers).stream().map(tbl -> Long.parseLong(tbl.trim())).collect(Collectors.toList());
									// Check duplicates
									for (int i = 0; i < timeBufferLengths.size() - 1; i++) {
										for (int j = i + 1; j < timeBufferLengths.size(); j++) {
											if (timeBufferLengths.get(i).equals(timeBufferLengths.get(j))) {
												throw new RuntimeException(String.format("Duplicates in time buffer lengths: %d ms.", timeBufferLengths.get(i)));
											}
										}
									}
									try {
										Computer twCurrentComputer = new ExtraDataComputer(mux, prefix, timeBufferLengths.toArray(new Long[timeBufferLengths.size()]));
										nmeaDataComputers.add(twCurrentComputer);
									} catch (Exception ex) {
										ex.printStackTrace();
									}
									break;
								default:
									System.err.println(String.format("Computer type [%s] not supported.", type));
									break;
							}
						}
					}
					cptrIdx++;
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
}

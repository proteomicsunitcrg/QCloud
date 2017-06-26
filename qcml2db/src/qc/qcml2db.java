package qc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 
 * @author Roger Olivella
 * 
 *         Created: 18/06/2015
 * 
 *         Description: inserts qcML data into MySQL: 
 *         		- Quality parameters: total number of peptides, proteins, PSMs, etc. at qcml file. 
 *         		- Injection time: by reading mzML file, cv param = ion injection time
 *         		- Total Ion Current: by reading mzML file, cv param = TICs
 *         		- Mass accuracy: from fetaureXML (same info can be found at idXML). 
 *         
 *         Major modifications: 
 *                           22/02/2017: adapted to get some key parameters from a db instead of the .properties file. 
 *               	     Code cleaning and optimization
 *               	     25/04/2017: added TTOF insert to DB
 * 
 */

public class qcml2db {

    	public static Logger logger = Logger.getLogger("MyLog");
	public static String minFileSize;
	public static String minFileAge;
	public static String forceDateDir;
	public static String inputDir;
	public static String instrumentDirs;
	public static String toppasOutputDir;
	public static String logDir;
	public static String JDBC_DRIVER, DB_URL, USER,PASS;
	public static String DB_URL_MAIN, USER_MAIN, PASS_MAIN;
	public static String qcPattern;
	public static String qcCode = "";
	public static List<String> instrumentDirsList = new ArrayList<String>();
	public static List<String> finalDataDirList = new ArrayList<String>();
	public static List<String> qcCodeList = new ArrayList<String>();
	public static List<String> featureDirList = new ArrayList<String>();
	public static List<String> idxmlDirList = new ArrayList<String>();
	public static List<String> mzList = new ArrayList<String>();
	public static List<String> thresholdareaList = new ArrayList<String>();
	public static List<String> chargeList = new ArrayList<String>();
	public static List<String> drtList = new ArrayList<String>();
	public static List<String> refrtList = new ArrayList<String>();
	public static List<String> dmzList = new ArrayList<String>();
	public static List<String> sequenceList = new ArrayList<String>();
	public static List<String> typeList = new ArrayList<String>();
	public static List<String> userList = new ArrayList<String>();
	public static List<String> mzmlFolderList = new ArrayList<String>();
	public static List<String> workflowList = new ArrayList<String>();
	public static List<String> instrcodeList = new ArrayList<String>();
	
	public static void main(String[] args) {
	    
		if (args.length == 1) {//check JAR arguments
		    
			getProperties(args[0] + File.separator + "qcml2db.properties");
			getDBparameters_VM(DB_URL,USER,PASS);
			
			configureLogFile(logDir);
			List<String> dateDirsList = getInputDirs(forceDateDir);
			
			logger.info("############## START qcml2db.jar ##############");
			if (dateDirsList.size() > 0) {
				for (int i = 0; i < dateDirsList.size(); i++) {// for each date dir...
					logger.info("Collecting qcML filenames...");
					ArrayList<String> qcmlsToProcess = getQcmlToProcess(dateDirsList.get(i));
					if (qcmlsToProcess.size() > 0) {
						for (int j = 0; j < qcmlsToProcess.size(); j++) {// for each filename...
						    for (int k = 0; k < qcCodeList.size(); k++) {// for each QCode...
						    	qcCode = qcCodeList.get(k);
						    	if(getQcodeFromAnyString(qcmlsToProcess.get(j)).equals(qcCode)){
								boolean isFileInDB = insertQcmlIntoDB(qcmlsToProcess.get(j), qcCode, dateDirsList.get(i), typeList.get(k), userList.get(k), instrumentDirsList.get(k), mzmlFolderList.get(k), workflowList.get(k), instrcodeList.get(k));
								if(!isFileInDB){
									if(workflowList.get(k).equals("shotgun")){
									    insertFeaturesIntoDB(qcmlsToProcess.get(j),sequenceList.get(k),thresholdareaList.get(k),mzList.get(k),chargeList.get(k),drtList.get(k),dmzList.get(k),qcCode,"false",featureDirList.get(k),idxmlDirList.get(k));
									} else if(workflowList.get(k).equals("srm")){
									    insertSRMFeaturesIntoDB(qcmlsToProcess.get(j), qcCode, sequenceList.get(k), featureDirList.get(k));
									} else if(workflowList.get(k).equals("ttof")){
									    insertTTOFeaturesIntoDB(qcmlsToProcess.get(j), qcCode, sequenceList.get(k), featureDirList.get(k),mzList.get(k),thresholdareaList.get(k),chargeList.get(k),drtList.get(k),dmzList.get(k),refrtList.get(k));
									} else if(workflowList.get(k).equals("phospho")){
									    insertCountPhosphoIntoDB(qcmlsToProcess.get(j), sequenceList.get(k),thresholdareaList.get(k),mzList.get(k),chargeList.get(k),drtList.get(k),dmzList.get(k),qcCode,idxmlDirList.get(k));
									    insertFeaturesIntoDB(qcmlsToProcess.get(j),sequenceList.get(k),thresholdareaList.get(k),mzList.get(k),chargeList.get(k),drtList.get(k),dmzList.get(k),qcCode,"true",featureDirList.get(k),idxmlDirList.get(k));
									}								    
								}
						    	}
						    }
						}
						
					} else {
						logger.info("NO FILES TO PROCESS.");
					}
				}
			}
			logger.info("############## END qcml2db.jar ##############");
		} else {
			logger.info("You must specify where the properties file is, e.g. java -jar qcml2db.jar /users/pr/shared/QC/scripts/jar");
		}
	}// end main

	private static void getDBparameters_VM(String DB_URL, String USER, String PASS){
	    
	    	Connection conn = null;
		Statement stmt = null;
		try {//start db conn

			Class.forName(JDBC_DRIVER);
			conn = DriverManager.getConnection(DB_URL, USER, PASS);
			stmt = conn.createStatement();
		
			//Get params from db: 
			ResultSet rs = stmt.executeQuery("SELECT instrcode,internal,workflow,serverfolder,mzmlFolder,qcode.type,iduser,finalDataDir,featureDir,idxmlDir,sequence,mz,thresholdarea,charge,refrt,drt,dmz FROM qcode INNER JOIN monitoredpeptides ON qcode.idmonitoredpeptides =  monitoredpeptides.idmonitoredpeptides INNER JOIN instrument ON instrument.idinstrument = qcode.idinstrument WHERE internal <> ''");
			while (rs.next()) {
			    qcCodeList.add(rs.getString("internal"));
			    featureDirList.add(rs.getString("featureDir"));
			    finalDataDirList.add(rs.getString("finalDataDir"));
			    idxmlDirList.add(rs.getString("idxmlDir"));
			    sequenceList.add(rs.getString("sequence"));
			    mzList.add(rs.getString("mz"));
			    thresholdareaList.add(rs.getString("thresholdarea"));
			    chargeList.add(rs.getString("charge"));
			    drtList.add(rs.getString("drt"));
			    refrtList.add(rs.getString("refrt"));
			    dmzList.add(rs.getString("dmz"));
			    typeList.add(rs.getString("type"));
			    userList.add(rs.getString("iduser"));
			    instrumentDirsList.add(rs.getString("serverfolder"));
			    mzmlFolderList.add(rs.getString("mzmlFolder"));
			    workflowList.add(rs.getString("workflow"));
			    instrcodeList.add(rs.getString("instrcode"));
			}
			
		} catch (SQLException sqle) {
			logger.warning("Query SQLException ==> " + sqle.getMessage());
			sqle.printStackTrace();
		} catch (Exception e) {
			logger.warning("General Exception ==> " + e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if (stmt != null) stmt.close();
			} catch (SQLException stmte) {
				logger.warning("Closing SQL Statement exception ==> " + stmte.getMessage());
				stmte.printStackTrace();
			}
			try {
				if (conn != null) conn.close();
			} catch (SQLException conne) {
				logger.warning("Closing SQL Connection exception ==> " + conne.getMessage());
				conne.printStackTrace();
			}
		} // end db conn 
	    	
	}
	
	private static double getMedian(List<Double> input){
	    Collections.sort(input);
	    double median;
	    if (input.size() % 2 == 0)
		median = ((double)input.get(input.size()/2) + (double)input.get((input.size()/2 - 1)))/2;
	    else
		median = (double) input.get(input.size()/2);
	    return median;
	}
	
	private static double getMedianInjectionTime(String filename, String dateDir, String instrumentsDir, String mzmlfolder, String MSlevel) {
	    
		List<Double> injection_time_array = new ArrayList<Double>();
		try {
			String filename_path = inputDir 
				             + File.separator + instrumentsDir + File.separator
					     + mzmlfolder + File.separator + dateDir + File.separator
					     + filename
					     + ".mzML";
			
			if (new File(filename_path).exists() && !new File(filename_path).isDirectory()) {
			    
				logger.info("Reading " + filename + " mzML file to extract INJECTION TIME MS"+MSlevel+"...");
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document doc = builder.parse(filename_path);

				XPathFactory xPathfactory = XPathFactory.newInstance();
				XPath xpath = xPathfactory.newXPath();
				
				XPathExpression exprMS1 = xpath.compile("/indexedmzML/mzML/run/spectrumList/spectrum");
				NodeList allSpectrumNodes = (NodeList) exprMS1.evaluate(doc,XPathConstants.NODESET);
				
				boolean isMSLevel = false;
				
				for (int i = 0; i < allSpectrumNodes.getLength(); i++) {
				    isMSLevel = false;
					Node spectrumNode = allSpectrumNodes.item(i);
					NodeList allChildsSpectrumNode = spectrumNode.getChildNodes();
					for (int k = 0; k < allChildsSpectrumNode.getLength(); k++) {
					    Node childSpectrumNode = allChildsSpectrumNode.item(k);
					    if (childSpectrumNode != null && childSpectrumNode.getNodeType() == Node.ELEMENT_NODE) {
						if(childSpectrumNode.getNodeName().equals("cvParam")){
						    if (childSpectrumNode.getAttributes().getNamedItem("accession").getNodeValue().equals("MS:1000511")) {
							    if (childSpectrumNode.getAttributes().getNamedItem("value").getNodeValue().equals(MSlevel)) {
								isMSLevel = true;
							    }
						    }
						} else if(childSpectrumNode.getNodeName().equals("scanList")){
						    NodeList scanListNodes = childSpectrumNode.getChildNodes();
						    for (int m = 0; m < scanListNodes.getLength(); m++) {
							Node scanListChild = scanListNodes.item(m);
							if (scanListChild != null && scanListChild.getNodeType() == Node.ELEMENT_NODE) {
							    if(scanListChild.getNodeName().equals("scan")){
								NodeList scanNodes = scanListChild.getChildNodes();
								for (int n = 0; n < scanNodes.getLength(); n++) {
								    Node scanNode = scanNodes.item(n);
								    if (scanNode != null && scanNode.getNodeType() == Node.ELEMENT_NODE) {
									if(scanNode.getNodeName().equals("cvParam")){
									    if (scanNode.getAttributes().getNamedItem("accession").getNodeValue().equals("MS:1000927") & isMSLevel) {
										injection_time_array.add(Double.parseDouble(scanNode.getAttributes().getNamedItem("value").getNodeValue()));
									    }
									}
								    }
								}
							    }
							}
						    }
						    
						}
					    }
					}
				}
				
			} else {
				logger.warning("It's not possible to extract the INJECTION TIME because the following file does not exist: " + filename_path);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		
		List<Double> test = new ArrayList<Double>();
		
		return getMedian(injection_time_array);
	}
	
	private static Double getCumsumTIC(String filename, String dateDir) {
		double TIC_cumsum = 0;
		try {
			
		    	String filename_path = filename;
			
			if (new File(filename_path).exists() && !new File(filename_path).isDirectory()) {
				logger.info("Reading " + filename + " file to extract CUMULATIVE SUM OF TIC...");
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document doc = builder.parse(filename_path);

				XPathFactory xPathfactory = XPathFactory.newInstance();
				XPath xpath = xPathfactory.newXPath();

				XPathExpression expr = xpath.compile("/qcML/runQuality/attachment");
				NodeList ParamList = (NodeList) expr.evaluate(doc,XPathConstants.NODESET);

				for (int i = 0; i < ParamList.getLength(); i++) {
					Node Param = ParamList.item(i);
					if (Param != null && Param.getNodeType() == Node.ELEMENT_NODE) {
					    if (Param.getAttributes().getNamedItem("name").getNodeValue().equals("TICs")) {
						NodeList attachmentTicChildNodes = Param.getChildNodes();
						for (int j = 0; j < attachmentTicChildNodes.getLength(); j++) {
						    Node attachmentTicChild = attachmentTicChildNodes.item(j);
						    if (attachmentTicChild != null && attachmentTicChild.getNodeType() == Node.ELEMENT_NODE) {
							    for(int k = 2; k < attachmentTicChild.getChildNodes().getLength(); k++){
								if(attachmentTicChild.getChildNodes().item(k).getFirstChild() != null){
								    TIC_cumsum = TIC_cumsum + Double.parseDouble(attachmentTicChild.getChildNodes().item(k).getFirstChild().getNodeValue().split(" ")[1]);
								}
							    }
						    }
						}
					    }
					}
				}
				
				
			} else {
				logger.warning("It's not possible to extract the  CUMULATIVE SUM OF TIC because the following file does not exist: " + filename_path);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}


		return TIC_cumsum;
	}

	public static String getQcodeFromAnyString(String input){
	    	int index = input.lastIndexOf("QC");
		return input.substring(index, index + 4);
	}

	private static List<String> getInputDirs(String forceDateDir) {
		List<String> dateDirList = new ArrayList<String>();
		if (forceDateDir.equals("present")) {
			dateDirList.add(getYYMMfolderName(new Date()));
		} else {
			dateDirList = Arrays.asList(forceDateDir.split("\\s*,\\s*"));
		}
		return dateDirList;
	}

	private static void getProperties(String paramFile) {

		// Getting properties parameters:
		Properties prop = new Properties();
		InputStream inputProp = null;

		try {
			inputProp = new FileInputStream(paramFile);
			prop.load(inputProp);
			minFileSize = prop.getProperty("minFileSize");
			minFileAge = prop.getProperty("minFileAge");
			forceDateDir = prop.getProperty("forceDateDir");
			toppasOutputDir = prop.getProperty("toppasOutputDir");
			inputDir = prop.getProperty("inputDir");
			instrumentDirs = prop.getProperty("instrumentDirs");
			logDir = prop.getProperty("logDir");
			JDBC_DRIVER = prop.getProperty("JDBC_DRIVER");
			DB_URL = prop.getProperty("DB_URL");
			USER = prop.getProperty("USER");
			PASS = prop.getProperty("PASS");
			DB_URL_MAIN = prop.getProperty("DB_URL_MAIN");
			USER_MAIN = prop.getProperty("USER_MAIN");
			PASS_MAIN = prop.getProperty("PASS_MAIN");

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static String getYYMMfolderName(Date now) {
		SimpleDateFormat year_format = new SimpleDateFormat("yy");
		SimpleDateFormat month_format = new SimpleDateFormat("MM");
		String year_formatted = year_format.format(now);
		String month_formatted = month_format.format(now);
		return year_formatted + month_formatted;
	}

	private static void configureLogFile(String logDir) {
		FileHandler fh;
		String logName = "qcml2db-" + getYYMMfolderName(new Date());
		try {
			File logFile = new File(logDir + File.separator + logName + ".log");
			if (!logFile.exists()) {
				fh = new FileHandler(logDir + File.separator + logName + ".log");
			} else {
				fh = new FileHandler(logDir + File.separator + logName + ".log", true);// append
			}
			logger.addHandler(fh);
			SimpleFormatter formatter = new SimpleFormatter();
			fh.setFormatter(formatter);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static ArrayList getQcmlToProcess(String dateDir) {
	    
		ArrayList<String> filesToProcess = new ArrayList<String>();
		List<String> scannedServerFoldersList = new ArrayList<String>();
		
		for (int m = 0; m < instrumentDirsList.size(); m++) {// loop into all instrument subfolders

		    	if (!scannedServerFoldersList.contains(instrumentDirsList.get(m).toString())){
				// List contents of input and output dirs:
				File[] listOfInputFiles = new File(inputDir + File.separator + instrumentDirsList.get(m).toString() + File.separator + finalDataDirList.get(m).toString() + File.separator + dateDir).listFiles();

				//logger.info("Scanning input folder: " + inputDir + File.separator + instrumentDirsList.get(m).toString() + File.separator + File.separator + finalDataDirList.get(m).toString() + File.separator + dateDir);

				// Retrieve non processed files comparing input and output folders:
				try {
					if (listOfInputFiles != null) {
						for (int i = 0; i < listOfInputFiles.length; i++) {
							BasicFileAttributes attr = Files.readAttributes(Paths.get(listOfInputFiles[i].getPath()),BasicFileAttributes.class);
							String filename = listOfInputFiles[i].getName();
							if (attr.size() == 0) {// if there were an error with openms at mzml2qcml.java
								filesToProcess.add(listOfInputFiles[i].toString());
							} else if ((attr.size() > Long.parseLong(minFileSize.trim())) && (Long.parseLong(minFileAge.trim()) < (System.currentTimeMillis() - attr.creationTime().toMillis()))) {// size
								for (int k = 0; k < qcCodeList.size(); k++) {
									if (isPatternPresent(qcCodeList.get(k),filename)) {// check if the file has qcPattern substring
										filesToProcess.add(listOfInputFiles[i].toString());
									}
								}
							}
						}
					} else {
						//logger.info("Input dir either is empty or does not exist.");
					}
					
				} catch (FileNotFoundException fnfee) {
					logger.warning("==> Get files to process FileNotFoundException: "+ fnfee.getMessage() + ".");
					fnfee.printStackTrace();
				} catch (AccessDeniedException ade) {
					logger.warning("==> Get files to process AccessDeniedException: "+ ade.getMessage() + ".");
				} catch (IOException ioe) {
					logger.warning("==> Get files to process IOException: "+ ioe.getMessage() + ".");
				} catch (Exception e) {
					logger.warning("==> Get files to process Exception: "+ e.getMessage()+ ". Please check the input and ouput paths.");
					e.printStackTrace();
				} finally {
				    scannedServerFoldersList.add(instrumentDirsList.get(m).toString());
				}		    	    
		    	}

		}
		return filesToProcess;
	}

	private static boolean isPatternPresent(String patternStr,
			String stringToMatch) {
		Pattern patternPtr = Pattern.compile(".*(" + patternStr + ").*");
		Matcher matcher = patternPtr.matcher(stringToMatch);
		if (matcher.matches()) {// Check if the file contains the pattern patternStr
			return true;
		} else {
			return false;
		}
	}

	private static boolean insertQcmlIntoDB(String filename, String qcPattern, String dateDir, String type, String user, String instrumentsDir, String mzmlFolder, String workflow, String instrcode) {
	    
	    	boolean isFileInDB = true;
	    
		// yyyyMMddHHmm to yyyy-MM-dd HH:mm
		String creationDate = filename.substring(filename.lastIndexOf("-") + 1,filename.lastIndexOf("."));
		String year = creationDate.substring(0, 4);
		String month = creationDate.substring(4, 6);
		String day = creationDate.substring(6, 8);
		String hour = creationDate.substring(8, 10);
		String minute = creationDate.substring(10, 12);
		creationDate = year + "-" + month + "-" + day + " " + hour + ":" + minute;
		
		//Date of insertion to database: 
		String insertdbdate = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());

		if (!year.equals("1970")) {

			Connection conn = null;
			Statement stmt = null;
			boolean isFileInserted = false;

			try {
				int idfile = 0;
				int idAttachment = 0;

				Class.forName(JDBC_DRIVER);
				conn = DriverManager.getConnection(DB_URL_MAIN, USER_MAIN, PASS_MAIN);
				stmt = conn.createStatement();

				File destFile = new File(filename);

				if (destFile.exists() && !destFile.isDirectory()) {
				    
					if (destFile.canWrite()) {// check if the file is locked (is being processed...)

						// Check if the file to insert was already inserted:
						String sqlFileName = "SELECT filename FROM file WHERE filename = '" + new File(filename).getName() + "'";
						ResultSet rsFileName = stmt.executeQuery(sqlFileName);
						
						//Insert the qcML file if it's NOT in the database: 
						if (!rsFileName.next()) {
						    
						    	isFileInDB = false;

							BasicFileAttributes attr = Files.readAttributes(Paths.get(filename),BasicFileAttributes.class);
							String openms = "";
							if (attr.size() == 0) {
								openms = "ERR";
							} else {
								openms = "OK";
							}
							
							logger.info("INSERTING "+filename+" INTO database...");
							
							// Extract instrument name:
							String instrument = instrcode;

							///////////////////////////////////
							// INSERTs into 'file' table///////
							///////////////////////////////////
							logger.info("Inserting into \'file\' table...");
							String sqlFile = "INSERT INTO file (creationdate,filename,instrument,type,openms,iduser,insertdbdate) VALUES ('"
									+ creationDate
									+ "','"
									+ new File(filename).getName()
									+ "','"
									+ instrument
									+ "','"
									+ type
									+ "','" + openms
									+ "'," + user + ",'"+insertdbdate+"')";
							
							int resultInsert = stmt.executeUpdate(sqlFile);

							// SELECT fileid of the current file:
							String sqlIdFile = "SELECT idfile FROM file WHERE filename = '" + new File(filename).getName() + "'";
							ResultSet rs = stmt.executeQuery(sqlIdFile);
							while (rs.next()) {
								idfile = rs.getInt("idfile");
							}

							if (openms.equals("OK")) {
								
							    	// Load XML file and configure reader (XPath):
								DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
								DocumentBuilder builder = factory.newDocumentBuilder();
								Document doc = builder.parse(filename);
								XPathFactory xPathfactory = XPathFactory.newInstance();
								XPath xpath = xPathfactory.newXPath();

								//////////////////////////////////////////
								// insert into 'qualityParemeter' table///
								//////////////////////////////////////////
								logger.info("Insert into \'qualityparameter\' table...");
								XPathExpression expr = xpath.compile("/qcML/runQuality/qualityParameter");
								NodeList nl = (NodeList) expr.evaluate(doc,XPathConstants.NODESET);
								for (int i = 0; i < nl.getLength(); i++) {
									String name = "";
									String ID = "";
									String cvRef = "";
									String accession = "";
									String value = "";
									Node nodeName = nl.item(i).getAttributes().getNamedItem("name");
									Node nodeID = nl.item(i).getAttributes().getNamedItem("ID");
									Node nodeCvRef = nl.item(i).getAttributes().getNamedItem("cvRef");
									Node nodeAccession = nl.item(i).getAttributes().getNamedItem("accession");
									Node nodeValue = nl.item(i).getAttributes().getNamedItem("value");
									name = (nodeName != null) ? nl.item(i).getAttributes().getNamedItem("name").getNodeValue() : "";
									ID = (nodeID != null) ? nl.item(i).getAttributes().getNamedItem("ID").getNodeValue() : "";
									cvRef = (nodeCvRef != null) ? nl.item(i).getAttributes().getNamedItem("cvRef").getNodeValue() : "";
									accession = (nodeAccession != null) ? nl.item(i).getAttributes().getNamedItem("accession").getNodeValue() : "";
									value = (nodeValue != null) ? nl.item(i).getAttributes().getNamedItem("value").getNodeValue() : "";
									String sqlQP = "INSERT INTO qualityparameter (idfile,name,ID,cvRef,accession,value) VALUES ("
											+ idfile
											+ ",'"
											+ name
											+ "','"
											+ ID
											+ "','"
											+ cvRef
											+ "','"
											+ accession
											+ "','" + value + "')";
									int resultInsertQP = stmt.executeUpdate(sqlQP);
								}

								
								if(workflow.equals("shotgun")){
									////////////////////////////////////////
									// Insert into 'injection_time' table //
									////////////////////////////////////////
									String filename_subtracted_date = filename.substring(filename.lastIndexOf(File.separator),filename.lastIndexOf('-'));

									double injection_time_ms1 = getMedianInjectionTime(filename_subtracted_date, dateDir, instrumentsDir, mzmlFolder, "1");//MS1
									double injection_time_ms2 = getMedianInjectionTime(filename_subtracted_date, dateDir, instrumentsDir, mzmlFolder, "2");//MS2
									
									String sqlIT = "INSERT INTO injection_time (it_median_ms1,it_median_ms2,injection_time_mean,injection_time_max,injection_time_min,accession,idfile) VALUES ("
											+ injection_time_ms1
											+ ","
											+ injection_time_ms2
											+ ","
											+ 0
											+ ","
											+ 0
											+ ","
											+ 0
											+ ",'MS:1000927'," + idfile + ")";
									int resultInsertIT = stmt.executeUpdate(sqlIT);	
									

									////////////////////////////
									// Insert into 'tic' table//
									////////////////////////////
									Double cumsumtic = getCumsumTIC(filename, dateDir);
									String sqlTIC = "INSERT INTO tic (cumsumtic,idfile) VALUES ("
											+ cumsumtic
											+ ","
											+ idfile + ")";
									int resultInsertTIC = stmt.executeUpdate(sqlTIC);								    
								}
								
							} else {
							    
								logger.info("File "+filename+" NOT INSERTED [openms = ERR].");
								
							}
							rs.close();// for SELECTs
						} 
						rsFileName.close();// for SELECTs
					} else {// if locked
						logger.warning("==> Skipping the file "
								+ new File(filename).getAbsolutePath()
								+ " because is locked (being processed right now by OpenMS), so it will be not inserted into de database.");
					}
				} else {
					logger.warning("==> The file "
							+ new File(filename).getAbsolutePath()
							+ " does not exist. Please check configuration.properties.");
				}


			} catch (SQLException sqle) {
				logger.warning("Query SQLException ==> " + sqle.getMessage());
				sqle.printStackTrace();
			} catch (Exception e) {
				logger.warning("General DB conn Exception ==> " + e.getMessage());
				e.printStackTrace();
			} finally {
				try {
					if (stmt != null) stmt.close();
				} catch (SQLException stmte) {
					logger.warning("Closing SQL Statement exception ==> " + stmte.getMessage());
					stmte.printStackTrace();
				}
				try {
					if (conn != null) conn.close();
				} catch (SQLException conne) {
					logger.warning("Closing SQL Connection exception ==> " + conne.getMessage());
					conne.printStackTrace();
				}
				
			}// end db conn
			
			return isFileInDB;

		}
		return isFileInDB;
	}
	
	private static String getRTfromIDxml(String sequence_QC, String idxmlFilename, double featureRT) {

		String RT_QC = "";
		
		ArrayList<Double> RTlistdiff = new ArrayList<Double>();
		ArrayList<String> RTlist = new ArrayList<String>();

		if (new File(idxmlFilename).exists()
				&& !new File(idxmlFilename).isDirectory()) {
			try {
				DocumentBuilderFactory factory = DocumentBuilderFactory
						.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document doc = builder.parse(idxmlFilename);

				XPathFactory xPathfactory = XPathFactory.newInstance();
				XPath xpath = xPathfactory.newXPath();

				XPathExpression expr = xpath.compile("/IdXML/IdentificationRun/PeptideIdentification");
				NodeList idList = (NodeList) expr.evaluate(doc,XPathConstants.NODESET);

				String RT = "";
				String sequence = "";

				for (int i = 0; i < idList.getLength(); i++) {
					Node PeptideIdentification = idList.item(i);
					if (PeptideIdentification != null && PeptideIdentification.getNodeType() == Node.ELEMENT_NODE) {
						RT = PeptideIdentification.getAttributes().getNamedItem("RT").getNodeValue();
						NodeList PeptideHitNodes = PeptideIdentification.getChildNodes();
						for (int j = 0; j < PeptideHitNodes.getLength(); j++) {
							Node PeptideHit = PeptideHitNodes.item(j);
							if (PeptideHit != null && PeptideHit.getNodeType() == Node.ELEMENT_NODE && PeptideHit.getNodeName().equals("PeptideHit")) {
								sequence = PeptideHit.getAttributes().getNamedItem("sequence").getNodeValue();
								if (sequence.equals(sequence_QC)) {
								    	RTlistdiff.add(Math.abs(Double.parseDouble(RT)-featureRT));
								    	RTlist.add(RT);
								}
							}
						}
					}
				}
				
				//Find the RTidxml closer to RTfeaturexml: 
				int minIndex = RTlistdiff.indexOf(Collections.min(RTlistdiff));
				RT_QC = RTlist.get(minIndex);

			} catch (Exception e) {
				logger.warning("==> " + e.getMessage());
				e.printStackTrace();
			}
		} else {
			logger.warning("==> The file " + idxmlFilename + " does not exist.");
		}

		return RT_QC;
	}
	
	private static void insertSRMFeaturesIntoDB(String filename, String qcCode, String qcPeptides, String featureDir) {

		Connection conn = null;
		Statement stmt = null;
		int idfile = 0;
		String creationdate = "";

		try {
			Class.forName(JDBC_DRIVER);
			conn = DriverManager.getConnection(DB_URL_MAIN, USER_MAIN, PASS_MAIN);
			stmt = conn.createStatement();

			String inputFilename = new File(filename).getName();

			String filename_subtracted_date = filename.substring(filename.lastIndexOf(File.separator),filename.lastIndexOf('-'));

			filename = toppasOutputDir + File.separator + "TOPPAS_out" + File.separator + featureDir + File.separator + filename_subtracted_date + ".featureXML"; 

			if (new File(filename).exists() && !new File(filename).isDirectory()) {

				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document doc = builder.parse(filename);

				XPathFactory xPathfactory = XPathFactory.newInstance();
				XPath xpath = xPathfactory.newXPath();

				XPathExpression expr = xpath.compile("/featureMap/featureList/feature");
				NodeList featureList = (NodeList) expr.evaluate(doc,XPathConstants.NODESET);

				String id = "";
				String rt = "";
				String mz = "";
				String intensity = "";
				String charge = "";
				String sequence = "";
				String sn_ratio = "";
				String delta_rt = "";
				double delta_rt_previous = -1;
				int idfile_previous = -1;
				String sequence_previous = "";

				ArrayList<String> sequenceList = new ArrayList<String>();

				List<String> peptideList = Arrays.asList(qcPeptides.split("\\s*,\\s*"));

				// SELECT fileid of the current file:
				String sqlIdFile = "SELECT idfile,creationdate FROM file WHERE filename = '" + inputFilename + "'";
				ResultSet rs = stmt.executeQuery(sqlIdFile);
				while (rs.next()) {
					idfile = rs.getInt("idfile");
					creationdate = rs.getString("creationdate");
				}

				
				// INSERT assigned peptides that match certain conditions:
				for (int i = 0; i < featureList.getLength(); i++) {
					Node feature = featureList.item(i);
					if (feature != null && feature.getNodeType() == Node.ELEMENT_NODE) {
						id = feature.getAttributes().getNamedItem("id").getNodeValue();// feature id
						NodeList featureChildNodes = feature.getChildNodes();
						for (int j = 0; j < featureChildNodes.getLength(); j++) {
							Node featureChild = featureChildNodes.item(j);
							if (featureChild != null && featureChild.getNodeType() == Node.ELEMENT_NODE) {
								if (featureChild.getNodeName().equals("position")) {
									if (featureChild.getAttributes().getNamedItem("dim").getNodeValue().equals("0")) {
										rt = featureChild.getTextContent();// RT
									}
								}
								if (featureChild.getNodeName().equals("position")) {
									if (featureChild.getAttributes().getNamedItem("dim").getNodeValue().equals("1")) {
										mz = featureChild.getTextContent();// M/Z
									}
								}
								if (featureChild.getNodeName().equals("userParam")) {
									if (featureChild.getAttributes().getNamedItem("name").getNodeValue().equals("sn_ratio")) {
										sn_ratio = featureChild.getAttributes().getNamedItem("value").getNodeValue();// sn_ratio
									}
								}
								if (featureChild.getNodeName().equals(
										"userParam")) {
									if (featureChild.getAttributes().getNamedItem("name").getNodeValue().equals("delta_rt")) {
										delta_rt = featureChild.getAttributes().getNamedItem("value").getNodeValue();// delta_rt
									}
								}
								if (featureChild.getNodeName().equals("charge")) {
									charge = featureChild.getTextContent();// charge
								}
								if (featureChild.getNodeName().equals("intensity")) {
									intensity = featureChild.getTextContent();// intensity
								}
								if (featureChild.getNodeName().equals("PeptideIdentification")) {// sequence
									NodeList peptideHit = featureChild.getChildNodes();
									for (int k = 0; k < peptideHit.getLength(); k++) {
										Node peptideHitNode = peptideHit.item(k);
										if (peptideHitNode != null && peptideHitNode.getNodeType() == Node.ELEMENT_NODE) {
											if (peptideHitNode.getAttributes().getNamedItem("sequence") != null) {
												sequence = peptideHitNode.getAttributes().getNamedItem("sequence").getNodeValue();
												sequenceList.add(sequence);// sometimes there's more than one PeptideIdentification inside a feature!
											}
										}
									}
								}// if sequence
							}
						}
						

						int indexPepSeq = peptideList.indexOf(sequence);

						for (int n = 0; n < sequenceList.size(); n++) {
							if (peptideList.contains(sequenceList.get(n))) {
								sequence = sequenceList.get(n);
							}
						}

						if (indexPepSeq != -1) {// The peptide is in the list of QC peptides
						    
						    	String sqlFeatureSeq = "SELECT * FROM feature WHERE sequence = '"
								+ sequence
								+ "' AND idfile="
								+ idfile;
        						ResultSet rsFeatureSeq;
        						rsFeatureSeq = stmt.executeQuery(sqlFeatureSeq);
        						double dbI = 0;//database I
        						double dI = Double.parseDouble(intensity);
        						while (rsFeatureSeq.next()) {
        							dbI = rsFeatureSeq.getDouble("intensity");
        						}
						    
							// Filter peptides:
							// 1.- Only with sn_ratio > 5 and
							// 2.- Peptides with the lower deltaRT
							if (delta_rt_previous != -1 
							 && Math.abs(Double.parseDouble(delta_rt)) < Math.abs(delta_rt_previous) 
							 && sequence.equals(sequence_previous)
							 && dI > dbI) {// Lower delta_rt if there're more than one intensities 
							    				         //for the same peptide)
								String sqlRT = "UPDATE feature SET id='"
										+ id
										+ "',rt="
										+ rt
										+ ",mz="
										+ mz
										+ ",intensity="
										+ intensity
										+ ",charge="
										+ charge
										+ ",fwhm='0',comment='srm ok' WHERE idfile="
										+ idfile_previous + " AND sequence='"
										+ sequence + "'";
								stmt.executeUpdate(sqlRT);
								delta_rt_previous = Double.parseDouble(delta_rt);
								idfile_previous = idfile;
							} else if (delta_rt_previous == -1 
								|| !sequence.equals(sequence_previous)
								&& Double.parseDouble(sn_ratio) > 5) {
								String sqlFeatures = "INSERT INTO feature (idfile,id,rt,mz,intensity,charge,sequence,fwhm,comment,mass_accuracy) VALUES ("
										+ idfile
										+ ",'"
										+ id
										+ "',"
										+ rt
										+ ","
										+ mz
										+ ","
										+ intensity
										+ ","
										+ charge
										+ ",'" + sequence + "','0','srm ok',0)";
								stmt.executeUpdate(sqlFeatures);
								delta_rt_previous = Double.parseDouble(delta_rt);
								idfile_previous = idfile;
								sequence_previous = sequence;
							} else if (delta_rt_previous == -1
								|| !sequence.equals(sequence_previous)
								&& Double.parseDouble(sn_ratio) < 5) {
								String sqlFeatures = "INSERT INTO feature (idfile,id,rt,mz,intensity,charge,sequence,fwhm,comment,mass_accuracy) VALUES ("
										+ idfile
										+ ",'"
										+ id
										+ "',"
										+ rt
										+ ","
										+ mz
										+ ","
										+ intensity
										+ ","
										+ charge
										+ ",'"
										+ sequence
										+ "','0','srm (sn_ratio < 5)',0)";
								stmt.executeUpdate(sqlFeatures);
								delta_rt_previous = Double.parseDouble(delta_rt);
								idfile_previous = idfile;
								sequence_previous = sequence;
							}
						} else {
							//logger.warning("Peptide discarded: sequence "+sequence+", RT "+rt+", M/Z "+mz+" and charge "+charge+".");
						}
					}// if
				}// for

				// INSERT peptides that are in the QC list but not in the
				// featureXML files:
				for (int k = 0; k < peptideList.size(); k++) {
					String qcPeptide = peptideList.get(k);
					String sqlQCpeptide = "SELECT * FROM feature WHERE sequence = '" + qcPeptide + "' AND idfile=" + idfile;
					ResultSet rsQCpeptide;
					rsQCpeptide = stmt.executeQuery(sqlQCpeptide);
					if (!rsQCpeptide.next()) {
						String sqlNotfoundInsert = "INSERT INTO feature (idfile,id,rt,mz,intensity,charge,sequence,fwhm,comment,mass_accuracy) VALUES ("
								+ idfile
								+ ",0,0,0,0,0,'"
								+ qcPeptide
								+ "',0,'srm (not found)',0)";
						stmt.executeUpdate(sqlNotfoundInsert);
					}
				}

			}// end for list features

			stmt.close();
			conn.close();

		} catch (SQLException sqle) {
			logger.warning("Query SQLException ==> " + sqle.getMessage());
			sqle.printStackTrace();
		} catch (Exception e) {
			logger.warning("General DB conn Exception ==> " + e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if (stmt != null) stmt.close();
			} catch (SQLException stmte) {
				logger.warning("Closing SQL Statement exception ==> " + stmte.getMessage());
				stmte.printStackTrace();
			}
			try {
				if (conn != null) conn.close();
			} catch (SQLException conne) {
				logger.warning("Closing SQL Connection exception ==> " + conne.getMessage());
				conne.printStackTrace();
			}
		}// end db conn
	}
	
	private static void insertTTOFeaturesIntoDB(String filename, String qcCode, String qcPeptides, String featureDir, String referenceMZ, String referenceI, String peptidesCharge, String deltaRT, String deltaMZ, String refrt) {

		Connection conn = null;
		Statement stmt = null;
		int idfile = 0;
		String creationdate = "";

		try {
			Class.forName(JDBC_DRIVER);
			conn = DriverManager.getConnection(DB_URL_MAIN, USER_MAIN, PASS_MAIN);
			stmt = conn.createStatement();

			String inputFilename = new File(filename).getName();

			String filename_subtracted_date = filename.substring(filename.lastIndexOf(File.separator),filename.lastIndexOf('-'));

			filename = toppasOutputDir + File.separator + "TOPPAS_out" + File.separator + featureDir + File.separator + filename_subtracted_date + ".featureXML"; 

			if (new File(filename).exists() && !new File(filename).isDirectory()) {

				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document doc = builder.parse(filename);

				XPathFactory xPathfactory = XPathFactory.newInstance();
				XPath xpath = xPathfactory.newXPath();

				XPathExpression expr = xpath.compile("/featureMap/featureList/feature");
				NodeList featureList = (NodeList) expr.evaluate(doc,XPathConstants.NODESET);

				String id = "";
				String rt = "";
				String mz = "";
				String fwhm = "";
				String intensity = "";
				String charge = "";

				List<String> referenceMZlist = Arrays.asList(referenceMZ.split("\\s*,\\s*"));
				List<String> peptidesChargeList = Arrays.asList(peptidesCharge.split("\\s*,\\s*"));
				List<String> referenceIlist = Arrays.asList(referenceI.split("\\s*,\\s*"));
				List<String> peptideList = Arrays.asList(qcPeptides.split("\\s*,\\s*"));
				List<String> refrtList = Arrays.asList(refrt.split("\\s*,\\s*"));

				// SELECT fileid of the current file:
				String sqlIdFile = "SELECT idfile,creationdate FROM file WHERE filename = '" + inputFilename + "'";
				ResultSet rs = stmt.executeQuery(sqlIdFile);
				while (rs.next()) {
					idfile = rs.getInt("idfile");
					creationdate = rs.getString("creationdate");
				}

				// INSERT assigned peptides that match certain conditions:
				for (int i = 0; i < featureList.getLength(); i++) {
					Node feature = featureList.item(i);
					if (feature != null && feature.getNodeType() == Node.ELEMENT_NODE) {
						id = feature.getAttributes().getNamedItem("id").getNodeValue();// feature id
						NodeList featureChildNodes = feature.getChildNodes();
						for (int j = 0; j < featureChildNodes.getLength(); j++) {
							Node featureChild = featureChildNodes.item(j);
							if (featureChild != null && featureChild.getNodeType() == Node.ELEMENT_NODE) {
								if (featureChild.getNodeName().equals("position")) {
									if (featureChild.getAttributes().getNamedItem("dim").getNodeValue().equals("0")) {
										rt = featureChild.getTextContent();// RT
									}
								}
								if (featureChild.getNodeName().equals("position")) {
									if (featureChild.getAttributes().getNamedItem("dim").getNodeValue().equals("1")) {
										mz = featureChild.getTextContent();// M/Z
									}
								}
								if (featureChild.getNodeName().equals("charge")) {
									charge = featureChild.getTextContent();// charge
								}
								if (featureChild.getNodeName().equals("intensity")) {
									intensity = featureChild.getTextContent();// intensity
								}
								if (featureChild.getNodeName().equals("userParam")) {
									if (featureChild.getAttributes().getNamedItem("name").getNodeValue().equals("FWHM")) {
										fwhm = featureChild.getAttributes().getNamedItem("value").getNodeValue();// fwhm
									}
								}
							}
						}

						for (int k = 0; k < referenceMZlist.size(); k++) {
						    
							double dRefMZ = Double.parseDouble(referenceMZlist.get(k));
							double dMz = Double.parseDouble(mz);
							double mass_accuracy_ppm = (Math.abs(dMz - dRefMZ) / dRefMZ) * 1000000;
							double mass_accuracy_ppm_relative = ((dMz - dRefMZ) / dRefMZ) * 1000000;
							double dDeltaMZ = Double.parseDouble(deltaMZ);
							double dI = Double.parseDouble(intensity);
							double intensity_threshold = Double.parseDouble(referenceIlist.get(k));
							double dRT = Double.parseDouble(rt);
							double dDeltaRT = Double.parseDouble(deltaRT);
							String sequence = peptideList.get(k);
							
							if (charge.equals(peptidesChargeList.get(k))
					                 && mass_accuracy_ppm <= dDeltaMZ 
							 && dI >= intensity_threshold) { 
							    
								double dRefRT = Double.parseDouble(refrtList.get(k));
								
								if (dRefRT != 0 && dRT >= (dRefRT - dDeltaRT)&& dRT <= (dRefRT + dDeltaRT)) {
									String sqlFeatureSeq = "SELECT * FROM feature WHERE sequence = '"
											+ sequence
											+ "' AND idfile="
											+ idfile;
									ResultSet rsFeatureSeq;
									rsFeatureSeq = stmt.executeQuery(sqlFeatureSeq);
									double dbRT = 0;//database RT
									double dbI = 0;//database I
									boolean toUpdate = false;
									boolean doNothing = false;
									while (rsFeatureSeq.next()) {
										dbRT = rsFeatureSeq.getDouble("rt");
										dbI = rsFeatureSeq.getDouble("intensity");
										if (Math.abs(dRT - dRefRT) < Math.abs(dbRT - dRefRT) && dI > dbI) {//10
											toUpdate = true;
										} else {
											doNothing = true;
										}
									}
									if (!toUpdate && !doNothing) {
										logger.info("INSERTING peptide with sequence "+ sequence + ".");
										String sqlFeatures = "INSERT INTO feature (idfile,id,rt,mz,intensity,charge,sequence,fwhm,comment,mass_accuracy) VALUES ("
												+ idfile
												+ ",'"
												+ id
												+ "',"
												+ rt
												+ ","
												+ mz
												+ ","
												+ intensity
												+ ","
												+ charge
												+ ",'"
												+ sequence
												+ "',"
												+ fwhm
												+ ",'univocal',"
												+ mass_accuracy_ppm_relative
												+ ")";
										stmt.executeUpdate(sqlFeatures);
									} else if (toUpdate && !doNothing) {
										logger.info("UPDATING peptide with sequence "
												+ sequence + ".");
										String sqlRT = "UPDATE feature SET id='"
												+ id
												+ "',rt="
												+ rt
												+ ",mz="
												+ mz
												+ ",intensity="
												+ intensity
												+ ",charge="
												+ charge
												+ ",fwhm="
												+ fwhm
												+ ",comment='mindist' WHERE idfile="
												+ idfile
												+ " AND sequence='"
												+ sequence + "'";
										stmt.executeUpdate(sqlRT);
									}
								} else if (dRefRT == 0) {
									logger.info("INSERTING peptide with sequence " + sequence + " and RT=0.");
									String sqlFeatures = "INSERT INTO feature (idfile,id,rt,mz,intensity,charge,sequence,fwhm,comment,mass_accuracy) VALUES ("
											+ idfile
											+ ",'"
											+ id
											+ "',"
											+ rt
											+ ","
											+ mz
											+ ","
											+ intensity
											+ ","
											+ charge
											+ ",'"
											+ sequence
											+ "',"
											+ fwhm
											+ ",'rt=0',"
											+ mass_accuracy_ppm_relative
											+ ")";
									stmt.executeUpdate(sqlFeatures);
								}									
							} else {
								// logger.warning("Peptide discarded: sequence "+sequence+", RT "+rt+", M/Z "+mz+" and charge "+charge+". Reference RT: "+referenceRTlist.get(indexPepSeq)+" Reference MZ: "+referenceMZlist.get(indexPepSeq));
							}						    
						}
							
					}// if
				}// for
				
				// INSERT peptides that are in the QC list but not in the
				// featureXML files:
				for (int k = 0; k < peptideList.size(); k++) {
					String qcPeptide = peptideList.get(k);
					String sqlQCpeptide = "SELECT * FROM feature WHERE sequence = '" + qcPeptide + "' AND idfile=" + idfile;
					ResultSet rsQCpeptide;
					rsQCpeptide = stmt.executeQuery(sqlQCpeptide);
					if (!rsQCpeptide.next()) {
						String sqlNotfoundInsert = "INSERT INTO feature (idfile,id,rt,mz,intensity,charge,sequence,fwhm,comment,mass_accuracy) VALUES ("
								+ idfile
								+ ",0,0,0,0,0,'"
								+ qcPeptide
								+ "',0,'srm (not found)',0)";
						stmt.executeUpdate(sqlNotfoundInsert);
					}
				}

			}// end for list features

			stmt.close();
			conn.close();

		} catch (SQLException sqle) {
			logger.warning("Query SQLException ==> " + sqle.getMessage());
			sqle.printStackTrace();
		} catch (Exception e) {
			logger.warning("General DB conn Exception ==> " + e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if (stmt != null) stmt.close();
			} catch (SQLException stmte) {
				logger.warning("Closing SQL Statement exception ==> " + stmte.getMessage());
				stmte.printStackTrace();
			}
			try {
				if (conn != null) conn.close();
			} catch (SQLException conne) {
				logger.warning("Closing SQL Connection exception ==> " + conne.getMessage());
				conne.printStackTrace();
			}
		}// end db conn
	}
	
	private static void insertFeaturesIntoDB(String filename,String qcPeptides, String referenceI, String referenceMZ,String peptidesCharge, String deltaRT, String deltaMZ, String qcCode, String isPhospho, String featureDir, String idxmlDir) {

		Connection conn = null;
		Statement stmt = null;
		int idfile = 0;
		String creationdate = "";
		boolean bIsPhospho = Boolean.parseBoolean(isPhospho);

		try {
			Class.forName(JDBC_DRIVER);
			conn = DriverManager.getConnection(DB_URL_MAIN, USER_MAIN, PASS_MAIN);
			stmt = conn.createStatement();

			String qcmlFilename = new File(filename).getName();
			String idxmlFilename = "";

			String filename_subtracted_date = filename.substring(filename.lastIndexOf(File.separator),filename.lastIndexOf('-'));

			filename = toppasOutputDir + File.separator + "TOPPAS_out" + File.separator + featureDir + File.separator + filename_subtracted_date + ".featureXML"; 
			idxmlFilename = toppasOutputDir + File.separator + "TOPPAS_out" + File.separator + idxmlDir + File.separator + filename_subtracted_date + ".idXML";

			if (new File(filename).exists() && !new File(filename).isDirectory()) { 

				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document doc = builder.parse(filename);

				XPathFactory xPathfactory = XPathFactory.newInstance();
				XPath xpath = xPathfactory.newXPath();

				XPathExpression expr = xpath.compile("/featureMap/featureList/feature");
				NodeList featureList = (NodeList) expr.evaluate(doc,XPathConstants.NODESET);

				String id = "";
				String rt = "";
				String mz = "";
				String intensity = "";
				String charge = "";
				String fwhm = "";
				String sequence = "";

				ArrayList<String> sequenceList = new ArrayList<String>();
				List<String> peptideList = Arrays.asList(qcPeptides.split("\\s*,\\s*"));
				List<String> referenceIlist = Arrays.asList(referenceI.split("\\s*,\\s*"));
				List<String> referenceMZlist = Arrays.asList(referenceMZ.split("\\s*,\\s*"));
				List<String> peptidesChargeList = Arrays.asList(peptidesCharge.split("\\s*,\\s*"));

				//Closer M/Z search mode (for phosphopeptides):
				List<String> arrayCloserFeatureID = new ArrayList<String>();
				List<String> arrayCloserRT = new ArrayList<String>();
				List<String> arrayCloserMZ = new ArrayList<String>();
				List<String> arrayCloserIntensity = new ArrayList<String>();
				List<String> arrayCloserFWHM = new ArrayList<String>();
				List<String> arrayCloserMassAcc = new ArrayList<String>();
				//Initialize arrayCloserMZ: 
				for(int d = 0; d < referenceMZlist.size(); d++){
				    arrayCloserMZ.add(d, "0");
				    arrayCloserFeatureID.add(d, "0");
				    arrayCloserRT.add(d, "0");
				    arrayCloserIntensity.add(d, "0");
				    arrayCloserFWHM.add(d, "0");
				    arrayCloserMassAcc.add(d, "0");
				}
				
				// SELECT fileid of the current file:
				String sqlIdFile = "SELECT idfile,creationdate FROM file WHERE filename = '" + qcmlFilename + "'";
				ResultSet rs = stmt.executeQuery(sqlIdFile);
				while (rs.next()) {
					idfile = rs.getInt("idfile");
					creationdate = rs.getString("creationdate");
				}
				
				// FOR each feature of featureXML file: 
				for (int i = 0; i < featureList.getLength(); i++) {
				    	
				    	//Initialization: 
				    	int indexPepSeq = -1;
				    	sequenceList.clear();
					
				    	Node feature = featureList.item(i);
					if (feature != null && feature.getNodeType() == Node.ELEMENT_NODE) {
						id = feature.getAttributes().getNamedItem("id").getNodeValue();// feature id
						NodeList featureChildNodes = feature.getChildNodes();
						for (int j = 0; j < featureChildNodes.getLength(); j++) {
							Node featureChild = featureChildNodes.item(j);
							if (featureChild != null && featureChild.getNodeType() == Node.ELEMENT_NODE) {
								if (featureChild.getNodeName().equals("position")) {
									if (featureChild.getAttributes().getNamedItem("dim").getNodeValue().equals("0")) {
										rt = featureChild.getTextContent();// RT
									}
								}
								if (featureChild.getNodeName().equals("position")) {
									if (featureChild.getAttributes().getNamedItem("dim").getNodeValue().equals("1")) {
										mz = featureChild.getTextContent();// M/Z
									}
								}
								if (featureChild.getNodeName().equals("intensity")) {
									intensity = featureChild.getTextContent();// intensity
								}
								if (featureChild.getNodeName().equals("charge")) {
									charge = featureChild.getTextContent();// charge
								}
								if (featureChild.getNodeName().equals("userParam")) {
									if (featureChild.getAttributes().getNamedItem("name").getNodeValue().equals("FWHM")) {
										fwhm = featureChild.getAttributes().getNamedItem("value").getNodeValue();// fwhm
									}
								}
								if (featureChild.getNodeName().equals("PeptideIdentification")) {// sequence
									NodeList peptideHit = featureChild.getChildNodes();
									for (int k = 0; k < peptideHit.getLength(); k++) {
										Node peptideHitNode = peptideHit.item(k);
										if (peptideHitNode != null && peptideHitNode.getNodeType() == Node.ELEMENT_NODE) {
											if (peptideHitNode.getAttributes().getNamedItem("sequence") != null) {
												sequence = peptideHitNode.getAttributes().getNamedItem("sequence").getNodeValue();
												sequenceList.add(sequence);// sometimes there's more than one PeptideIdentification inside a feature!
											}
										}
									}
								}// if sequence
							}
						}
						
						if(id.equals("f_14941286097937321058")){
						    System.out.println("stop");
						}
						
						//NORMAL SEARCH MODE (RT): 
						if(!bIsPhospho){
							// Checks and filters:
							// 1.- qcPeptides, referenceMZ and peptidesCharge
							// arrays qcml2db.properties have the same size.
							// 2.- When there's more than one
							// <PeptideIdentification> inside a <feature>, take only
							// the QC sequence.
							// 3.- The peptide is in the list of QC peptides.
							// 4.- The peptide has the same charge
							// Filter order: M/Z, I and RT:
							// 5.- The peptide has a M/Z between the M/Z tolerance.
							// 6.- The peptide has an intensity equal or above the
							// intensity threshold.
							// 7.- Now the RT filter is quite complex. First search RefRT at idXML: 
								// 8.- If RefRT is not found in the idXML file, search for the more recent RT <> 0 in the DB.
						    		// 9.- If RefRT is found and is not 0, get it and check if the current feature dRT is inside the window: RT >= (RefRT-DeltaRT) && RT <= (RefRT+DeltaRT)
						    			// 10.- If dRT it's inside the window, check if there's already another feature in the 
						    			//      db with the same sequence and Math.abs(dRT - dRefRT) < Math.abs(dbRT - dRefRT) and check if the feature 
						    			//      intensity is bigger than the intensity in the DB (to avoid data SPIKES):
						    					//11.- If current dRT is closer from dRefRT than the one in the database, update.
						    					//12.- If current dRT is NOT closer from dRefRT than the one in the database. do nothing (leave the one in the db).
						    					//13.- If current dRT is not present in the database, just INSERT it.
						    			// 14.- If it's OUTSIDE the window, it will be after in the algorithm classified as "unassigned". 
						    
							if (peptideList.size() == referenceMZlist.size() 
							 && referenceIlist.size() == referenceMZlist.size() 
							 && peptidesChargeList.size() == referenceMZlist.size()) {// (1)
							    
							    if (indexPepSeq == -1) {
								for (int n = 0; n < sequenceList.size(); n++) {// (2)
							    		if (peptideList.contains(sequenceList.get(n))) {
							    		    sequence = sequenceList.get(n);
									}
							    	}
							    	indexPepSeq = peptideList.indexOf(sequence);
							    }
							    
							    if (indexPepSeq == -1) { 
								    for (int z = 0; z < peptideList.size(); z++) {
									double dRefMZ_feature =  Double.parseDouble(referenceMZlist.get(z));
									double dMz_feature = Double.parseDouble(mz);
									double dDeltaMZ_feature = Double.parseDouble(deltaMZ);
									double mass_accuracy_ppm_feature = (Math.abs(dMz_feature - dRefMZ_feature) / dRefMZ_feature) * 1000000;
									if(mass_accuracy_ppm_feature <= dDeltaMZ_feature){
									    indexPepSeq = z;
									    sequence = peptideList.get(indexPepSeq);
									}
								    }
							    }
							 
							    if (indexPepSeq != -1) {// (3)
								
									double dRefMZ = Double.parseDouble(referenceMZlist.get(indexPepSeq));
									double dMz = Double.parseDouble(mz);
									double mass_accuracy_ppm = (Math.abs(dMz - dRefMZ) / dRefMZ) * 1000000;
									double mass_accuracy_ppm_relative = ((dMz - dRefMZ) / dRefMZ) * 1000000;
									double dDeltaMZ = Double.parseDouble(deltaMZ);
									double dI = Double.parseDouble(intensity);
									double intensity_threshold = Double.parseDouble(referenceIlist.get(indexPepSeq));
									double dRT = Double.parseDouble(rt);
									double dDeltaRT = Double.parseDouble(deltaRT);
									
									if (charge.equals(peptidesChargeList.get(indexPepSeq))// (4)
							                 && mass_accuracy_ppm <= dDeltaMZ // (5)
									 && dI >= intensity_threshold) { // (6)
									    
										double dRefRT = 0;
										String RT_from_idxml = getRTfromIDxml(sequence, idxmlFilename, dRT);
										if (!RT_from_idxml.equals("")) {// (7)
											dRefRT = Double.parseDouble(RT_from_idxml);
										} else if (RT_from_idxml.equals("")) {// (8)
											String sqlBeforeRT = "SELECT rt,filename, creationdate FROM feature JOIN file ON feature.idfile=file.idfile WHERE feature.sequence = '"+sequence+"' AND file.creationdate < '"+creationdate+"' AND rt <> 0 AND filename LIKE '%"+qcCode+"%' LIMIT 1";
											ResultSet rs1 = stmt.executeQuery(sqlBeforeRT);
											while (rs1.next()) {
												dRefRT = Double.parseDouble(rs1.getString("rt"));
											}
										}
										if (dRefRT != 0 && dRT >= (dRefRT - dDeltaRT)&& dRT <= (dRefRT + dDeltaRT)) {// (9)
											String sqlFeatureSeq = "SELECT * FROM feature WHERE sequence = '"
													+ sequence
													+ "' AND idfile="
													+ idfile;
											ResultSet rsFeatureSeq;
											rsFeatureSeq = stmt.executeQuery(sqlFeatureSeq);
											double dbRT = 0;//database RT
											double dbI = 0;//database I
											boolean toUpdate = false;
											boolean doNothing = false;
											while (rsFeatureSeq.next()) {
												dbRT = rsFeatureSeq.getDouble("rt");
												dbI = rsFeatureSeq.getDouble("intensity");
												if (Math.abs(dRT - dRefRT) < Math.abs(dbRT - dRefRT) && dI > dbI) {//10
													toUpdate = true;//11
												} else {
													doNothing = true;//12
												}
											}
											if (!toUpdate && !doNothing) {//13
												logger.info("INSERTING peptide with sequence "+ sequence + ".");
												String sqlFeatures = "INSERT INTO feature (idfile,id,rt,mz,intensity,charge,sequence,fwhm,comment,mass_accuracy) VALUES ("
														+ idfile
														+ ",'"
														+ id
														+ "',"
														+ rt
														+ ","
														+ mz
														+ ","
														+ intensity
														+ ","
														+ charge
														+ ",'"
														+ sequence
														+ "',"
														+ fwhm
														+ ",'univocal',"
														+ mass_accuracy_ppm_relative
														+ ")";
												stmt.executeUpdate(sqlFeatures);
											} else if (toUpdate && !doNothing) {
												logger.info("UPDATING peptide with sequence "
														+ sequence + ".");
												String sqlRT = "UPDATE feature SET id='"
														+ id
														+ "',rt="
														+ rt
														+ ",mz="
														+ mz
														+ ",intensity="
														+ intensity
														+ ",charge="
														+ charge
														+ ",fwhm="
														+ fwhm
														+ ",comment='mindist' WHERE idfile="
														+ idfile
														+ " AND sequence='"
														+ sequence + "'";
												stmt.executeUpdate(sqlRT);
											}
										} else if (dRefRT == 0) {//13
											String sqlFeatureSeq = "SELECT * FROM feature WHERE sequence = '"
												+ sequence
												+ "' AND idfile="
												+ idfile;
            										ResultSet rsFeatureSeq;
            										rsFeatureSeq = stmt.executeQuery(sqlFeatureSeq);
            										double dbI = 0;//database I
            										boolean toUpdate = false;
            										boolean doNothing = false;
            										while (rsFeatureSeq.next()) {
            											dbI = rsFeatureSeq.getDouble("intensity");
            											if (dI > dbI) {//10
            												toUpdate = true;//11
            											} else {
            												doNothing = true;//12
            											}
            										}
            										if (!toUpdate && !doNothing) {//13
            											logger.info("INSERTING peptide with sequence "+ sequence + ".");
            											String sqlFeatures = "INSERT INTO feature (idfile,id,rt,mz,intensity,charge,sequence,fwhm,comment,mass_accuracy) VALUES ("
            													+ idfile
            													+ ",'"
            													+ id
            													+ "',"
            													+ rt
            													+ ","
            													+ mz
            													+ ","
            													+ intensity
            													+ ","
            													+ charge
            													+ ",'"
            													+ sequence
            													+ "',"
            													+ fwhm
            													+ ",'univocal',"
            													+ mass_accuracy_ppm_relative
            													+ ")";
            											stmt.executeUpdate(sqlFeatures);
            										} else if (toUpdate && !doNothing) {
            											logger.info("UPDATING peptide with sequence "
            													+ sequence + ".");
            											String sqlRT = "UPDATE feature SET id='"
            													+ id
            													+ "',rt="
            													+ rt
            													+ ",mz="
            													+ mz
            													+ ",intensity="
            													+ intensity
            													+ ",charge="
            													+ charge
            													+ ",fwhm="
            													+ fwhm
            													+ ",comment='mindist' WHERE idfile="
            													+ idfile
            													+ " AND sequence='"
            													+ sequence + "'";
            											stmt.executeUpdate(sqlRT);
            										}
										}									
									} else {
										// logger.warning("Peptide discarded: sequence "+sequence+", RT "+rt+", M/Z "+mz+" and charge "+charge+". Reference RT: "+referenceRTlist.get(indexPepSeq)+" Reference MZ: "+referenceMZlist.get(indexPepSeq));
									}
							}
							} else {
								logger.warning("==> Size of peptide lists are not equal. Please check qcml2db.properties.");
							}	
							
						//PHOSPHO SEARCH MODE (CLOSER MZ):
						} else if(bIsPhospho) {
							if (peptideList.size() == referenceMZlist.size() 
							 && referenceIlist.size() == referenceMZlist.size() 
							 && peptidesChargeList.size() == referenceMZlist.size()) {
								
							     double dMz = Double.parseDouble(mz);
							     double dDeltaMZ = Double.parseDouble(deltaMZ);
							     double dI = Double.parseDouble(intensity);
							     double dRT = Double.parseDouble(rt);
							     double dDeltaRT = Double.parseDouble(deltaRT);
									
							     //1.- For a given MZ, search closer MZ in referenceMZlist: 
							     double distance = 0;
							     int closerIndex = 0;
							     for(int c = 0; c < referenceMZlist.size(); c++){
								 double cdistance = Math.abs(Double.parseDouble(referenceMZlist.get(c)) - dMz);
								 if(c == 0){
								     distance = cdistance;
								     closerIndex = c;
								 } else {
								     if(cdistance < distance){
									 closerIndex = c;
									 distance = cdistance;
								     }
								 }
							     }
								
							     //2.- Get closer MZ value: 
							     double dCloserRefMZ = Double.parseDouble(referenceMZlist.get(closerIndex));
							     double dCloserMZ = Double.parseDouble(arrayCloserMZ.get(closerIndex));
							     
							     //3.- Replace value at arrayCloserMZ if it's closer to dMz:
							     if(Math.abs(dMz - dCloserRefMZ) < Math.abs(dCloserMZ - dCloserRefMZ)){
								 //Insert MZ et al.: 
								 double mass_accuracy_ppm = (Math.abs(dMz - dCloserRefMZ) / dCloserRefMZ) * 1000000;
						                 double intensity_threshold = Double.parseDouble(referenceIlist.get(closerIndex));
								 if(mass_accuracy_ppm <= dDeltaMZ
							         && dI >= intensity_threshold
							         && charge.equals(peptidesChargeList.get(closerIndex))){
						                     arrayCloserMZ.set(closerIndex, String.valueOf(dMz));
						                     arrayCloserFeatureID.set(closerIndex, id);
								     arrayCloserRT.set(closerIndex, String.valueOf(dRT));
								     arrayCloserIntensity.set(closerIndex, String.valueOf(dI));
								     arrayCloserFWHM.set(closerIndex, String.valueOf(fwhm));
								     arrayCloserMassAcc.set(closerIndex, String.valueOf(mass_accuracy_ppm));
						                 }
							     }
							}
						}
					}
				}// end for list features (for)
				
				//INSERT PHOSPHOpeptides:
				if(bIsPhospho){
				    for(int m = 0; m < peptideList.size(); m++){
					String sequenceToInsert = peptideList.get(m);
					logger.info("INSERTING PHOSPHOpeptide with sequence "+ sequenceToInsert + ".");
					String sqlFeatures = "INSERT INTO feature (idfile,id,rt,mz,intensity,charge,sequence,fwhm,comment,mass_accuracy) VALUES ("
							+ idfile
							+ ",'"
							+ arrayCloserFeatureID.get(m)
							+ "',"
							+ arrayCloserRT.get(m)
							+ ","
							+ arrayCloserMZ.get(m)
							+ ","
							+ arrayCloserIntensity.get(m)
							+ ","
							+ peptidesChargeList.get(m)
							+ ",'"
							+ sequenceToInsert
							+ "',"
							+ arrayCloserFWHM.get(m)
							+ ",'phospho',"
							+ arrayCloserMassAcc.get(m)
							+ ")";
					stmt.executeUpdate(sqlFeatures);					
				    }
				}

				if(!bIsPhospho){
					// INSERT unassigned peptides:
					String UnassignedSequence = "";
					String UnassignedCharge = "";
					expr = xpath.compile("/featureMap/UnassignedPeptideIdentification/PeptideHit");
					NodeList UnassignedPeptideList = (NodeList) expr.evaluate(doc,XPathConstants.NODESET);
					for (int i = 0; i < UnassignedPeptideList.getLength(); i++) {
						Node UnassignedPeptide = UnassignedPeptideList.item(i);
						if (UnassignedPeptide != null && UnassignedPeptide.getNodeType() == Node.ELEMENT_NODE) {
							UnassignedSequence = UnassignedPeptide.getAttributes().getNamedItem("sequence").getNodeValue();
							UnassignedCharge = UnassignedPeptide.getAttributes().getNamedItem("charge").getNodeValue();
							int indexUnassignedSequence = peptideList.indexOf(UnassignedSequence);
							if (indexUnassignedSequence != -1) {
								if (peptidesChargeList.get(indexUnassignedSequence).equals(UnassignedCharge)) {
									String sqlDuplicatePeptide = "SELECT count(*) as duplicate FROM feature WHERE sequence = '"
											+ UnassignedSequence
											+ "' AND idfile="
											+ idfile;
									ResultSet rsSqlDuplicatePeptide;
									rsSqlDuplicatePeptide = stmt.executeQuery(sqlDuplicatePeptide);
									while (rsSqlDuplicatePeptide.next()) {
										if (rsSqlDuplicatePeptide.getDouble("duplicate") == 0) {// if there are not duplicates, insert
											logger.info("INSERTING peptide with sequence " + UnassignedSequence + " and UNASSIGNED.");
											String sqlUnasigned = "INSERT INTO feature (idfile,id,rt,mz,intensity,charge,sequence,fwhm,comment,mass_accuracy) VALUES ("
													+ idfile
													+ ",'',0,0,0,"
													+ UnassignedCharge
													+ ",'"
													+ UnassignedSequence
													+ "',0,'unassigned',0)";
											stmt.executeUpdate(sqlUnasigned);
										} else {
											logger.info("SKIPPING duplicate peptide with sequence " + sequence + ".");
										}
										break;
									}
								}
							}
						}
					}//

					// INSERT not found or out of range (rt or mass) peptides:
					for (int k = 0; k < peptideList.size(); k++) {
						String peptideNotFound = peptideList.get(k);
						String sqlNotfound = "SELECT * FROM feature WHERE sequence = '"
								+ peptideNotFound + "' AND idfile=" + idfile;
						ResultSet rsNotFound;
						rsNotFound = stmt.executeQuery(sqlNotfound);
						if (!rsNotFound.next()) {
							expr = xpath
									.compile("/featureMap/featureList/feature/PeptideIdentification/PeptideHit[@sequence='"
											+ peptideNotFound + "']");
							NodeList notfoundPeptideList = (NodeList) expr
									.evaluate(doc, XPathConstants.NODESET);
							if (notfoundPeptideList.getLength() == 0) {
								String sqlDuplicatePeptide = "SELECT count(*) as duplicate FROM feature WHERE sequence = '"
										+ peptideNotFound
										+ "' AND idfile="
										+ idfile;
								ResultSet rsSqlDuplicatePeptide;
								rsSqlDuplicatePeptide = stmt
										.executeQuery(sqlDuplicatePeptide);
								while (rsSqlDuplicatePeptide.next()) {
									if (rsSqlDuplicatePeptide
											.getDouble("duplicate") == 0) {// if
																			// there
																			// aren't
																			// duplicates,
																			// then
																			// INSERT
										logger.info("INSERTING peptide with sequence "
												+ peptideNotFound
												+ " and NOT FOUND.");
										String sqlNotfoundInsert = "INSERT INTO feature (idfile,id,rt,mz,intensity,charge,sequence,fwhm,comment,mass_accuracy) VALUES ("
												+ idfile
												+ ",'',0,0,0,0,'"
												+ peptideNotFound
												+ "',0,'not found',0)";
										stmt.executeUpdate(sqlNotfoundInsert);
									} else {
										logger.info("SKIPPING duplicate peptide with sequence "
												+ peptideNotFound + ".");
									}
									break;
								}
							} else {// if we assume that all not found peptides are
									// out of range...
								String sqlDuplicatePeptide = "SELECT count(*) as duplicate FROM feature WHERE sequence = '"
										+ peptideNotFound
										+ "' AND idfile="
										+ idfile;
								ResultSet rsSqlDuplicatePeptide;
								rsSqlDuplicatePeptide = stmt
										.executeQuery(sqlDuplicatePeptide);
								while (rsSqlDuplicatePeptide.next()) {
									if (rsSqlDuplicatePeptide
											.getDouble("duplicate") == 0) {// if
																			// there
																			// aren't
																			// duplicates,
																			// then
																			// INSERT
										logger.info("INSERTING peptide with sequence "
												+ peptideNotFound
												+ " and OUT OF RANGE.");
										String sqlNotfoundInsert = "INSERT INTO feature (idfile,id,rt,mz,intensity,charge,sequence,fwhm,comment,mass_accuracy) VALUES ("
												+ idfile
												+ ",'',0,0,0,0,'"
												+ peptideNotFound
												+ "',0,'out of range (either RT, m/z or I)',0)";
										stmt.executeUpdate(sqlNotfoundInsert);
									} else {
										logger.info("SKIPPING duplicate peptide with sequence "
												+ peptideNotFound + ".");
									}
									break;
								}
							}
						}
					}//for				    
				}

			} else {
				logger.warning("==> The file "
						+ new File(filename).getAbsolutePath()
						+ " does not exist. Please check configuration.properties.");
			}

			stmt.close();
			conn.close();

		} catch (SQLException sqle) {
			logger.warning("Query SQLException ==> " + sqle.getMessage());
			sqle.printStackTrace();
		} catch (Exception e) {
			logger.warning("General DB conn Exception ==> " + e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if (stmt != null) stmt.close();
			} catch (SQLException stmte) {
				logger.warning("Closing SQL Statement exception ==> " + stmte.getMessage());
				stmte.printStackTrace();
			}
			try {
				if (conn != null) conn.close();
			} catch (SQLException conne) {
				logger.warning("Closing SQL Connection exception ==> " + conne.getMessage());
				conne.printStackTrace();
			}
		}// end db conn
	}
	
	private static void insertCountPhosphoIntoDB(String filename,String qcPeptides, String referenceI, String referenceMZ, String peptidesCharge, String deltaRT, String deltaMZ, String qcCode, String idxmlDir) {

		Connection conn = null;
		Statement stmt = null;
		int idfile = 0;
		String creationdate = "";

		try {
			Class.forName(JDBC_DRIVER);
			conn = DriverManager.getConnection(DB_URL_MAIN, USER_MAIN, PASS_MAIN);
			stmt = conn.createStatement();

			String qcmlFilename = new File(filename).getName();
			String idxmlFilename = "";
			String filename_subtracted_date = filename.substring(filename.lastIndexOf(File.separator),filename.lastIndexOf('-'));
			
			idxmlFilename = toppasOutputDir + File.separator + "TOPPAS_out" + File.separator + idxmlDir + File.separator + filename_subtracted_date + ".idXML";

			if (new File(idxmlFilename).exists() && !new File(idxmlFilename).isDirectory()) {
				try {
					logger.info("Searching phosphopeptides at " + idxmlFilename + "...");
					DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
					DocumentBuilder builder = factory.newDocumentBuilder();
					Document doc = builder.parse(idxmlFilename);

					XPathFactory xPathfactory = XPathFactory.newInstance();
					XPath xpath = xPathfactory.newXPath();

					XPathExpression expr = xpath.compile("/IdXML/IdentificationRun/PeptideIdentification");
					NodeList idList = (NodeList) expr.evaluate(doc,XPathConstants.NODESET);

					String sequence = "";
					int numPhospho = 0;

					// SELECT fileid of the current file:
					String sqlIdFile = "SELECT idfile,creationdate FROM file WHERE filename = '" + qcmlFilename + "'";
					ResultSet rs = stmt.executeQuery(sqlIdFile);
					while (rs.next()) {
						idfile = rs.getInt("idfile");
						creationdate = rs.getString("creationdate");
					}

					int numTotalPeptides = idList.getLength();

					for (int i = 0; i < numTotalPeptides; i++) {
						Node PeptideIdentification = idList.item(i);
						if (PeptideIdentification != null && PeptideIdentification.getNodeType() == Node.ELEMENT_NODE) {
							NodeList PeptideHitNodes = PeptideIdentification.getChildNodes();
							for (int j = 0; j < PeptideHitNodes.getLength(); j++) {
								Node PeptideHit = PeptideHitNodes.item(j);
								if (PeptideHit != null && PeptideHit.getNodeType() == Node.ELEMENT_NODE && PeptideHit.getNodeName().equals("PeptideHit")) {
									sequence = PeptideHit.getAttributes().getNamedItem("sequence").getNodeValue();
									if (sequence.toLowerCase().contains("(Phospho)".toLowerCase())) {
										numPhospho++;
									}
								}
							}
						}
					}

					String sqlPhospho = "INSERT INTO phospho (idfile,numphospho,numtotal) VALUES ("
							+ idfile
							+ ","
							+ numPhospho
							+ ","
							+ numTotalPeptides + ")";
					stmt.executeUpdate(sqlPhospho);

				} catch (Exception e) {
					logger.warning("==> " + e.getMessage());
					e.printStackTrace();
				}

			} else {
				logger.warning("==> The file " + idxmlFilename
						+ " does not exist.");
			}

			stmt.close();
			conn.close();

		} catch (SQLException sqle) {
			logger.warning("Query SQLException ==> " + sqle.getMessage());
			sqle.printStackTrace();
		} catch (Exception e) {
			logger.warning("General DB conn Exception ==> " + e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if (stmt != null) stmt.close();
			} catch (SQLException stmte) {
				logger.warning("Closing SQL Statement exception ==> " + stmte.getMessage());
				stmte.printStackTrace();
			}
			try {
				if (conn != null) conn.close();
			} catch (SQLException conne) {
				logger.warning("Closing SQL Connection exception ==> " + conne.getMessage());
				conne.printStackTrace();
			}
		}// end db conn
	}

} // class
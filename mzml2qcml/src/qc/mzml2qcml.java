package qc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * 
 * @author Roger Olivella
 * 
 * Created: 08/06/2015
 * 
 * Description: Converts from mzML to qcML by using OpenMS ExecutePipeline.
 * 
 * Major modif.: 16/06/2015: splitted into mzxml2qcml and qcml2db packages.
 *               31/07/2015: include BSA, an other types of QC samples.
 *               19/01/2015: include SRM processing (qtrap)
 *               20/02/2017: adapted to get some key parameters from a db instead of the .properties file. 
 *               	     Refactor: mzxml2qcml to mzml2qcml
 *               	     Code cleaning and optimization
 *               03/03/2017: now process only the date coming from one node
 *
 */

public class mzml2qcml {
	
    	//Hardcodes: 
    	public static String propertiesFilename = "mzml2qcml.properties";
    	public static String mzmlExt = "mzML";
    	
    	//Var. def.: 
    	public static Logger logger = Logger.getLogger("MyLog");	
    	public static Connection conn = null;
    	public static Statement stmt = null;
	public static String JDBC_DRIVER, DB_URL, USER,PASS;
	public static List<String> instrumentDirsList = new ArrayList<String>();
	public static List<String> finalDataDirList = new ArrayList<String>();
	public static List<String> startDataDirList = new ArrayList<String>();
	public static List<String> qcPatternList = new ArrayList<String>();
	public static List<String> startFileExtList = new ArrayList<String>();
	public static List<String> finalFileExtList = new ArrayList<String>();
	public static List<String> toppasWorkflowOutputList = new ArrayList<String>();
	public static List<String> mzmlFolderList = new ArrayList<String>();
	public static String isLocal, minFileSize, minFileAge, insertToDB, forceDateDir, 
	inputDir, outputDir, instrumentDirs, startDataDir, finalDataDir, 
	startFileExt, finalFileExt, mzmlFolder, toppasWorkflowOutput, logDir, trfDir, isLinux, toppasWorkflowDir,
	toppasOutputDir, qcCode, pathBinExecutePipeline, numJobs, openmsVer, forceNode;
	public static boolean processFilesResult = false;
	
	public static void main(String[] args) {
	    
		if(args.length == 2){//check JAR arguments
		    
		    	getProperties(args[0]+File.separator+propertiesFilename);
		    	forceNode = args[1];
		    	getDBparameters_VM(DB_URL,USER,PASS);
		    
		    	configureLogFile(logDir);
		    	List<String> dateDirsList = getInputDirs(forceDateDir);
		    
			//Start qcML generation: 
			logger.info("############## START ##############");
			if(dateDirsList.size() > 0) {
				for(int i = 0; i < dateDirsList.size(); i++){//for each date dir (1702, 1703)...
					logger.info("Step 1 - Getting non-processed mzMLs filenames...");
					ArrayList<String> fileNamesToProcess = getFilesToProcess(dateDirsList.get(i));
					if(fileNamesToProcess.size() > 0){
						for(int j = 0; j < fileNamesToProcess.size(); j++){//for each filename...
							logger.info("Processing "+fileNamesToProcess.get(j)+" file...");
							logger.info("Step 2 - OpenMS "+openmsVer+" processing");
							logger.info("Step 2.1 - OpenMS - Modifying .trf configuration file...");
							qcCode = modifyTRF(trfDir,dateDirsList.get(i),fileNamesToProcess.get(j));		
							logger.info("Step 2.2 - OpenMS - Launching ExecutePipeline (OpenMS) to create the qcML file...");
							processFilesResult = processFiles(dateDirsList.get(i),fileNamesToProcess.get(j));
							//cleanToppasOutputDir(qcCode);
							if(processFilesResult){
								logger.info("Finished processing file "+fileNamesToProcess.get(j)+".");
							} else {
								logger.warning("The file "+fileNamesToProcess.get(j)+" was not processed because is already being processed right now (ExecutePipeline running on the server) or there's another qcml file with the same name and size = 0.");
							}
						}
					} else {
						logger.info("No files to process.");
					}
				}
			} 					
			logger.info("############## END ##############");
			//End qcML generation: 
		    
			
		} else {
			logger.info("You must specify where the properties file and the node to process, e.g. java -jar mzml2qcml.jar /users/pr/shared/QC/scripts/jar crg");
		}
		
		
	}//end main
	
	public static String getQcodeFromAnyString(String input){
	    	int index = input.lastIndexOf("QC");
		return input.substring(index, index + 4);
	}

	private static void getDBparameters_VM(String DB_URL, String USER, String PASS){
	    
	    	try {//start db conn

			Class.forName(JDBC_DRIVER);
			conn = DriverManager.getConnection(DB_URL, USER, PASS);
			stmt = conn.createStatement();
		
			//Get instruments folder and finalDataDir from db: 
			String query = "SELECT distinct(serverfolder),finalDataDir,startFileExt,startDataDir,finalFileExt,toppasWorkflowOutput,mzmlFolder FROM qcode INNER JOIN instrument ON instrument.idinstrument = qcode.idinstrument AND serverfolder <> '' AND qcode.description = '"+forceNode+"'";
			ResultSet rsInstruments = stmt.executeQuery(query);
			while (rsInstruments.next()) {
			    instrumentDirsList.add(rsInstruments.getString("serverfolder"));
			    finalDataDirList.add(rsInstruments.getString("finalDataDir"));
			    startFileExtList.add(rsInstruments.getString("startFileExt"));
			    finalFileExtList.add(rsInstruments.getString("finalFileExt"));
			    toppasWorkflowOutputList.add(rsInstruments.getString("toppasWorkflowOutput"));
			    startDataDirList.add(rsInstruments.getString("startDataDir"));
			    mzmlFolderList.add(rsInstruments.getString("mzmlFolder"));
			}
			
			//Get qcpatterns folder from db: 
			ResultSet rsQcpatterns = stmt.executeQuery("SELECT internal FROM qcode WHERE internal <> '' AND qcode.description = '"+forceNode+"'");
			while (rsQcpatterns.next()) {
			    qcPatternList.add(rsQcpatterns.getString("internal"));
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

	private static List<String> getInputDirs(String forceDateDir) {
		List<String> dateDirList = new ArrayList<String>();
		if(forceDateDir.equals("present")){
			dateDirList.add(getYYMMfolderName(new Date()));
		} else {
			dateDirList = Arrays.asList(forceDateDir.split("\\s*,\\s*"));
		}
		return dateDirList;
	}
	
	private static void getProperties(String paramFile) {
		
		//Getting properties parameters: 
		Properties prop = new Properties();
		InputStream inputProp = null;	
		
		try {
			inputProp = new FileInputStream(paramFile);
			prop.load(inputProp);
			minFileSize = prop.getProperty("minFileSize");
			minFileAge = prop.getProperty("minFileAge");
			forceDateDir = prop.getProperty("forceDateDir");
			inputDir = prop.getProperty("inputDir");
			outputDir = prop.getProperty("outputDir");
			logDir = prop.getProperty("logDir");
			JDBC_DRIVER = prop.getProperty("JDBC_DRIVER");
			DB_URL = prop.getProperty("DB_URL");
			USER = prop.getProperty("USER");
			PASS = prop.getProperty("PASS");
			isLinux = prop.getProperty("isLinux");
			trfDir = prop.getProperty("trfDir");
			toppasWorkflowDir = prop.getProperty("toppasWorkflowDir");
			toppasOutputDir = prop.getProperty("toppasOutputDir");
			finalFileExt = prop.getProperty("finalFileExt");
			pathBinExecutePipeline = prop.getProperty("pathBinExecutePipeline");
			numJobs = prop.getProperty("numJobs");
			openmsVer = prop.getProperty("openmsVer");
			isLocal = prop.getProperty("isLocal");
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	private static String getYYMMfolderName(Date now){
		SimpleDateFormat year_format = new SimpleDateFormat("yy");
		SimpleDateFormat month_format = new SimpleDateFormat("MM");
		String year_formatted = year_format.format(now);
		String month_formatted = month_format.format(now);
		return year_formatted+month_formatted;
	}
	
	private static void configureLogFile(String logDir){
		FileHandler fh;  
		String logName = "mzxml2qcml-"+getYYMMfolderName(new Date());
		//String logName = "mzxml2qcml";
		try {
			File logFile = new File(logDir+File.separator+logName+".log");
			if(!logFile.exists()) {
				fh = new FileHandler(logDir+File.separator+logName+".log");  
        	} else {
        		fh = new FileHandler(logDir+File.separator+logName+".log",true);//append
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
	
	private static ArrayList getFilesToProcess(String dateDir){
		
		ArrayList<String> filesToProcess = new ArrayList<String>();
		boolean isProcessed = false; 
		
		for(int m = 0; m < instrumentDirsList.size(); m++){//loop into all instrument subfolders (e.g. orbitrap_velos, etc). 
		    
			/*
			 * For example:
			 * inputFilesDir =  C:\\Users\\rolivella\\qc\\input\\orbitrap_velos\\mzML\\1702
			 * outputFilesDir = C:\\Users\\rolivella\\qc\\output\\orbitrap_velos\\qcml\\1702
			*/
			String inputFilesDir = inputDir+File.separator+instrumentDirsList.get(m).toString()+File.separator+mzmlFolderList.get(m)+File.separator+dateDir;
			String outputFilesDir = outputDir+File.separator+instrumentDirsList.get(m).toString()+File.separator+finalDataDirList.get(m)+File.separator+dateDir;
			
			File[] listOfInputFiles = new File(inputFilesDir).listFiles();
			File[] listOfOutputFiles = new File(outputFilesDir).listFiles();
			logger.info("Comparing contents of "+inputFilesDir+" with "+outputFilesDir);
			
			//2.-Retrieve non processed files comparing input and output folders:  
			try {
			    
			    	//Create output folder if it doesn't exist: 
			    	if(listOfInputFiles == null) {
			    	    //logger.info("No pending files to process.");
				} else if(listOfOutputFiles == null) {
				    if(!new File(outputFilesDir).exists()){//create output dir if it does not exist
					boolean result = false; 
        	            		try {
        	            			result = new File(outputFilesDir).mkdirs();
        	            		} catch(SecurityException se){
        	            			logger.warning("==> Java error while creating the output dir ("+outputFilesDir+"): "+se.getMessage());
        	            		} 
        	            		if(result) 
        	            		    logger.info("Output dir created: "+outputFilesDir+".");
        	            		else 
        	            		    logger.warning("==> Output dir cannot be created: "+outputFilesDir+". Please check dir path and permissions.");
				    }
				}
				
			    	
				if(listOfInputFiles != null){
					for(int i = 0; i < listOfInputFiles.length; i++){
						RawFile rawfile = new RawFile(listOfInputFiles[i].getPath());
						if(listOfOutputFiles != null){
							for(int j = 0; j < listOfOutputFiles.length; j++){
								String inputFileFullName = listOfInputFiles[i].getName();
								String inputFileBaseName = inputFileFullName.substring(0, inputFileFullName.lastIndexOf("."));
								String outputFileFullName = listOfOutputFiles[j].getName();
								String outputFileBaseName = outputFileFullName.substring(0, outputFileFullName.lastIndexOf("-"));//up to "-" at filename-YYYYMMDDHHMM.qcml
								if(inputFileBaseName.equals(outputFileBaseName)) isProcessed = true;
							}
						} 
						if(!isProcessed){
							if ((rawfile.getFileSizeBytes() > Long.parseLong(minFileSize.trim())) && (Long.parseLong(minFileAge.trim()) < (System.currentTimeMillis()-rawfile.getFileCreationTimeInMillis()))) {//check size and creation time) 
								if (rawfile.getFileExtension().equals(mzmlExt)){
									if(!rawfile.getFileName().toLowerCase().contains("(") &&
									   !rawfile.getFileName().toLowerCase().contains("rep") &&
									   !rawfile.getFileName().toLowerCase().contains("corrupted") &&
									   !rawfile.getFileName().toLowerCase().contains("-") &&
									   !rawfile.getFileName().toLowerCase().contains(" ") &&
									   !rawfile.getFileName().toLowerCase().contains("~") &&
									   !rawfile.getFileName().toLowerCase().contains(",") &&
									   !rawfile.getFileName().toLowerCase().contains("?") &&
									   !rawfile.getFileName().toLowerCase().contains(")")){
										for(int k = 0; k < qcPatternList.size(); k++) {
											if(rawfile.isPatternPresent(qcPatternList.get(k))) {//check if the file has qcPattern substring
												filesToProcess.add(listOfInputFiles[i].toString());
											}										
										}
									}
								}
							}
						}
						isProcessed = false;
					}//end for					
				}
			} catch (Exception e){
				logger.warning("==> Get files to process error: "+e.getMessage()+". Please check the input and ouput paths.");	
				e.getMessage();
			}			
		}
		return filesToProcess;
	}
	
	private static String modifyTRF(String trfDir, String dateDir, String fileToAdd){
	    
		String trfFile = "";
		String qcCodeFinal = getQcodeFromAnyString(fileToAdd);
		trfFile = trfDir+File.separator+qcCodeFinal+".trf";
		
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document doc = null;
			if(new File(trfFile).exists() && !new File(trfFile).isDirectory()) {
				doc = docBuilder.parse(trfFile);
			} else {
				logger.warning("==> The file "+new File(trfFile).getAbsolutePath()+" does not exist. Please check mzml2qcml.properties file.");
			}
			if(doc != null){
				Node parameters = doc.getFirstChild();
				Node listitem = doc.getElementsByTagName("LISTITEM").item(0);
				NamedNodeMap attr = listitem.getAttributes();
				Node nodeAttr = attr.getNamedItem("value");
				String cleanFilePath = fileToAdd.replace("C:", "");
				cleanFilePath = cleanFilePath.replace("\\", "/"); 
				nodeAttr.setTextContent("file://"+cleanFilePath);
				TransformerFactory transformerFactory = TransformerFactory.newInstance();
				Transformer transformer = transformerFactory.newTransformer();
				DOMSource source = new DOMSource(doc);
				StreamResult result = new StreamResult(new File(trfFile));
				transformer.transform(source, result);				
			} 

		} catch (Exception e){
			logger.warning("==> Modifying TRF error: "+e.getMessage());
		}
		return qcCodeFinal;
	}
	
	private static boolean processFiles(String dateDir,String filename) {

	    	String absPathFile = filename;
	    	File qcmlOutputFile = null;
    		File sourceInstrumentFile = null;
    		String instrumentFinalDir = "";
    		String startFileExt = "";
    		String startDataDir = "";
    		String mzmlFolderFinal = "";
    		boolean processFilesOutput = false;
    		String sourceFileCreationDate = "";
		String outputDataDir = "";
		filename = new File(filename).getName();
		String basename = filename.split("\\.")[0];
		
		//Get instrument dir, extension from filename: 
	    	for(int i=0;i<instrumentDirsList.size();i++){
	    		if(absPathFile.contains(instrumentDirsList.get(i))){
	    			instrumentFinalDir   = instrumentDirsList.get(i);
	    			startFileExt         = startFileExtList.get(i);
	    			startDataDir         = startDataDirList.get(i);
	    			finalFileExt         = finalFileExtList.get(i);
	    			toppasWorkflowOutput = toppasWorkflowOutputList.get(i);
	    			outputDataDir        = finalDataDirList.get(i);
	    			mzmlFolderFinal      = mzmlFolderList.get(i);
	    		}
	    	}
        	
		// OpenMS ExecutePipeline paths generation: 
	    	/*
	    	 * For example(local): 
	    	 * 
	    	 * toppasWorkflowFile     = C:\Users\rolivella\qc\QC1V.toppas
	    	 * trfFile	          = C:\Users\rolivella\qc\QC1V.trf
	    	 * sourceInstrumentFile   = C:\Users\rolivella\qc\input\orbitrap_velos\Raw\1702\QC1V\170131_Q_QC1V_01_03.raw
	    	 * mzmlFilePath		  = C:\Users\rolivella\qc\input\orbitrap_velos\mzML\1702\170131_Q_QC1V_01_03.mzML
	    	 * destDirStr             = C:\Users\rolivella\qc\output\orbitrap_velos\qcml\1702
	    	 * qcmlOutputFile         = C:\Users\rolivella\qc\pipeline\TOPPAS_out\010-QCCalculator\170131_Q_QC1V_01_03.qcml
	    	 * 
	    	 */
		String toppasWorkflowFile = toppasWorkflowDir+File.separator+qcCode+".toppas";
		String trfFile = trfDir+File.separator+qcCode+".trf";
		sourceInstrumentFile = new File(inputDir+File.separator+instrumentFinalDir+File.separator+startDataDir+File.separator+dateDir+File.separator+qcCode+File.separator+filename.substring(0, filename.lastIndexOf('.'))+"."+startFileExt);
        	String mzmlFilePath = inputDir+File.separator+instrumentFinalDir+File.separator+mzmlFolderFinal+File.separator+dateDir+File.separator+basename+"."+mzmlExt;
        	
        	if(new File(mzmlFilePath).exists()){//Try to get the date from raw (mzml) file: 
        		sourceFileCreationDate = getCreationDateFromMZML("yyyyMMddHHmm",mzmlFilePath);
        		logger.info("Get RAW run date from mzML file: "+sourceFileCreationDate);
        	} else {//if not possible, get the creation date of the file: 
        		RawFile rawfile = new RawFile(sourceInstrumentFile.toString());
        		sourceFileCreationDate = rawfile.getFileCreationTimeFormatted("yyyyMMddHHmm");
        		logger.info("mzML file: "+mzmlFilePath+" does not exist so date from creation of RAW file. Date:"+sourceFileCreationDate);
        	}
    	
        	String destDirStr = outputDir+File.separator+instrumentFinalDir+File.separator+outputDataDir+File.separator+dateDir;
        	File destFile = new File(destDirStr+File.separator+filename.substring(0, filename.lastIndexOf('.'))+"-"+sourceFileCreationDate+"."+finalFileExt);
    		
        	qcmlOutputFile = new File(toppasOutputDir+File.separator+"TOPPAS_out"+File.separator+toppasWorkflowOutput+File.separator+basename+"."+finalFileExt);
        	
		try {
			
			if(!destFile.exists()){
			    
				if(destFile.createNewFile()){
					
					destFile.setWritable(false);//lock the file
					
					String arg1 = "";
					String arg2 = "";
					String pathToExecutePipeline = "";
					
					if(isLinux.equals("true")){
						arg1 = "/bin/sh";
						arg2 = "-c";
						pathToExecutePipeline = pathBinExecutePipeline;
					} else {
						arg1 = "cmd.exe";
						arg2 = "/c";
					}
					
					String singularity_command = pathToExecutePipeline+"ExecutePipeline -num_jobs "+numJobs+" -in "+toppasWorkflowFile+" -out_dir "+toppasOutputDir+" -resource_file "+trfFile;
					
					logger.info("Singularity command: "+singularity_command);
					
					ProcessBuilder testBuilder = new ProcessBuilder(arg1, arg2, singularity_command);
					//ProcessBuilder testBuilder = new ProcessBuilder(arg1, arg2, pathToExecutePipeline+"FileInfo -in "+mzmlFilePath);//
					
					testBuilder.redirectErrorStream(true);
                		        Process p = testBuilder.start();
                		        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                		        String line;
                		        BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
                		        
                		        String s = "";
                		        //String outputTOPPAS = "TOPPAS log: \n\n";
                		        while ((s = stdInput.readLine()) != null) {
                		            //outputTOPPAS += s+"\n";
                		            logger.info(s+"\n");
                		        }
                		        //outputTOPPAS = outputTOPPAS+"\n\nEND TOPPAS log.";
                		        //logger.info(outputTOPPAS);
                		        
                		        int endProcessCode = p.waitFor();
                		        
                		        if (endProcessCode == 0){
                		            	destFile.setWritable(true);//unlock file
                		            	logger.info("ExecutePipeline finished without any error.");
                		            	if(qcmlOutputFile.exists() && !qcmlOutputFile.isDirectory()) {
                		            		if(destFile.exists() && destFile.length() == 0){
                		            			destFile.delete();
                		            			Files.copy(qcmlOutputFile.toPath(), destFile.toPath());//Copy file to output dir
                		                		logger.info("The file "+qcmlOutputFile.getName()+" was copied to "+destFile.toPath()+".");	
                		                		processFilesOutput = true;
                		            		} else {
                		            			logger.info("The destination file "+destFile.getAbsolutePath()+" already exists but it's size is > than 0 bytes so it will we preserved in the destination folder.");
                		            		}
                		            	} else {
                		            		logger.warning("==> The file "+qcmlOutputFile.getAbsolutePath()+" does not exist. Please check properties file.");
                		            	}
                		            	
                		        } else {
                		            	destFile.setWritable(true);//unlock file
                		            	logger.warning("==> ExecutePipeline for the file "+filename+" finished with errors. An empty qcml file has been created with name "+destFile.getName());
                		        }				
				}				
			} else {
				logger.info("The destination file "+destFile.getAbsolutePath()+" already exists so OpenMS will be not run now.");
				destFile.delete();
			}

		} catch (Exception e) {
			e.printStackTrace();			
		}
		
		return processFilesOutput;
		
	}//end launch pipeline

	private static String getCreationDateFromMZML(String format,String mzmlFilePath){
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	  	DocumentBuilder builder;
	  	String startTimeStamp = "";
		String yyyy = null;
		String MM = null;
		String dd = null;
		String HH = null;
		String mm = null;
  		try {
			  builder = factory.newDocumentBuilder();
		  	  Document doc = builder.parse(mzmlFilePath);
		      XPathFactory xPathfactory = XPathFactory.newInstance();
			  XPath xpath = xPathfactory.newXPath();
			  XPathExpression expr = xpath.compile("/indexedmzML/mzML/run");
			  NodeList featureList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
			  for (int i = 0; i < featureList.getLength(); i++) {
				  Node feature = featureList.item(i);
				  if (feature != null && feature.getNodeType() == Node.ELEMENT_NODE) {
					  startTimeStamp = feature.getAttributes().getNamedItem("startTimeStamp").getNodeValue();// feature id
				  }
			  }
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}
		//Process date 2016-04-30T13:25:19Z -> (yyyyMMddHHmm) :
		if(!startTimeStamp.equals("")){
			yyyy = startTimeStamp.substring(0,4);
			MM = startTimeStamp.substring(5,7);
			dd = startTimeStamp.substring(8,10);
			HH = startTimeStamp.substring(11,13);
			mm = startTimeStamp.substring(14,16);
		}	
		
		String formatedDate = "";
		
		if(yyyy == null || MM == null || dd == null || HH == null || mm == null ){
			formatedDate = "197001010000";
			logger.warning("==> Some (or all) substring of the mzML date is null so setting formatedDate to "+formatedDate);
		} else {
			formatedDate = yyyy+MM+dd+HH+mm;
		}
		
		return formatedDate;	
	}

}//end class
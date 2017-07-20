/**
 * 
 * @author Roger Olivella
 * 
 * Created: 18/07/2017
 * 
 * Description: Copies FTP data to repository. 
 * 
 * Major modif.: none.
 *
 */
package qc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ftp2repo {
    
    public static String propertiesFilename = "ftp2repo.properties";
    
    public static String FTP_ROOT_private, FTP_ROOT_public, HOST, isLinuxOS,FTP_USER_private,
    FTP_PASS_private, FTP_USER_public, FTP_PASS_public, REPO_ROOT_EXT, REPO_FOLDER_INTERNAL, 
    REPO_FOLDER_MZML, REPO_FOLDER_QCML, JDBC_DRIVER, DB_URL, 
    DB_USER, DB_PASS, forceNode, logDir;
    public static Connection conn = null;
    public static Statement stmt = null;
    public static Logger logger = Logger.getLogger("MyLog");
    public static List<String> serverFolderList = new ArrayList<String>();
    public static List<String> internalQcodeList = new ArrayList<String>();
    public static List<String> externalQcodeList = new ArrayList<String>();
    public static List<String> qcrawlerUsersList = new ArrayList<String>();
 
    public static void main(String[] args) {
 
	if(args.length == 2){//check JAR arguments
	    
	    	//Get command line arguments:
	    	getProperties(args[0]+File.separator+propertiesFilename);
	    	forceNode = args[1];
	    
		//Get DB parameters: 
		getDBparameters_VM(DB_URL, DB_USER, DB_PASS, forceNode);
		
		//Log file:
		configureLogFile(logDir);
		
		//Run FTP download and rename for each instrument
		logger.info("############## START ##############");
		for(int i = 0; i < serverFolderList.size(); i++){
		    processFiles(serverFolderList.get(i),internalQcodeList.get(i),externalQcodeList.get(i),qcrawlerUsersList.get(i));
		}
		logger.info("##############  END  ##############");
		
	} else {
		logger.info("You must specify where the properties file and the node to process, e.g. java -jar ftp2repo.jar /users/pr/shared/QC/scripts/jar crg");
	}
    }
    
    private static void getProperties(String paramFile) {
		
		//Getting properties parameters: 
		Properties prop = new Properties();
		InputStream inputProp = null;	
		
		try {
			inputProp = new FileInputStream(paramFile);
			prop.load(inputProp);
			FTP_ROOT_private = prop.getProperty("FTP_ROOT_private");
			FTP_ROOT_public = prop.getProperty("FTP_ROOT_public");
			HOST = prop.getProperty("HOST");
			isLinuxOS = prop.getProperty("isLinuxOS");
			FTP_USER_private = prop.getProperty("FTP_USER_private");
			FTP_PASS_private = prop.getProperty("FTP_PASS_private");
			FTP_USER_public = prop.getProperty("FTP_USER_public");
			FTP_PASS_public = prop.getProperty("FTP_PASS_public");
			REPO_ROOT_EXT = prop.getProperty("REPO_ROOT_EXT");
			JDBC_DRIVER = prop.getProperty("JDBC_DRIVER");
			DB_URL = prop.getProperty("DB_URL");
			DB_USER = prop.getProperty("DB_USER");
			DB_PASS = prop.getProperty("DB_PASS");
			logDir = prop.getProperty("logDir");
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}		
    }
    
    private static void processFiles(String serverFolder, String qCodeInternal, String qCodeExternal, String qCrawlerUser) {
	
		try {
		    		String arg1, arg2, ftp_command = "", rename_command = "";
		    		String REPO_FOLDER_NODES = "", FTP_FOLDER = "", FINAL_USER = "", FINAL_PASS = "", FINAL_FTP_ROOT = "";
		    		int indexSlash = serverFolder.indexOf("/");
		    		String yymm = getYYMMfolderName(new Date());
		    		
		    		//Choose FTP credentials: 
		    		FINAL_USER = FTP_USER_public;
		    		FINAL_PASS = FTP_PASS_public;
		    		if(qCrawlerUser == null){
		    		    FINAL_USER = FTP_USER_public;
		    		    FINAL_PASS = FTP_PASS_public; 
		    		    FINAL_FTP_ROOT = FTP_ROOT_public;
		    		} else if(qCrawlerUser.equals(FTP_USER_private)){
		    		    FINAL_USER = FTP_USER_private;
		    		    FINAL_PASS = FTP_PASS_private;
		    		    FINAL_FTP_ROOT = FTP_ROOT_private;
		    		}
		    		
		    		//Build REPO & FTP folders: 
		    		if(indexSlash != -1){//External nodes
		    		    String lab = serverFolder.substring(0, indexSlash);
		    		    String instrument = serverFolder.substring(indexSlash+1, serverFolder.length());
		    		    REPO_FOLDER_NODES = REPO_ROOT_EXT + "/" + lab + "/" + instrument + "/" + "Raw" + "/" + yymm + "/" + qCodeExternal;
		    		    REPO_FOLDER_INTERNAL = REPO_ROOT_EXT + "/" + lab + "/" + instrument + "/" + "Raw" + "/" + yymm + "/" + qCodeInternal;
		    		    REPO_FOLDER_MZML = REPO_ROOT_EXT + "/" + lab + "/" + instrument + "/" + "mzML" + "/" + yymm;
		    		    REPO_FOLDER_QCML = REPO_ROOT_EXT + "/" + lab + "/" + instrument + "/" + "qcml" + "/" + yymm;
		    		    FTP_FOLDER	= FINAL_FTP_ROOT + "/" + lab + "/" + instrument + "/" + yymm + "/" + qCodeExternal;
		    		} 
		    		
		    		//Create REPO & FTP folders if they don't exist: 
		    		new File(REPO_FOLDER_NODES).mkdirs();
		    		new File(REPO_FOLDER_INTERNAL).mkdirs();
		    		new File(REPO_FOLDER_MZML).mkdirs();
		    		new File(REPO_FOLDER_QCML).mkdirs();
		    		
		    		if(isLinuxOS.equals("true")){
		    		    arg1 = "/bin/sh";
				    arg2 = "-c";
				    ftp_command = "lftp -e \"\nopen " + HOST + " \nuser " + FINAL_USER + " '" + FINAL_PASS + "' \nlcd " + REPO_FOLDER_NODES + "\nmkdir -p " + FTP_FOLDER + " \nmirror --Remove-source-files --include " + qCodeExternal + " --verbose " + FTP_FOLDER + " " + REPO_FOLDER_NODES + "\n\nbye\"";
				    runCommand(arg1, arg2, ftp_command);//FTP
				    rename_command = "for f in " + REPO_FOLDER_NODES + "/*.raw; do mv -n \"$f\" \"`echo $f | sed s/"+qCodeExternal+"/"+qCodeInternal+"/g`\"; done";
				    runCommand(arg1, arg2, rename_command);//Rename
		    		} 

		} catch (Exception e) {
		    	logger.info("\n\n==> Exception at processFiles(): \n\n"+e.getMessage());
			e.printStackTrace();			
		}
		
    }//end
    
    private static boolean runCommand(String arg1, String arg2, String command){
	
	boolean isCommandSuccessful = false;
	
	try {
		ProcessBuilder pbuilder = new ProcessBuilder(arg1, arg2, command);
		pbuilder.redirectErrorStream(true);
	        Process p = pbuilder.start();
	        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
	        String line;
	        BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
	        String s = "";
	        
	        while ((s = stdInput.readLine()) != null) {
	            logger.info(s+"\n");
	        }
        
	        int endProcessCode = p.waitFor();
        
	        if (endProcessCode == 0){
	            	logger.info("\n\n==> Command SUCCESSFUL: \n\n"+command);
	            	isCommandSuccessful = true;
	        } else {
            		logger.warning("\n\n==> Command ERROR: \n\n"+command+". Please check.");
	        }	
	        
	} catch (Exception e) {
		e.printStackTrace();			
	}
	
	return isCommandSuccessful;
    }
    
    private static void getDBparameters_VM(String DB_URL, String USER, String PASS, String node){
	    
	    	try {//start db conn

			Class.forName(JDBC_DRIVER);
			conn = DriverManager.getConnection(DB_URL, USER, PASS);
			stmt = conn.createStatement();
		
			//Get instruments folder and finalDataDir from db: 
			String query = "SELECT distinct(serverfolder),qcode.name,qcode.internal,qcollector.ftpuser,qcode.description FROM qcode INNER JOIN instrument ON qcode.idinstrument = instrument.idinstrument LEFT JOIN qcollector ON instrument.idinstrument = qcollector.idinstrument WHERE qcode.description = '" + node + "' AND serverfolder <> '' AND qcode.description <> 'c4l' AND qcode.name <> 'cal'";
			ResultSet rsInstruments = stmt.executeQuery(query);
			while (rsInstruments.next()) {
			    serverFolderList.add(rsInstruments.getString("serverfolder"));
			    internalQcodeList.add(rsInstruments.getString("internal"));
			    externalQcodeList.add(rsInstruments.getString("name"));
			    qcrawlerUsersList.add(rsInstruments.getString("ftpuser"));
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
    
    private static String getYYMMfolderName(Date now){
		SimpleDateFormat year_format = new SimpleDateFormat("yy");
		SimpleDateFormat month_format = new SimpleDateFormat("MM");
		String year_formatted = year_format.format(now);
		String month_formatted = month_format.format(now);
		return year_formatted+month_formatted;
    }

    private static void configureLogFile(String logDir){
	FileHandler fh;  
	String logName = "ftp2repo-"+getYYMMfolderName(new Date());
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
	
}//class
package qc;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * @author Roger Olivella
 * 
 * Created: 25/06/2015
 * 
 * Description: raw file info.
 * 
 *
 */

public class RawFile {
	
	private String rawFileAbsolutePath = "";

	public RawFile(String rawFileAbsolutePath) {
		this.rawFileAbsolutePath = rawFileAbsolutePath; 
	}

	long getFileSizeBytes() {
		BasicFileAttributes attr = null;
		try {
			attr = Files.readAttributes(Paths.get(new File(this.rawFileAbsolutePath).getPath()), BasicFileAttributes.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return attr.size();
	}
	
	String getFileName() {
		return new File(this.rawFileAbsolutePath).getName();
	}
	
	String getFileExtension() {
		String filename = new File(this.rawFileAbsolutePath).getName();
		return filename.substring(filename.lastIndexOf('.')+1, filename.length());
	}
	
	long getFileCreationTimeInMillis() {
		BasicFileAttributes attr = null;
		try {
			attr = Files.readAttributes(Paths.get(new File(this.rawFileAbsolutePath).getPath()), BasicFileAttributes.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return attr.creationTime().toMillis();
	}
	
	String getFileCreationTimeFormatted(String format) {
		BasicFileAttributes attr = null;
		try {
			attr = Files.readAttributes(Paths.get(new File(this.rawFileAbsolutePath).getPath()), BasicFileAttributes.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		DateFormat df = new SimpleDateFormat(format);
		return df.format(attr.creationTime().toMillis());
	}
	
	boolean isPatternPresent(String patternStr){
		Pattern patternPtr = Pattern.compile(".*("+patternStr+").*");
		Matcher matcher = patternPtr.matcher(new File(this.rawFileAbsolutePath).getName());
		if(matcher.matches()) {//Check if the file contains the pattern patternStr
			return true;
		} else {
			return false;
		}
	}
}
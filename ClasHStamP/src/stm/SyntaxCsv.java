package stm;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

public class SyntaxCsv {
	private HashMap<String, HashMap<String, String>> syntaxMap = new HashMap<String, HashMap<String,String>>();
	public SyntaxCsv(String csvFilePath) {
		try {
			Reader in = new InputStreamReader(new FileInputStream(csvFilePath), "UTF-8");
			Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(in);
			ArrayList<String> header = new ArrayList<String>();
			
			int j = 0;
			for (CSVRecord record : records) {
				HashMap<String, String> recordMap = new HashMap<String, String>();
				
				for (int i = 0; i < record.size(); i++) {
					if (j == 0) {
						header.add(record.get(i));
					} else {
						recordMap.put(header.get(i), record.get(i));
					}
				}
				syntaxMap.put(record.get(0), recordMap);
				j++;
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public String get(String row, String col) {
		String result;
		try {
			result = syntaxMap.get(row).get(col);
		} catch (Exception e) {
			result = null;
		}
		return result;
	}
	
	public String indentByTab(int indent, String path) {
		String indentTabs = "";
		for (int i = 0; i < indent; i++) {
			indentTabs += "    ";
		}
		return path.replaceAll(Pattern.quote("[->]"), indentTabs); 
	}

	public static String insertToFirstLineEnd(String original, String toInsert) {
	    int newlineIndex = original.indexOf('\n');

	    if (newlineIndex == -1) {
	        // Single-line string: just append
	        return original + toInsert;
	    } else {
	        // Multi-line: insert after first line
	        String firstLine = original.substring(0, newlineIndex);
	        String rest = original.substring(newlineIndex); // includes the '\n'
	        return firstLine + toInsert + rest;
	    }
	}
	
	public String get(int indent, String row, String col) {
		String result;
		try {
			result = indentByTab(indent, syntaxMap.get(row).get(col));
		} catch (Exception e) {
			result = null;
		}
		String sDebug = "";
		if (System.getenv("PATH_DBG") != null && System.getenv("PATH_DBG").equals("ON")) {
			sDebug = " /* " + row + "." + col + " */";
		}
		return insertToFirstLineEnd(result, sDebug);
	}
}

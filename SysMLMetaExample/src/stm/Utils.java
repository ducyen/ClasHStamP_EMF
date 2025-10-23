package stm;
import java.util.regex.*;

public class Utils {
	public static String strMixedCaseToLowerCase(String input) {
		String result = new String();
		int i, curPos = 0;
		for (i = 0; i < input.length(); i++) {
			if (i > 0 && Character.isUpperCase(input.charAt(i))) {
				result += input.substring(curPos, i).toLowerCase();
				result += "_";
				curPos = i;
			}
		}
		result += input.substring(curPos).toLowerCase();
				
		return result;		
	}
	
	public static String keepAlphanumericOnly(String input) {
		String output = "";
		for (int i = 0; i < input.length(); i++) {
			char ch = input.charAt(i);
			if (ch == '_' || Character.isLetterOrDigit(ch)) {
				output += ch;
			}
		}
		return output;
	}

	public static String strConvertToMixedCase(String input) {
		String result = new String();
		for (String currentTok: input.split("_")) {
			if (currentTok.isEmpty()) {
				result += "_";
			} else {
				result += currentTok.substring(0, 1).toUpperCase();
				result += currentTok.substring(1).toLowerCase();
			}
		}
		return result;
	}

	public static String strMixedCaseToUpperCase(String input) {
		String result = new String();
		int i, curPos = 0;
		for (i = 0; i < input.length(); i++) {
			if (i > 0 && Character.isUpperCase(input.charAt(i))) {
				result += input.substring(curPos, i).toUpperCase();
				result += "_";
				curPos = i;
			}
		}
		result += input.substring(curPos).toUpperCase();
				
		return result;		
	}
	
	private static enum StrCase {
		AllUpper, AllLower, Mixed, Nothing
	}
	private static StrCase getStrCase(String input){
		if (input.matches("[A-Z0-9_]*")){					// something like "THIS_IS_DUC"
			return StrCase.AllUpper;
		}else if (input.matches("[a-z0-9_]*")) {			// something like "this_is_duc"
			return StrCase.AllLower;
		}else if (input.matches("[A-Z][a-zA-Z0-9_]*")){		// something like "ThisIsDuc"
			return StrCase.Mixed;
		}else{												// something like "tHISiSdUC"
			return StrCase.Nothing;
		}
	}
	private static String convertStrCase(String input, String template) {
		String result = input;
		if (getStrCase(input) == StrCase.Mixed){
			if (getStrCase(template) == StrCase.AllLower){
				result = strMixedCaseToLowerCase(input);
			}else if(getStrCase(template) == StrCase.AllUpper){
				result = strMixedCaseToUpperCase(input);
			}
		}else if (getStrCase(input) == StrCase.AllUpper) {
			if (getStrCase(template) == StrCase.AllLower){
				result = input.toLowerCase();
			}else if(getStrCase(template) == StrCase.Mixed){
				result = strConvertToMixedCase(input);
			}
		}else if (getStrCase(input) == StrCase.AllLower) {
			if (getStrCase(template) == StrCase.AllUpper){
				result = input.toUpperCase();
			}else if(getStrCase(template) == StrCase.Mixed){
				result = strConvertToMixedCase(input);
			}
		}else {
			if (getStrCase(template) == StrCase.AllUpper){
				result = strMixedCaseToUpperCase(input);
			}else if(getStrCase(template) == StrCase.AllLower){
				result = strMixedCaseToLowerCase(input);
			}else if(getStrCase(template) == StrCase.Mixed){
				result = input.substring(0, 1).toUpperCase() + input.substring(1);
			}
		}
		return result;
	}
	private static String updateAllStrCases(String input, String var, String lower, String mixed, String upper, String nothing) {
		String text = input;
		text = text.replace("[" + lower + "]", convertStrCase(var, lower));
		text = text.replace("[" + mixed + "]", convertStrCase(var, mixed));
		text = text.replace("[" + upper + "]", convertStrCase(var, upper));
		text = text.replace("[" + nothing + "]", convertStrCase(var, nothing));
		String ltrim = text.replaceAll("^\\t+","");
		if (ltrim.isEmpty()) {
			return "";
		}
		return text;
	}
	private static boolean isCharCJK(final char c) {
	    if ((Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS)
            || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A)
            || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B)
            || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS)
            || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS)
            || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT)
            || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION)
            || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS)
            || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HIRAGANA)
            || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.KATAKANA)
        ) {
	        return true;
	    }
	    return false;
	}
	private static String insertSpaces(String text) {
		String result = text;
		for (String line: text.split("\\r?\\n")) {
			while (true) {
				String regEx = "\\[\\-\\>(\\d+)\\]";
				Matcher m = Pattern.compile(regEx).matcher(line);
				if (m.find()) {
					String absIndent = m.group(1);
					int matchPos = 0;
					for (int i = 0; i < m.start(); i++) {
						if (i < text.length()) {
							if (isCharCJK(text.charAt(i))) {
								matchPos += 2;
							} else {
								matchPos += 1;
							}
						}
					}
					
					int numberOfSpace = Integer.parseInt(absIndent) - matchPos;
					String spaces = "";
					for (int i = 0; i < numberOfSpace; i++) {
						spaces += " ";
					}
					line = line.replaceFirst("\\[\\-\\>" + absIndent + "\\]", spaces);
					result = result.replaceFirst("\\[\\-\\>" + absIndent + "\\]", spaces);
					continue;
				}
				break;
			}
		}
		return result;
	}
	
	private static int nestedCount = 0;
	public static String get(String path) throws Exception {
		path = path.replaceAll("\\r?\\n", System.getProperty("line.separator"));
		return path;
	}

	public static String get(String path, String name) throws Exception {
		nestedCount++;
		String text = get(path);
		nestedCount--;
		text = updateAllStrCases(text, name, "name", "Name", "NAME", "nAME");
		if (nestedCount == 0) {
			text = insertSpaces(text);
		}
		return text;
	}

	public static String get(String path, String name, String type) throws Exception {
		nestedCount++;
		String text = get(path, name);
		nestedCount--;
		text = updateAllStrCases(text, type, "type", "Type", "TYPE", "tYPE");
		if (nestedCount == 0) {
			text = insertSpaces(text);
		}
		return text;
	}

	public static String get(String path, String name, String type, String container) throws Exception {
		nestedCount++;
		String text = get(path, name, type);
		nestedCount--;
		text = updateAllStrCases(text, container, "container", "Container", "CONTAINER", "cONTAINER");
		if (nestedCount == 0) {
			text = insertSpaces(text);
		}
		return text;
	}

	public static String get(String path, String name, String type, String _class, String value) throws Exception {
		nestedCount++;
		String text = get(path, name, type, _class);
		nestedCount--;
		text = updateAllStrCases(text, value, "value", "Value", "VALUE", "vALUE");
		if (nestedCount == 0) {
			text = insertSpaces(text);
		}
		return text;
	}

	public static String get(String path, String name, String type, String _class, String value, String modifier) throws Exception {
		nestedCount++;
		String text = get(path, name, type, _class, value);
		nestedCount--;
		text = updateAllStrCases(text, modifier, "modifier", "Modifier", "MODIFIER", "mODIFIER");
		if (nestedCount == 0) {
			text = insertSpaces(text);
		}
		return text;
	}

	public static String get(String path, String name, String type, String _class, String value, String modifier, String desc) throws Exception {
		nestedCount++;
		String text = get(path, name, type, _class, value, modifier);
		nestedCount--;
		text = updateAllStrCases(text, desc, "desc", "Desc", "DESC", "dESC");
		if (nestedCount == 0) {
			text = insertSpaces(text);
		}
		return text;
	}

	public static String get(String path, String name, String type, String _class, String value, String modifier, String desc, String scope) throws Exception {
		nestedCount++;
		String text = get(path, name, type, _class, value, modifier, desc);
		nestedCount--;
		text = updateAllStrCases(text, scope, "scope", "Scope", "SCOPE", "sCOPE");
		if (nestedCount == 0) {
			text = insertSpaces(text);
		}
		return text;
	}

	public static String trimEnd(String value) {
	    int len = value.length();
	    int st = 0;
	    while ((st < len) && value.charAt(len - 1) == ' ') {
	      len--;
	    }
	    return value.substring(0, len);
	}
}

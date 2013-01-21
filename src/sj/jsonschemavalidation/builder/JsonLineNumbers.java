package sj.jsonschemavalidation.builder;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/** For getting Line numbers for JSON pointers */ 
public class JsonLineNumbers {
	private static String stackToPointer(Stack<Object> s, String leaf) {
		StringBuilder pointer = new StringBuilder();
		List<Object> items = Collections.list(s.elements());
		for (Object item : items) {
			pointer.append("/" + item);
		}
		if (leaf != null) {
			pointer.append("/" + leaf);
		}
		return pointer.toString();
	}
	
	private static Logger logger = Logger.getAnonymousLogger();
	
	/** 
	 * Returns mapping of JSON pointers to line numbers.
	 * Pointers like "/Products/5/Name"
	 * 
	 * @param content JSON string 
	 */
	public static Map<String,Integer> handleString(String content) {		
		Map<String,Integer> lineNumbersByJsonPointer = new HashMap<String,Integer>();
		Stack<Object> stack = new Stack<Object>();		

		try {
			JsonFactory f = new JsonFactory();
			JsonParser jp = f.createJsonParser(content);
			JsonToken token = jp.nextToken();
			if (token != JsonToken.START_OBJECT) {
				logger.finer("Token is " + jp.nextToken());
				return Collections.emptyMap();
			}
			token = jp.nextToken();
			while (jp.hasCurrentToken()) {				
				if (token == JsonToken.START_OBJECT) {
					logger.finer("[ ");
					String name = jp.getCurrentName();
					if (name == null) {
						Object top = null;
						if(! stack.empty()) { 
							top = stack.peek(); 
						}
						if (top instanceof Integer) {
							stack.pop();
							stack.push(((Integer) top) + 1);
						} else {
							// Unnamed object not root object?
							String pointer = stackToPointer(stack, jp.getCurrentName());
							int lineNo = jp.getCurrentLocation().getLineNr();
							logger.finer(lineNo + " " + pointer);
							lineNumbersByJsonPointer.put(pointer, lineNo);
						}
					} else {
						stack.push(name);
					}
				} else if (token == JsonToken.END_OBJECT) {				
					// laving object
					// pop unless array item
					if (!stack.empty() && !(stack.peek() instanceof Integer)) {
						logger.finer("} ");
						if(! stack.empty()) stack.pop();
					}
				} else if (token == JsonToken.START_ARRAY) {
					logger.finer("[ ");
					stack.push(jp.getCurrentName());
					stack.push(Integer.valueOf(-1));
				} else if (token == JsonToken.END_ARRAY) {
					logger.finer("] ");
					if(! stack.empty()) stack.pop(); // array index
					if(! stack.empty()) stack.pop(); // field name
				} else if (token == JsonToken.FIELD_NAME) {
					String pointer = stackToPointer(stack, jp.getCurrentName());
					int lineNo = jp.getCurrentLocation().getLineNr();
					logger.finer(lineNo + " " + pointer);
					lineNumbersByJsonPointer.put(pointer, lineNo);
				}
				token = jp.nextToken();
			} // while
			
			return lineNumbersByJsonPointer;
		} catch (JsonParseException e) {
			logger.fine("Syntax error in file starting with: \"" + 
						content.substring(0, Math.min(33, content.length())) + "\"");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Collections.emptyMap();
	}
}

package sj.jsonschemavalidation.builder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eel.kitchen.jsonschema.main.JsonSchema;
import org.eel.kitchen.jsonschema.main.JsonSchemaFactory;
import org.eel.kitchen.jsonschema.report.ValidationReport;
import org.eel.kitchen.jsonschema.util.JsonLoader;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;


public class ValidateJson {
	private static final String MARKER_TYPE = "sj.jsonschemavalidation.jsonProblem";
	private static Map<IResource, List<IFile>> datafileBySchema = new HashMap<IResource, List<IFile>>();
	private static final Logger logger = Logger.getAnonymousLogger();
	
	// remove all our eclipse error markers from file
	private static void deleteMarkers(IFile file) {
		try {
			int nMarkers = file.findMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO).length;
			logger.fine("Removing old markers: " + nMarkers);
			file.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
		} catch (CoreException ce) {
		}
	}

	/*
	 * Finds and returns the member resource identified by the given path in this container, or null if no such resource exists. 
	 * Unlike org.eclipse.core.resources.IContainer.findMember It is not case sensitive.
	 */
	private static IResource findMemberIgnoreCase(IContainer container, String name) {
		try {
			for (IResource res : container.members()) {
				if (res.getName().equalsIgnoreCase(name)) {
					return res;
				}
			}
		} catch (CoreException e) {
			logger.warning(e.toString());
		}
		return null;
	}
	
	
	/* Look for a schema that can be applied to the given file.	  
	 * matches by name. Foo.json -> FooSchema.json etc.
	 * Returns null, if no schema found 
	 */
	private static IResource getSchemaResource(IFile file) {
		final String filename = file.getName();
		final String extension = file.getFileExtension();
		final String basename = file.getName().substring(0, filename.length() - 1 - extension.length());
		
		// no schemas for schemas
		if(filename.toLowerCase().endsWith("schema.json")) {
			return null;
		}
		
		// possible file names of a schema
		final String[] candidates = {
				basename + "Schema" + ".json",
				basename + ".schema" + ".json",
				"schema.json",
		};
		
		// try to find schema
		for (final String wantedSchemaFileName : candidates) {
			logger.fine("Looking for \"" + wantedSchemaFileName + "\" in \"" + file.getParent() + "\"");
			
			// IResource schemaFile = file.getParent().findMember(wantedSchemaFileName);
			IResource schemaFile = findMemberIgnoreCase(file.getParent(), wantedSchemaFileName);
			if (schemaFile != null && schemaFile instanceof IFile) {
				// Add schema->datafile mapping to index
				if (! datafileBySchema.containsKey(schemaFile)) {
					datafileBySchema.put(schemaFile, new ArrayList<IFile>());
				}
				List<IFile> datafileList = datafileBySchema.get(schemaFile);
				if (!datafileList.contains(file)) {
					datafileList.add(file); 
				}
				return schemaFile;
			}
		}
		
		// no schema found
		return null;
	}
	

	
	
	// Add eclipse error marker
	private static void addMarker(IFile file, String message, int lineNumber,
			int severity) {
		try {
			IMarker marker = file.createMarker(MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.SEVERITY, severity);
			if (lineNumber == -1) {
				lineNumber = 1;
			}
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			logger.fine("New marker: Line " + lineNumber + " for " + marker.getResource() + ", msg=" + marker.getAttribute(IMarker.MESSAGE, "(none)"));
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	
	private static String readFile(IFile file) throws IOException, CoreException {
		InputStreamReader reader = new InputStreamReader(file.getContents());
		BufferedReader in = new BufferedReader(reader);
		String line;
		StringBuilder all = new StringBuilder();
		while((line = in.readLine()) != null) {
			all.append(line).append('\n');
		}
		return all.toString();
	}
	
	/* Validate JSON file
	 * 
	 * Looks for schema file by name @see getSchemaResource
	 */
	public static void checkResource(IResource resource) {
		// JSON file?
		if (resource instanceof IFile && resource.getName().endsWith(".json")) {
			IFile file = (IFile) resource;
			if (!file.exists()) {
				logger.fine("File removed: " + file.getName());
				return;
			}
			
			try {
				// has schema?
				IResource schemaResource = getSchemaResource(file);
				if (schemaResource != null && schemaResource instanceof IFile) {
					IFile schemaFile = (IFile) schemaResource;
					
					// validate
					deleteMarkers(file);
					checkAgainst(file, schemaFile);
						
				} else {
					// is schema?
					if (!(resource instanceof IFile)) return;
					IFile schemaOrDataFile = (IFile) resource;
					List<IFile> dependent = datafileBySchema.get(resource);
					if (dependent != null && dependent.size() > 0) {
						for (IFile datafile : dependent) {
							logger.fine(datafile  + " status affected by schema " + schemaOrDataFile);
							deleteMarkers(datafile);
							checkAgainst(datafile, schemaOrDataFile);
						}
					} else {
						// Has, and is, no schema? check syntax
						logger.fine("No Schema for " + file.toString());
						deleteMarkers(schemaOrDataFile);
						checkAgainst(schemaOrDataFile, null);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
	}


	/* add marker for given resource file using parse exception. */ 
	private static void handleParseException(IFile file, JsonParseException parseException) {
		// ignore, if syntax is already checked by JSON Editor
		IProject proj = file.getProject();
		if (ProjectProperties.hasValidationNature(proj)) {
			logger.info("No marker for \"" + file.getName() + "\" since \"" + proj.getName() + "\" is checked by JSON Editor.");
			return;
		}
		
		JsonLocation where = parseException.getLocation();
		addMarker(file, parseException.getOriginalMessage(), where.getLineNr(), IMarker.SEVERITY_ERROR);
	}

	private static void checkAgainst(IFile file, IFile schemaFile)
			throws IOException, CoreException {
		String where = " (" + file.getName() + ". Schema: " + 
			(schemaFile == null ? "(none)" : schemaFile.getName()) + ")"; 
		// File ioFile = new File(file.getLocation().toPortableString());				
		// JsonNode root = JsonLoader.fromFile(ioFile);
		
		final String all = readFile(file);
		Map<String, Integer> lineNumbersByJsonPointer = JsonLineNumbers.handleString(all);
		
		JsonNode root;
		
		try {
			root = JsonLoader.fromString(all);
		} catch (JsonParseException parseException) {
			// not well-formed JSON
			handleParseException(file, parseException);
			return;
		}
		
		// .getLocation().toPortableString()
		JsonNode schemaJson = JsonLoader.fromString("{}");
		// No schema? Use empty schema to get syntax messages.
		if (schemaFile != null) {
			schemaJson = JsonLoader.fromString(readFile(schemaFile));
		}
		final JsonSchemaFactory factory = JsonSchemaFactory.defaultFactory();

		final JsonSchema schema = factory.fromSchema(schemaJson);

		ValidationReport report;

		report = schema.validate(root);
		logger.finer(report + where);
		
		
		// add markers
		for (String msg : report.getMessages()) {
			logger.fine(msg + where);
			int lineNo = 1;
			if (msg != null && msg.length() > 1 && msg.substring(1).indexOf('"') > -1) {
				String pointer = msg.substring(1, msg.substring(1).indexOf('"')+1);
				if (lineNumbersByJsonPointer.containsKey(pointer)) {
					lineNo = lineNumbersByJsonPointer.get(pointer);
				} else {
					logger.warning("Unknown line number of \""+ pointer + "\"");
				}
			} else {
				logger.warning("No pointer in message: \""+ msg + "\"");
			}
			addMarker(file, msg, lineNo, IMarker.SEVERITY_ERROR);
		}
	}


	/** Handles removed resources */
	public static void removeResource(IResource resource) {
		// iterate over schema->data mapping
		for (IResource schemaFile : datafileBySchema.keySet()) {
			// Removed res is a schema file?
			if (resource.equals(schemaFile)) {
				// remove mappings for schema
				List<IFile> dependent = datafileBySchema.get(schemaFile);
				datafileBySchema.remove(schemaFile);
				
				// look for affected data files
				for (IFile datafile : dependent) {
					// re-check
					if (dependent.contains(datafile)) {
						checkResource(datafile);
					}
				}
				
				return;				
			}
			
			// removed res had schema? remove from mapping
			List<IFile> datafileList = datafileBySchema.get(schemaFile);
			if (!datafileList.contains(resource)) {
				datafileList.remove(resource);
				return;
			}
		}
	}

}

package sj.jsonschemavalidation.builder;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;

/**
 * Helpers for accessing Eclipse project properties.
 */
public class ProjectProperties {
	// Validation Nature ID used by the JSON Editor
	private static final String JSON_EDITOR_VALIDATION_NATURE_ID = "json.validation.nature";
	
	/**
	 * Checks whether the given project uses the validation provided
	 * by the JSON Editor Plugin.
	 * (http://sourceforge.net/projects/eclipsejsonedit/)
	 */
	public static boolean hasValidationNature(IProject project) {
		IProjectDescription description;
		try {
			description = project.getDescription();
			String[] natures = description.getNatureIds();

			for (int i = 0; i < natures.length; ++i) {
				if (JSON_EDITOR_VALIDATION_NATURE_ID.equals(natures[i])) {
					return true;
				}
			}
		} catch (CoreException e) {
			e.printStackTrace();
		}
		
		return false;
	}
}

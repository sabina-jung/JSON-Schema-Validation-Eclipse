# About
JSON Schema Validation is an Plug-In for the Eclipse IDE that validates JSON files
and shows error markers. Using the [JSON Schema Validatior](https://github.com/fge/json-schema-validator), 
it checks for syntactic and -- if a [schema](http://json-schema.org/latest/json-schema-core.html)
is present -- semantic errors in JSON files. 
It may be used together with the 
[Eclipse JSON Editor](http://sourceforge.net/projects/eclipsejsonedit/).

The Plug-In looks for Schemas in the same directory as the data file
having the same basename followed by `Schema.json` or `.schema.json`.
E. g. when a `BooksSchema.json` is present, it will be taken as schema for a file named `Books.json`.
If no such file exists, it looks for a file `schema.json` in the current directory. 

The terms of the Mozilla Public License Version 2.0 apply for distributing this software. See `LICENSE.txt`.


# Requirements
Requires Eclipse Juno or later. 


# Usage
To activate JSON Validation, right click a project, and under 
`Configure` choose `Add/Remove Json Validation Nature`.


# Limitations
* Does not recognize sub-schemas.


# Building
Either of the following will generate a JAR file
suitable to be placed into the `dropins`-folder of Eclipse for installatation.


## Using Maven
Requires: Maven 3

You can run maven with `mvn package` to generate a JAR (in directory `target`)
suitable to be placed into the `dropins`-folder of eclipse for installation.


## Using Eclipse
Requires: Maven 3, Eclipse with Plug-In Development Tools (PDE) installed 
(Tested with "Juno" release and Java 7).

To copy the dependecies to /lib run this command in the project directory (containing `pom.xml`):

		mvn -P copylib package
		
The plug-in can now be built, packaged and ran by Eclipse.
Export the project as `Deployable Plug-Ins and Fragments` to get a JAR-File. 
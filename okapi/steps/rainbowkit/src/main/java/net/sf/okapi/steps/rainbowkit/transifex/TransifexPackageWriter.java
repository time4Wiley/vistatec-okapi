/*===========================================================================
  Copyright (C) 2011 by the Okapi Framework contributors
-----------------------------------------------------------------------------
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
===========================================================================*/

package net.sf.okapi.steps.rainbowkit.transifex;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import net.sf.okapi.common.Event;
import net.sf.okapi.common.Util;
import net.sf.okapi.common.XMLWriter;
import net.sf.okapi.common.exceptions.OkapiIOException;
import net.sf.okapi.common.filters.IFilter;
import net.sf.okapi.common.resource.ITextUnit;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.filters.po.POFilter;
import net.sf.okapi.filters.po.POWriter;
import net.sf.okapi.filters.rainbowkit.Manifest;
import net.sf.okapi.filters.rainbowkit.MergingInfo;
import net.sf.okapi.filters.transifex.Project;
import net.sf.okapi.lib.transifex.TransifexClient;
import net.sf.okapi.steps.rainbowkit.common.BasePackageWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransifexPackageWriter extends BasePackageWriter {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private POWriter potWriter;
	private POWriter trgWriter;

	public TransifexPackageWriter () {
		super(Manifest.EXTRACTIONTYPE_TRANSIFEX);
		setSupporstOneOutputPerInput(false);
	}
	
	@Override
	protected void processStartBatch () {
		manifest.setSubDirectories("original", "uploads", "downloads", "done", null, null, true);
		setTMXInfo(false, null, true, true, false);
		super.processStartBatch();
	}

	@Override
	protected void processEndBatch () {
		super.processEndBatch();
		XMLWriter report = null;
		PrintWriter pw = null;
		
		// Get the parameters/options for the Transifex project
		Parameters options = new Parameters();
		// Get the options from the parameters
		if ( !Util.isEmpty(params.getWriterOptions()) ) {
			options.fromString(params.getWriterOptions());
		}

		try {
			// Start the TXP file
			pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(
				manifest.getPackageRoot()+options.getProjectId()+".txp"), StandardCharsets.UTF_8));
			pw.println(Project.HOST + "=" + options.getServer());
			pw.println(Project.USER + "=" + options.getUser());
			pw.println(Project.PROJECTID + "=" + options.getProjectId());
			pw.println(Project.SOURCELOCALE + "=" + manifest.getSourceLocale().toString());
			pw.println(Project.TARGETLOCALE + "=" + manifest.getTargetLocale().toString());
	
			// Start HTML page with links
			String reportPath = manifest.getPackageRoot()+"linksToTransifex.html";
			report = new XMLWriter(reportPath);
			report.writeStartDocument();
			report.writeRawXML("<h1>Transifex Package Summary</h1>");
			report.writeLineBreak();
			report.writeRawXML(String.format("<p>Resources uploaded to Transifex in the project "
				+ "<b><a target='_blank' href='%s'>%s</a></b></p>",
				options.getServerWithoutAPI() + "projects/p/" + options.getProjectId() + "/",
				options.getProjectName()));
			report.writeLineBreak();
			report.writeRawXML("<table border='1' cellspacing='0' cellpadding='5'>");
			report.writeRawXML("<tr><th>Transifex Resource</th><th>Original Source File</th></tr>");
			report.writeLineBreak();
	
			// Create the Transifex client and initialize it
			TransifexClient cli = new TransifexClient(options.getServer());
			cli.setProject(options.getProjectId());
			cli.setCredentials(options.getUser(), options.getPassword());
			
			// Create the project
			//String[] res1 = cli.createProject(options.getProjectId(), options.getProjectName(), null, null);
			String[] res1 = cli.createProject(options.getProjectId(), options.getProjectName(), null,
				manifest.getSourceLocale(), false, options.getProjectUrl());
			if ( res1[0] == null ) {
				// Could not create the project
				logger.error(res1[1]);
				return;
			}
			for ( int id : manifest.getItems().keySet() ) {
				MergingInfo info = manifest.getItem(id);
				
				// Skip non-extracted files
				if ( info.getExtractionType().equals(Manifest.EXTRACTIONTYPE_NONE) ) continue;
				
				String poPath = manifest.getTempSourceDirectory() + info.getRelativeInputPath() + ".po";
				
				// Compute the resource filename to use in Transifex
				String resourceFile = Util.getFilename(poPath, true);
				String subdir = Util.getDirectoryName(info.getRelativeInputPath());
				if ( !subdir.isEmpty() ) {
					resourceFile = Util.makeId(subdir) + "_" + resourceFile;
				}
				
				res1 = cli.putSourceResource(poPath, manifest.getSourceLocale(), resourceFile);
	 			if ( res1[0] == null ) {
					logger.error(res1[1]);
					return;
				}
				// Else: set the resource id
				info.setResourceId(res1[1]);
				
				// write the resource in the TXP file
				pw.println(res1[1]);

				// Write the link to the resource in the HTML file
				report.writeRawXML(String.format("<tr><td><a target='_blank' href=\"%s\">%s</a></td>",
					options.getServerWithoutAPI() + "projects/p/" + options.getProjectId() + "/language/"+manifest.getSourceLocale().toPOSIXLocaleId()+"/",
					resourceFile));
				report.writeRawXML(String.format("<td>%s</td></tr>", info.getRelativeInputPath()));
				report.writeLineBreak();
				
				// Try to put the translated resource
				// In V2 this works only if there is at least one translation in the target PO
				poPath = makeTargetPath(info);
				if ( hasPreTranslation(poPath) ) {
					String[] res2 = cli.putTargetResource(poPath, manifest.getTargetLocale(), res1[1], resourceFile);
					if ( res2[0] == null ) {
						logger.error(res2[1]);
					}
				}
			
			}
			
			report.writeRawXML("</table>");
			report.writeRawXML("<p>For more information about this package, see: "
				+ "<a target='_blank' href='http://www.opentag.com/okapi/wiki/index.php?title=Rainbow_TKit_-_Transifex_Project'>"
				+ "Rainbow TKit - Transifex Project</a>.");
			report.writeRawXML("<p><font size='2'>Note: This report was generated when creating the translation package, "
				+ "the Transifex project may have been updated with other files since.</font></p>");
			report.close();
			
			// Save the manifest again (for the esourceId)
			if ( params.getOutputManifest() ) {
				manifest.save(null);
			}
	
			Util.openURL("file:///"+reportPath);
			
		}
		catch ( IOException e ) {
			throw new OkapiIOException("Error at the end of the batch.\n"+e.getMessage(), e);
		}
		finally {
			if ( report != null ) report.close();
			if ( pw != null ) pw.close();
		}
		
	}
	
	private boolean hasPreTranslation (String poPath) {
		IFilter filter = new POFilter();
		try {
			filter.open(new RawDocument(new File(poPath).toURI(), "UTF-8",
				manifest.getSourceLocale(), manifest.getTargetLocale()));
			while ( filter.hasNext() ) {
				Event event = filter.next();
				if ( event.isTextUnit() ) {
					ITextUnit tu = event.getTextUnit();
					if ( tu.hasTarget(manifest.getTargetLocale()) ) return true;
				}
			}
		}
		catch ( Throwable e ) {
			logger.error("Error while looking for pre-translation.\n"+e.getLocalizedMessage());
		}
		finally {
			if ( filter != null ) {
				filter.close();
			}
		}
		return false;
	}
	
	@Override
	protected void processStartDocument (Event event) {
		super.processStartDocument(event);

		// Set the source POT file
		potWriter = new POWriter();
		net.sf.okapi.filters.po.Parameters params = (net.sf.okapi.filters.po.Parameters)potWriter.getParameters();
		params.setOutputGeneric(true);
		potWriter.setMode(true, true, true);
		potWriter.setOptions(manifest.getSourceLocale(), "UTF-8");

		MergingInfo item = manifest.getItem(docId);
		String path = manifest.getTempSourceDirectory() + item.getRelativeInputPath() + ".po";
		potWriter.setOutput(path);

		// Set the target PO file
		trgWriter = new POWriter();
		params = (net.sf.okapi.filters.po.Parameters)trgWriter.getParameters();
		params.setOutputGeneric(true);
		trgWriter.setMode(true, false, false);
		trgWriter.setOptions(manifest.getTargetLocale(), "UTF-8");
		
		path = makeTargetPath(item);
		trgWriter.setOutput(path);
		
		potWriter.handleEvent(event);
		trgWriter.handleEvent(event);
	}
	
	@Override
	protected Event processEndDocument (Event event) {
		potWriter.handleEvent(event);
		trgWriter.handleEvent(event);
		close();
		return event;
	}

	@Override
	protected void processStartSubDocument (Event event) {
		potWriter.handleEvent(event);
		trgWriter.handleEvent(event);
	}
	
	@Override
	protected void processEndSubDocument (Event event) {
		potWriter.handleEvent(event);
		trgWriter.handleEvent(event);
	}
	
	@Override
	protected void processTextUnit (Event event) {
		// Skip non-translatable
		ITextUnit tu = event.getTextUnit();
		if ( !tu.isTranslatable() ) return;
		
		potWriter.handleEvent(event);
		trgWriter.handleEvent(event);
		writeTMXEntries(event.getTextUnit());
	}

	@Override
	public void close () {
		if ( potWriter != null ) {
			potWriter.close();
			potWriter = null;
		}
		if ( trgWriter != null ) {
			trgWriter.close();
			trgWriter = null;
		}
	}

	@Override
	public String getName () {
		return getClass().getName();
	}

	private String makeTargetPath (MergingInfo item) {
		String ex = Util.getExtension(item.getRelativeInputPath());
		String sd = Util.getDirectoryName(item.getRelativeInputPath());
		String fn = Util.getFilename(item.getRelativeInputPath(), false);
		
		return manifest.getTempSourceDirectory()
			+ ( sd.isEmpty() ? "" : sd + "/" )
			+ fn + "_" + manifest.getTargetLocale().toPOSIXLocaleId()
			+ ex + ".po";
	}

}

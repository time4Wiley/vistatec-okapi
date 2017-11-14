/*===========================================================================
  Copyright (C) 2009-2013 by the Okapi Framework contributors
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

package net.sf.okapi.steps.formatconversion;

import java.io.File;
import java.net.URI;

import net.sf.okapi.common.Event;
import net.sf.okapi.common.EventType;
import net.sf.okapi.common.ExecutionContext;
import net.sf.okapi.common.IParameters;
import net.sf.okapi.common.IResource;
import net.sf.okapi.common.IUserPrompt;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.UsingParameters;
import net.sf.okapi.common.Util;
import net.sf.okapi.common.filterwriter.IFilterWriter;
import net.sf.okapi.common.filterwriter.TMXFilterWriter;
import net.sf.okapi.common.pipeline.BasePipelineStep;
import net.sf.okapi.common.pipeline.annotations.StepParameterMapping;
import net.sf.okapi.common.pipeline.annotations.StepParameterType;
import net.sf.okapi.common.resource.Ending;
import net.sf.okapi.common.resource.ITextUnit;
import net.sf.okapi.common.resource.Property;
import net.sf.okapi.filters.pensieve.PensieveFilterWriter;
import net.sf.okapi.filters.po.POWriter;

@UsingParameters(Parameters.class)
public class FormatConversionStep extends BasePipelineStep {

	private static final int PO_OUTPUT = 0;
	private static final int TMX_OUTPUT = 1;
	private static final int TABLE_OUTPUT = 2;
	private static final int PENSIEVE_OUTPUT = 3;
	private static final int CORPUS_OUTPUT = 4;
	private static final int WORDTABLE_OUTPUT = 5;
	
	private Parameters params;
	private IFilterWriter writer;
	private boolean firstOutputCreated;
	private int outputType;
	private URI inputURI;
	private URI outputURI;
	private LocaleId targetLocale;
	private String rootDir;
	private ExecutionContext context;

	public FormatConversionStep () {
		params = new Parameters();
	}

	@StepParameterMapping(parameterType = StepParameterType.INPUT_URI)
	public void setInputURI (URI inputURI) {
		this.inputURI = inputURI;
	}
	
	@StepParameterMapping(parameterType = StepParameterType.OUTPUT_URI)
	public void setOutputURI (URI outputURI) {
		this.outputURI = outputURI;
	}
	
	@SuppressWarnings("deprecation")
	@StepParameterMapping(parameterType = StepParameterType.TARGET_LOCALE)
	public void setTargetLocale (LocaleId targetLocale) {
		this.targetLocale = targetLocale;
	}
	
	@StepParameterMapping(parameterType = StepParameterType.ROOT_DIRECTORY)
	public void setRootDirectory (String rootDir) {
		this.rootDir = rootDir;
	}

	@StepParameterMapping(parameterType = StepParameterType.EXECUTION_CONTEXT)
	public void setExecutionContext (ExecutionContext context) {
		this.context = context;
	}

	public String getDescription () {
		return "Converts the output of a filter into a specified file format."
			+ " Expects: filter events. Sends back: filter events.";
	}

	public String getName () {
		return "Format Conversion";
	}

	@Override
	public void setParameters (IParameters params) {
		this.params = (Parameters)params;
	}

	@Override
	public Parameters getParameters () {
		return params;
	}
	
	public Event handleEvent (Event event) {
		switch (event.getEventType()) {
		case START_BATCH:
			firstOutputCreated = false;
			if ( params.getOutputFormat().equals(Parameters.FORMAT_PO) ) {
				outputType = PO_OUTPUT;
				createPOWriter();
			}
			else if ( params.getOutputFormat().equals(Parameters.FORMAT_TMX) ) {
				outputType = TMX_OUTPUT;
				createTMXWriter();
			}
			else if ( params.getOutputFormat().equals(Parameters.FORMAT_PENSIEVE) ) {
				outputType = PENSIEVE_OUTPUT;
				createPensieveWriter();
			}
			else if ( params.getOutputFormat().equals(Parameters.FORMAT_TABLE) ) {
				outputType = TABLE_OUTPUT;
				createTableWriter();
			}
			else if ( params.getOutputFormat().equals(Parameters.FORMAT_CORPUS) ) {
				outputType = CORPUS_OUTPUT;
				createCorpusWriter();
			}
			else if ( params.getOutputFormat().equals(Parameters.FORMAT_WORDTABLE) ) {
				outputType = WORDTABLE_OUTPUT;
				createWordTableWriter();
			}
			// Start sending event to the writer
			writer.handleEvent(event);
			break;
			
		case END_BATCH:
			if ( params.getSingleOutput() ) {
				Ending ending = new Ending("end");
				writer.handleEvent(new Event(EventType.END_DOCUMENT, ending));
				writer.close();
			}
			break;
			
		case START_DOCUMENT:
			if ( !firstOutputCreated )
				writer.setOptions(targetLocale, "UTF-8"); // in case target locale changed in this document
			if ( !firstOutputCreated || !params.getSingleOutput() ) {
				switch ( outputType ) {
				case PO_OUTPUT:
					startPOOutput();
					break;
				case TMX_OUTPUT:
					startTMXOutput();
					break;
				case TABLE_OUTPUT:
					startTableOutput();
					break;
				case PENSIEVE_OUTPUT:
					startPensieveOutput();
					break;
				case CORPUS_OUTPUT:
					startCorpusOutput();
					break;
				case WORDTABLE_OUTPUT:
					startWordTableOutput();
					break;
				}
				writer.handleEvent(event);
			}
			break;
			
		case END_DOCUMENT:
			if ( !params.getSingleOutput() ) {
				writer.handleEvent(event);
				writer.close();
			}
			// Else: Do nothing
			break;
			
		case START_SUBDOCUMENT:
		case END_SUBDOCUMENT:
		case START_GROUP:
		case END_GROUP:
		case START_SUBFILTER:
		case END_SUBFILTER:
			writer.handleEvent(event);
			break;

		case TEXT_UNIT:
			processTextUnit(event);
			break;
			
		case START_BATCH_ITEM:
		case END_BATCH_ITEM:
		case RAW_DOCUMENT:
		case DOCUMENT_PART:
		case CUSTOM:
		case CANCELED:
		case MULTI_EVENT:
		case NO_OP:
		case PIPELINE_PARAMETERS:
			// Do nothing
			break;
		}
		return event;
	}

	protected void processTextUnit (Event event) {
		ITextUnit tu = event.getTextUnit();
		// Skip empty or code-only entries
		if ( params.getSkipEntriesWithoutText() ) {
			if ( !tu.getSource().hasText(true, false) ) return;
		}
		
		// Skip non-approved entries if requested
		if ( params.getApprovedEntriesOnly() ) {
			boolean approved = false;
			if ( tu.hasTargetProperty(targetLocale, Property.APPROVED) ) {
				approved = tu.getTargetProperty(targetLocale, Property.APPROVED).getBoolean();
			}
			if ( !approved ) return;
		}
		
		// If requested, overwrite the target
		switch ( params.getTargetStyle() ) {
		case Parameters.TRG_FORCEEMPTY:
			tu.createTarget(targetLocale, true, IResource.CREATE_EMPTY);
			break;
		case Parameters.TRG_FORCESOURCE:
			tu.createTarget(targetLocale, true, IResource.COPY_ALL);
			break;
		}
		writer.setOptions(targetLocale, "UTF-8"); // we were getting a new raw document but no start document
		writer.handleEvent(event);
	}

	private void createPOWriter () {
		if (params.getWriter() == null) {
			writer = new POWriter();
		} else {
			writer = params.getWriter();
		}
		net.sf.okapi.filters.po.Parameters outParams = (net.sf.okapi.filters.po.Parameters)writer.getParameters();
		outParams.setOutputGeneric(params.getUseGenericCodes());
	}
	
	private void startPOOutput () {
		File outFile;
		if ( params.getSingleOutput() ) {
			outFile = new File(Util.fillRootDirectoryVariable(params.getOutputPath(), rootDir));
		}
		else {
			if ( params.getAutoExtensions() ) {
				outFile = new File(inputURI.getPath() + ".po");
			}
			else {
				outFile = new File(outputURI.getPath());
			}
		}
		if (outFile.exists()) promptShouldOverwrite();
		writer.setOutput(outFile.getPath());
		// Not needed, writer does this: Util.createDirectories(outFile.getAbsolutePath());
		writer.setOptions(targetLocale, "UTF-8");
		firstOutputCreated = true;
	}

	private void createTMXWriter () {
		if (params.getWriter() == null) {
			writer = new TMXFilterWriter();
		} else {
			writer = params.getWriter();
		}
//		net.sf.okapi.filters.po.Parameters outParams = (net.sf.okapi.filters.po.Parameters)writer.getParameters();
//		outParams.outputGeneric = params.getUseGenericCodes();
	}
	
	private void startTMXOutput () {
		File outFile;
		if ( params.getSingleOutput() ) {
			outFile = new File(Util.fillRootDirectoryVariable(params.getOutputPath(), rootDir));
		}
		else {
			if ( params.getAutoExtensions() ) {
				outFile = new File(inputURI.getPath() + ".tmx");
			}
			else {
				outFile = new File(outputURI);
			}
		}
		if (outFile.exists()) promptShouldOverwrite();
		writer.setOutput(outFile.getPath());
		writer.setOptions(targetLocale, "UTF-8");
		firstOutputCreated = true;
	}

	private void createCorpusWriter () {
		if (params.getWriter() == null) {
			writer = new CorpusFilterWriter();
		} else {
			writer = params.getWriter();
		}
	}
	
	private void startCorpusOutput () {
		File outFile;
		if ( params.getSingleOutput() ) {
			outFile = new File(Util.fillRootDirectoryVariable(params.getOutputPath(), rootDir));
		}
		else {
			if ( params.getAutoExtensions() ) {
				outFile = new File(inputURI.getPath() + ".txt"); 
			}
			else {
				outFile = new File(outputURI);
			}
		}
		if (outFile.exists()) promptShouldOverwrite();
		writer.setOutput(outFile.getPath());
		writer.setOptions(targetLocale, "UTF-8");
		firstOutputCreated = true;
	}

	private void createTableWriter () {
		if ( params.getWriter() == null ) { 
			writer = new TableFilterWriter();
		}
		else {
			writer = params.getWriter();
		}
		writer.setOptions(targetLocale, "UTF-8");
		TableFilterWriterParameters options = (TableFilterWriterParameters)writer.getParameters();
		options.fromString(params.getFormatOptions());
		// Format options for table are not set view via UI so we need to
		// ensure option from step overrides default for generic inline case
		if ( params.getUseGenericCodes() ) {
			options.setInlineFormat(TableFilterWriterParameters.INLINE_GENERIC);
		}
	}
	
	private void startTableOutput () {
		File outFile;
		if ( params.getSingleOutput() ) {
			outFile = new File(Util.fillRootDirectoryVariable(params.getOutputPath(), rootDir));
		}
		else {
			if ( params.getAutoExtensions() ) {
				outFile = new File(inputURI.getPath() + ".txt");
			}
			else {
				outFile = new File(outputURI);
			}
		}
		if (outFile.exists()) promptShouldOverwrite();
		// Not needed, writer does this: Util.createDirectories(outFile.getAbsolutePath());
		writer.setOutput(outFile.getPath());
		writer.setOptions(targetLocale, "UTF-8");
		firstOutputCreated = true;
	}

	private void createWordTableWriter () {
		if ( params.getWriter() == null ) { 
			writer = new WordTableFilterWriter();
		}
		else {
			writer = params.getWriter();
		}
	}
	
	private void startWordTableOutput () {
		File outFile;
		if ( params.getSingleOutput() ) {
			outFile = new File(Util.fillRootDirectoryVariable(params.getOutputPath(), rootDir));
		}
		else {
			if ( params.getAutoExtensions() ) {
				outFile = new File(inputURI.getPath() + ".rtf");
			}
			else {
				outFile = new File(outputURI);
			}
		}
		if (outFile.exists()) promptShouldOverwrite();
		// Not needed, writer does this: Util.createDirectories(outFile.getAbsolutePath());
		writer.setOutput(outFile.getPath());
		writer.setOptions(targetLocale, "windows-1252"); // Internal codes are Unicode
		firstOutputCreated = true;
	}

	private void createPensieveWriter () {
		if (params.getWriter() == null) {
			writer = new PensieveFilterWriter();
		} else {
			writer = params.getWriter();
		}
		((PensieveFilterWriter)writer).setOverwriteSameSource(params.getOverwriteSameSource());
	}

	private void startPensieveOutput () {
		File outFile;
		if ( params.getSingleOutput() ) {
			outFile = new File(Util.fillRootDirectoryVariable(params.getOutputPath(), rootDir));
		}
		else {
			if ( params.getAutoExtensions() ) {
				outFile = new File(inputURI.getPath() + ".pentm");
			}
			else {
				outFile = new File(outputURI.getPath());
			}
		}
		if (outFile.exists()) promptShouldOverwrite();
		writer.setOutput(outFile.getPath());
		writer.setOptions(targetLocale, "UTF-8");
		firstOutputCreated = true;
	}

	private void promptShouldOverwrite() {
		if (context == null || context.getIsNoPrompt()) return;
		
		String promptClass = context.getIsGui() ? "net.sf.okapi.common.ui.UserPrompt"
			: "net.sf.okapi.common.UserPrompt";
		
		IUserPrompt p;
		try {
			p = (IUserPrompt)Class.forName(promptClass).newInstance();
			p.initialize(context.getUiParent(), context.getApplicationName());
		}
		catch ( Throwable e ) {
			throw new InstantiationError("Could not instantiate user prompt.");
		}
		p.promptOKCancel("A file already exists in the target location.\nSelect \"OK\" to overwrite it.");
	}
}

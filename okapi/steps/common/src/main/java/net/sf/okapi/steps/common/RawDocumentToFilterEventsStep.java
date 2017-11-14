/*===========================================================================
  Copyright (C) 2008-2011 by the Okapi Framework contributors
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

package net.sf.okapi.steps.common;

import net.sf.okapi.common.Event;
import net.sf.okapi.common.EventType;
import net.sf.okapi.common.UsingParameters;
import net.sf.okapi.common.Util;
import net.sf.okapi.common.exceptions.OkapiException;
import net.sf.okapi.common.exceptions.OkapiIOException;
import net.sf.okapi.common.filters.IFilter;
import net.sf.okapi.common.filters.IFilterConfigurationMapper;
import net.sf.okapi.common.pipeline.BasePipelineStep;
import net.sf.okapi.common.pipeline.annotations.StepParameterMapping;
import net.sf.okapi.common.pipeline.annotations.StepParameterType;
import net.sf.okapi.common.resource.RawDocument;

/**
 * Converts a {@link RawDocument} into filter events.
 * <p>This class implements the {@link net.sf.okapi.common.pipeline.IPipelineStep}
 * interface for a step that takes a {@link RawDocument} and to generate its 
 * corresponding events either: a provided {@link IFilter} implementation, or the
 * filter configuration mapper accessible through the pipeline's context.
 * <p>This step can also work with the filters that generates multiple documents.
 * @see FilterEventsToRawDocumentStep
 * @see FilterEventsWriterStep
 */
@UsingParameters() // No parameters
public class RawDocumentToFilterEventsStep extends BasePipelineStep {

	private IFilter filter;
	private boolean filterfromSetFilter;
	private boolean isDone;
	private IFilterConfigurationMapper fcMapper;
	private String filterConfigId;
	private boolean multiDocuments;

	/**
	 * Creates a new RawDocumentToFilterEventsStep object. This constructor is
	 * needed to be able to instantiate an object from newInstance()
	 */
	public RawDocumentToFilterEventsStep () {
	}
	
	/**
	 * Creates a new RawDocumentToFilterEventsStep object with a given filter.
	 * Use this constructor to create an object that is using a filter set using
	 * the one provided here, or using {@link #setFilter(IFilter)}, not using
	 * the filter configuration mapper of the pipeline context.
	 * 
	 * @param filter
	 *            the filter to set.
	 */
	public RawDocumentToFilterEventsStep (IFilter filter) {
		setFilter(filter);
	}

	// This is redundant with the other parameters
	// So it is not part of the 'published' parameters, but remain
	// accessible to manual coder
	public void setFilter (IFilter filter) {
		filterfromSetFilter = true;
		this.filter = filter;
	}

	@StepParameterMapping(parameterType = StepParameterType.FILTER_CONFIGURATION_MAPPER)
	public void setFilterConfigurationMapper (IFilterConfigurationMapper fcMapper) {
		this.fcMapper = fcMapper;
	}
	
	@StepParameterMapping(parameterType = StepParameterType.FILTER_CONFIGURATION_ID)
	public void setFilterConfigurationId (String filterConfigId) {
		this.filterConfigId = filterConfigId;
	}
	
	public String getName () {
		return "Raw Document to Filter Events";
	}

	public String getDescription () {
		return "Convert a raw document into filter events."
			+ " Expects: raw document. Sends back: filter events.";
	}

	@Override
	public Event handleEvent (Event event) {
		switch (event.getEventType()) {
		case START_BATCH:
			isDone = true;
			break;

		case START_BATCH_ITEM:
			// Needed because the process() method of the pipeline expects
			// hasEvents to be set to true to prime things.
			isDone = false;
			return event;

		// Initialize the filter on RAW_DOCUMENT
		case RAW_DOCUMENT:
			multiDocuments = false;
			if ( !filterfromSetFilter ) {
				// Filter is to be set from the batch item info
				if ( Util.isEmpty(filterConfigId) ) {
					// No filter configuration provided: just pass it down
					isDone = true;
					return event;
				}
				// Else: Get the filter to use
				filter = fcMapper.createFilter(filterConfigId, filter);
				if ( filter == null ) {
					throw new OkapiException(String.format("Unsupported filter type '%s'.", filterConfigId));
				}
			}
			// Is it a filter that creates events for multiple documents or just one
			// Note: we could also add a method in IFilter for that information
			multiDocuments = "okf_rainbowkit;okf_transifex;".contains(filter.getName());
			isDone = false;
			// Open the document
			filter.open(event.getRawDocument());
			// Return the first event from the filter
			if ( filter.hasNext() ) {
				return filter.next();
			}
			// Else: no events
			throw new OkapiIOException(String.format("No events available from '%s'.", filter.getDisplayName()));
			
		case END_BATCH_ITEM:
			if (filter != null) filter.close();
			return event;
			
		default: // Do nothing otherwise
			break;
		}

		if ( isDone ) {
			return event;
		}
		else {
			// Get events from the filter
			Event e = event;
			if ( filter != null ) {
				if ( multiDocuments ) {
					// Multi-documents: end when the filter says so.
					if ( filter.hasNext() ) {
						e = filter.next();
					}
					else { // We are done
						e = Event.NOOP_EVENT;
						isDone = true;
					}
				}
				else { // One document only: stop when getting the end-of-document event.
					e = filter.next();
					if ( e.getEventType() == EventType.END_DOCUMENT ) {
						// END_DOCUMENT is the end of this raw document
						isDone = true;
					}
				}
			}
			return e;
		}
	}

	@Override
	public boolean isDone () {
		return isDone;
	}

	public void destroy () {
		if (filter != null) {
			filter.close();
		}
	}

	public void cancel () {
		if (filter != null) {
			filter.cancel();
		}
	}

}

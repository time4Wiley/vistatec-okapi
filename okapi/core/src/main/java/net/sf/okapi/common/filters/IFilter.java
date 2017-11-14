/*===========================================================================
  Copyright (C) 2008-2013 by the Okapi Framework contributors
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

package net.sf.okapi.common.filters;

import java.util.List;

import net.sf.okapi.common.Event;
import net.sf.okapi.common.IParameters;
import net.sf.okapi.common.encoder.EncoderManager;
import net.sf.okapi.common.filterwriter.IFilterWriter;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.common.skeleton.ISkeletonWriter;

/**
 * Common set of methods to extract translatable text and its associated data.
 * <p>The following example shows a typical use of IFilter:
 * <pre>
 * MyUtlity myUtility = new MyUtility(); // Some object that do things with filter events
 * IFilter filter = new MyFilter(); // A filter implementation
 * filter.open(new RawDocument(URI("myFile.ext"), "UTF-8", "en");
 * while ( filter.hasNext() ) {
 *    myUtility.handleEvent(filter.next());
 * }
 * filter.close();
 * </pre>
 */
public interface IFilter extends AutoCloseable {
	
	/**
	 * Prefix marker indicating a sub-filter in the name of a
	 * {@link net.sf.okapi.common.resource.StartDocument StartDocument} object created
	 * when processing content with sub-filters. 
	 */
	public static String SUB_FILTER = "sub-filter:"; 

	/**
	 * Gets the name/identifier of this filter.
	 * @return The name/identifier of the filter.
	 */
	public String getName ();
	
	/**
	 * Gets the localizable display name of this filter.
	 * @return the localizable display name of this filter.
	 */
	public String getDisplayName ();

	/**
	 * Opens the input document described in a give RawDocument object.
	 * Skeleton information is always created when you use this method.
	 * @param input The RawDocument object to use to open the document.
	 */
	public void open (RawDocument input);

	/**
	 * Opens the input document described in a give RawDocument object, and
	 * optionally creates skeleton information.
	 * @param input The RawDocument object to use to open the document.
	 * @param generateSkeleton true to generate the skeleton data, false otherwise.
	 */
	public void open (RawDocument input,
		boolean generateSkeleton);

	/**
	 * Closes the input document. Developers should call this method from within their code
	 * before sending the last event: This can allow writer objects to overwrite the input file
	 * when they receive the last event. This method must also be safe to call even
	 * if the input document is not opened.
	 */
	public void close ();

	/**
	 * Indicates if there is an event to process.
	 * <p>Implementer Note: The caller must be able to call this method several times without changing state.
	 * @return True if there is at least one event to process, false if not.
	 */
	public boolean hasNext ();

	/**
	 * Gets the next event available.
	 * Calling this method can be done only once on each event.
	 * @return The next event available or null if there are no events.
	 */
	public Event next ();	

	/**
	 * Cancels the current process.
	 */
	public void cancel ();

	/**
	 * Gets the current parameters for this filter.
	 * @return The current parameters for this filter, or null if this filter
	 * has no parameters.
	 */
	public IParameters getParameters ();

	/**
	 * Sets new parameters for this filter.
	 * @param params The new parameters to use.
	 */
	public void setParameters (IParameters params);

	/**
	 * Sets the filter configuration mapper for this filter. This object is
	 * used by this filter if it needs to instantiate sub-filters. The implementations
	 * of IFilter that do not use sub-filters can use an empty stub for this method. 
	 * @param fcMapper the mapper to set.
	 */
	public void setFilterConfigurationMapper (IFilterConfigurationMapper fcMapper);

	/**
	 * Creates a new ISkeletonWriter object that corresponds to the type of skeleton 
	 * this filter uses.
	 * @return A new instance of ISkeletonWriter for the type of skeleton this filter
	 * uses.
	 */
	public ISkeletonWriter createSkeletonWriter ();
	
	/**
	 * Creates a new IFilterWriter object from the most appropriate class to
	 * use with this filter.
	 * @return A new instance of IFilterWriter for the preferred implementation
	 * for this filter.
	 */
	public IFilterWriter createFilterWriter ();

	/**
	 * Gets the EncoderManager object for this filter. This encoder manager should
	 * provided the mappings to the different MIME types used by the filter.
	 * @return the EncoderManager for this filter.
	 */
	public EncoderManager getEncoderManager ();
	
	/**
	 * Gets the MIME type of the format supported by this filter.
	 * @return The MIME type of the format supported by this filter.
	 */
	public String getMimeType ();

	/**
	 * Gets the list of all predefined configurations for this filter. 
	 * @return a list of the all predefined configurations for this filter.
	 */
	public List<FilterConfiguration> getConfigurations();	
}

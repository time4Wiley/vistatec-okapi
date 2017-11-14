/*===========================================================================
  Copyright (C) 2008-2009 by the Okapi Framework contributors
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

package net.sf.okapi.lib.extra.filters;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedList;

import net.sf.okapi.common.Event;
import net.sf.okapi.common.EventType;
import net.sf.okapi.common.IParameters;
import net.sf.okapi.common.Util;
import net.sf.okapi.common.exceptions.OkapiException;
import net.sf.okapi.common.filters.FilterConfiguration;
import net.sf.okapi.common.filters.IFilter;
import net.sf.okapi.common.filterwriter.IFilterWriter;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.common.resource.StartDocument;
import net.sf.okapi.common.skeleton.ISkeletonWriter;
import net.sf.okapi.lib.extra.Notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 * 
 * @version 0.1, 10.06.2009
 */
public class CompoundFilter extends AbstractBaseFilter {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private LinkedList<IFilter> subFilters = new LinkedList<IFilter>();
	
	private IFilter activeSubFilter = null;
	private RawDocument input;
	
	public IFilter getActiveSubFilter() {
		
		return activeSubFilter;
	}

	protected void setActiveSubFilter(IFilter activeSubFilter) {
		
		this.activeSubFilter = activeSubFilter;
		
		IParameters params = getParameters();
		if (params instanceof CompoundFilterParameters && activeSubFilter instanceof AbstractBaseFilter)
			((CompoundFilterParameters) params).setActiveParameters(
					((AbstractBaseFilter) activeSubFilter).getParametersClassName());
	}

	protected <A extends AbstractBaseFilter> boolean addSubFilter(Class<A> subFilterClass) {
		
		if (subFilters == null) return false;
		boolean res = false;
	
		IFilter curSubFilter = null;
		
		try {
			Constructor<A> cc = (Constructor<A>) subFilterClass.getConstructor(new Class[] {});
			if (cc == null) return false;
			
			curSubFilter = (IFilter) cc.newInstance(new Object[] {});
			
		} catch (InstantiationException e) {
			
			//e.printStackTrace();
			logger.debug("Subfilter instantiation failed: {}", e.getMessage());
			return false;
			
		} catch (IllegalAccessException e) {
			
			//e.printStackTrace();
			logger.debug("Subfilter instantiation failed: {}", e.getMessage());
			return false;
			
		} catch (SecurityException e) {
			
			//e.printStackTrace();
			logger.debug("Subfilter instantiation failed: {}", e.getMessage());
			return false;
			
		} catch (NoSuchMethodException e) {
			
			//e.printStackTrace();
			logger.debug("Subfilter instantiation failed: {}", e.getMessage());
			return false;
			
		} catch (IllegalArgumentException e) {
			
			//e.printStackTrace();
			logger.debug("Subfilter instantiation failed: {}", e.getMessage());
			return false;
			
		} catch (InvocationTargetException e) {
			
			//e.printStackTrace();
			logger.debug("Subfilter instantiation failed: {}", e.getMessage());
			return false;
		}
		
		res = subFilters.add(curSubFilter);
		
		if (!res) return false;
		
		curSubFilter = subFilters.getLast();
		if (curSubFilter == null) return false;
		
		this.addConfigurations(curSubFilter.getConfigurations());
			
		
		if (activeSubFilter == null)
			activeSubFilter = curSubFilter; // The first non-empty registered one will become active
				
		return res;
	}
	
	@Override
	public void setParameters(IParameters params) {
		
		super.setParameters(params);
		
		if (params == null && activeSubFilter != null)
			activeSubFilter.setParameters(null);

//		if (params instanceof CompoundFilterParameters) {
//			
//			((CompoundFilterParameters) params).filter = this;
//			updateSubfilter();
//		}
	}
	
	public IParameters getActiveParameters() {
		
		return (activeSubFilter != null) ? activeSubFilter.getParameters() : null; 
	}
	
	/**
	 * Get a configId string identifying the filter's default configuration (first on the list of configurations) 
	 * @return configId of default configuration
	 */
	private String getDefaultConfigId() {
		
		if (Util.isEmpty(configList)) return "";
		
		FilterConfiguration config = configList.get(0);
		if (config == null) return "";
		
		return config.configId;
	}
	
@Override
	public boolean setConfiguration(String configId) {
		
		boolean res = super.setConfiguration(configId);
		
		if (Util.isEmpty(configId))
			configId = getDefaultConfigId();
		
		IFilter subFilter = findConfigProvider(configId);
		res &= (subFilter != null); 
		
		if (res && activeSubFilter != subFilter) {
			
			// Some finalization of the previous one might be needed
			//activeSubFilter = subFilter;
			
			setActiveSubFilter(subFilter);
		}
		
		// Load config from its config file
		
		FilterConfiguration config = findConfiguration(configId);
		if (config == null) return res;
		
		IParameters params = getParameters();
		
		if (config.parametersLocation != null && params instanceof CompoundFilterParameters) {
			
			URL url = this.getClass().getResource(config.parametersLocation);
			params.load(url, false);
		}
			
		IParameters params2 = getActiveParameters();
		
		if (params2 != null)
			params2.fromString(params.toString());
		
		return res;
	}

	/**
	 * Finds the sub-filter handling the given configuration.
	 * @param configId configuration identifier
	 * @return a sub-filter reference or null if the configuration is not supported by any sub-filter 
	 */
	private IFilter findConfigProvider(String configId) {
		
		if (Util.isEmpty(configList)) return null;
		
		for (FilterConfiguration config : configList) {
			
			if (config == null) continue;
			if (config.configId.equalsIgnoreCase(configId)) 
				return findSubFilter(config.filterClass);
		}
		
		return null;
	}

	/**
	 * Finds an instance of the given class in the internal list of sub-filters.
	 * @param filterClass name of the class sought
	 * @return a sub-filter reference or null if no sub-filter was found 
	 */
	private IFilter findSubFilter(String filterClass) {
		
		if (Util.isEmpty(filterClass)) return null;
		if (subFilters == null) return null;
		
		for (IFilter subFilter : subFilters) {
			
			if (subFilter == null) continue;
			if (subFilter.getClass() == null) continue;
			
			if (subFilter.getClass().getName().equalsIgnoreCase(filterClass)) 
				return subFilter;
		}
		
		return null;
	}
	
	private IFilter findSubFilterByParameters(String parametersClassName) {
		
		if (Util.isEmpty(parametersClassName)) return null;
		if (subFilters == null) return null;		
		
		for (IFilter subFilter : subFilters) {

			if (!(subFilter instanceof AbstractBaseFilter)) continue;
			
			if (((AbstractBaseFilter) subFilter).getParametersClassName().equalsIgnoreCase(parametersClassName)) 
				return subFilter;
		}
		
		return null;
	}

	public void cancel() {
		
		if (activeSubFilter != null) activeSubFilter.cancel();
	}

	public void close() {
		if (input != null) {
			input.close();
		}
		if (activeSubFilter != null) activeSubFilter.close();
	}

	public IFilterWriter createFilterWriter() {
		
		return (activeSubFilter != null) ? activeSubFilter.createFilterWriter() : null;
	}

	public ISkeletonWriter createSkeletonWriter() {
		
		return (activeSubFilter != null) ? activeSubFilter.createSkeletonWriter() : null;
	}

	public boolean hasNext() {
		
		return (activeSubFilter != null) ? activeSubFilter.hasNext() : false;
	}

	public Event next() {
		
		Event event = (activeSubFilter != null) ? activeSubFilter.next() : null;
		
		if (event != null && event.getEventType() == EventType.START_DOCUMENT) {

			// Fix START_DOCUMENT to return compound filter parameters, and not the activeSubFilter's
			StartDocument startDoc = (StartDocument) event.getResource();
			startDoc.setFilterParameters(getParameters());
		}
		return event; 
	}

	public void open(RawDocument input) {
		
//		updateSubfilter();
		this.input = input;
		if (activeSubFilter != null) activeSubFilter.open(input);
	}

	public void open(RawDocument input, boolean generateSkeleton) {
		
//		updateSubfilter();
		if (activeSubFilter != null) activeSubFilter.open(input, generateSkeleton);
	}

	private void updateSubfilter() {

		IParameters params = getParameters();
		
		String className = "";
		
		if (params instanceof CompoundFilterParameters)
			className = ((CompoundFilterParameters) params).getParametersClassName();
		else
			return;
		
		if (Util.isEmpty(className)) return;
		
		activeSubFilter = findSubFilterByParameters(className); // !!! not seveActiveSubFilter() to prevent a deadlock
		
		IParameters params2 = getActiveParameters();
		
		if (params2 != null && params != null)
			params2.fromString(params.toString());
		
		if (activeSubFilter != null)
			activeSubFilter.setParameters(params2); // to update internal rules of regex filter for example
	}

	@Override
	public boolean exec(Object sender, String command, Object info) {
		
		if (super.exec(sender, command, info)) return true;
		
		if (command.equalsIgnoreCase(Notification.PARAMETERS_CHANGED)) {
			
			updateSubfilter();
			return true;
		}
		
		return false;
	}

	/* (non-Javadoc)
	 * @see net.sf.okapi.common.framework.AbstractComponent#component_done()
	 */
	@Override
	protected void component_done() {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see net.sf.okapi.common.framework.AbstractComponent#component_init()
	 */
	@Override
	protected void component_init() {
		// TODO Auto-generated method stub
		
	}
	
}

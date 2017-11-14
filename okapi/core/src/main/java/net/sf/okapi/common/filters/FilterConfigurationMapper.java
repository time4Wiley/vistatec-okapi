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

package net.sf.okapi.common.filters;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import net.sf.okapi.common.DefaultFilenameFilter;
import net.sf.okapi.common.IParameters;
import net.sf.okapi.common.IParametersEditor;
import net.sf.okapi.common.ParametersEditorMapper;
import net.sf.okapi.common.Util;
import net.sf.okapi.common.exceptions.OkapiFilterCreationException;
import net.sf.okapi.common.plugins.PluginItem;
import net.sf.okapi.common.plugins.PluginsManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the {@link IFilterConfigurationMapper} interface.
 * In this implementation the custom configurations are stored as simple files in the file system
 * of the machine and the value for {@link FilterConfiguration#parametersLocation} for a custom
 * configuration is filename of the parameters file. The directory where the files are
 * located is defined with the {@link #setCustomConfigurationsDirectory(String)}.
 */
public class FilterConfigurationMapper extends ParametersEditorMapper implements IFilterConfigurationMapper {
	private final Logger LOGGER = LoggerFactory.getLogger(getClass());
	
	/**
	 * Extension of the custom configuration files of this mapper.
	 */
	public static final String CONFIGFILE_EXT = ".fprm";
	/**
	 * Character used to separate the filter name from the custom configuration name
	 * in a custom configuration identifier for this mapper. 
	 */
	public static final char CONFIGFILE_SEPARATOR = '@';

	private LinkedHashMap<String, FilterConfiguration> configMap;
	private ArrayList<FilterInfo> filters;
	private String customParmsDir;
	private IFilter tmpFilter;
	
	/**
	 * Splits a configuration identifier into a filter identifier and the parameters info.
	 * @param configId the configuration identifier to split.
	 * @return an array of two strings: 0=filter (e.g. "okf_xml", 1=parameter info (or null).
	 */
	static public String[] splitFilterFromConfiguration (String configId) {
		String[] res = new String[2];
		// Get the filter
		int n  = configId.indexOf(CONFIGFILE_SEPARATOR);
		if ( n == -1 ) {
			// Try '-' then
			n = configId.indexOf('-');
			if ( n == -1 ) {
				// Try '_'
				n = configId.indexOf('_');
				if ( n == -1 ) {
					res[0] = configId;
					return res; // The filter is the configID (default case)
				}
				else { // Check for "okf_" case
					if ( configId.substring(0, n).equals("okf") ) {
						n = configId.indexOf('_', n+1);
						if ( n == -1 ) {
							res[0] = configId;
							return res; // The filter is the configID (default case) 
						}
					}
				}
			}
		}
		res[0] = configId.substring(0, n);
		res[1] = configId.substring(n+1);
		return res;
	}
	
	/**
	 * Creates a new FilterConfigurationMapper object with no mappings and the
	 * custom configuration directory set to the current directory.
	 */
	public FilterConfigurationMapper () {
		super();
		configMap = new LinkedHashMap<String, FilterConfiguration>();
		filters = new ArrayList<FilterInfo>();
		String customDir;
		try {
			customDir = Util.getDirectoryName((new File(".")).getAbsolutePath());
		} catch (SecurityException ex) {
			// Resolving "." will try to read the user.dir system property,
			// which is restricted in some scenarios so we fall back to
			// something safe in that case. See "Forbidden System Properties":
			// https://docs.oracle.com/javase/tutorial/deployment/doingMoreWithRIA/properties.html
			customDir = "/";
		}
		setCustomConfigurationsDirectory(customDir);
	}

	public void addFromPlugins (PluginsManager pm) {
		java.util.List<PluginItem> list = pm.getList();
		for ( PluginItem item : list ) {
			if ( item.getType() == PluginItem.TYPE_IFILTER ) {
				String paramsClassName = addConfigurations(item.getClassName(), pm.getClassLoader());
				if ( item.getEditorDescriptionProvider() != null ) {
					addDescriptionProvider(item.getEditorDescriptionProvider(), paramsClassName);
				}
				if ( item.getParamsEditor() != null ) {
					addEditor(item.getParamsEditor(), paramsClassName);
				}
			}
		}
	}

	@Override
	public void addConfigurations (String filterClass) {
		addConfigurations(filterClass, null);
	}

	@Override
	public void addConfiguration (FilterConfiguration config) {
		configMap.put(config.configId, config);
	}

	@Override
	public IFilter createFilter (String configId) {
		return createFilter(configId, null);
	}
	
	@Override
	public IFilter createFilter (String configId,
		IFilter existingFilter)
	{
		// Get the configuration object for the given configId
		FilterConfiguration fc = configMap.get(configId);
		if ( fc == null ) {
			LOGGER.error("Cannot find filter configuration '{}'", configId);
			return null;
		}
		
		// Instantiate the filter (or re-use one)
		IFilter filter = instantiateFilter(fc, existingFilter);

		// TODO SV: Should we assign this FCM to the created/reused filter?
		// filter.setFilterConfigurationMapper(this);
		
		IParameters params = filter.getParameters();
		if ( params == null ) {
			if ( fc.custom ) {
				throw new OkapiFilterCreationException(String.format(
					"Cannot create default parameters for '%s'.", fc.configId));
			}
			else { // No parameters, nothing more to do
				return filter;
			}
		}

		// Always load the parameters (if there are parameters)
		if ( fc.parametersLocation != null ) {
			if ( fc.custom ) {
				params = getCustomParameters(fc, filter);
			} else if (fc.parametersLocation != null ) {
				// Note that we cannot assume the parameters are the same
				// if we re-used an existing filter, as we cannot compare the 
				// configuration identifiers
				params.load(filter.getClass().getResourceAsStream(fc.parametersLocation), false);
			}
		} else if (fc.parameters != null) {
			params.fromString(fc.parameters.toString());
		} else { 
			params.reset();
		}
		
		return filter;
	}

	@Override
	public IParameters getParameters (FilterConfiguration config) {
		return getParameters(config, null);
	}
	
	@Override
	public IParameters getParameters (FilterConfiguration config,
		IFilter existingFilter)
	{
		IFilter filter = instantiateFilter(config, existingFilter);
		IParameters params = filter.getParameters();
		
		// No parameters for this filter
		if ( params == null ) {
			return null;
		}
		
		// Default parameters
		if ( config.parametersLocation == null && config.parameters == null ) {
			return params;
		}
		
		if ( config.custom ) {
			params = getCustomParameters(config, filter);
		} else if (config.parametersLocation != null) {
			// Note that we cannot assume the parameters are the same
			// if we re-used an existing filter, as we cannot compare the 
			// configuration identifiers
			URL url = filter.getClass().getResource(config.parametersLocation);
			params.load(url, false);
		}  else if (config.parameters != null) {
			params.fromString(config.parameters.toString());
		}

		return params;
	}

	@Override
	public IParametersEditor createConfigurationEditor (String configId) {
		return createConfigurationEditor(configId, null);
	}

	@Override
	public IParametersEditor createConfigurationEditor (String configId,
		IFilter existingFilter)
	{
		FilterConfiguration fc = configMap.get(configId);
		if ( fc == null ) return null;

		IFilter filter = instantiateFilter(fc, existingFilter);

		// Get the default parameters object
		IParameters params = filter.getParameters();
		if ( params == null ) {
			return null; // This filter does not have parameters
		}
		
		return createParametersEditor(params.getClass().getName());
	}

	@Override
	public FilterConfiguration getConfiguration (String configId) {
		return configMap.get(configId);
	}

	@Override
	public Iterator<FilterConfiguration> getAllConfigurations () {
		return configMap.values().iterator();
	}
	
	@Override
	public List<FilterConfiguration> getMimeConfigurations (String mimeType) {
		ArrayList<FilterConfiguration> list = new ArrayList<FilterConfiguration>();
		for ( FilterConfiguration config : configMap.values() ) {
			if ( config.mimeType != null ) {
				if ( config.mimeType.equals(mimeType) ) {
					list.add(config);
				}
			}
		}
		return list;
	}

	@Override
	public List<FilterConfiguration> getFilterConfigurations(String filterClass) {
		ArrayList<FilterConfiguration> list = new ArrayList<FilterConfiguration>();
		for ( FilterConfiguration config : configMap.values() ) {
			if ( config.filterClass != null ) {
				if ( config.filterClass.equals(filterClass) ) {
					list.add(config);
				}
			}
		}
		return list;
	}

	@Override
	public FilterConfiguration getDefaultConfiguration (String mimeType) {
		for ( FilterConfiguration config : configMap.values() ) {
			if ( config.mimeType != null ) {
				if ( config.mimeType.equals(mimeType) ) {
					return config;
				}
			}
		}
		return null;
	}
	
	@Override
	public FilterConfiguration getDefaultConfigurationFromExtension (String ext) {
		String tmp = ext.toLowerCase() + ";";
		for ( FilterConfiguration config : configMap.values() ) {
			if ( config.extensions != null ) {
				if ( config.extensions.indexOf(tmp) > -1 ) {
					return config;
				}
			}
		}
		return null;
	}

	@Override
	public void removeConfiguration (String configId) {
		configMap.remove(configId);
	}

	@Override
	public void removeConfigurations (String filterClass) {
		Entry<String, FilterConfiguration> entry;
		Iterator<Entry<String, FilterConfiguration>> iter = configMap.entrySet().iterator();
		while ( iter.hasNext() ) {
			entry = iter.next();
			if ( entry.getValue().filterClass.equals(filterClass) ) {
				iter.remove();
			}
		}
	}

	@Override
	public IParameters getCustomParameters (FilterConfiguration config) {
		return getCustomParameters(config, null);
	}

	/**
	 * Gets the parameters for a given custom filter configuration. This
	 * default implementation gets the custom data from a file located
	 * in the current directory at the time the method is called. 
	 */
	@Override
	public IParameters getCustomParameters (FilterConfiguration config,
		IFilter existingFilter)
	{
		// Instantiate a filter (or re-use one)
		IFilter filter = instantiateFilter(config, existingFilter);

		// Get the default parameters object
		IParameters params = filter.getParameters();
		if ( params == null ) {
			return null; // This filter does not have parameters
		}
		
		
		// Load the provided parameter file
		// In this implementation the file is stored in a given directory
		if (config.parametersLocation != null) {
			File file = new File(customParmsDir + config.parametersLocation);
		    params.load(Util.URItoURL(file.toURI()), false);
		} else if (config.parameters != null) {
			params.fromString(config.parameters.toString());
		}
		
		return params;
	}

	@Override
	public void deleteCustomParameters (FilterConfiguration config) {
		// In this implementation the file is stored in a given directory
		File file = new File(customParmsDir + config.parametersLocation);
		file.delete();
		config.parameters = null;
	}

	@Override
	public void saveCustomParameters (FilterConfiguration config,
		IParameters params)
	{
		File file;
		if (config.parametersLocation != null) {
			// In this implementation the file is stored in a given directory
			file = new File(customParmsDir + config.parametersLocation);
			params.save(file.getAbsolutePath());
		} else if (config.parameters != null) {
			// use the configId as the file name
			file = new File(customParmsDir + config.configId);
			params.save(file.getAbsolutePath());
		}
	}

	@Override
	public FilterConfiguration createCustomConfiguration (FilterConfiguration baseConfig) {
		// Create the new configuration and set its members as a copy of the base
		FilterConfiguration newConfig = new FilterConfiguration();
		String[] res = splitFilterFromConfiguration(baseConfig.configId);
		if ( res == null ) { // Cannot create the configuration because of ID 
			return null;
		}
		
		newConfig.custom = true;
		if ( res[1] == null ) {
			newConfig.configId = String.format("%s%ccopy-of-default",
				res[0], CONFIGFILE_SEPARATOR);
		}
		else {
			newConfig.configId = String.format("%s%ccopy-of-%s",
				res[0], CONFIGFILE_SEPARATOR, res[1]);
		}
		
		newConfig.classLoader = baseConfig.classLoader;
		newConfig.name = String.format(newConfig.configId);
		newConfig.description = "";
		newConfig.filterClass = baseConfig.filterClass;
		newConfig.mimeType = baseConfig.mimeType;
		newConfig.parametersLocation = newConfig.configId + CONFIGFILE_EXT;
		newConfig.parameters = baseConfig.parameters;
		
		// Instantiate a filter and set the new parameters based on the base ones
		IFilter filter = instantiateFilter(baseConfig, null);
		IParameters baseParams = getParameters(baseConfig, filter);
		if ( baseParams == null ) { // Filter without parameters
			return null;
		}
		IParameters newParams = filter.getParameters();
		newParams.fromString(baseParams.toString());
		// Make sure to reset the path, the save function should set it
		newParams.setPath(null);
		return newConfig;
	}	

	@Override
	public void clearConfigurations (boolean customOnly) {
		if ( customOnly ) {
			Entry<String, FilterConfiguration> entry;
			Iterator<Entry<String, FilterConfiguration>> iter = configMap.entrySet().iterator();
			while ( iter.hasNext() ) {
				entry = iter.next();
				if ( entry.getValue().custom ) {
					iter.remove();
				}
			}
		}
		else {
			configMap.clear();
		}
	}

	/**
	 * Gets the directory where the custom configuration files are stored.
	 * @return the directory where the custom configuration files are stored.
	 */
	public String getCustomConfigurationsDirectory () {
		return customParmsDir;
	}
	
	/**
	 * Sets the directory where the custom configuration files are stored.
	 * You should call {@link #updateCustomConfigurations()} after this to
	 * update the list of the custom configurations in this mapper.
	 * @param dir the new directory where the custom configuration files are stored.
	 */
	public void setCustomConfigurationsDirectory (String dir) {
		customParmsDir = dir;
		if ( !dir.endsWith(File.separator) ) {
			customParmsDir += File.separator;
		}
	}
	
	public void addCustomConfiguration (String configId, IParameters parameters) {
		FilterConfiguration fc;
		fc = new FilterConfiguration();
		fc.custom = true;
		fc.configId = configId;
		
		// Get the filter
		String[] res = splitFilterFromConfiguration(fc.configId);
		if ( res == null ) { // Cannot found the filter in the ID
			LOGGER.error("Cannot find filter ID in: {}. Cannot add configuration", configId);
			return;
		}
		// Create the filter (this assumes the base-name is the default config ID)
		tmpFilter = createFilter(res[0], tmpFilter);
		if ( tmpFilter == null ) { 
			LOGGER.error("Cannot find filter with ID: {}. Cannot add configuration", res[0]);
			return;
		}
		
		// Set the data
		//fc.classLoader = (URLClassLoader) tmpFilter.getClass().getClassLoader();
		fc.parametersLocation = fc.configId + CONFIGFILE_EXT;
		fc.filterClass = tmpFilter.getClass().getName();
		fc.mimeType = tmpFilter.getMimeType();
		fc.description = "Configuration "+fc.configId; // Temporary
		fc.name = fc.configId; // Temporary 
		fc.parameters = parameters;
		addConfiguration(fc);
	}
	
	public void addCustomConfiguration (String configId) {
		addCustomConfiguration(configId, null);
	}
	
	/**
	 * Updates the custom configurations for this mapper. This should
	 * be called if the custom configurations directory has changed.
	 */
	public void updateCustomConfigurations () {
		File dir = new File(customParmsDir);
		String res[] = dir.list(new DefaultFilenameFilter(CONFIGFILE_EXT));
		clearConfigurations(true); // Only custom configurations
		if ( res == null ) return;
		
		for ( int i=0; i<res.length; i++ ) {
			addCustomConfiguration(Util.getFilename(res[i], false));
		}
	}

	/**
	 * Instantiate a filter from a given configuration, trying to re-use an existing one.
	 * @param config the configuration corresponding to the filter to load.
	 * @param existingFilter an optional existing filter we can try to reuse.
	 * @return the instance of the requested filter, or null if an error occurred.
	 * @throws OkapiFilterCreationException if the filter could not be instantiated.
	 */
	protected IFilter instantiateFilter (FilterConfiguration config,
		IFilter existingFilter)
	{
		IFilter filter = null;
		if ( existingFilter != null ) {
			if ( config.filterClass.equals(existingFilter.getClass().getName()) ) {
				filter = existingFilter;
			}
		}
		if ( filter == null ) {
			try {
				
				// If config has no classLoader, attempt to query from known configs.
				if ( config.classLoader == null ) {
					String[] res = splitFilterFromConfiguration(config.configId);
					config.classLoader = configMap.containsKey(res[0]) ? configMap.get(res[0]).classLoader : null;
				}
				
				if ( config.classLoader == null ) {
					filter = (IFilter)Class.forName(config.filterClass).newInstance();
				}
				else {
					filter = (IFilter)Class.forName(config.filterClass, true, config.classLoader).newInstance();					
				}
				filter.setFilterConfigurationMapper(this);
			}
			catch ( InstantiationException e ) {
				throw new OkapiFilterCreationException(
					String.format("Cannot instantiate a filter from the configuration '%s'", config.configId), e);
			}
			catch ( IllegalAccessException e ) {
				throw new OkapiFilterCreationException(
					String.format("Cannot instantiate a filter from the configuration '%s'", config.configId), e);
			}
			catch ( ClassNotFoundException e ) {
				throw new OkapiFilterCreationException(
					String.format("Cannot instantiate a filter from the configuration '%s'", config.configId), e);
			}
		}
		return filter;
	}

	@Override
	public List<FilterInfo> getFiltersInfo () {
		Collections.sort(filters); // Sort before returning
		return filters;
	}

	private String addConfigurations (String filterClass, URLClassLoader classLoader) {
		// Instantiate the filter to get the available configurations
		IFilter filter = null;
		try {
			if ( classLoader == null ) {
				filter = (IFilter)Class.forName(filterClass).newInstance();
			}
			else {
				filter = (IFilter)Class.forName(filterClass, true, classLoader).newInstance();
			}
			filter.setFilterConfigurationMapper(this);
		}
		catch ( InstantiationException e ) {
			LOGGER.warn(String.format("Cannot instantiate the filter '%s'. (InstantiationException)", filterClass));
			return null;
		}
		catch ( IllegalAccessException e ) {
			LOGGER.warn(String.format("Cannot instantiate the filter '%s'. (IllegalAccessException)", filterClass));
			return null;
		}
		catch ( ClassNotFoundException e ) {
			// Make this a debug log statement as this happens all the time and clutters our logs
			LOGGER.debug(String.format("Cannot instantiate the filter '%s'. (ClassNotFoundException)", filterClass));
			return null;
		}
		
		// Add the filter to the list
		FilterInfo info = new FilterInfo();
		info.className = filterClass;
		info.name = filter.getName();
		info.displayName = filter.getDisplayName();
		filters.add(info);
		
		// Get the available configurations for this filter
		List<FilterConfiguration> list = filter.getConfigurations();
		if (( list == null ) || ( list.size() == 0 )) {
			LOGGER.warn(String.format("No configuration provided for '%s'", filterClass));
			return null;
		}
		// Add the configurations to the mapper
		for ( FilterConfiguration config : list ) {
			if ( config.filterClass == null ) {
				LOGGER.warn(String.format("Configuration without filter class name in '%s'", config.toString()));
				config.filterClass = filterClass;
			}
			if ( config.name == null ) {
				LOGGER.warn(String.format("Configuration without name in '%s'", config.toString()));
				config.name = config.toString();
			}
			if ( config.description == null ) {
				LOGGER.warn(String.format("Configuration without description in '%s'", config.toString()));
				config.description = config.toString();
			}
			config.classLoader = classLoader;
			configMap.put(config.configId, config);
		}

		// Get the class name of the parameters if any is available
		// this is returned as information
		IParameters params = filter.getParameters();
		if ( params == null ) {
			return null; // This filter does not have parameters
		}
		return params.getClass().getName();
	}

}

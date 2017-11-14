/*===========================================================================
  Copyright (C) 2008 Jim Hargrave
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

package net.sf.okapi.filters.abstractmarkup.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.sf.okapi.common.exceptions.OkapiException;

import org.yaml.snakeyaml.Yaml;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class YamlConfigurationReader implements AutoCloseable {
	private static final String REGEX_META_CHARS_REGEX = "[\\(\\[\\{\\^\\$\\|\\]\\}\\)\\?\\*\\+]+";
	private static final Pattern REGEX_META_CHARS_PATTERN = Pattern.compile(REGEX_META_CHARS_REGEX);

	private boolean preserveWhitespace;
	private Yaml yaml;
	private Map<String, Object> config;
	private Map<String, Object> elementRules;
	private Map<String, Object> attributeRules;
	private Map<String, Object> elementRegexRules;
	private Map<String, Pattern> elementCompiledRegexRules;
	private Map<String, Object> attributeRegexRules;
	private Map<String, Pattern> attributeCompiledRegexRules;
	private InputStreamReader reader;

	public boolean isPreserveWhitespace() {
		return preserveWhitespace;
	}

	public void setPreserveWhitespace(boolean preserveWhitespace) {
		this.preserveWhitespace = preserveWhitespace;
	}

	/**
	 * Default Tagged Configuration
	 */
	public YamlConfigurationReader() {
		yaml = new Yaml();
		config = (Map) yaml.load("collapse_whitespace: false\nassumeWellformed: true");
		initialize();
	}

	public YamlConfigurationReader(URL configurationPathAsResource) {
		try {
			reader = new InputStreamReader(configurationPathAsResource.openStream(), Charset.forName("UTF-8"));
			yaml = new Yaml();
			config = (Map) yaml.load(reader);
			initialize();
		} catch (FileNotFoundException e) {
			throw new OkapiException(e);
		} catch (IOException e) {
			throw new OkapiException(e);
		}
	}

	public YamlConfigurationReader(File configurationFile) {
		try {
			reader = new InputStreamReader(new FileInputStream(configurationFile), Charset.forName("UTF-8"));
			yaml = new Yaml();
			config = (Map) yaml.load(reader);
			initialize();
		} catch (FileNotFoundException e) {
			throw new OkapiException(e);
		}
	}

	public YamlConfigurationReader(String configurationScript) {
		yaml = new Yaml();
		config = (Map) yaml.load(configurationScript);
		initialize();
	}
	
	private void initialize() {
		elementRules = new HashMap<String, Object>();
		attributeRules = new HashMap<String, Object>();
		Map<String, Object> er = (Map<String, Object>) config.get("elements");
		Map<String, Object> ar = (Map<String, Object>) config.get("attributes");
		
		if (er != null) {
			elementRules = er;
		}
		if (ar != null) {
			attributeRules = ar;
		}
		
		elementRegexRules = new HashMap<String, Object>();
		attributeRegexRules = new HashMap<String, Object>();
		findRegexRules();
		compileRegexRules();
	}

	@Override
	public String toString() {
		// FIXME: If rules are added after the fact this is not up to date
		return yaml.dump(config);
	}

	/**
	 * Find element or attribute rules
	 */
	public List<Map> getRules(String ruleName) {
		List<Map> rules = new LinkedList<Map>();
		Map rule = getElementRule(ruleName);
		if (rule != null) {
			rules.add(rule);
		}
		rule = getAttributeRule(ruleName);
		if (rule != null) {
			rules.add(rule);
		}
		
		return rules;
	}

	/*
	 * Find element rules only (including regex)
	 */
	public Map getElementRule(String ruleName) {
		Map rule = (Map)elementRules.get(ruleName);

		// check our element regex patterns
		if (rule == null && !elementRegexRules.isEmpty()) {
			for (String r : elementRegexRules.keySet()) {
				Matcher m = elementCompiledRegexRules.get(r).matcher(ruleName);
				if (m.matches()) {
					rule = (Map) elementRegexRules.get(r);
				}
			}
		}
		return rule;
	}

	/**
	 * Non regex element rules only
	 * @param ruleName rule name (aka tag name)
	 * @return true if there is a matched non-regex rule
	 */
	public Map getNonRegexElementRule(String ruleName) {
		return (Map)elementRules.get(ruleName);
	}
	
	/*
	 * Find regex element rules only
	 */
	public Map getRegexElementRule(String ruleName) {
		Map rule = null;
		// check our element regex patterns
		if (rule == null && !elementRegexRules.isEmpty()) {
			for (String r : elementRegexRules.keySet()) {
				Matcher m = elementCompiledRegexRules.get(r).matcher(ruleName);
				if (m.matches()) {
					rule = (Map) elementRegexRules.get(r);
				}
			}
		}
		return rule;
	}

	/*
	 * Find attribute rules only (including regex)
	 */
	public Map getAttributeRule(String ruleName) {
		Map rule = (Map)attributeRules.get(ruleName);

		// check our element regex patterns
		if (rule == null && !attributeRegexRules.isEmpty()) {
			for (String r : attributeRegexRules.keySet()) {
				Matcher m = attributeCompiledRegexRules.get(r).matcher(ruleName);
				if (m.matches()) {
					rule = (Map) attributeRegexRules.get(r);
				}
			}
		}
		return rule;
	}

	/*
	 * Find attribute rules only (including regex)
	 */
	public Map getRegexAttributeRule(String ruleName) {
		Map rule = null;

		// check our element regex patterns
		if (rule == null && !attributeRegexRules.isEmpty()) {
			for (String r : attributeRegexRules.keySet()) {
				Matcher m = attributeCompiledRegexRules.get(r).matcher(ruleName);
				if (m.matches()) {
					rule = (Map) attributeRegexRules.get(r);
				}
			}
		}
		return rule;
	}

	public Object getProperty(String property) {
		return config.get(property);
	}

	public void addProperty(String property, boolean value) {
		config.put(property, value);
	}

	public void addProperty(String property, String value) {
		config.put(property, value);
	}

	public void addElementRule(String ruleName, Map rule) {
		elementRules.putAll(rule);
	}

	public void addAttributeRule(String ruleName, Map rule) {
		attributeRules.putAll(rule);
	}

	public void clearRules() {
		config.clear();
		elementRules.clear();
		attributeRules.clear();
		elementRegexRules.clear();
		elementCompiledRegexRules.clear();
		attributeRegexRules.clear();
		attributeCompiledRegexRules.clear();
	}

	private void findRegexRules() {
		for (String r : elementRules.keySet()) {
			try {
				Matcher m = REGEX_META_CHARS_PATTERN.matcher(r);
				if (m.find()) {
					elementRegexRules.put(r, elementRules.get(r));
				}
			} catch (PatternSyntaxException e) {
				throw new IllegalConditionalAttributeException(e);
			}
		}

		for (String r : attributeRules.keySet()) {
			try {
				Matcher m = REGEX_META_CHARS_PATTERN.matcher(r);
				if (m.find()) {
					attributeRegexRules.put(r, attributeRules.get(r));
				}
			} catch (PatternSyntaxException e) {
				throw new IllegalConditionalAttributeException(e);
			}
		}
	}

	private void compileRegexRules() {
		if (!elementRegexRules.isEmpty()) {
			elementCompiledRegexRules = new HashMap<String, Pattern>();
			for (String r : elementRegexRules.keySet()) {
				Pattern compiledRegex = Pattern.compile(r);
				elementCompiledRegexRules.put(r, compiledRegex);
			}
		}

		if (!attributeRegexRules.isEmpty()) {
			attributeCompiledRegexRules = new HashMap<String, Pattern>();
			for (String r : attributeRegexRules.keySet()) {
				Pattern compiledRegex = Pattern.compile(r);
				attributeCompiledRegexRules.put(r, compiledRegex);
			}
		}
	}

	public 	Map<String, Object> getAttributeRules () {
		return attributeRules;
	}

	public 	Map<String, Object> getElementRules () {
		return elementRules;
	}

	@Override
	public void close() throws Exception {
		reader.close();
	}
}

/*===========================================================================
  Copyright (C) 2016-2017 by the Okapi Framework contributors
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

package net.sf.okapi.filters.openxml;

import java.util.List;

/**
 * Provides a clarifiable attribute.
 */
class ClarifiableAttribute {

    private String prefix;
    private String name;
    private List<String> values;

    ClarifiableAttribute(String prefix, String name, List<String> values) {
        this.prefix = prefix;
        this.name = name;
        this.values = values;
    }

    String getPrefix() {
        return prefix;
    }

    String getName() {
        return name;
    }

    List<String> getValues() {
        return values;
    }
}

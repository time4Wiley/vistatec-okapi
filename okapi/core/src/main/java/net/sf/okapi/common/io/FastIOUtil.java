/*===========================================================================
  Copyright (C) 2010-2014 by the Okapi Framework contributors
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

package net.sf.okapi.common.io;

public class FastIOUtil {
	
	/*
	 * Ensures that a range given by an offset and a length fits an array of
	 * given length.
	 * 
	 * <P>This method may be used whenever an array range check is needed. *
	 */
	public static  void ensureOffsetLength(final int arrayLength, final int offset,
			final int length) {
		if (offset < 0)
			throw new ArrayIndexOutOfBoundsException("Offset (" + offset
					+ ") is negative");
		if (length < 0)
			throw new IllegalArgumentException("Length (" + length
					+ ") is negative");
		if (offset + length > arrayLength)
			throw new ArrayIndexOutOfBoundsException("Last index ("
					+ (offset + length) + ") is greater than array length ("
					+ arrayLength + ")");
	}
}

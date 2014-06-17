/*
 * (C) Copyright IBM Corp. 2014
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
 * implied. See the License for the specific language governing 
 * permissions and limitations under the License.
 */
package com.ibm.portal.samples.mail.helper;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

/**
 * Helper base class to support a map like access to indexed methods on beans.
 * Strange that this does not exist as a J2EE utility class, yet. What are beans
 * supposed to do if they require indexed access to the properties?
 * 
 * @author cleue
 */
public abstract class AbstractJstlMap<K, V> extends AbstractMap<K, V> {

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#entrySet()
	 */
	@Override
	public final Set<Map.Entry<K, V>> entrySet() {
		throw new UnsupportedOperationException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#get(java.lang.Object)
	 */
	@Override
	public final V get(final Object key) {
		try {
			return getValue(key);
		} catch (final Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	/**
	 * Must be overridden and dispatched to the indexed getter
	 * 
	 * @param key
	 *            index
	 * @return the value
	 * @throws Exception
	 */
	protected abstract V getValue(final Object key) throws Exception;
}

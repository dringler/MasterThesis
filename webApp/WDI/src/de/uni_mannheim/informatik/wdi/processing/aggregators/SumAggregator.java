/** 
 *
 * Copyright (C) 2015 Data and Web Science Group, University of Mannheim, Germany (code@dwslab.de)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package de.uni_mannheim.informatik.wdi.processing.aggregators;

import de.uni_mannheim.informatik.wdi.processing.DataAggregator;

/**
 * @author Oliver Lehmberg (oli@dwslab.de)
 *
 */
public abstract class SumAggregator<KeyType, RecordType> implements DataAggregator<KeyType, RecordType, Integer> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public abstract Integer getValue(RecordType record);
	
	@Override
	public Integer aggregate(Integer previousResult,
			RecordType record) {
		if(previousResult==null) {
			return getValue(record);
		} else {
			return previousResult+getValue(record);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uni_mannheim.informatik.wdi.processing.DataAggregator#initialise(java.lang.Object)
	 */
	@Override
	public Integer initialise(KeyType keyValue) {
		return null;
	}
	
}

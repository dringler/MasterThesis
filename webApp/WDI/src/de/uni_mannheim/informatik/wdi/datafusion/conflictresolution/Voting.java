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
package de.uni_mannheim.informatik.wdi.datafusion.conflictresolution;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import de.uni_mannheim.informatik.wdi.model.Fusable;
import de.uni_mannheim.informatik.wdi.model.FusableValue;
import de.uni_mannheim.informatik.wdi.model.FusedValue;
import de.uni_mannheim.informatik.wdi.model.Matchable;

/**
 * Vote {@link ConflictResolutionFunction}: returns the most frequent value, in
 * case of two or more similar frequent values the first one in the list of
 * {@link FusableValue}s is returned.
 * 
 * @author Oliver Lehmberg (oli@dwslab.de)
 * 
 * @param <ValueType>
 * @param <RecordType>
 */
public class Voting<ValueType, RecordType extends Matchable & Fusable<SchemaElementType>, SchemaElementType> extends
		ConflictResolutionFunction<ValueType, RecordType, SchemaElementType> {

	@Override
	public FusedValue<ValueType, RecordType, SchemaElementType> resolveConflict(
			Collection<FusableValue<ValueType, RecordType, SchemaElementType>> values) {

		// determine the frequencies of all values
		Map<ValueType, Integer> frequencies = new HashMap<>();

		for (FusableValue<ValueType, RecordType, SchemaElementType> value : values) {
			Integer freq = frequencies.get(value.getValue());
			if (freq == null) {
				freq = 0;
			}
			frequencies.put(value.getValue(), freq + 1);
		}

		// find the most frequent value
		ValueType mostFrequent = null;

		for (ValueType value : frequencies.keySet()) {
			if (mostFrequent == null
					|| frequencies.get(value) > frequencies.get(mostFrequent)) {
				mostFrequent = value;
			}
		}

		FusedValue<ValueType, RecordType, SchemaElementType> result = new FusedValue<>(
				mostFrequent);

		// collect all original records with the most frequent value
		for (FusableValue<ValueType, RecordType, SchemaElementType> value : values) {
			if (value.getValue().equals(mostFrequent)) {
				result.addOriginalRecord(value);
			}
		}

		return result;
	}

}

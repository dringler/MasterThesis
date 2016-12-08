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
package de.uni_mannheim.informatik.wdi.datafusion.conflictresolution.numeric;

import java.util.Collection;

import de.uni_mannheim.informatik.wdi.datafusion.conflictresolution.ConflictResolutionFunction;
import de.uni_mannheim.informatik.wdi.model.Fusable;
import de.uni_mannheim.informatik.wdi.model.FusableValue;
import de.uni_mannheim.informatik.wdi.model.FusedValue;
import de.uni_mannheim.informatik.wdi.model.Matchable;

/**
 * Average {@link ConflictResolutionFunction}: Returns the average of all values
 * 
 * @author Oliver Lehmberg (oli@dwslab.de)
 * 
 * @param <RecordType>
 */
public class Average<RecordType extends Matchable & Fusable<SchemaElementType>, SchemaElementType> extends
		ConflictResolutionFunction<Double, RecordType, SchemaElementType> {

	@Override
	public FusedValue<Double, RecordType, SchemaElementType> resolveConflict(
			Collection<FusableValue<Double, RecordType, SchemaElementType>> values) {

		if (values.size() == 0) {
			return new FusedValue<>((Double) null);
		} else {

			double sum = 0.0;
			double count = 0.0;

			for (FusableValue<Double, RecordType, SchemaElementType> value : values) {
				sum += value.getValue();
				count++;
			}

			return new FusedValue<>(sum / count);

		}
	}

}
